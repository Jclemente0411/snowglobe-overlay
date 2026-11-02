# SnowGlobe 0.14.0 beta

## Scope
- Real scene preview, overlay and wallpaper share one Canvas renderer.
- Steady, soft twinkle, slow blink, traveling-wave and alternating lighting.
- Matching animated falling bulbs and optional bounded floating fairy lights.
- Cached bulb/halo/decor assets and no per-particle offscreen layers.
- 30/60/up-to-120 fps budgets synchronized to display callbacks; Battery Saver caps at 30.
- Direct wallpaper Surface rendering, visibility/surface/ambient lifecycle, flurry on wake,
  and optional touch flurries only when Android delivers touch events.
- Explicit lock-screen wallpaper selection status and instructions.
- Real MaterialExpressiveTheme and MotionScheme.expressive from the pinned public
  Material3 1.5.0-alpha01 API. This is an internal beta dependency, NOT a claim to target Android 17.
- Version remains target API 36 / minimum API 26. All existing upload-key names are unchanged.

## Verification gates
11 pure motion tests, 4 Android Canvas rendering tests, actual activity navigation/screenshots
at normal and enlarged text, debug/release lint, signed AAB verification, bundletool validation,
and identity/version/icon/wallpaper checks against the BUILT manifest.

## Important device checks
Emulator success does not establish Pixel 11 Pro XL frame-time, thermal, battery, fingerprint,
AOD or lock-screen wallpaper behavior. A live wallpaper must be applied in Android's picker;
overlay access alone cannot do this. The wallpaper replaces the selected background. Tap
responses on a secure lock screen are not promised. No keyguard bypass, screen capture,
accessibility permission, or fingerprint interception is used.

## Research references (official)
- https://www.samsung.com/hk_en/news/product/exploring-good-lock-3-3-features-recommended-by-samsung-developers-and-newsroom-editors/
- https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine
- https://developer.android.com/reference/android/app/WallpaperManager
- https://developer.android.com/develop/ui/views/layout/custom-views/optimizing-view
- https://developer.android.com/jetpack/androidx/releases/compose-material3#1.5.0-alpha01

Do not claim a multiplier in quality or sustained 120 fps without measurements on the target device.
