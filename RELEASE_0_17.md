# SnowGlobe 0.17.0 (versionCode 17) — Christmas Magic Edition

This is a private Google Play **Internal testing** candidate. No public release or Play Console upload occurs from CI.

## What's new (shipped in this branch)
- Five carefully tuned, one-tap visual scenes: Christmas Eve, Snowy Village, Vintage Christmas, Arctic Night and North Pole Party.
- Real glass-bulb rendering with refined baked-in rim highlights, a subtle phase-offset incandescent mode, and no rapid strobe.
- Original scene-card artwork, a clearer current-scene label, and Undo; visual scene changes cannot override Pause, quality, battery, lock wallpaper or dormant commerce.
- Honest, guided Pixel setup explaining over-app effects, the separate live lock-screen wallpaper and the user-added Quick Settings control.
- Auto render budget targets 30 fps at conventional refresh rates and 60 fps on 90+ Hz displays, dropping to 30/15 with Battery Saver and heat. Existing manual Eco/Balanced/Smooth preferences are retained; previews remain capped at 30.
- Renderer skips particle movement work for particles not visible or enabled and uses immutable settings snapshots and cached light sprites.

## Preserved from 0.14–0.16
- Holiday Studio's Classic Christmas, Evergreen Lodge, Candlelight and Pixel/Material You appearances.
- Safe Pause (removes the actual overlay window), tap-through protection, Google Play quick launch and notification controls.
- Actual system-selected wallpaper (including wake flurry), not a fake lock-screen overlay or an AOD background.
- Three offline saved scene slots and Undo, consistent accessibility labels and large-text behavior.
- No billing UI, billing permission, payment requests, data collection, app monitoring, accessibility service or analytics SDK.

## Boundaries that matter
- No snowfall can be drawn above Android's secure notification shade/fingerprint UI via TYPE_APPLICATION_OVERLAY.
- Protected login, banking, checkout or Google Play controls may reject any external overlay; pause SnowGlobe before using them.
- This is Pixel-focused positioning, NOT a new Pixel-only device gate or an affiliation with Google/Samsung.
- Emulator tests do not guarantee behavior on the owner's Android 17 Pixel 11 Pro XL.
- Auto FPS is a cap/target, not a claim of measured performance. Battery savings and smoothness require paired physical-device tests.

## QA gate
GitHub Actions performs :app:testDebugUnitTest and lint, cross-app UI tests + real screenshots on API 35 and 36, then builds and validates the signed app bundle using the existing upload key. AAB identity, service declarations, signature, no billing/network/accessibility permissions, and exact version 17 are checked. If a gate fails, do not upload that artifact.

## Real Pixel acceptance before public release
1. Upload this signed AAB to Internal testing, then update over 0.12–0.16 without clearing app data.
2. Confirm all five recipes can be selected, stored settings persist, and Undo restores visuals without auto-resuming Pause.
3. Repeatedly test the Play Store, checkout and login buttons with overlay on, then after pressing Pause. Protected apps may intentionally reject overlaid taps.
4. Verify lock wallpaper selection and repeated screen lock/wake/unlock cycles; system panels should remain untouched.
5. Try Quick Settings tile, notification Pause, and Eco/Balanced/Smooth/Auto at 60 and 120 Hz display modes.
6. Collect frame-time and battery screenshots before claiming improvements; check reduced motion and large font.
7. Confirm there is no charge/purchase prompt. Propose $2.99 lifetime unlock only after Play Billing integration, ownership verification and license testing.

## Verified engineering references (2026-11-02)
https://developer.android.com/jetpack/androidx/releases/compose-material3
https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile
https://developer.android.com/topic/performance/rendering/profile-gpu
https://developer.android.com/develop/ui/views/quicksettings-tiles
https://developer.android.com/privacy-and-security/risks/tapjacking
https://developer.android.com/google/play/billing/test

The signed AAB is for testing, not an approved production release.
