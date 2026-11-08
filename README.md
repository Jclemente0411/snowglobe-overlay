# SnowGlobe

SnowGlobe brings layered snowfall, Christmas lights and original winter artwork to Android. Package: `com.snowglobe.overlay`.

## Current release scope

**0.22.2 / 100031** adds complimentary access codes for full-feature trials, retaining the effects-focused application and custom keyboard removal. The owner reports that the previous **0.22.1 / 100030** Play build now installs, opens and runs; the earlier installer root cause was not established. See the [0.22.2 release handoff](docs/PLAY_RELEASE_0222.md), [access-code operation](docs/COMPLIMENTARY_ACCESS.md), and [underlying application audit](docs/RELEASE_READINESS_0220.md). Minimum SDK 26; compile and target SDK 36.

Free features include full-height layered snow, snow speed/size/density/visibility and shape controls, lights and colors, the Christmas Eve and Arctic Night scene recipes, live wallpaper and two original 2160 × 3840 wallpapers. **SnowGlobe Holiday Plus** is the agreed one-time USD 2.99 upgrade inside this same app; there is no separate paid package or subscription.

Plus adds falling decorations, alternate directions and rotation, floating fairy lights, three additional scene recipes, and two exclusive 4K wallpapers. Access comes from either a valid signed Google Play receipt or a valid complimentary trial code. The trial is separate from permanent ownership, carries no charge, and does not renew automatically. A copied visual preference or saved scene cannot grant access. Missing billing verification configuration disables checkout while preserving both the free app and valid complimentary access. An implemented purchase screen does not establish that the Console product is active or that a real test purchase passed. See [Holiday Plus setup](docs/HOLIDAY_PLUS_SETUP.md).

## Build and verification

Use JDK 17, Android SDK 36 and Gradle 8.11.1:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :tap-probe:assembleDebug
```

The release workflow verifies every declared device test and required screenshot on Android 15 and Android 17 before signing an AAB with the existing upload key. The compiled manifest gate rejects all input method services and diagnostic transport upload components, requires the protected wallpaper/tile services, and checks Billing and network permissions. It also validates the bundle, signing certificate, native 16 KB layout and generated APK alignment. The actual Billing SDK is exercised in the short device preflight as well as the complete suite; see [dependency isolation](docs/BILLING_DEPENDENCY_ISOLATION.md).

Set the genuine Google Play licensing RSA public key as GitHub Actions variable `PLAY_BILLING_PUBLIC_KEY` or local Gradle property `playBillingPublicKey`. Invalid nonempty keys fail the build. An empty key is permitted for a free build, clearly recorded as `commerce-not-configured`. This is separate from the existing private upload keystore, which must never be replaced for this app.

A local `bundleRelease` without `PLAY_STORE_FILE` is an unsigned inspection artifact and cannot be uploaded to Google Play. Signed builds require the existing `PLAY_UPLOAD_*` workflow secrets.

## Using SnowGlobe

Open SnowGlobe and allow Display over other apps when starting the overlay. Full-screen snowfall is the default; top-quarter coverage remains an explicit optional setting. Pause is available in the app, notification and Quick Settings tile. Android can block overlays on protected payment screens, so the app pauses its own overlay before starting a Holiday Plus checkout.

Android controls live-wallpaper Home/Lock selection. An ordinary overlay cannot cover the secure lock screen or notification shade. The wallpaper collection saves original 4K PNG artwork to a destination selected through Android, without broad storage permission.

SnowGlobe no longer appears in the keyboard picker. Android continues to manage your normal keyboard; full-height snow is tested while the system keyboard opens and closes.

## Privacy

No Internet permission, advertising, analytics, publisher account or app-operated server. Google Play Billing communicates through the installed Play service. Signed purchase proof and complimentary trial records stay in separate private backup-excluded files; settings and scenes remain local. Read the [privacy policy](docs/PRIVACY.md), which is also available in the app.
