"""Reject empty/skipped instrumentation runs and missing release regression screenshots."""
import argparse
import xml.etree.ElementTree as ET
from pathlib import Path

REQUIRED_TESTS = {
    ("com.snowglobe.overlay.FullScreenOverlayTest", "actualWindowAndSnowPixelsReachAllFourQuarters"),
    ("com.snowglobe.overlay.FullScreenOverlayTest", "rotatingAndOpeningKeyboardCannotPermanentlyShrinkSnowfall"),
    ("com.snowglobe.overlay.KeyboardSmokeTest", "keyboardCanCommitCharactersIntoAnotherApp"),
}
REQUIRED_IMAGES = (
    "21-fast-keyboard-layout.png", "23-autocorrect-and-rgb.png",
    "27-full-height-snow.png", "28-snow-landscape.png",
    "29-snow-with-keyboard.png", "30-snow-after-keyboard.png",
)


def verify(results, screenshots):
    cases = []
    for report in results.rglob("*.xml"):
        cases.extend(ET.parse(report).getroot().iter("testcase"))
    if not cases:
        raise ValueError("Instrumentation produced no test cases")
    passed = set()
    for case in cases:
        if case.find("failure") is not None or case.find("error") is not None:
            raise ValueError("Instrumentation contains a failed test: " + str(case.attrib))
        if case.find("skipped") is None:
            passed.add((case.get("classname"), case.get("name")))
    missing = REQUIRED_TESTS - passed
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
    args = parser.parse_args()
    try:
        verify(args.results, args.screenshots)
    except (ValueError, OSError, ET.ParseError) as error:
        parser.exit(1, f"Device-evidence verification failed: {error}\n")


if __name__ == "__main__":
    main()
