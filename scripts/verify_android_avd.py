"""Reject an AVD whose metadata misidentifies a dotted Android API as android-0."""
import argparse
import re
import shutil
from pathlib import Path


def api_level(value):
    match = re.fullmatch(r"(?:android-)?([1-9][0-9]*)(?:\.([0-9]+))?", value.strip())
    if not match:
        raise ValueError("Invalid Android API target: " + value)
    return int(match[1]), int(match[2] or 0)


def properties(path):
    values = {}
    for line in path.read_text().splitlines():
        if "=" in line and not line.lstrip().startswith(("#", ";")):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def verify(avd_home, name, expected_api, evidence=None):
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", name):
        raise ValueError("Invalid AVD name")
    root_ini = avd_home / (name + ".ini")
    if evidence is not None:
        evidence.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(root_ini, evidence / "avd.ini")
    root = properties(root_ini)
    avd_path = Path(root.get("path", ""))
    if not avd_path.is_absolute():
        raise ValueError("AVD root metadata must contain its absolute path")
    config_path = avd_path / "config.ini"
    if evidence is not None:
        shutil.copyfile(config_path, evidence / "avd-config.ini")
    config = properties(config_path)
    expected = api_level(expected_api)
    target = root.get("target", "")
    if api_level(target) != expected:
        raise ValueError(f"AVD target {target} does not match expected Android {expected_api}")
    image = config.get("image.sysdir.1", "").replace("\\", "/")
    match = re.search(r"(?:^|/)system-images/(android-[^/]+)(?:/|$)", image)
    if not match or api_level(match[1]) != expected:
        raise ValueError(f"AVD system image {image} does not match expected Android {expected_api}")
    print(f"Verified AVD {name}: target={target}, image={image}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--avd-home", type=Path, required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--expected-api", required=True)
    parser.add_argument("--evidence", type=Path)
    args = parser.parse_args()
    try:
        verify(args.avd_home, args.name, args.expected_api, args.evidence)
    except (ValueError, OSError) as error:
        parser.exit(1, f"AVD metadata verification failed: {error}\n")


if __name__ == "__main__":
    main()
