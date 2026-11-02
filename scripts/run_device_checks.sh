#!/usr/bin/env bash
# Keep the test display awake and collect evidence even if a test fails.
set -uo pipefail
status=0
mkdir -p qa-screenshots
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put secure show_ime_with_hard_keyboard 0
gradle --no-daemon -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true :app:connectedDebugAndroidTest || status=$?
if ! adb pull /sdcard/Android/data/com.snowglobe.overlay/files/screenshots qa-screenshots; then
  echo '::error::UI screenshot evidence was not collected'
  if [ "$status" -eq 0 ]; then status=1; fi
fi
adb logcat -d -s SnowGlobeWallpaper SnowGlobeEffects AndroidRuntime > qa-screenshots/runtime.log || true
adb shell dumpsys window > qa-screenshots/windows.txt || true
exit "$status"
