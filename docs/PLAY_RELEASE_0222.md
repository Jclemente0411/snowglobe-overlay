# SnowGlobe 0.22.2 release handoff

## Build identity

- App: **SnowGlobe** (`com.snowglobe.overlay`).
- Version: **0.22.2**; Android version code: **100031**.
- Intended signed upload: `SnowGlobe-0.22.2-build100031.aab`.
- Minimum Android API 26; target API 36.
- Upload certificate SHA-256: `73:45:EE:F8:97:0F:7F:8D:E6:20:BB:F7:C0:4B:28:03:0D:F9:C2:F7:A1:D6:8B:68:E5:E0:72:C1:04:8F:48:A4`.

This patch adds complimentary full-feature code entry, separate trial entitlement and expiry, and access propagation through the Studio, overlay, live wallpaper, scenes and wallpaper exports. It does not restore the removed input method. See [complimentary access](COMPLIMENTARY_ACCESS.md) for exact campaign terms and operational limits.

## Validation status

This file initially records the release candidate. Completed CI results, artifact digest and source identity must be added after the release workflow finishes; their absence must not be interpreted as a verified signed deliverable.

## Play delivery

Upload the signed `.aab` to the existing app's intended test track, then complete its rollout and update SnowGlobe from that same Play track. The code-entry feature requires version 0.22.2; it is not present in 0.22.1. Trial codes are entered inside SnowGlobe, not in the Play Store's Redeem code screen.

The owner reports 0.22.1 now installs, opens and runs. That report establishes recovery on the owner's device; the old installer failure's root cause was not identified. Device installation of this new version, Play publication and the real paid checkout are separate observations from CI verification.
