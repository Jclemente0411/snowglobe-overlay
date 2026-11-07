"""Adversarial release fixtures: malformed artifacts must never become green approvals."""
import copy
import importlib.util
import json
import struct
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]


def load(name):
    spec = importlib.util.spec_from_file_location(name, SCRIPTS / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


native = load("verify_native_libraries")
manifest_check = load("verify_bundle")
evidence = load("verify_device_evidence")
CONFIG = {"optimizations": {"uncompressNativeLibraries": {"enabled": True, "alignment": "PAGE_ALIGNMENT_16K"}}}


def elf(elf_class=2, alignment=16384, address=0, offset=0, memory=0x8000, relro_end=None):
    header_size, entry_size = (64, 56) if elf_class == 2 else (52, 32)
    number = 1 if relro_end is None else 2
    data = bytearray(512)
    data[:7] = b"\x7fELF" + bytes((elf_class, 1, 1))
    fmt = "<HHIQQQIHHHHHH" if elf_class == 2 else "<HHIIIIIHHHHHH"
    struct.pack_into(fmt, data, 16, 3, 183 if elf_class == 2 else 40, 1, 0, header_size, 0, 0,
                     header_size, entry_size, number, 0, 0, 0)
    if elf_class == 2:
        struct.pack_into("<IIQQQQQQ", data, header_size, 1, 6, offset, address, address, 256, memory, alignment)
        if relro_end is not None:
            struct.pack_into("<IIQQQQQQ", data, header_size + entry_size, 0x6474E552, 4, 0, 0, 0, 0, relro_end, 1)
    else:
        struct.pack_into("<IIIIIIII", data, header_size, 1, offset, address, address, 256, memory, 6, alignment)
    return data


def padded_relro_elf(next_address=0x9FD0):
    """Model graphics-path's valid ARM64 padding; optionally move .bss into its protected page."""
    data = bytearray(0x2000)
    data[:7] = b"\x7fELF" + bytes((2, 1, 1))
    struct.pack_into("<HHIQQQIHHHHHH", data, 16, 3, 183, 1, 0, 64, 0, 0, 64, 56, 4, 0, 0, 0)
    headers = (
        (1, 5, 0, 0, 0, 0x1C40, 0x1C40, 0x4000),
        (1, 6, 0x1C40, 0x5C40, 0x5C40, 0x390, 0x390, 0x4000),
        (1, 6, next_address % 0x4000, next_address, next_address, 0, 0x10, 0x4000),
        (0x6474E552, 4, 0x1C40, 0x5C40, 0x5C40, 0x390, 0x3C0, 1),
    )
    for index, header in enumerate(headers):
        struct.pack_into("<IIQQQQQQ", data, 64 + index * 56, *header)
    return data


class NativeLibraryVerificationTest(unittest.TestCase):
    def verify(self, data):
        return native.verify_elf(data, "base/lib/arm64-v8a/libfixture.so", "arm64-v8a")

    def test_16k_loads_and_aligned_relro_pass(self):
        self.assertEqual((1, True), self.verify(elf(relro_end=0x4000)))

    def test_relro_suffix_without_writable_tail_passes(self):
        self.verify(elf(memory=0x1000, relro_end=0x1000))

    def test_unaligned_relro_with_writable_tail_fails(self):
        with self.assertRaisesRegex(ValueError, "RELRO end"):
            self.verify(elf(relro_end=0x1000))

    def test_relro_padding_past_load_end_is_safe_when_next_writable_page_is_separate(self):
        self.assertEqual((3, True), self.verify(padded_relro_elf()))

    def test_padded_relro_cannot_cover_adjacent_writable_load(self):
        with self.assertRaisesRegex(ValueError, "RELRO end.*writable data"):
            self.verify(padded_relro_elf(next_address=0x6FD0))

    def test_unaligned_relro_start_cannot_cover_preceding_writable_data(self):
        data = elf(relro_end=0x4000)
        struct.pack_into("<Q", data, 64 + 56 + 16, 0x1000)
        struct.pack_into("<Q", data, 64 + 56 + 40, 0x3000)
        with self.assertRaisesRegex(ValueError, "RELRO start.*writable data"):
            self.verify(data)

    def test_4k_load_fails(self):
        with self.assertRaisesRegex(ValueError, "below 16 KB"):
            self.verify(elf(alignment=4096))

    def test_alignment_field_alone_cannot_hide_bad_mapping(self):
        with self.assertRaisesRegex(ValueError, "incongruent"):
            self.verify(elf(address=4096))
        with self.assertRaisesRegex(ValueError, "power of two"):
            self.verify(elf(alignment=16385))

    def test_truncated_and_zero_stride_program_headers_fail(self):
        with self.assertRaises(ValueError):
            self.verify(elf()[:80])
        data = elf()
        struct.pack_into("<H", data, 54, 0)
        with self.assertRaisesRegex(ValueError, "program-header size"):
            self.verify(data)

    def test_32bit_4k_library_does_not_falsely_fail_64bit_requirement(self):
        self.assertEqual((1, False), native.verify_elf(elf(elf_class=1, alignment=4096), "lib32.so", "armeabi-v7a"))

    def test_encoding_and_abi_mismatch_fail(self):
        data = elf()
        data[5] = 0
        with self.assertRaisesRegex(ValueError, "encoding"):
            self.verify(data)
        with self.assertRaisesRegex(ValueError, "class"):
            native.verify_elf(elf(elf_class=1), "lib.so", "arm64-v8a")

    def test_alignment_string_elsewhere_cannot_fake_packaging(self):
        config = {"note": "PAGE_ALIGNMENT_16K", "optimizations": {"uncompressNativeLibraries": {"enabled": True, "alignment": "PAGE_ALIGNMENT_4K"}}}
        with self.assertRaisesRegex(ValueError, "alignment"):
            native.verify_packaging(config)
        config = copy.deepcopy(CONFIG)
        config["optimizations"]["uncompressNativeLibraries"]["enabled"] = False
        with self.assertRaisesRegex(ValueError, "uncompressed"):
            native.verify_packaging(config)

    def test_python_optimization_cannot_disable_gate(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            with zipfile.ZipFile(folder / "invalid.aab", "w") as archive:
                archive.writestr("base/lib/arm64-v8a/libfixture.so", elf(alignment=4096))
            (folder / "config.json").write_text(json.dumps(CONFIG))
            result = subprocess.run([sys.executable, "-O", str(SCRIPTS / "verify_native_libraries.py"), str(folder / "invalid.aab"), str(folder / "config.json")], capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("below 16 KB", result.stderr)


class ManifestVerificationTest(unittest.TestCase):
    def setUp(self):
        self.meta = {"applicationId": "com.snowglobe.overlay", "versionCode": 100029, "versionName": "0.22.0", "targetSdk": 36}
        self.manifest = ET.parse(SCRIPTS.parent / "app/src/main/AndroidManifest.xml").getroot()
        # This fixture stands in for a merged manifest: source removal directives do not
        # become packaged permissions/components. The production verifier never ignores
        # a real compiled permission, even if an unexpected tools attribute is present.
        for parent in self.manifest.iter():
            for child in list(parent):
                if child.get("{http://schemas.android.com/tools}node") == "remove":
                    parent.remove(child)
        self.a = manifest_check.ANDROID
        self.manifest.set("package", self.meta["applicationId"])
        self.manifest.set(self.a + "versionCode", "100029")
        self.manifest.set(self.a + "versionName", "0.22.0")
        ET.SubElement(self.manifest, "uses-sdk", {self.a + "targetSdkVersion": "36"})

    def test_release_contract_passes(self):
        manifest_check.verify(self.meta, self.manifest)

    def test_sdk_qualified_internet_permission_fails(self):
        ET.SubElement(self.manifest, "uses-permission-sdk-23", {self.a + "name": "android.permission.INTERNET"})
        with self.assertRaisesRegex(ValueError, "INTERNET"):
            manifest_check.verify(self.meta, self.manifest)

    def test_transitive_network_state_permission_fails(self):
        for removal_marker in (False, True):
            with self.subTest(removal_marker=removal_marker):
                manifest = copy.deepcopy(self.manifest)
                attributes = {self.a + "name": "android.permission.ACCESS_NETWORK_STATE"}
                if removal_marker:
                    attributes["{http://schemas.android.com/tools}node"] = "remove"
                ET.SubElement(manifest, "uses-permission", attributes)
                with self.assertRaisesRegex(ValueError, "ACCESS_NETWORK_STATE"):
                    manifest_check.verify(self.meta, manifest)

    def test_transport_cannot_return_without_its_network_permission(self):
        ET.SubElement(self.manifest.find("application"), "service", {
            self.a + "name": "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery",
            self.a + "enabled": "false"})
        with self.assertRaisesRegex(ValueError, "diagnostic transport component"):
            manifest_check.verify(self.meta, self.manifest)

    def test_billing_permission_is_required_for_the_lifetime_upgrade(self):
        for permission in list(self.manifest.findall("uses-permission")):
            if permission.get(self.a + "name") == "com.android.vending.BILLING":
                self.manifest.remove(permission)
        with self.assertRaisesRegex(ValueError, "Billing permission"):
            manifest_check.verify(self.meta, self.manifest)

    def test_renamed_or_disabled_input_methods_cannot_return(self):
        for marker in ("permission", "action", "metadata"):
            with self.subTest(marker=marker):
                item = copy.deepcopy(self.manifest)
                service = ET.SubElement(item.find("application"), "service", {
                    self.a + "name": ".RenamedKeyboard", self.a + "enabled": "false"})
                if marker == "permission":
                    service.set(self.a + "permission", "android.permission.BIND_INPUT_METHOD")
                elif marker == "action":
                    intent = ET.SubElement(service, "intent-filter")
                    ET.SubElement(intent, "action", {self.a + "name": "android.view.InputMethod"})
                else:
                    ET.SubElement(service, "meta-data", {self.a + "name": "android.view.im"})
                with self.assertRaisesRegex(ValueError, "input method"):
                    manifest_check.verify(self.meta, item)

    def test_removed_keyboard_class_cannot_remain_as_an_ordinary_service(self):
        ET.SubElement(self.manifest.find("application"), "service", {
            self.a + "name": ".ChristmasKeyboardService"})
        with self.assertRaisesRegex(ValueError, "Removed keyboard service"):
            manifest_check.verify(self.meta, self.manifest)

    def test_debug_or_test_only_bundle_fails(self):
        for attribute in ("debuggable", "testOnly"):
            with self.subTest(attribute=attribute):
                item = copy.deepcopy(self.manifest)
                item.find("application").set(self.a + attribute, "true")
                with self.assertRaises(ValueError):
                    manifest_check.verify(self.meta, item)

    def test_tile_cannot_be_exported_without_its_system_permission(self):
        for service in self.manifest.find("application").findall("service"):
            if service.get(self.a + "name") == ".SnowGlobeTileService":
                del service.attrib[self.a + "permission"]
        with self.assertRaisesRegex(ValueError, "TileService must be system-protected"):
            manifest_check.verify(self.meta, self.manifest)

    def test_wrong_version_is_not_verified(self):
        self.manifest.set(self.a + "versionCode", "100027")
        with self.assertRaisesRegex(ValueError, "version code"):
            manifest_check.verify(self.meta, self.manifest)

    def test_missing_launcher_or_overlay_permission_fails(self):
        for element in self.manifest.findall("uses-permission"):
            if element.get(self.a + "name") == "android.permission.SYSTEM_ALERT_WINDOW":
                self.manifest.remove(element)
        with self.assertRaisesRegex(ValueError, "SYSTEM_ALERT_WINDOW"):
            manifest_check.verify(self.meta, self.manifest)


class WorkflowContractTest(unittest.TestCase):
    def test_only_central_release_workflow_uses_existing_upload_secrets(self):
        users = [path.name for path in (SCRIPTS.parent / ".github/workflows").glob("*.yml")
                 if "secrets.PLAY_UPLOAD_" in path.read_text()]
        self.assertEqual(["release-verify.yml"], sorted(users),
                         "Legacy signing workflows must not bypass the Android 15/17 gates")


class DeviceEvidenceVerificationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.folder = Path(self.directory.name)
        self.results = self.folder / "results"
        self.images = self.folder / "screenshots"
        self.results.mkdir()
        self.images.mkdir()
        self.suite = ET.Element("testsuite")
        for classname, name in sorted(evidence.REQUIRED_TESTS):
            ET.SubElement(self.suite, "testcase", {"classname": classname, "name": name})
        self.write_report()
        for name in evidence.REQUIRED_IMAGES:
            (self.images / name).write_bytes(b"\x89PNG\r\n\x1a\n" + bytes(24))

    def write_report(self):
        ET.ElementTree(self.suite).write(self.results / "TEST-fixture.xml")

    def test_required_regressions_and_images_pass(self):
        evidence.verify(self.results, self.images)

    def test_empty_or_skipped_run_fails(self):
        ET.SubElement(self.suite.find("testcase"), "skipped")
        self.write_report()
        with self.assertRaisesRegex(ValueError, "skipped test"):
            evidence.verify(self.results, self.images)
        (self.results / "TEST-fixture.xml").unlink()
        with self.assertRaisesRegex(ValueError, "no test cases"):
            evidence.verify(self.results, self.images)

    def test_skipped_noncritical_case_also_fails(self):
        case = ET.SubElement(self.suite, "testcase", {"classname": "other.Test", "name": "oneMoreCase"})
        ET.SubElement(case, "skipped")
        self.write_report()
        with self.assertRaisesRegex(ValueError, "skipped test"):
            evidence.verify(self.results, self.images)

    def test_partial_run_cannot_omit_a_declared_device_test(self):
        expected = evidence.REQUIRED_TESTS | {("com.snowglobe.overlay.ExtraTest", "declaredCase")}
        with self.assertRaisesRegex(ValueError, "declaredCase"):
            evidence.verify(self.results, self.images, expected)

    def test_declared_test_discovery_follows_current_suite_instead_of_stale_counts(self):
        sources = self.folder / "sources"
        sources.mkdir()
        (sources / "ExampleTest.java").write_text(
            "package example; public final class ExampleTest {\n"
            "@Test public void retainedCase() {}\n"
            "@Test(timeout=1000) public void addedCase() {}\n"
            "public void helper() {}\n}")
        self.assertEqual({("example.ExampleTest", "retainedCase"), ("example.ExampleTest", "addedCase")},
                         evidence.declared_tests(sources))

    def test_missing_snow_screenshot_fails(self):
        (self.images / "30-snow-after-keyboard.png").unlink()
        with self.assertRaisesRegex(ValueError, "30-snow-after-keyboard"):
            evidence.verify(self.results, self.images)

    def test_failed_report_cannot_hide_behind_zero_gradle_status(self):
        ET.SubElement(self.suite.find("testcase"), "failure")
        self.write_report()
        with self.assertRaisesRegex(ValueError, "failed test"):
            evidence.verify(self.results, self.images)


if __name__ == "__main__":
    unittest.main()
