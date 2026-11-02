# SnowGlobe 0.15 - Pixel-focused launch readiness

## Preserve the test baseline
0.14.0 is commit 2b803960adfc9a52eb1a54bd9e985d1181c826df, signed AAB run 69. This branch does not alter that commit, artifact, or Play release. No new signed release is requested until the owner tests 0.14 on a physical Pixel. Version 15 in this branch is a DEVELOPMENT candidate, not a published beta.

## Implemented first increment
- Public Android Quick Settings TileService: tap to start/pause over-app effects; long-press opens Studio.
- Missing overlay permission opens setup instead of displaying false success.
- Tile status checks the actual service, not merely a saved enabled flag.
- Unlock is required before toggling from the secure lock screen; no security bypass.
- No new data collection, automatic tile installation, boot startup, or wallpaper changes.
- Six unit-test methods for tile decisions and truthful status; isolated compile/unit/lint workflow.
- User adds the tile through Quick Settings edit. In-app add-tile prompt is a later UI task, not implemented here.

## Next increments, not implemented by this commit
1. My scenes: save three named local scenes; preview before applying; Undo restores visual settings only, never permission or service state.
2. Pixel setup card: distinct over-app, lock-screen selection, renderer error, and battery-saver states. Do not show a success state based only on permission.
3. Refine existing Material 3 Expressive UI with documented motion tokens, persistent per-tab scroll, progressive disclosure, and accessible labels. Keep camera cutout and large-text tests. No OS-version rebrand.
4. User-photo live wallpaper via Android photo picker, only after bounded decoding/cropping and memory tests. Explain replacement of the selected wallpaper and obtain explicit selection.
5. Performance evidence: release-mode frame measurements, memory trend, screen-off behavior, and paired baseline/0.15 battery sessions on actual Pixels. Do not advertise sustained 120 fps or battery percentages without measurements.

## Pixel positioning and exclusivity
Proposed: 'Christmas magic, designed for Pixel.' Independent app; not affiliated with Google or Samsung. Samsung describes Good Lock as a Galaxy customization tool; this does not establish that Pixel lacks all customization or that SnowGlobe is the first/only option. No unverified market-size or conversion claims.

The product's intended audience is Pixel users only. Actual Google Play availability is a separate Device Catalog action and must be verified before public release. This commit does NOT silently block non-Pixel internal testers or spoof a Pixel-only hardware requirement. Google says device exclusions do not apply to internal testers. Treat brand/model heuristics as diagnostics, not reliable access control. Confirm support per physical Pixel model/Android version; emulator results are not Pixel certification.

## Holiday launch gates
- Verify account type and production access. For personal accounts created after 2023-11-13, Google's documented path requires 12 closed testers opted in continuously for 14 days before production-access application. An internal track is not that closed test.
- Foreground-service declaration and a real demonstration video; app content, privacy policy, Data safety, rating, and accurate screenshots.
- 0.14 physical Pixel smoke test before merging this branch.
- Signed release compilation/lint, unit and emulator UI tests, AAB identity/signature validation, then upgrade install over 0.14 without losing settings.
- No paid unlock until billing acknowledgement, pending purchase, cancellation, restoration and reinstall are verified. Retain approved one-time price proposal; no subscription added.
- Music and multi-holiday expansion are deferred until core launch gates pass.

## Evidence and acceptance, not '100x' claims
Quick tile: toggle ten times, revoke overlay permission, reboot/reopen, stop via notification, and test locked/unlocked states. No repeated foreground-start crash; no false On status. Saved scenes: full round-trip and Undo tests. Wallpaper: explicit lock selection, wake/lock/unlock ten cycles, no AOD animation or fingerprint interception. Pixel performance: compare identical settings, refresh mode and duration against 0.14; publish measurements, not adjectives.

## Primary references checked 2026-11-02
- https://developer.android.com/develop/ui/compose/designsystems/material3
- https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme
- https://developer.android.com/develop/ui/views/quicksettings-tiles
- https://developer.android.com/reference/android/service/quicksettings/TileService
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://support.google.com/googleplay/android-developer/answer/7353455
- https://support.google.com/googleplay/android-developer/answer/14151465
- https://support.google.com/googleplay/android-developer/answer/13392821
- https://www.samsung.com/ae/support/mobile-devices/good-lock-elevate-your-samsung-experience/

No claim of App Store/Play approval, Samsung feature parity, Pixel-wide testing, or finished 0.15 release is made.
