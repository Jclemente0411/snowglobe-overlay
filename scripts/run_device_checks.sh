#!/usr/bin/env bash
# Evidence comes from real emulator execution, not generated mockups.
set -uo pipefail
status=0
mkdir -p qa-screenshots
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put secure show_ime_with_hard_keyboard 0
if [ -f tap-probe/build/outputs/apk/debug/tap-probe-debug.apk ]; then
  adb install -r tap-probe/build/outputs/apk/debug/tap-probe-debug.apk || exit 1
fi
gradle --no-daemon -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true :app:connectedDebugAndroidTest || status=$?
if ! adb pull /sdcard/Android/data/com.snowglobe.overlay/files/screenshots qa-screenshots; then
  echo '::error::UI screenshot evidence was not collected'
  if [ "$status" -eq 0 ]; then status=1; fi
fi
adb logcat -d -s SnowGlobeWallpaper SnowGlobeEffects SnowGlobeTile AndroidRuntime > qa-screenshots/runtime.log || true
adb shell dumpsys window > qa-screenshots/windows.txt || true
adb shell dumpsys gfxinfo com.snowglobe.overlay > qa-screenshots/gfxinfo.txt || true
adb shell dumpsys meminfo com.snowglobe.overlay > qa-screenshots/memory.txt || true
adb shell dumpsys power > qa-screenshots/power-state.txt || true
exit "$status"
