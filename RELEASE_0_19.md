# SnowGlobe 0.19.0 (build 19) — Automatic unlock resume

Internal-testing candidate. Not deployed, not a public release, not tested on the owner's physical Pixel 11 Pro XL.

## Main reliability fix
- When effects were explicitly started, the foreground service now rechecks the system lock state for a bounded 4.8-second interval (12 attempts spaced 400ms) after screen-on, user-present and display configuration changes.
- Android can announce screen wake before the keyguard is completely dismissed. Version 0.18 checked only once, leaving effects invisible until the user reopened the Studio.
- Retrying stops as soon as the effects window is attached, Studio editing begins, the display sleeps, the user pauses, permission disappears, or the retry budget expires.
- A private boot/update receiver restores a previously enabled foreground service after a system reboot or package update when Android permits such a start.
- Existing sticky foreground service restarts remain active; no endless timer, wakelock, exact alarms, forced accessibility service, usage-monitor permission or battery optimization bypass.
- Explicit Pause is sticky, including after lock, unlock and package update. The app never starts effects automatically without the saved user-enabled setting and overlay permission.

## Wallpaper distinction
- Over-app snow deliberately does not appear on the secure lock-screen surface. Android prohibits ordinary application overlays from drawing there.
- To show snowfall behind the lock-screen clock, the user must set SnowGlobe as the actual live wallpaper using Android's live wallpaper picker. This replaces the user's static ornament wallpaper for that screen.
- Android can hide/stop a foreground service despite the recovery logic. If so, use the Studio Start or Quick Settings tile. This is not a guarantee after a force-stop.
- Google Play purchase screens can still block touches until users Pause the over-app effects. No security bypass is attempted.

## Carried forward from 0.18
- Christmas Studio and four appearance choices, five curated scenes, vintage lights and decor, advanced snow visibility / shade controls, top-edge touch-friendly overlay option, thermal and Battery Saver frame limits, saved scenes, Pause and Quick Settings.
- No payment activation or billing permission; the proposed $2.99 premium model remains dormant.
- Same Android package and signing key; versionCode 19 is a regular update from versionCode 18.

## Quality gate
- Unit tests for bounded retry and boot user opt-in, as well as existing snow, battery and touch-safety logic.
- Android 15 and 16 instrumented tests including three real lock/wake/unlock cycles with no Studio relaunch and ensuring Pause cannot be defeated by the update receiver.
- Existing wallpaper, cross-app protected-button tests, holiday theme tests, large text and the 1000-frame renderer stress suite.
- Signing key, icon, package, exact version, live wallpaper, private boot receiver, requested permission and disabled commerce verified in the compiled AAB.
- Passing emulator tests are not proof of performance or battery life on a physical Android 17 Pixel.

## Follow-up Pixel test
1. Upload the signed versionCode 19 AAB to Internal testing and update through Google Play.
2. Start effects once from Studio. Swipe Home. Lock the phone, tap to wake, face-unlock or swipe Home, without opening Studio.
3. Repeat at least three cycles. Verify the effects return automatically after unlocking and that explicit Pause stays paused.
4. If you want lock-screen effects too, open Settings > Set lock-screen wallpaper and select SnowGlobe; remember this replaces the currently selected lock-screen photo.
5. Try after device restart and after the Play internal update. Some device power settings can still suppress restarts.
6. Measure actual frame times and battery behavior; Android Play checkout may still require Pause.

## Official platform references
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/reference/android/app/Service
- https://developer.android.com/reference/android/app/WallpaperManager
- https://developer.android.com/about/versions/15/behavior-changes-15
