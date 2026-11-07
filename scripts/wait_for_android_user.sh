#!/usr/bin/env bash
# sys.boot_completed alone can be true while Android's system processes crash-loop.
set -euo pipefail
boot_timeout=${SNOWGLOBE_BOOT_TIMEOUT_SECONDS:-180}
stable_seconds=${SNOWGLOBE_BOOT_STABLE_SECONDS:-30}
poll_seconds=${SNOWGLOBE_BOOT_POLL_SECONDS:-2}
if ! [[ "$boot_timeout" =~ ^[1-9][0-9]*$ && "$stable_seconds" =~ ^[1-9][0-9]*$ && "$poll_seconds" =~ ^[0-9]+([.][0-9]+)?$ ]] ||
   [ "$boot_timeout" -lt "$stable_seconds" ] || [[ "$poll_seconds" =~ ^0+([.]0+)?$ ]]; then
  echo '::error::Invalid bounded Android readiness intervals'
  exit 2
fi
unlock_deadline=$((SECONDS + boot_timeout))
stable_since=-1
stable_processes=''
last_sample='No Android readiness sample received'
launcher_restarts=0
boot_command() {
  local command_limit=$1
  shift
  local remaining=$((unlock_deadline - SECONDS))
  if [ "$remaining" -le 0 ]; then return 124; fi
  if [ "$remaining" -gt "$command_limit" ]; then remaining=$command_limit; fi
  timeout "${remaining}s" "$@"
}
capture_startup_dialog() {
  local evidence="${SNOWGLOBE_BOOT_DIAGNOSTICS_DIR:-qa-screenshots/startup}/dialog-$launcher_restarts"
  mkdir -p "$evidence"
  printf '%s\n' "$last_sample" > "$evidence/readiness.txt"
  boot_command 10 adb shell dumpsys window > "$evidence/windows.txt" 2>&1 || true
  boot_command 10 adb logcat -b all -d -v threadtime > "$evidence/logcat-all.log" 2>&1 || true
  boot_command 5 adb logcat -b crash -d -v threadtime > "$evidence/logcat-crash.log" 2>&1 || true
  boot_command 10 adb exec-out screencap -p > "$evidence/screen.png" 2> "$evidence/screen-error.txt" || true
}
# emulator-runner already injects MENU during boot. Another early MENU can ANR
# the busy launcher; wake and dismiss keyguard without sending app input.
boot_command 10 adb shell 'input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard' || true
while [ "$SECONDS" -lt "$unlock_deadline" ]; do
  # One bounded ADB call avoids a long chain of timeouts when services disappear.
  sample=$(boot_command 10 adb shell '
    boot=$(getprop sys.boot_completed)
    current_user=$(am get-current-user 2>/dev/null)
    user_state=""
    case "$current_user" in
      ""|*[!0-9]*) ;;
      *) user_state=$(am get-started-user-state "$current_user" 2>/dev/null) ;;
    esac
    focus=$(dumpsys window | sed -n "s/^[[:space:]]*mCurrentFocus=//p" | head -n 1)
    printf "boot=%s\nuser=%s\nstate=%s\nactivity=%s\npackage=%s\nserver=%s\nsurface=%s\nfocus=%s\n" \
      "$boot" "$current_user" "$user_state" "$(service check activity)" \
      "$(service check package)" "$(pidof system_server)" "$(pidof surfaceflinger)" "$focus"
  ' | tr -d '\r') || sample=''
  last_sample=$sample
  boot='' current_user='' user_state='' activity_service='' package_service='' server_pid='' surface_pid='' focused_window=''
  while IFS='=' read -r key value; do
    case "$key" in
      boot) boot=$value ;; user) current_user=$value ;; state) user_state=$value ;;
      activity) activity_service=$value ;; package) package_service=$value ;;
      server) server_pid=$value ;; surface) surface_pid=$value ;;
      focus) focused_window=$value ;;
    esac
  done <<< "$sample"
  core_ready=false
  if [ "$boot" = 1 ] && [[ "$current_user" =~ ^[0-9]+$ ]] && [ "$user_state" = RUNNING_UNLOCKED ] &&
     [ "$activity_service" = 'Service activity: found' ] && [ "$package_service" = 'Service package: found' ] &&
     [[ "$server_pid" =~ ^[1-9][0-9]*$ && "$surface_pid" =~ ^[1-9][0-9]*$ ]]; then
    core_ready=true
  fi
  if [[ "$focused_window" = *'Application Not Responding:'* || "$focused_window" = *'Application Error:'* ]]; then
    anr_package=''
    if [[ "$focused_window" =~ Application[[:space:]]Not[[:space:]]Responding:[[:space:]]([^\}[:space:]]+)[[:space:]]*\}$ ]]; then
      anr_package=${BASH_REMATCH[1]}
    fi
    if [ "$anr_package" != com.google.android.apps.nexuslauncher ] || [ "$launcher_restarts" -ne 0 ]; then
      capture_startup_dialog
      echo "::error::Blocking startup error is not eligible for launcher recovery: $focused_window"
      exit 1
    fi
    if "$core_ready"; then
      capture_startup_dialog
      launcher_restarts=1
      echo 'Recovering the pre-install Pixel launcher ANR once; its diagnostics are retained'
      if ! restart=$(boot_command 20 adb shell "am force-stop --user $current_user com.google.android.apps.nexuslauncher && am start -W --user $current_user -a android.intent.action.MAIN -c android.intent.category.HOME -p com.google.android.apps.nexuslauncher" 2>&1); then
        printf '::error::Pixel launcher restart failed: %s\n' "$restart"
        exit 1
      fi
      if [[ "$restart" != *'Status: ok'* || "$restart" = *Error* || "$restart" = *Exception* ]]; then
        printf '::error::Pixel launcher did not restart successfully: %s\n' "$restart"
        exit 1
      fi
    fi
    stable_since=-1
    stable_processes=''
  elif "$core_ready" && [[ "$focused_window" = Window\{*\} ]]; then
    processes="$current_user:$server_pid:$surface_pid"
    if [ "$processes" != "$stable_processes" ] || [ "$stable_since" -lt 0 ]; then
      stable_since=$SECONDS
      stable_processes=$processes
    elif [ "$((SECONDS - stable_since))" -ge "$stable_seconds" ]; then
      printf 'Android user %s and system services remained ready for %s seconds (system_server=%s, surfaceflinger=%s)\n' \
        "$current_user" "$stable_seconds" "$server_pid" "$surface_pid"
      exit 0
    fi
  else
    stable_since=-1
    stable_processes=''
  fi
  sleep "$poll_seconds"
done
echo "::error::Android did not remain booted, unlocked and stable within ${boot_timeout} seconds"
printf '%s\n' "$last_sample" >&2
exit 1
