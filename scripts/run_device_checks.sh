#!/usr/bin/env bash
# Always collect the emulator evidence, while preserving every test failure.
set -uo pipefail
status=0
mkdir -p qa-screenshots
gradle --no-daemon -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true :app:connectedDebugAndroidTest || status=$?
if ! adb pull /sdcard/Android/data/com.snowglobe.overlay/files/screenshots qa-screenshots; then
  echo '::error::UI screenshot evidence was not collected'
  if [ "$status" -eq 0 ]; then status=1; fi
fi
adb logcat -d -s SnowGlobeWallpaper SnowGlobeEffects AndroidRuntime > qa-screenshots/runtime.log || true
exit "$status"
