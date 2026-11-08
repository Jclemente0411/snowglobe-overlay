# SnowGlobe 0.22.3 candidate — touch compatibility and signed updates

Prepared November 8, 2026. **Not approved for paid production rollout.**

## Intended identity

- Package: `com.snowglobe.overlay`; version name `0.22.3`; candidate code `100033`.
- Source starts from the reviewed `fix/ship-readiness-0216` release handoff.
- Keep the existing upload key and Play App Signing identity. No signing-key reset.
- Before upload, inspect Play Console's highest uploaded version code. If 100033 is already used, increase it and update all release/upgrade expectations before rebuilding.
- Do not replace, unpublish, or delete the working 0.22.2 release during preparation.

## Corrective behavior

- Remove the app-overlay window completely when all enabled visual content is invisible. A zero-opacity particle slider alone did not previously remove this window.
- Home and Settings expose **Use wallpaper only**. This immediately pauses and removes the over-app overlay, then opens Android's live-wallpaper selector. Canceling the picker does not resume the overlay. Android lets the user choose the wallpaper/screens; the action does not silently replace their wallpaper.
- A quick Pause shortcut is discoverable from Home. Full-screen and top-edge overlay modes remain explicit choices.
- Home footer shows both version name and version code for update identification.
- Complimentary access remains independent of verified paid ownership. There is no blanket premium bypass.

Android lets other apps reject obscured touches, including partial occlusion. SnowGlobe cannot detect or override each app's security filtering through its existing permissions. Wallpaper-only and Pause restore an unobscured screen; lowering window alpha is not a universal fix. No accessibility service, usage tracking, security bypass, or app inventory is added.

## Required verification

The workflow runs the complete JVM/lint and device suites on Android 15 and 17, including repeated cross-UID taps, typing, single-overlay checks, empty-overlay removal, recovery UI, full-height snow, keyboard/rotation and trial access. Results must be recorded from the completed run, not inferred from this checklist.

After signing, separate disposable-emulator jobs derive the baseline APK from the exact delivered 0.22.2 AAB (SHA-256 `bab3bc1f2f20d86f29e8b04c2eec0e548aab120c5e65a9c27ea5e1fdb0797b24`). Both APKs and the CI-only instrumentation are signed with the existing upload certificate. Each job tests:

1. Install baseline 100031, seed paused settings/scene, replace with 100033 using `adb install -r` without uninstalling, verify installed version, data, scene, overlay permission and sticky Pause.
2. Repeat from a separate fresh baseline with effects enabled; verify data and that effects resume after opening Studio and returning Home.

These establish same-certificate Android replacement only. They do **not** establish Play App Signing delivery, store propagation, installation on the owner's phone, trial receipt continuity across a real device update, payment, or performance at 120 Hz. Validate the update from the actual Play test track on a device already running 0.22.2, without uninstalling or clearing data. Check the new in-app version/build number and existing access-code expiry.

## Billing gate and remaining account work

The workflow accepts `require_paid_commerce`. For a billing-enabled candidate, run it with **true**. It supplies `-PrequirePaidCommerce=true`; an absent public licensing key blocks the build. A normal regression build may retain commerce disabled and must not be advertised as purchase-ready. A formatted RSA key only proves configuration shape, not the app/key match or a real transaction.

In Play Console, inspect the existing app's payment/merchant setup and actual uploaded version codes. Inspect/activate `holiday_plus_lifetime` as a single, non-consumable permanent **Buy** option (suggested option ID `lifetime`) at **USD 2.99** in the U.S.; review region availability and tax classification. Copy this exact app's licensing public key into the repository Actions variable `PLAY_BILLING_PUBLIC_KEY`. Never copy a private signing key, password or fingerprint into that field.

Use a separate Google license tester who is also eligible for the internal track. Install from Play and confirm Google's test notice and test instrument before completing any purchase. Use a clean device/profile without an active complimentary trial to distinguish paid unlocking from trial access.

Verify price/activation, cancellation, declined/pending/approved payments, acknowledgement, no repeat purchase, Restore, reinstall under the owning account, and refund-and-revoke followed by a successful online ownership query. Check a code-enabled installation separately to confirm its access remains independent. No subscription-renewal cancellation exists for this lifetime product. The client-only verifier does not provide immediate offline refund detection.

## Rollout approval

Present the actual diff, completed checks, exact signed artifact/checksum, commerce status and unresolved Console/test blockers before asking for approval of an internal-test upload. Paid production remains blocked until a real Play test transaction and Play-delivered update have been observed and the owner explicitly approves the rollout. Retain the old artifact and evidence.

## Persisted-scene correction

The first 100032 candidate passed the complete debug device suites but failed the signed update check: a baseline saved scene acquired trailing blank-line indentation after its SharedPreferences XML was reloaded. The old strict parser rejected that padding. Do not use that superseded candidate for rollout.

Build 100033 avoids terminal newlines in newly encoded scenes and accepts trailing blank lines from already saved scenes. Interior blank lines, unknown fields, duplicate fields, invalid ranges and unsupported schemas remain rejected. The signed update probe preserves the exact baseline scene bytes and validates its visual contents after replacement, so this compatibility fix is checked on persisted data rather than only an in-memory round trip.

The upgrade instrumentation is explicitly registered through AGP's final DSL hook; the full device suite continues to use AndroidJUnitRunner. The temporary immutable-candidate diagnostic workflow is archived, retaining the single gated signing workflow.


## Observed build 100033 handoff

Verified November 8, 2026, from source `ab6bae86602ddcf585d1cfb74d963fbc3f248c18`.
[Release run 42506126322](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42506126322) completed successfully, including both full quality jobs, the signing job and both signed-upgrade jobs.

| Gate | Android 15 / API 35 | Android 17 / API 37 |
| --- | --- | --- |
| JVM regressions and lint | Passed | Passed |
| Full device suite | 44 passed, no failures or skips | 44 passed, no failures or skips |
| Required screenshots | 12 verified | 12 verified |
| 100 cross-app taps, typing and single-window checks | Passed | Passed |
| Empty-window removal and wallpaper-only recovery | Passed | Passed |
| Signed 100031 → 100033 replacement, paused state | Passed | Passed |
| Signed 100031 → 100033 replacement, enabled state | Passed | Passed |
| Persisted visual settings, saved scene bytes/content and overlay permission | Preserved | Preserved |

The device suite also includes the 1,000-frame rendering stress case. These are emulator fixture results, not a performance guarantee for a physical Pixel or proof that every third-party app accepts overlays. Some protected controls still require Pause or wallpaper-only mode.

The signed bundle passed package/version metadata, existing upload-certificate, archive, bundle and 16 KB alignment checks. Actual signing-job metadata records **`commerceConfigured: false` / `commerce-not-configured`** for `holiday_plus_lifetime`. Paid checkout remains disabled; complimentary access remains independent.

Final artifact: **`SnowGlobe-0223-signed-AAB-28-1`**, ID **13628382065**.
[Download the verified artifact ZIP](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42506126322/artifacts/13628382065), then extract **`SnowGlobe-0.22.3-build100033.aab`**.
The ZIP is **8,112,527 bytes**, SHA-256 **`609c73dfd1e24bb4a2fe9c31d63380ca9cca2fccb23671a3606a80c921816477`**. This digest covers the enclosing ZIP, not the AAB; the per-AAB digest is in its `SHA256SUMS.txt`. The final archive was not separately downloaded and byte-verified in the chat workspace because that execution environment became unavailable. The CI checks and upload digest above were observed directly from GitHub.

The earlier 100032 artifact is superseded. No Play upload or rollout was performed. The highest Console version code, actual Play-delivered update on the owner's phone, payments profile, product activation, genuine licensing key, and real purchase/Restore/reinstall/refund behavior remain unverified. Complete the account-side setup and license-tester scenarios before paid production approval. A changed billing-enabled bundle must use a higher code if 100033 has already been uploaded.
