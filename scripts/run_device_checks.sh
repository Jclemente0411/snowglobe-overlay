#!/usr/bin/env bash
# Evidence comes from real emulator execution, not generated mockups.
set -euo pipefail
status=0
# A retry must not inherit successful reports or screenshots from an earlier run.
rm -rf qa-screenshots app/build/outputs/androidTest-results/connected
mkdir -p qa-screenshots
# Retain system failures even when readiness or APK installation fails before tests.
logcat_pid=''
capture_diagnostics() {
  local original_status=$1
  trap - EXIT
  set +e
  if [ -n "$logcat_pid" ]; then kill "$logcat_pid" 2>/dev/null; wait "$logcat_pid" 2>/dev/null; fi
  timeout 20s adb logcat -b all -d -v threadtime > qa-screenshots/logcat-all.log 2>&1
  timeout 15s adb logcat -b crash -d -v threadtime > qa-screenshots/logcat-crash.log 2>&1
  timeout 15s adb logcat -d -s SnowGlobeWallpaper SnowGlobeEffects SnowGlobeTile AndroidRuntime > qa-screenshots/runtime.log 2>&1
  timeout 15s adb shell 'getprop sys.boot_completed; getprop init.svc.bootanim; cat /proc/uptime; am get-current-user; pidof system_server; pidof surfaceflinger; service check activity; service check package; dumpsys activity processes; df -h /data /sdcard; cat /proc/meminfo' > qa-screenshots/boot-and-services.txt 2>&1
  timeout 15s adb shell dumpsys window > qa-screenshots/windows.txt 2>&1
  timeout 15s adb shell dumpsys gfxinfo com.snowglobe.overlay > qa-screenshots/gfxinfo.txt 2>&1
  timeout 15s adb shell dumpsys meminfo com.snowglobe.overlay > qa-screenshots/memory.txt 2>&1
  timeout 15s adb shell dumpsys power > qa-screenshots/power-state.txt 2>&1
  git rev-parse HEAD > qa-screenshots/source-commit.txt 2>/dev/null
  exit "$original_status"
}
trap 'capture_diagnostics "$?"' EXIT
adb logcat -b all -v threadtime > qa-screenshots/logcat-live.log 2>&1 &
logcat_pid=$!
if [ -n "${SNOWGLOBE_AVD_NAME:-}" ]; then
  python3 scripts/verify_android_avd.py --avd-home "${ANDROID_AVD_HOME:-$HOME/.android/avd}" \
    --name "$SNOWGLOBE_AVD_NAME" --expected-api "${EXPECTED_ANDROID_API:?Expected API is required for AVD verification}" --evidence qa-screenshots
fi
bash scripts/wait_for_android_user.sh
actual_api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
if [ -n "${EXPECTED_ANDROID_API:-}" ] && [ "$actual_api" != "${EXPECTED_ANDROID_API%%.*}" ]; then
  echo "::error::Expected Android API ${EXPECTED_ANDROID_API%%.*}, received $actual_api"
  exit 1
fi
printf '%s\n' "$actual_api" > qa-screenshots/android-api.txt
adb shell getprop ro.build.fingerprint > qa-screenshots/build-fingerprint.txt
git rev-parse HEAD > qa-screenshots/source-commit.txt
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put secure show_ime_with_hard_keyboard 0
adb shell rm -rf /sdcard/Android/data/com.snowglobe.overlay/files/screenshots
probe=tap-probe/build/outputs/apk/debug/tap-probe-debug.apk
if [ ! -s "$probe" ]; then
  echo '::error::The separate-app keyboard/touch probe was not built'
  exit 1
fi
adb install -r "$probe"
gradle --no-daemon -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true :app:connectedDebugAndroidTest || status=$?
if ! adb pull /sdcard/Android/data/com.snowglobe.overlay/files/screenshots qa-screenshots; then
  echo '::error::UI screenshot evidence was not collected'
  if [ "$status" -eq 0 ]; then status=1; fi
fi
python3 scripts/verify_device_evidence.py app/build/outputs/androidTest-results/connected qa-screenshots || status=1
exit "$status"
