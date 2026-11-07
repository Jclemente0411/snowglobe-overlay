# SnowGlobe

SnowGlobe is an Android holiday effects app with an optional full-screen overlay, system-selected live wallpaper, and a local English Christmas RGB keyboard. Package: `com.snowglobe.overlay`.

## Current release build

The signed **0.21.6 / 100028** AAB is verified and ready for Google Play upload, with minimum SDK 26 and target SDK 36. [Release run 42327417503](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42327417503) passed all three CI jobs: 131 host tests, 35 script regressions, two separate preflight cases and all 50 full-suite device tests on each of Android 15 and Android 17, followed by signed-bundle verification. See [release readiness](docs/RELEASE_READINESS_0216.md) for the exact artifact, source, checksum and upload certificate. Google Play submission/approval, physical Pixel acceptance and complete Gboard equivalence have not been established.

The keyboard uses a custom Android `InputMethodService` and Canvas view. It offers tap typing, local word suggestions, conservative known-typo correction, RGB themes, adjustable height, two-thumb tap ordering, spacebar cursor movement, a small local emoji palette, and accessible virtual keys. Glide typing, voice input, full multilingual prediction, a clipboard manager and Gboard's proprietary language models are not implemented. All current features remain unlocked; the proposed premium purchase flow is not implemented.

## Verification and release

Use JDK 17, Android SDK 36 and Gradle 8.11.1:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :tap-probe:assembleDebug
```

The JVM tests include real Android framework behavior simulated through Robolectric at API 35. They supplement device instrumentation; they do not measure physical-device typing latency or display frame rate.

The `release-verify.yml` workflow runs Android 15 and Android 17 device checks before using the **existing upload key** to build a signed AAB. It verifies the bundle's signature, package/version/SDK and service declarations, native 16 KB alignment and generated APK alignment. Retained evidence includes screenshots, test reports, bundle metadata, source commit, certificate information and SHA-256.

A local `bundleRelease` without `PLAY_STORE_FILE` intentionally produces an **unsigned inspection artifact**. It must not be uploaded to Google Play. Release signing requires the existing `PLAY_UPLOAD_*` GitHub secrets; no replacement key should be generated for an app already on Play.

## Device setup

Open SnowGlobe and allow Display over other apps for the overlay. Full-screen snowfall is the default; top-quarter coverage is an optional setting. Android can block overlays and obscured touches on protected screens; use Pause when needed. Android controls live-wallpaper Home/Lock choices and does not allow an ordinary overlay above its secure lock screen or notification shade.

Enable the optional keyboard in Android keyboard settings, then select it using the system keyboard switcher. Theme, RGB effect/brightness/speed, keyboard height, suggestions, autocorrection and haptics are configurable in SnowGlobe.

## Privacy

The current app has no Internet permission, analytics, accounts or billing integration. Keyboard text is processed in temporary local memory and is not logged, saved as a typing history or sent to the publisher. Android may back up local settings. Read the [privacy policy](docs/PRIVACY.md), also available within the app.
