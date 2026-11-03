# SnowGlobe 0.18.0 / versionCode 18 — stability and visibility

Private Android internal-testing candidate. This build is NOT a Google Play public rollout.
It includes the full 0.17 Christmas Magic feature set and all prior holiday UI/stability work.

## Actual changes

### Snow appearance
- Snow visibility slider (0 to 100%), default 100% for existing users.
- Snow shade: bright white (default), soft silver, dark charcoal. Helps contrast with different underlying apps.
- Separate artistic controls from Android window alpha: adjusting appearance never bypasses payment-app protections.
- User's snow contrast and edge mode are global preferences and survive scene switching, saving, and Undo.

### Protected-app compatibility
- Optional **Top-edge-only effects** mode (defaults OFF so existing look isn't silently changed): user chooses to confine over-app visuals to the top quarter.
- Only the top quarter is an application-overlay window in this mode; the rest of the screen isn't underneath this window. It may help taps on buttons below it, depending on the protected app.
- Full-screen overlay still available for Christmas effect fans.
- Sticky Pause and notification/Quick Settings controls remain. The "Pause & open Google Play" action removes the actual overlay before opening the store.
- No use of accessibility services, usage access, simulated touches, security overrides, PACKAGE_USAGE_STATS, or automatic monitoring of checkout screens.
- **Cannot guarantee purchases work while the overlay is visible**. Google Play can reject checkout taps on security grounds; pause is the safe, documented route.

### Performance changes
- Particle motion loop iterates only visible/configured flakes and decorations instead of the fixed 128-element array every animated frame.
- Zero snow visibility and no decorations can eliminate flake simulation; offscreen snow is skipped during drawing.
- Top-edge mode reduces the number of simulated particles and the app-overlay window area.
- Previous 30/60/120 upper bounds, Auto frame budgets, Battery Saver and thermal reductions, screen-off pausing, cached bulb artwork and live wallpaper lifecycle preserved.
- No numerical performance or battery savings claim until measured on a physical Pixel.

### Other invariants
- Default Classic Christmas Studio, Evergreen Lodge, Candlelight, Pixel colors, curated Christmas scenes, saved scenes.
- App identifier com.snowglobe.overlay; same signing key required for updates.
- Premium foundation remains completely disabled. No billing permission, purchase launcher, paywall or feature restriction.
- Snow cannot cover the Android notification shade through a normal app overlay.

## Quality gates
- Unit tests for opacity, shade, particle count, top-edge geometry, saved settings and security policy.
- Emulator tests on Android 15 (API 35) and Android 16 (API 36): full-screen and top-only cross-UID protected taps, transparent/dark snow raster rendering, interface appearance, saved scenes and wallpaper.
- Signed AAB built only if both emulators pass; archive, package/version, icon, signing key and disabled commerce verified.

## Real Pixel test plan
1. Install update over 0.17 on Internal testing. No data deletion.
2. In **Snow**, adjust Snow visibility from 0% to 100%, then select Bright white, Soft silver and Dark charcoal while opening light and dark apps.
3. In **Home**, turn Top-edge-only effects on. Try ordinary app controls below the top quarter, then Google Play checkout. Android may still require Pause.
4. Turn Top-edge-only OFF to see the full-screen effect; pause from the notification/Quick Settings before any payment or login.
5. Measure frame time, CPU and memory alongside 0.17 under identical brightness, density, speed, scene and refresh-rate conditions. Do not infer battery changes from one run.
6. Repeat lock, wake, wallpaper setup and setting persistence. All premium features must stay free while testing.

## Official behavior
Android 12+ blocks unsafe untrusted-overlay touches. Protected apps may use filterTouchesWhenObscured even with a translucent overlay. The only universal fix for that app is to remove SnowGlobe's overlay via Pause:
- https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch-events
- https://developer.android.com/privacy-and-security/risks/tapjacking
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams
- https://developer.android.com/topic/performance/issues/render

No Pixel 11 Pro XL / Android 17 real-device certification, Play approval, phone battery percentage or guaranteed checkout parity is claimed.
