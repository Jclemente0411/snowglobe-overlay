"""Exercise the CI readiness gates against false boot completion and runtime restarts."""
import importlib.util
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("verify_android_avd", SCRIPTS / "verify_android_avd.py")
avd = importlib.util.module_from_spec(spec)
spec.loader.exec_module(avd)


class AvdMetadataTest(unittest.TestCase):
    def fixture(self, folder, target="android-37.0", image="android-37.0"):
        folder = Path(folder)
        device = folder / "test.avd"
        device.mkdir()
        (folder / "test.ini").write_text(f"target={target}\npath={device}\n")
        (device / "config.ini").write_text(f"image.sysdir.1=system-images/{image}/google_apis/x86_64/\n")
        return folder

    def test_integer_and_zero_minor_api_metadata_are_equivalent(self):
        with tempfile.TemporaryDirectory() as folder:
            path = self.fixture(folder, target="android-37")
            avd.verify(path, "test", "37.0", path / "evidence")
            self.assertTrue((path / "evidence/avd.ini").is_file())
        with tempfile.TemporaryDirectory() as folder:
            avd.verify(self.fixture(folder, "android-35", "android-35"), "test", "35")

    def test_android_zero_is_rejected_even_when_image_path_is_correct(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaisesRegex(ValueError, "Invalid Android API"):
                avd.verify(self.fixture(folder, "android-0"), "test", "37.0")

    def test_correct_target_cannot_hide_wrong_system_image(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaisesRegex(ValueError, "system image"):
                avd.verify(self.fixture(folder, image="android-35"), "test", "37.0")


READY = {
    "boot": "1", "user": "10", "state": "RUNNING_UNLOCKED",
    "activity": "Service activity: found", "package": "Service package: found",
    "server": "100", "surface": "200",
}
FAKE_ADB = '''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
base = Path(os.environ["FAKE_ANDROID_STATE"])
with (base / "commands.jsonl").open("a") as output:
    output.write(json.dumps(sys.argv[1:]) + "\\n")
if any("boot=%s" in arg for arg in sys.argv[1:]):
    counter = base / "sample-count"
    index = int(counter.read_text()) if counter.exists() else 0
    counter.write_text(str(index + 1))
    states = json.loads((base / "states.json").read_text())
    state = states[min(index, len(states) - 1)]
    if os.environ.get("FAKE_ANDROID_CHURN") == "1":
        state["server"] = str(100 + index)
    print("\\n".join(key + "=" + value for key, value in state.items()))
else:
    print("fixture diagnostic")
'''


class AndroidReadinessTest(unittest.TestCase):
    def setup_fixture(self, folder, states):
        path = Path(folder)
        (path / "adb").write_text(FAKE_ADB)
        (path / "adb").chmod(0o755)
        (path / "states.json").write_text(json.dumps(states))
        env = dict(os.environ)
        env.update(PATH=str(path) + os.pathsep + env["PATH"], FAKE_ANDROID_STATE=str(path),
                   SNOWGLOBE_BOOT_TIMEOUT_SECONDS="3", SNOWGLOBE_BOOT_STABLE_SECONDS="1",
                   SNOWGLOBE_BOOT_POLL_SECONDS="0.05")
        env.pop("SNOWGLOBE_AVD_NAME", None)
        env.pop("FAKE_ANDROID_CHURN", None)
        return path, env

    def run_wait(self, env):
        return subprocess.run(["bash", str(SCRIPTS / "wait_for_android_user.sh")],
                              env=env, text=True, capture_output=True, timeout=12)

    def test_boot_completed_does_not_accept_locked_foreground_user(self):
        locked = dict(READY, state="RUNNING_LOCKED", background="RUNNING_UNLOCKED")
        with tempfile.TemporaryDirectory() as folder:
            _, env = self.setup_fixture(folder, [locked])
            env["SNOWGLOBE_BOOT_TIMEOUT_SECONDS"] = "1"
            result = self.run_wait(env)
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)

    def test_restarting_system_server_never_satisfies_stable_gate(self):
        with tempfile.TemporaryDirectory() as folder:
            _, env = self.setup_fixture(folder, [READY])
            env["FAKE_ANDROID_CHURN"] = "1"
            env["SNOWGLOBE_BOOT_TIMEOUT_SECONDS"] = "2"
            result = self.run_wait(env)
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)

    def test_missing_services_then_stable_new_processes_can_recover(self):
        missing = dict(READY, activity="Service activity: not found", package="Service package: not found")
        recovered = dict(READY, server="350", surface="450")
        with tempfile.TemporaryDirectory() as folder:
            path, env = self.setup_fixture(folder, [missing, READY, recovered])
            result = self.run_wait(env)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("system_server=350, surfaceflinger=450", result.stdout)
            self.assertGreater(int((path / "sample-count").read_text()), 3)

    def test_failure_collects_system_diagnostics_without_changing_exit_status(self):
        with tempfile.TemporaryDirectory() as folder:
            path, env = self.setup_fixture(folder, [READY])
            scripts = path / "scripts"
            scripts.mkdir()
            shutil.copyfile(SCRIPTS / "run_device_checks.sh", scripts / "run_device_checks.sh")
            (scripts / "wait_for_android_user.sh").write_text("#!/usr/bin/env bash\nexit 23\n")
            result = subprocess.run(["bash", "scripts/run_device_checks.sh"], cwd=path, env=env,
                                    text=True, capture_output=True, timeout=15)
            self.assertEqual(23, result.returncode, result.stdout + result.stderr)
            commands = [json.loads(line) for line in (path / "commands.jsonl").read_text().splitlines()]
            self.assertTrue(any(command[:4] == ["logcat", "-b", "all", "-d"] for command in commands))
            self.assertTrue(any(command[:4] == ["logcat", "-b", "crash", "-d"] for command in commands))
            self.assertTrue((path / "qa-screenshots/boot-and-services.txt").is_file())


if __name__ == "__main__":
    unittest.main()
