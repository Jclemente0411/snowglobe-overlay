# SnowGlobe 0.22.2 release handoff

## Build identity

- App: **SnowGlobe** (`com.snowglobe.overlay`).
- Version: **0.22.2**; Android version code: **100031**.
- Verified signed upload: `SnowGlobe-0.22.2-build100031.aab` (**8,180,791 bytes**).
- AAB SHA-256: `bab3bc1f2f20d86f29e8b04c2eec0e548aab120c5e65a9c27ea5e1fdb0797b24`.
- Minimum Android API 26; target API 36.
- Release source: `f6f65700f00559e6cb36776d7020a92f301f2341`.
- Release workflow: [run 42431191659](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42431191659).
- Upload certificate SHA-256: `73:45:EE:F8:97:0F:7F:8D:E6:20:BB:F7:C0:4B:28:03:0D:F9:C2:F7:A1:D6:8B:68:E5:E0:72:C1:04:8F:48:A4`.

This patch adds complimentary full-feature code entry, separate trial entitlement and expiry, and access propagation through the Studio, overlay, live wallpaper, scenes and wallpaper exports. It does not restore the removed input method. See [complimentary access](COMPLIMENTARY_ACCESS.md) for exact campaign terms and operational limits.

## Validation status

The release workflow completed successfully on the source above, including both Android 15 and Android 17 device jobs and the existing-key signing job.

| Gate | Android 15 / API 35 | Android 17 / API 37 |
| --- | --- | --- |
| JVM tests | 110 passed, including 19 trial tests | 110 passed, including 19 trial tests |
| Device preflight | 5 passed | 5 passed |
| Full device tests | 41 passed, including 3 trial tests | 41 passed, including 3 trial tests |
| Required screenshots | 12 verified | 12 verified |

The new device cases cover invalid codes and forged preference flags, valid-code activation and premium controls, controller/activity recreation, wallpaper export access, and expiry that relocks controls and removes premium effects from an attached renderer. These are emulator results; they do not establish a real purchase or installation of this version on the owner's phone.

The signing job's build, release lint, signature, package/version metadata, 16 KB library checks, temporary-key removal and artifact upload all passed. The downloaded AAB passed archive and checksum verification; all 197 signed payload entries were checked, and the embedded upload certificate matches the existing identity above. The release contains the correct hashed access campaigns, no plaintext access codes or test campaigns, and no custom keyboard service.

The standalone AAB is ready to upload. It is contained in Actions artifact **`SnowGlobe-0222-signed-AAB-22-1`**, ID **13598571700**. The enclosing artifact ZIP has SHA-256 `23f0eca7f805bda01ba688cb97127ea7d7f7cd3f18f325e894c10445ad146073`; this differs from the AAB digest above because it covers the evidence archive. See [delivery evidence](evidence/DELIVERY_0222.json) for the exact artifact and per-OS verification records.

Actual release metadata records **`commerceConfigured: false` / `commerce-not-configured`**. Complimentary codes work independently; the $2.99 Play checkout remains disabled pending account configuration and real purchase testing.

## Play delivery

Upload the signed `.aab` to the existing app's intended test track, then complete its rollout and update SnowGlobe from that same Play track. The code-entry feature requires version 0.22.2; it is not present in 0.22.1. Trial codes are entered inside SnowGlobe, not in the Play Store's Redeem code screen.

In the updated app, open **Home → Explore Holiday Plus → Access code**, enter the code, then select **Activate code**. **Settings & safety → Holiday Plus & access codes** reaches the same screen. Successful activation shows **Holiday Plus trial active** and the local expiry time. Owner access lasts up to 90 days and shared access up to 7 days; both end no later than March 1, 2027 at 00:00 UTC. Re-entering a code does not extend its first redemption. Neither code charges the user or renews automatically.

The paid `holiday_plus_lifetime` upgrade remains separate. Complimentary access does not configure the $2.99 checkout, create a purchase receipt or prove that a real Play transaction works. See [paid release setup](HOLIDAY_PLUS_SETUP.md) for the remaining account-side steps.

The owner reports 0.22.1 now installs, opens and runs. That report establishes recovery on the owner's device; the old installer failure's root cause was not identified. Device installation of this new version, Play publication and the real paid checkout are separate observations from CI verification.
