"""Check the compiled bundle manifest rather than only Gradle's source configuration."""
import argparse
import json
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def component_name(element, package):
    name = element.get(ANDROID + "name", "")
    return package + name if name.startswith(".") else package + "." + name if "." not in name else name


def actions(element):
    return {action.get(ANDROID + "name") for intent in element.findall("intent-filter") for action in intent.findall("action")}


def verify(meta, manifest):
    package = meta["applicationId"]
    require(manifest.get("package") == package, "Wrong package")
    require(int(manifest.get(ANDROID + "versionCode", "-1")) == meta["versionCode"], "Wrong version code")
    require(manifest.get(ANDROID + "versionName") == meta["versionName"], "Wrong version name")
    sdk = manifest.find("uses-sdk")
    require(sdk is not None and int(sdk.get(ANDROID + "targetSdkVersion", "-1")) == meta["targetSdk"], "Wrong target SDK")
    app = manifest.find("application")
    require(app is not None, "Missing application")
    require(app.get(ANDROID + "debuggable", "false") == "false", "Debug bundle")
    require(app.get(ANDROID + "testOnly", "false") == "false", "Test-only bundle")
    require(app.get(ANDROID + "enabled", "true") == "true", "Disabled application")
    require(app.get(ANDROID + "icon"), "Missing launcher icon")
    require(app.get(ANDROID + "usesCleartextTraffic") == "false", "Cleartext networking must stay disabled")

    def component(tag, suffix):
        found = [element for element in app.findall(tag) if component_name(element, package) == package + "." + suffix]
        require(len(found) == 1, "Missing or duplicated " + suffix)
        require(found[0].get(ANDROID + "enabled", "true") == "true", "Disabled " + suffix)
        return found[0]

    def bound_service(suffix, permission, action, metadata=None):
        service = component("service", suffix)
        require(service.get(ANDROID + "permission") == permission, suffix + " must be system-protected")
        require(service.get(ANDROID + "exported") == "true", "Android must be able to bind " + suffix)
        require(action in actions(service), "Missing action for " + suffix)
        if metadata:
            require(any(m.get(ANDROID + "name") == metadata and m.get(ANDROID + "resource") for m in service.findall("meta-data")),
                    "Missing metadata for " + suffix)
        return service

    bound_service("SnowWallpaperService", "android.permission.BIND_WALLPAPER",
                  "android.service.wallpaper.WallpaperService", "android.service.wallpaper")
    bound_service("ChristmasKeyboardService", "android.permission.BIND_INPUT_METHOD",
                  "android.view.InputMethod", "android.view.im")
    bound_service("SnowGlobeTileService", "android.permission.BIND_QUICK_SETTINGS_TILE",
                  "android.service.quicksettings.action.QS_TILE")
    effects = component("service", "EffectsService")
    require(effects.get(ANDROID + "exported") == "false", "Effects service must stay private")
    require(effects.get(ANDROID + "foregroundServiceType") in ("specialUse", "0x40000000", "1073741824"), "Wrong foreground service type")
    require(any(p.get(ANDROID + "name") == "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" and p.get(ANDROID + "value", "").strip()
                for p in effects.findall("property")), "Missing special-use explanation")

    # SDK-qualified permissions are real grants too; do not let them bypass the offline/privacy checks.
    permissions = {element.get(ANDROID + "name") for element in manifest
                   if element.tag in ("uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m")}
    for permission in ("SYSTEM_ALERT_WINDOW", "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_SPECIAL_USE", "RECEIVE_BOOT_COMPLETED"):
        require("android.permission." + permission in permissions, "Missing permission: " + permission)
    for forbidden in ("com.android.vending.BILLING", "android.permission.INTERNET", "android.permission.PACKAGE_USAGE_STATS"):
        require(forbidden not in permissions, "Unexpected permission: " + forbidden)
    require(not any(s.get(ANDROID + "permission") == "android.permission.BIND_ACCESSIBILITY_SERVICE" for s in app.findall("service")),
            "Unexpected accessibility service")
    receiver = component("receiver", "EffectsStartupReceiver")
    require(receiver.get(ANDROID + "exported") == "false", "Resume receiver must be private")
    require("android.intent.action.BOOT_COMPLETED" in actions(receiver), "Boot receiver action missing")
    require("android.intent.action.MY_PACKAGE_REPLACED" in actions(receiver), "Update receiver action missing")
    launcher = component("activity", "StudioActivity")
    require(launcher.get(ANDROID + "exported") == "true", "Launcher must be exported")
    require(any("android.intent.action.MAIN" in {x.get(ANDROID + "name") for x in intent.findall("action")}
                and "android.intent.category.LAUNCHER" in {x.get(ANDROID + "name") for x in intent.findall("category")}
                for intent in launcher.findall("intent-filter")), "Missing launcher intent")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("metadata", type=Path)
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    try:
        meta = json.loads(args.metadata.read_text())
        verify(meta, ET.parse(args.manifest).getroot())
    except (ValueError, KeyError, OSError, ET.ParseError) as error:
        parser.exit(1, f"Bundle verification failed: {error}\n")
    print("VERIFIED:", meta)


if __name__ == "__main__":
    main()
