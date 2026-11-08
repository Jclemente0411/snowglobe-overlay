#!/usr/bin/env bash
# Disposable CI devices only. Real same-certificate replacement of the delivered 100031 APK.
set -euo pipefail
: "${UPGRADE_BASELINE_APK:?Verified signed baseline APK required}"
: "${UPGRADE_CANDIDATE_APK:?Verified signed candidate APK required}"
: "${UPGRADE_PROBE_APK:?Same-certificate instrumentation APK required}"
evidence=upgrade-evidence
mkdir -p "$evidence"
bash scripts/wait_for_android_user.sh
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
probe() {
  local phase=$1 enabled=$2
  adb shell am instrument -w -e phase "$phase" -e enabled "$enabled" \
    com.snowglobe.overlay.test/com.snowglobe.overlay.UpgradeProbeInstrumentation \
    | tee "$evidence/$enabled-$phase.txt"
  grep -F "upgrade_result=PASS $phase enabled=$enabled" "$evidence/$enabled-$phase.txt"
  grep -F 'INSTRUMENTATION_CODE: -1' "$evidence/$enabled-$phase.txt"
}
for enabled in false true; do
  # Each scenario has its own baseline install. Never uninstall between seed and verify.
  adb uninstall com.snowglobe.overlay.test >/dev/null 2>&1 || true
  adb uninstall com.snowglobe.overlay >/dev/null 2>&1 || true
  adb install "$UPGRADE_BASELINE_APK" | tee "$evidence/$enabled-install-baseline.txt"
  adb install "$UPGRADE_PROBE_APK" | tee "$evidence/$enabled-install-probe.txt"
  adb shell appops set com.snowglobe.overlay SYSTEM_ALERT_WINDOW allow
  probe seed "$enabled"
  adb shell dumpsys package com.snowglobe.overlay > "$evidence/$enabled-package-before.txt"
  adb install -r "$UPGRADE_CANDIDATE_APK" | tee "$evidence/$enabled-install-update.txt"
  probe verify "$enabled"
  adb shell dumpsys package com.snowglobe.overlay > "$evidence/$enabled-package-after.txt"
  adb shell dumpsys window > "$evidence/$enabled-windows-after.txt"
  adb shell dumpsys gfxinfo com.snowglobe.overlay > "$evidence/$enabled-gfxinfo.txt"
  adb shell dumpsys meminfo com.snowglobe.overlay > "$evidence/$enabled-memory.txt"
done
adb logcat -b crash -d > "$evidence/crashes.txt"
python3 - <<'PY'
import json
from pathlib import Path
p=Path('upgrade-evidence')
for state in ('false','true'):
    for phase in ('seed','verify'):
        assert f'upgrade_result=PASS {phase} enabled={state}' in (p/f'{state}-{phase}.txt').read_text()
(p/'summary.json').write_text(json.dumps({'baselineVersionCode':100031,'candidateVersionCode':100032,
    'sameCertificateAdbReplacement':True,'pausedStatePreserved':True,'enabledStatePreserved':True,
    'visualSettingsAndSavedScenesPreserved':True,'realPlayDeliveryVerified':False,
    'purchaseVerified':False},indent=2)+'\n')
PY
