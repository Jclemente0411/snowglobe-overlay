# SnowGlobe 0.15.0 / build 15 - stability beta

This is an internal-testing candidate, NOT a published update or a production approval.
The 0.14 branch and its AAB are unchanged. The owner controls all Play Console uploads.

## Implemented
- One noninteractive overlay window with conservative, system-bounded alpha. Pausing removes the window immediately; opening the Studio, locking or switching the screen off removes it too.
- Persistent manual pause. Scene selection, app reopening and wallpaper setup do not override it.
- Pause & open Google Play action; user-added Quick Settings toggle with explicit setup/retry states.
- Actual service/window status, permission handling and error recovery; no accessibility service, usage access, app monitoring, wake lock or boot receiver.
- Event-driven Battery Saver and thermal response, 30 fps preview cap, lower frame budget when warm, delayed vsync scheduling, coalesced settings updates, and bounded wallpaper render retries.
- Three local scene slots with names, replace/delete, and Undo. Only an allowlisted visual snapshot is restored; playback, power, permissions and billing settings are excluded.
- Retains actual MaterialExpressiveTheme/MotionScheme, safe insets, per-tab scroll, large-text layout, adds optional Pixel/Material You Studio colors and consistent vector navigation icons.
- New scalable snow-globe launcher mark and transparent monochrome artwork for themed icons.
- Dormant premium policy: proposed USD 2.99 one-time product holiday_plus_lifetime; all beta features remain unlocked. No paywall UI, BillingClient, network or purchase launcher is included.

## What the tap fix can and cannot do
Android permits some touch-through overlays below its maximum opacity. An app can independently reject obscured taps or hide overlays. We respect that. Pause removes our window; it is not a bypass. Google Play itself still needs a real-Pixel retest. We cannot infer that every Play problem was caused by SnowGlobe.

The cross-app test fixture has its own package and UID. It checks ordinary taps with the overlay active and protected taps after Pause. It is only installed on CI emulators and is never included in the SnowGlobe AAB.

## System boundaries
No snow above the notification shade or fingerprint UI is promised. The Quick Settings tile controls effects; it does not decorate System UI. Lock-screen snowfall uses the system-selected SnowGlobe wallpaper, replaces the selected wallpaper, and may require Home and lock screens in the system picker. AOD is not animated.

## Verification gate
The branch workflow runs unit/lint and Android 15 + Android 16 emulator checks before producing a signed AAB. It validates archive, signature, package/version, icon, wallpaper service, tile declaration and absence of billing/network/accessibility permissions. A successful run is NOT a measured battery-life improvement, sustained 120 fps claim or physical Pixel certification.

## Owner test, before inviting more people
1. Install over 0.14 and confirm version 0.15.0. Do not clear data.
2. Start effects and test ordinary app taps. Use Pause & open Google Play; ensure Play buttons and updates work. Resume manually only after leaving protected screens.
3. Add the Quick Settings tile, start/pause repeatedly, and confirm the notification Pause also stops effects.
4. Save a scene, change it, load it, then Undo. A paused overlay must remain paused.
5. Test screen off/wake and lock wallpaper; check the device stays comfortable during ordinary use. Compare identical settings against 0.14 before claiming a battery gain.
6. Confirm every effect remains free and no purchase screen appears.

## Not activated or claimed complete
No Google Play rollout, device-catalog restriction, production declaration, payment product, server purchase verifier, refund handling, music, or user-photo wallpaper feature is deployed. Photo wallpaper is intentionally deferred to avoid adding memory/privacy risk to a stability release. Live billing needs a later explicit activation and license-tester verification.
