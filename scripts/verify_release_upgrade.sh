#!/usr/bin/env bash
# Exercises the archived release binaries. Does not rebuild, uninstall, clear data,
# access app-private files, or test Play's signing/delivery path.
set -Eeuo pipefail

: "${UPGRADE_ROOT:?Set UPGRADE_ROOT containing old/ and new/ artifact directories}"
: "${RUNNER_TEMP:?RUNNER_TEMP is required}"
: "${PLAY_KEY_ALIAS:?PLAY_KEY_ALIAS is required}"

PACKAGE=com.snowglobe.overlay
ACTIVITY="$PACKAGE/.StudioActivity"
OLD_IME="$PACKAGE/.ChristmasKeyboardService"
EVIDENCE="${UPGRADE_EVIDENCE_DIR:-$PWD/upgrade-evidence}"
WORK="$RUNNER_TEMP/snowglobe-release-upgrade"
BUNDLETOOL="$RUNNER_TEMP/bundletool.jar"
LOGCAT_PID=
STAGE=preflight
mkdir -p "$EVIDENCE" "$WORK"

fail() { printf '::error::%s\n' "$*" >&2; exit 1; }

capture() {
    local seconds="$1" name="$2" result=0
    shift 2
    STAGE="$name"
    if timeout --signal=TERM --kill-after=10s "${seconds}s" "$@" \
        >"$EVIDENCE/$name.stdout.txt" 2>"$EVIDENCE/$name.stderr.txt"; then
        result=0
    else
        result=$?
    fi
    printf '%s\n' "$result" >"$EVIDENCE/$name.exit-code.txt"
    if (( result != 0 )); then
        tail -n 50 "$EVIDENCE/$name.stdout.txt" "$EVIDENCE/$name.stderr.txt" >&2 || true
        fail "$name failed (exit $result); see upgrade-evidence."
    fi
}

finish() {
    local result=$?
    trap - EXIT
    set +e
    if [[ -n "$LOGCAT_PID" ]]; then
        kill "$LOGCAT_PID" 2>/dev/null
        wait "$LOGCAT_PID" 2>/dev/null
    fi
    timeout --kill-after=3s 15s adb shell dumpsys package "$PACKAGE" >"$EVIDENCE/final-package.txt" 2>&1
    timeout --kill-after=3s 15s adb shell dumpsys activity activities >"$EVIDENCE/final-activities.txt" 2>&1
    timeout --kill-after=3s 15s adb shell dumpsys input_method >"$EVIDENCE/final-input-method.txt" 2>&1
    timeout --kill-after=3s 15s adb exec-out screencap -p >"$EVIDENCE/final-screen.png" 2>"$EVIDENCE/final-screen.stderr.txt"
    python3 - "$EVIDENCE" "$result" "$STAGE" <<'PY'
import json, pathlib, sys
out, code, stage = pathlib.Path(sys.argv[1]), int(sys.argv[2]), sys.argv[3]
report = {
    "status": "passed" if code == 0 else "failed", "exitCode": code, "lastStage": stage,
    "scope": "Archived 0.21.6 to 0.22.0 device-specific split APK upgrade, package identity, launch and retired IME fallback",
    "limitations": ["APKs use the common upload key; this does not test Play app-signing or delivery.",
                    "No settings, saved-scene, purchase, or private-data retention assertion is made.",
                    "UID and firstInstallTime are package-continuity checks, not proof of all app data retention."]
}
for label in ("before", "after"):
    p = out / (label + "-package-summary.json")
    if p.exists():
        report[label] = json.loads(p.read_text())
(out / "upgrade-report.json").write_text(json.dumps(report, indent=2) + "\n")
PY
    exit "$result"
}
trap finish EXIT

for command in adb java python3 timeout; do command -v "$command" >/dev/null || fail "Missing command: $command"; done
for input in "$BUNDLETOOL" "$RUNNER_TEMP/upload-key.jks" "$RUNNER_TEMP/store-pass.txt" "$RUNNER_TEMP/key-pass.txt"; do
    [[ -s "$input" ]] || fail "Missing required input: $input"
done

# Recheck the payload hashes so an incorrectly wired artifact directory fails closed.
python3 - "$UPGRADE_ROOT" "$WORK" "$EVIDENCE" <<'PY'
import hashlib, json, pathlib, sys
root, work, evidence = map(pathlib.Path, sys.argv[1:])
expected = {"old": "bb5c88a8a55607c1b9083f03e32771c50094055df72b7ae4772a145f33742c77",
            "new": "226e92d2121d5a3cd6934b22e00fec7449643d11e50d298d4e26ef8ac126681b"}
report = {}
for label, digest in expected.items():
    found = list((root / label).rglob("*.aab"))
    if len(found) != 1:
        raise SystemExit(f"Expected exactly one {label} AAB; found {len(found)}")
    path = found[0].resolve()
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != digest:
        raise SystemExit(f"Unexpected {label} AAB SHA-256: {actual}")
    (work / (label + "-aab-path.txt")).write_text(str(path) + "\n")
    report[label] = {"filename": path.name, "sha256": actual}
(evidence / "input-bundles.json").write_text(json.dumps(report, indent=2) + "\n")
PY
OLD_AAB=$(<"$WORK/old-aab-path.txt")
NEW_AAB=$(<"$WORK/new-aab-path.txt")

capture 60 wait-for-device adb wait-for-device
capture 20 initial-packages adb shell pm list packages --user 0 "$PACKAGE"
if tr -d '\r' <"$EVIDENCE/initial-packages.stdout.txt" | grep -Fxq "package:$PACKAGE"; then
    fail "The emulator already contains $PACKAGE; a clean test emulator is required. No uninstall will be attempted."
fi
capture 30 device-spec java -jar "$BUNDLETOOL" get-device-spec --output="$WORK/device-spec.json"
cp "$WORK/device-spec.json" "$EVIDENCE/device-spec.json"
capture 15 android-fingerprint adb shell getprop ro.build.fingerprint
capture 15 android-sdk adb shell getprop ro.build.version.sdk
[[ "$(tr -d '\r\n' <"$EVIDENCE/android-sdk.stdout.txt")" == 37 ]] || fail "Expected Android API 37"
capture 15 page-size adb shell getconf PAGE_SIZE
capture 15 wake-screen adb shell input keyevent KEYCODE_WAKEUP
capture 15 dismiss-keyguard adb shell wm dismiss-keyguard

for label in old new; do
    if [[ "$label" == old ]]; then bundle="$OLD_AAB"; else bundle="$NEW_AAB"; fi
    capture 180 "build-$label-apks" java -jar "$BUNDLETOOL" build-apks \
        --bundle="$bundle" --output="$WORK/$label.apks" --overwrite \
        --device-spec="$WORK/device-spec.json" \
        --ks="$RUNNER_TEMP/upload-key.jks" --ks-key-alias="$PLAY_KEY_ALIAS" \
        --ks-pass="file:$RUNNER_TEMP/store-pass.txt" --key-pass="file:$RUNNER_TEMP/key-pass.txt"
    [[ ! -e "$WORK/$label-apks" ]] || fail "APK extraction directory already exists: $WORK/$label-apks"
    capture 60 "extract-$label-apks" java -jar "$BUNDLETOOL" extract-apks \
        --apks="$WORK/$label.apks" --device-spec="$WORK/device-spec.json" --output-dir="$WORK/$label-apks"
done

load_apks() {
    local label="$1"
    mapfile -d '' -t APKS < <(python3 - "$WORK/$label-apks" <<'PY'
import pathlib, sys
for p in sorted(pathlib.Path(sys.argv[1]).rglob("*.apk")):
    sys.stdout.buffer.write(str(p).encode() + b"\0")
PY
    )
    (( ${#APKS[@]} > 0 )) || fail "No $label device-specific APKs extracted"
    printf '%s\n' "${APKS[@]}" >"$EVIDENCE/$label-apk-paths.txt"
}

package_snapshot() {
    local label="$1" expected_version="$2"
    capture 20 "$label-package" adb shell dumpsys package "$PACKAGE"
    capture 20 "$label-uid" adb shell pm list packages -U --user 0 "$PACKAGE"
    python3 - "$EVIDENCE" "$label" "$PACKAGE" "$expected_version" <<'PY'
import json, pathlib, re, sys
root, label, package, expected = pathlib.Path(sys.argv[1]), *sys.argv[2:]
dump = (root / (label + "-package.stdout.txt")).read_text()
uid_dump = (root / (label + "-uid.stdout.txt")).read_text()
version = re.search(r"\bversionCode=(\d+)", dump)
installed = re.search(r"\bfirstInstallTime=([^\r\n]+)", dump)
line = next((s for s in uid_dump.splitlines() if re.match(r"^package:" + re.escape(package) + r"(?:\s|$)", s)), "")
uid = re.search(r"\buid:(\d+)", line)
if not version or version.group(1) != expected or not installed or not uid:
    raise SystemExit(f"Cannot verify {label} package identity/version {expected}")
data = {"package": package, "versionCode": int(version.group(1)), "uid": int(uid.group(1)),
        "firstInstallTime": installed.group(1).strip()}
(root / (label + "-package-summary.json")).write_text(json.dumps(data, indent=2) + "\n")
if label == "after":
    previous = json.loads((root / "before-package-summary.json").read_text())
    for key in ("package", "uid", "firstInstallTime"):
        if data[key] != previous[key]:
            raise SystemExit(f"Upgrade changed {key}: {previous[key]} -> {data[key]}")
PY
}

launch_and_check() {
    local label="$1"
    capture 30 "$label-launch" adb shell am start -W -n "$ACTIVITY"
    tr -d '\r' <"$EVIDENCE/$label-launch.stdout.txt" | grep -Fxq 'Status: ok' || fail "$label activity launch did not report Status: ok"
    # A bounded observation period lets startup and package-replaced work settle.
    sleep 3
    capture 20 "$label-activities" adb shell dumpsys activity activities
    python3 - "$EVIDENCE/$label-activities.stdout.txt" <<'PY'
import pathlib, re, sys
lines = pathlib.Path(sys.argv[1]).read_text().splitlines()
if not any(re.search(r"(?:mResumedActivity|topResumedActivity|ResumedActivity)", line) and
           re.search(r"com\.snowglobe\.overlay/(?:\.StudioActivity|com\.snowglobe\.overlay\.StudioActivity)(?:\s|\})", line)
           for line in lines):
    raise SystemExit("StudioActivity is not the resumed activity")
PY
    capture 15 "$label-screen" adb exec-out screencap -p
    mv "$EVIDENCE/$label-screen.stdout.txt" "$EVIDENCE/$label-screen.png"
}

# Start at the newest log entry; do not erase existing emulator logs.
timeout --signal=TERM --kill-after=5s 600s adb logcat -b main -b system -b crash -v threadtime -T 1 \
    >"$EVIDENCE/upgrade-logcat.txt" 2>"$EVIDENCE/upgrade-logcat.stderr.txt" &
LOGCAT_PID=$!

load_apks old
capture 120 old-install adb install-multiple --no-streaming "${APKS[@]}"
tr -d '\r' <"$EVIDENCE/old-install.stdout.txt" | grep -Fxq 'Success' || fail "Old release installation did not report Success"
package_snapshot before 100028
launch_and_check before
capture 20 old-ime-list adb shell ime list -a -s
capture 20 old-ime-enable adb shell ime enable --user 0 "$OLD_IME"
capture 20 old-ime-select adb shell ime set --user 0 "$OLD_IME"
capture 15 old-ime-selected adb shell settings --user 0 get secure default_input_method
SELECTED=$(tr -d '\r\n' <"$EVIDENCE/old-ime-selected.stdout.txt")
[[ "$SELECTED" == "$OLD_IME" || "$SELECTED" == "$PACKAGE/$PACKAGE.ChristmasKeyboardService" ]] || fail "The old SnowGlobe IME was not selected"

load_apks new
# Exactly one update attempt. No uninstall, pm clear, downgrade allowance, or retry.
capture 120 new-install adb install-multiple -r --no-streaming "${APKS[@]}"
tr -d '\r' <"$EVIDENCE/new-install.stdout.txt" | grep -Fxq 'Success' || fail "New release upgrade did not report Success"
package_snapshot after 100029
launch_and_check after
capture 20 new-ime-list adb shell ime list -a -s
capture 15 new-ime-selected adb shell settings --user 0 get secure default_input_method
python3 - "$EVIDENCE" "$PACKAGE" <<'PY'
import json, pathlib, sys
root, package = pathlib.Path(sys.argv[1]), sys.argv[2]
methods = {s.strip() for s in (root / "new-ime-list.stdout.txt").read_text().splitlines() if s.strip()}
selected = (root / "new-ime-selected.stdout.txt").read_text().strip()
retired = {package + "/.ChristmasKeyboardService", package + "/" + package + ".ChristmasKeyboardService"}
if methods & retired:
    raise SystemExit("The retired SnowGlobe keyboard is still registered")
if selected in retired or selected in ("", "null") or selected not in methods:
    raise SystemExit(f"No registered fallback input method selected: {selected!r}")
(root / "ime-fallback.json").write_text(json.dumps({"retiredImeRemoved": True, "selectedFallback": selected}, indent=2) + "\n")
PY

# Keep a further bounded window for asynchronous launch/upgrade crashes.
sleep 5
kill -0 "$LOGCAT_PID" 2>/dev/null || fail "Logcat capture stopped before verification completed"
kill "$LOGCAT_PID" 2>/dev/null || true
wait "$LOGCAT_PID" 2>/dev/null || true
LOGCAT_PID=
STAGE=crash-check
python3 - "$EVIDENCE" "$PACKAGE" <<'PY'
import pathlib, re, sys
root, package = pathlib.Path(sys.argv[1]), sys.argv[2]
lines = (root / "upgrade-logcat.txt").read_text(errors="replace").splitlines()
if not lines:
    raise SystemExit("Logcat capture is empty; crash absence cannot be verified")
hits = []
for i, line in enumerate(lines):
    if "FATAL EXCEPTION" in line:
        context = lines[max(0, i - 2):i + 28]
        if any(re.search(r"\bProcess:\s*" + re.escape(package) + r"(?=[:,\s]|$)", s) for s in context):
            hits.extend(context)
    if re.search(r"(?:>>>\s*" + re.escape(package) + r"\s*<<<|\bCmdline:\s*" + re.escape(package) + r"(?=[:\s]|$))", line):
        hits.extend(lines[max(0, i - 8):i + 16])
(root / "matching-crashes.txt").write_text("\n".join(hits) + ("\n" if hits else ""))
if hits:
    raise SystemExit("SnowGlobe fatal crash found in upgrade logcat")
PY
STAGE=completed
printf 'Archived 0.21.6 -> 0.22.0 package upgrade and IME fallback passed. Evidence: %s\n' "$EVIDENCE"
