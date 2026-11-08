"""Reject empty/skipped instrumentation runs and missing release regression screenshots."""
import argparse
import re
import xml.etree.ElementTree as ET
from pathlib import Path

REQUIRED_TESTS = {
    ("com.snowglobe.overlay.FullScreenOverlayTest", "actualWindowAndSnowPixelsReachAllFourQuarters"),
    ("com.snowglobe.overlay.FullScreenOverlayTest", "rotatingAndOpeningKeyboardCannotPermanentlyShrinkSnowfall"),
    ("com.snowglobe.overlay.EffectsOnlyPackageTest", "installedAppDoesNotProvideAnyInputMethod"),
    ("com.snowglobe.overlay.EffectsOnlyPackageTest", "visualServicesKeepTheirSystemPermissionBoundaries"),
    ("com.snowglobe.overlay.StudioSmokeTest", "navigatesAndCapturesActualUi"),
    ("com.snowglobe.overlay.ChristmasMagicTest", "pixelSetupAndChristmasScenesAreDiscoverable"),
    ("com.snowglobe.overlay.OverlaySafetyTest", "separateAppReceivesNormalTapsAndPauseRestoresProtectedTaps"),
    ("com.snowglobe.overlay.WallpaperSmokeTest", "wallpaperIsSelectableAndDrawsAfterWake"),
    ("com.snowglobe.overlay.HolidayPlusUiTest", "holidayPlusAndRestoreAreDiscoverableWithoutCharging"),
    ("com.snowglobe.overlay.HolidayPlusUiTest", "wallpaperCollectionShowsTwoFreeAndTwoPlusDesigns"),
}
REQUIRED_IMAGES = (
    "01-home.png", "02-lights.png", "03-snow.png", "04-decor.png", "05-large-text.png",
    "06-holiday-plus.png", "07-wallpaper-collection.png",
    "27-full-height-snow.png", "28-snow-landscape.png",
    "29-snow-with-keyboard.png", "30-snow-after-keyboard.png",
    "31-holiday-trial-active.png",
)


def declared_tests(source_dir):
    """This project's device suite uses Java JUnit4 public @Test methods."""
    if not source_dir.is_dir():
        raise ValueError("Missing instrumentation source directory: " + str(source_dir))
    expected = set()
    for source in source_dir.rglob("*.java"):
        text = source.read_text()
        methods = re.findall(r"@Test\s*(?:\([^)]*\)\s*)?public\s+void\s+(\w+)\s*\(", text)
        if not methods:
            continue
        package = re.search(r"\bpackage\s+([\w.]+)\s*;", text)
        classname = re.search(r"\bpublic\s+(?:final\s+)?class\s+(\w+)\b", text)
        if package is None or classname is None:
            raise ValueError("Cannot identify device test class: " + str(source))
        expected.update((package.group(1) + "." + classname.group(1), method) for method in methods)
    if not expected:
        raise ValueError("Instrumentation source declares no test cases")
    return expected


def verify(results, screenshots, expected_tests=None):
    cases = []
    for report in results.rglob("*.xml"):
        cases.extend(ET.parse(report).getroot().iter("testcase"))
    if not cases:
        raise ValueError("Instrumentation produced no test cases")
    passed = set()
    for case in cases:
        if case.find("failure") is not None or case.find("error") is not None:
            raise ValueError("Instrumentation contains a failed test: " + str(case.attrib))
        if case.find("skipped") is not None:
            raise ValueError("Instrumentation contains a skipped test: " + str(case.attrib))
        passed.add((case.get("classname"), case.get("name")))
    missing = (REQUIRED_TESTS | (expected_tests or set())) - passed
    if missing:
        raise ValueError("Required device regressions did not pass: " + str(sorted(missing)))
    for name in REQUIRED_IMAGES:
        matches = list(screenshots.rglob(name))
        if len(matches) != 1 or matches[0].stat().st_size < 24 or matches[0].read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
            raise ValueError("Missing or invalid device screenshot: " + name)
    print(f"Verified {len(passed)} passed device tests and {len(REQUIRED_IMAGES)} required screenshots")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("results", type=Path)
    parser.add_argument("screenshots", type=Path)
    parser.add_argument("--source-dir", type=Path,
                        default=Path(__file__).resolve().parents[1] / "app/src/androidTest/java")
    args = parser.parse_args()
    try:
        verify(args.results, args.screenshots, declared_tests(args.source_dir))
    except (ValueError, OSError, ET.ParseError) as error:
        parser.exit(1, f"Device-evidence verification failed: {error}\n")


if __name__ == "__main__":
    main()
