# 0.18 second-pass stability revalidation

This change adds **instrumentation tests only**. No production source files, application
assets, package ID, versionCode (18), signing setup, app permissions, billing policy, or
Google Play publication settings were changed.

The existing CI on the 0.18 branch is triggered by this QA-only commit. It executes:
- Unit, lint and package compilation.
- Android 15 and Android 16 emulator device test suites, including all existing tests.
- New 1000-render-frame renderer stress across scenes, density, resize, wallpaper, contrast and reduced motion.
- Eight repeated top-edge/full-screen overlay switches, followed by immediate Pause,
  preset changes, and verification that the service does not restart.
- 40 settings changes after Pause to verify playback remains off.
- Signed release rebuild and bundle manifest / identity / signature validation.

Before uploading to Play Internal testing, compare the revalidated artifact with the
previous 0.18 AAB: versionCode, package, upload certificate, compressed bundle
integrity, and ideally app payload equivalence. Differences in archive metadata
do not imply different app features.

The emulator tests cannot replicate a real Google Play payment screen, Google
Pixel 11 Pro XL thermal behavior, battery drain or sustained high refresh rates.
Physical-device sign-off remains required for public release. No paywall enabled.
