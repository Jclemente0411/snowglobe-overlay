# SnowGlobe 0.22.0 release audit

Audit date: November 7, 2026. Candidate: `com.snowglobe.overlay`, version **0.22.0 / 100029**, minimum API 26 and target API 36. Integrated compilation, device execution and final signing are in progress; results will be recorded from the actual completed runs.

## Scope delivered in source

- Removed all SnowGlobe keyboard services, input-method metadata, keyboard UI and keyboard-only code/tests. The compiled-package gate explicitly rejects renamed or disabled IMEs.
- Layered snowfall: four bounded depth planes, multiple cached crystal silhouettes, softer foreground flakes, two time-based drift currents, stable depth ordering and full-height initial distribution. The 128-particle limit and single frame scheduler are preserved.
- Existing full-screen overlay, rotation, system-keyboard open/dismiss, pause, unlock resume, power/thermal response and lock-screen wallpaper safety remain under regression coverage.
- Refined adaptive launcher icon and an effects-focused home screen with clear Free/Plus boundaries.
- Four original, locally rendered 2160 × 3840 wallpaper designs; two free and two Plus. Export uses Android's selected document destination, runs off the UI thread, and closes streams/recycles the bitmap. No broad storage permission.
- Real one-time Holiday Plus purchase/restore implementation, signed-receipt ownership, pending/acknowledgement/revocation handling and fail-closed checkout when the genuine verification key/product are unavailable.
- Updated in-app and published privacy policy to match keyboard removal, billing proof and wallpaper export.

## Release gates

The workflow runs every currently declared device test, including full snow coverage, installed-package scope, Plus/Restore discovery and the artwork gallery. Required screenshots include the actual Home/Snow/Lights/Decor UI, large-text layout, Plus, wallpapers and snow with rotation/system keyboard. App failures remain fatal. A bounded launcher recovery is allowed only before app installation, as retained from the previously verified runner.

Signing uses the existing upload key only after both Android 15 and Android 17 jobs pass. The final AAB must pass strict signature verification, compiled package/version/service checks, bundletool validation and native/APK 16 KB alignment. The bundle includes exact source and checksum records plus a separate `commerce-status.txt`.

## Paid-release limitation

The prepared product is `holiday_plus_lifetime`, USD 2.99 once, in the existing app. Build metadata records whether a genuine RSA licensing public key was supplied. Missing configuration does not create a purchasable product or grant a local entitlement. It preserves a usable free app, with checkout disabled. See [Holiday Plus setup](HOLIDAY_PLUS_SETUP.md).

The Cloud browser's attempted Play Console access was rejected by automatic approval review because of an unapproved `google.play` redirect. No Console state or real test purchase has been verified, and the block has not been bypassed. Play activation, account declarations, Google review and any account-specific production-access requirements are distinct from an AAB passing technical checks.

## Evidence to record after completion

- Exact release source and successful workflow run.
- JVM, script and both device-suite results; lint errors and warnings.
- Visual review of actual native snow/artwork renders and emulator captures.
- Signed artifact filename, byte size, AAB SHA-256 and archive digest.
- Existing upload certificate comparison and local bundle validation.
- Actual `commerceConfigured` result and any unverified physical-device or Console steps.

No physical Pixel frame-time, 120 Hz guarantee, battery benchmark, complete TalkBack session or Google approval is claimed by emulator/host results.
