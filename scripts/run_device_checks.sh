#!/usr/bin/env bash
# Evidence comes from real emulator execution, not generated mockups.
set -euo pipefail
status=0
# A retry must not inherit successful reports or screenshots from an earlier run.
rm -rf qa-screenshots app/build/outputs/androidTest-results/connected app/build/reports/release-preflight
mkdir -p qa-screenshots
# Retain system failures even when readiness or APK installation fails before tests.
logcat_pid=''
capture_diagnostics() {
  local original_status=$1
  trap - EXIT
  set +e
  timeout 30s bash scripts/capture_emulator_host_diagnostics.sh device-finish || true
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
timeout 30s bash scripts/capture_emulator_host_diagnostics.sh device-start || true
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
timeout 15s adb shell rm -rf /sdcard/Android/data/com.snowglobe.overlay/files/screenshots
probe=tap-probe/build/outputs/apk/debug/tap-probe-debug.apk
if [ ! -s "$probe" ]; then
  echo '::error::The separate-app overlay and system-keyboard probe was not built'
  exit 1
fi
adb install -r "$probe"
# Fail early on the installed effects-only package and scene discovery, then run the complete suite.
# Keep preliminary evidence outside qa-screenshots and connected results so it
# cannot satisfy the full-suite gate or duplicate required screenshot names.
preflight_dir=app/build/reports/release-preflight
mkdir -p "$preflight_dir"
set +e
gradle --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8' \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  '-Pandroid.testInstrumentationRunnerArguments.class=com.snowglobe.overlay.EffectsOnlyPackageTest,com.snowglobe.overlay.ChristmasMagicTest#pixelSetupAndChristmasScenesAreDiscoverable' \
  :app:connectedDebugAndroidTest --rerun 2>&1 | tee "$preflight_dir/gradle.log"
preflight_pipeline=("${PIPESTATUS[@]}")
set -e
preflight_status=${preflight_pipeline[0]}
if [ "$preflight_status" -eq 0 ] && [ "${preflight_pipeline[1]}" -ne 0 ]; then preflight_status=1; fi
if [ -d app/build/outputs/androidTest-results/connected ]; then
  cp -a app/build/outputs/androidTest-results/connected "$preflight_dir/results" || true
fi
if [ -d app/build/reports/androidTests/connected ]; then
  cp -a app/build/reports/androidTests/connected "$preflight_dir/html" || true
fi
timeout 30s adb pull /sdcard/Android/data/com.snowglobe.overlay/files/screenshots "$preflight_dir/screenshots" || true
if [ "$preflight_status" -ne 0 ]; then
  echo '::error::Effects-only package and scene-discovery preflight failed; its reports and screenshots are preserved'
  exit "$preflight_status"
fi
# Restore the original fresh-install state; the preflight visits the Studio
# and can change local preferences. It must not seed the complete suite.
for test_package in com.snowglobe.overlay com.snowglobe.taptest; do
  clear_result=$(timeout 15s adb shell pm clear "$test_package" | tr -d '\r')
  if [ "$clear_result" != Success ]; then
    echo "::error::Could not reset CI test app $test_package: $clear_result"
    exit 1
  fi
done
rm -rf app/build/outputs/androidTest-results/connected app/build/reports/androidTests/connected
timeout 15s adb shell rm -rf /sdcard/Android/data/com.snowglobe.overlay/files/screenshots
gradle --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8' \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true :app:connectedDebugAndroidTest --rerun || status=$?
if ! timeout 30s adb pull /sdcard/Android/data/com.snowglobe.overlay/files/screenshots qa-screenshots; then
  echo '::error::UI screenshot evidence was not collected'
  if [ "$status" -eq 0 ]; then status=1; fi
fi
python3 scripts/verify_device_evidence.py app/build/outputs/androidTest-results/connected qa-screenshots || status=1
exit "$status"
