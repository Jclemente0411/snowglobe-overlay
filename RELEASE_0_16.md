# SnowGlobe 0.16.0 / build 16 - Holiday Studio

Internal-testing candidate. No Google Play rollout, payments, device restrictions or production claims.

## The visible change
- Default Classic Christmas Studio: cranberry surfaces, cream text, warm gold controls, evergreen/berry accents.
- Evergreen Lodge and light Candlelight appearances, plus an explicit Pixel/Material You option.
- Appearance persists in its own preference file. Selecting it never changes the real effects palette, wallpaper, Pause, performance mode or purchases. An explicitly saved 0.15 Pixel-color preference is honored on migration.
- Original static holiday illustrations for scene cards. No remote assets, fonts, downloads or extra continuous decorative animation.
- Real scene preview from the existing renderer, one clear playback control and a visible Pause & open Google Play action.
- Illustrated collection cards, saved scenes in a focused sheet, and setup/comfort controls in a separate Settings sheet rather than a long Home dashboard.
- Actual MaterialExpressiveTheme and MotionScheme, safe window insets, accessible semantic labels, persistent tab scroll and wrapping text.

## Stability preserved
The 0.15 overlay service, renderer, power/thermal policy, Quick Settings service, wallpaper service and dormant commerce policy are unchanged. All beta features remain free. Overlay safety and battery changes are not traded away for theme animations. Preview rendering pauses behind appearance/settings sheets.

## Verification gates
Unit tests include theme fallback/migration and 4.5:1 contrast for custom palette text roles. Instrumented tests cover theme selection/persistence, appearance isolation from playback/power, settings at enlarged text, previous navigation/screenshots, cross-app taps, saved scenes and wallpaper wake behavior on API 35 and 36. Signed AAB checks include identity, signature, version, icon, services and absence of billing/network/accessibility permissions.

A passing CI result is not physical Pixel certification, an Android 17 certification, proof of sustained 120 fps, or measured battery savings. This UI-only update intentionally retains target SDK 36 and the already compiled Expressive dependency. Real-device visual and battery checks remain necessary.

## On the Pixel
Install the update over the existing beta without clearing data. Open Appearance (palette icon) to choose a Studio look. Open Settings and safety (sliders icon) for wallpaper, Quick Settings and power choices. Effects and interface appearance are independent. Pause before protected shopping/payment screens.

## Official implementation references checked 2026-11-02
https://developer.android.com/develop/ui/compose/designsystems/material3
https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme
https://android-developers.googleblog.com/2025/05/androidify-building-delightful-ui-with-compose.html
https://developer.android.com/develop/ui/compose/system/insets-ui
https://developer.android.com/guide/topics/ui/accessibility/apps

No feature was measured as '10x' or '100x'. This release is a concrete holiday-themed UI redesign with testable behavior.
