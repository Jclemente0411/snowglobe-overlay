# 0.15 engineering decisions and evidence

Primary Android references checked 2026-11-02:
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams
- https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch-events
- https://developer.android.com/privacy-and-security/risks/tapjacking
- https://developer.android.com/security/fraud-prevention/activities
- https://developer.android.com/develop/ui/views/quicksettings-tiles
- https://developer.android.com/reference/android/os/PowerManager
- https://developer.android.com/reference/android/view/Choreographer
- https://developer.android.com/develop/ui/compose/designsystems/material3

An app's filterTouchesWhenObscured may reject taps even for a benign, transparent visual overlay. The previous 0.14 service already limited alpha; this was not sufficient to promise compatibility. 0.15 takes the additional action of removing the overlay window when paused/hidden. The app does not watch which other apps run and cannot automatically identify every payment screen. Explicit pause is the compatibility path.

TYPE_APPLICATION_OVERLAY is below critical system windows. Quick Settings customization consists of a documented tile, not a private System UI window or accessibility overlay.

Power budget changes are engineering measures, not battery-savings percentages. Thermal status >= MODERATE caps drawing at 30 fps, CRITICAL at 15 fps; previews cap at 30 fps. Screen-off and invisible hosts cancel scheduled frames. Choreographer remains the display timing source. CI gfxinfo/memory evidence is emulator-only; physical Pixel measurements remain a release decision.

The stable UI stays on the verified Material 3 Expressive dependency from 0.14. Android OS version, target SDK, Material library version and actual Pixel runtime verification are distinct. This candidate still targets API 36; no claim of Android 17 certification is made.
