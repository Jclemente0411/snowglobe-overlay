#!/usr/bin/env bash
# Evidence comes from real emulator execution, not generated mockups.
set -euo pipefail
status=0
# A retry must not inherit successful reports or screenshots from an earlier run.
rm -rf qa-screenshots app/build/outputs/androidTest-results/connected
mkdir -p qa-screenshots
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
adb logcat -d -s SnowGlobeWallpaper SnowGlobeEffects SnowGlobeTile AndroidRuntime > qa-screenshots/runtime.log || true
adb shell dumpsys window > qa-screenshots/windows.txt || true
adb shell dumpsys gfxinfo com.snowglobe.overlay > qa-screenshots/gfxinfo.txt || true
adb shell dumpsys meminfo com.snowglobe.overlay > qa-screenshots/memory.txt || true
adb shell dumpsys power > qa-screenshots/power-state.txt || true
python3 scripts/verify_device_evidence.py app/build/outputs/androidTest-results/connected qa-screenshots || status=1
exit "$status"
