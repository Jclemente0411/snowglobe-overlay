# SnowGlobe 0.22.0 — Premium Christmas Keyboard candidate

**Status:** draft/internal test only. No Google Play upload or live rollout is authorized by this change. Do not claim real-device success until the owner's Pixel 11 Pro XL is tested.

## IME capabilities in this branch
- Android InputMethodService registered in the original app, explicitly enabled/selected by the user, never an over-app touch overlay.
- Native rounded keyboard with touch feedback, short and long-press Delete, cursor movement by sliding Space, shift/caps-lock, numbers/phone/email/URL-specific layouts, editor actions, basic holiday emoji panel.
- Offline, bounded opt-in word completions derived solely from the current in-memory typing session. No logging, analytics, Internet permission, clipboard reading, or persistent typing history.
- Input-mode privacy: hide completion suggestions and Gemini action for password and no-personalized-learning fields; no automatic selected-text reading.
- Upper-left **Gemini shortcut** explicitly opens installed Gemini if available, otherwise the Gemini website. It does not embed Gemini, transfer editor content, or implement Gboard's proprietary features.
- Three Christmas color themes, battery-aware RGB light strip, adjustable speed/brightness, optional key haptics.
- Dedicated fifth **Keys** tab in Material 3 Expressive Studio, themed preview and independent keyboard controls.

## Hard limits
- This is not full Gboard feature parity: no machine-learning autocorrect, full multilingual dictionaries, handwriting, full emoji catalog, voice dictation, or glide typing.
- Material Expressive in the Studio is not itself an Android 17 SDK upgrade. Current compile/target SDK is 36; Android 17 device compatibility still requires real-device checks.
- The keyboard must be opt-in, and the user can return to Gboard from Android's keyboard switcher.
- Overlay security rules still apply. Leave top-edge effects enabled and pause the overlay for protected payment/login screens.
- Gemini may not be installed; fallback uses a browser. Google branding and APIs are not claimed as owned or integrated.

## QA gates before creating or uploading any AAB
1. Unit tests for password/input classification, input modes, offline completions and animation bounds.
2. Android 15/16 instrumented test runs: cross-app QWERTY input, password privacy, number/email keys, existing overlay/payment simulations, lock/unlock and wallpaper.
3. Debug compile, lint, renderer stress check and validated signed bundle with correct applicationId/signing key/version.
4. Physical Pixel: test typing into real apps, account logins, payments, long press, space cursor, rotation, 120Hz frame consistency, RGB energy/battery, Gemini shortcut fallback and accessibility with large text.
5. Test Android 17-specific keyboard visibility changes and system IME switcher before public release.

## Official references
- https://developer.android.com/reference/android/inputmethodservice/InputMethodService
- https://developer.android.com/guide/components/activities/secure-bal
- https://developer.android.com/about/versions/17/behavior-changes-all
