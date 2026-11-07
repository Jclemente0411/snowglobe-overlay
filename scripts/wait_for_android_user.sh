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
timeout 10s adb shell 'input keyevent KEYCODE_WAKEUP; input keyevent 82; wm dismiss-keyguard' || true
while [ "$SECONDS" -lt "$unlock_deadline" ]; do
  sample_timeout=$((unlock_deadline - SECONDS))
  if [ "$sample_timeout" -gt 10 ]; then sample_timeout=10; fi
  # One bounded ADB call avoids a long chain of timeouts when services disappear.
  sample=$(timeout "${sample_timeout}s" adb shell '
    boot=$(getprop sys.boot_completed)
    current_user=$(am get-current-user 2>/dev/null)
    user_state=""
    case "$current_user" in
      ""|*[!0-9]*) ;;
      *) user_state=$(am get-started-user-state "$current_user" 2>/dev/null) ;;
    esac
    printf "boot=%s\nuser=%s\nstate=%s\nactivity=%s\npackage=%s\nserver=%s\nsurface=%s\n" \
      "$boot" "$current_user" "$user_state" "$(service check activity)" \
      "$(service check package)" "$(pidof system_server)" "$(pidof surfaceflinger)"
  ' | tr -d '\r') || sample=''
  last_sample=$sample
  boot='' current_user='' user_state='' activity_service='' package_service='' server_pid='' surface_pid=''
  while IFS='=' read -r key value; do
    case "$key" in
      boot) boot=$value ;; user) current_user=$value ;; state) user_state=$value ;;
      activity) activity_service=$value ;; package) package_service=$value ;;
      server) server_pid=$value ;; surface) surface_pid=$value ;;
    esac
  done <<< "$sample"
  if [ "$boot" = 1 ] && [[ "$current_user" =~ ^[0-9]+$ ]] && [ "$user_state" = RUNNING_UNLOCKED ] &&
     [ "$activity_service" = 'Service activity: found' ] && [ "$package_service" = 'Service package: found' ] &&
     [[ "$server_pid" =~ ^[1-9][0-9]*$ && "$surface_pid" =~ ^[1-9][0-9]*$ ]]; then
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
