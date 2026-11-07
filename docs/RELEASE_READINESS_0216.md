# SnowGlobe 0.21.6 release readiness and keyboard re-audit

Audit date: November 7, 2026 (UTC). Status: **local build and automated re-audit passed; physical-device acceptance, signed release and Play submission remain unverified**.

## Candidate identity

| Item | Value |
| --- | --- |
| Package | `com.snowglobe.overlay` |
| Version | `0.21.6` / `100028` |
| Android | Minimum 26; compile and target 36 |
| Source base | `e983369b297818e31d8ea1d0d4707609f9617c26` |
| Working branch | `fix/ship-readiness-0216` |
| Commerce | All features unlocked; no purchase or entitlement implementation |

## Recovery and evidence integrity

The temporary workspace reset after the prior audit. The unpublished source and build outputs from that audit were no longer available. The current checkout was restored from the exact public source base above, and the corrections were reconstructed from the preserved review history before this stronger audit. The previous report of 61 JVM passes and an unsigned AAB is historical evidence only: it does not certify this reconstructed candidate. The current reconstructed candidate has its own verification record below.

The user's Gboard and SnowGlobe screenshots were recovered for private visual comparison. They informed key proportions and bottom-row placement. Private screenshots and conversation content are not included in the repository or release artifacts.

## Audit scope and concrete corrections

- Full display overlay bounds, keyboard resize behavior, full-height particle travel and user-selected top-quarter mode.
- Reliable overlapping-thumb input order, Shift/chords/caps, deletion and delayed repeat cleanup.
- Correct selected-text deletion, Unicode-safe cursor/delete behavior, guarded autocorrect/undo and selection callback handling.
- Bounded local English corrections; valid words must not be changed merely because the small bundled vocabulary omits them.
- Responsive RGB rendering, cached configuration, lifecycle cleanup, and reduced animation during typing.
- Keyboard height/proportions, navigation clearance, constrained layouts, editor actions and privacy-sensitive input modes.
- Familiar spacebar cursor movement and accessibility of the custom Canvas keyboard.
- Android-simulation regressions executed locally as well as device instrumentation and native rendering evidence.
- Privacy disclosure, accurate setup descriptions, compiled bundle checks, 16 KB libraries, and a release workflow that gates signing on Android 15/17 verification. Sixteen superseded release workflows were archived so they cannot bypass that gate.

## Gboard reference and practical limits

Google documents spacebar cursor movement, Shift double-tap caps lock, long-press alternatives, word suggestions, glide typing and other input modes. See [Gboard keyboard help](https://support.google.com/gboard/answer/2842292) and [glide typing help](https://support.google.com/gboard/answer/2811346). These describe externally visible behavior, not access to Google's proprietary implementation or language models.

Android warns that `InputConnection` reads can perform slow interprocess round trips. The ordinary key-commit path must not wait for surrounding-text retrieval or decorative work. See [InputConnection](https://developer.android.com/reference/android/view/inputmethod/InputConnection) and [creating an input method](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method).

Samsung describes Keys Cafe key/background/font colors and color/motion effects in [Good Lock support](https://www.samsung.com/levant/support/mobile-devices/good-lock-elevate-your-samsung-experience/). SnowGlobe's RGB decoration does not replace the requirement for a reliable input engine.

This candidate must be described in terms of the behavior verified below. It has no glide typing, voice typing, multilingual prediction engine, clipboard manager or Google-equivalent language model. Matching perceived fluidity, accuracy and layout on the owner's Pixel requires device comparison; no percentage of Gboard parity or numerical 120 Hz claim is established by a source review or host-side test.

## Final RGB and publication follow-up

Following the user's explicit publish instruction, a focused Samsung Keys Cafe comparison found that the short keypress sparkle had no frame-synchronized invalidation. It now uses one bounded Choreographer callback for the recent key only, with ambient palette work still throttled. The callback stops on expiry, editor replacement, reduced motion, power fallback and view shutdown. It does not change key geometry or defer text insertion.

After this narrow follow-up, all **33 keyboard-view regressions passed**, including three new animation/lifecycle cases. The full release workflow now needs to record the combined app-suite result and Android 15/17 device results for the published commit. The 125-test record and unsigned artifact checksum below describe the earlier local audit snapshot, before this RGB follow-up; they must not be substituted for the eventual signed artifact's evidence.

## Current verification record

The reconstructed candidate passed the full local Gradle run on November 7, 2026. Earlier failures were corrected before this result: the custom accessibility callback had a Java initialization error, optional thermal-listener registration could throw while opening the keyboard, and deleting punctuation or moving the cursor could preserve a stale capitalization hint. The thermal monitor now degrades conservatively, with explicit failure-path regressions. The final three touch regressions verify clearing stale capitalization hints and resetting the caps-lock gesture sequence after cursor movement.

| Verification | Observed result | Scope |
| --- | --- | --- |
| JVM and Android-simulation tests | **125 passed; 0 failed, errored or skipped** across 16 classes | Includes 30 editing/service regressions, 30 actual-view/touch/accessibility/native-render regressions, 4 dictionary cases and 2 thermal-failure cases. |
| Release-verifier regressions | **24 passed** | Tests malformed/unsigned bundles, native-library metadata, required device evidence and release-path rules using fixtures. These are not actual device-test results. |
| Debug and release lint | **0 errors; 21 warnings in each variant** | Reviewed warnings cover existing style/API annotations, resource/icon/tool constructors, dependency advice, and two lifecycle/accessibility heuristics discussed below. |
| Debug app and Android-test APKs | Built successfully | Device tests compile; their execution remains pending. |
| Release AAB | Built successfully as an **unsigned inspection artifact** | Official bundletool validation, compiled manifest identity, native ELF/RELRO and bundle packaging checks passed. Exact identity is below. It is not a Play-uploadable release. |
| Portrait and constrained-landscape renders | Generated from the actual view through Robolectric native graphics and visually inspected | No clipped keys or footer overflow in these cases. These are synthetic Android-simulation renders, not phone captures. |
| Tap-probe companion APK | Rebuilt after its editor fixture was corrected to request sentence capitalization | Enables meaningful cross-app keyboard smoke testing on the later device run. |

The two substantive-looking lint heuristics were reviewed in source: `EffectsService.current` is cleared in `onDestroy`, and each committed touch calls `performClick` through `commitPress`, while virtual accessibility nodes send their own click events. The warnings have not been hidden. Full TalkBack operation and system-managed lifecycle behavior still require device verification.

The editing tests include 1,200 sequential typed characters with **zero synchronous text-read calls on that normal typing path**. This is a behavioral test, not a physical latency measurement. Selection-aware Backspace still uses one bounded editor read to avoid deleting the wrong selection or Unicode grapheme. Voice, glide, broad multilingual prediction and large-vocabulary suggestion quality are not implied by the result.

The user explicitly authorized publishing the reviewed changes and producing the signed AAB on November 7, 2026. Android 15/17 instrumentation will run through the gated GitHub workflow; its observed results must be recorded before the release is certified. The local environment has no connected Android device or KVM-backed emulator. None of the added device tests, full-screen snow screenshots, physical typing measurements or real TalkBack sessions is represented as having run here.

## Exact inspected local artifact

The final consolidated Gradle run completed successfully in 1 minute 12 seconds. Subsequent checks operated on that rebuilt AAB, not the earlier pass-3 snapshot. Archive integrity, official bundletool 1.18.1 validation, package/version/service declarations and the native-library verifier all passed. Both 64-bit libraries meet the ELF/RELRO 16 KB checks; both 32-bit libraries passed structural checks. The bundle requests 16 KB native packaging. The built debug APK also passed `zipalign -c -P 16 -v 4`.

- Artifact: `app/build/outputs/bundle/release/app-release.aab`
- Size: **7,440,148 bytes**
- SHA-256: `96a872d8a6b5bec2fb64fe85feafff36503d69cf1b7b7ebd0ad0526ea7a0966e`
- Package/version: `com.snowglobe.overlay` / `0.21.6` / `100028`; target SDK 36.
- Signature: **unsigned**, confirmed by `jarsigner` and the absence of JAR signing entries. This artifact is for inspection and cannot be offered as the signed Play release.

The release verifier's own second review found that an AndroidX library's harmless padded RELRO range was incorrectly rejected. The verifier now considers page-rounded protection and rejects writable data outside RELRO within either protected boundary page. Regressions cover both the valid bundled layout and genuinely unsafe adjacent writable data. No dependency or native-library replacement was necessary. Reference: [Android Bionic linker page-protection implementation](https://android.googlesource.com/platform/bionic/+/android16-qpr2-release/linker/linker_phdr.cpp).

Evidence from this run is retained locally in `app/build/test-results/testDebugUnitTest/`, `app/build/reports/`, and the adjacent workspace's `release-verification-final/`. The final source review also passed `git diff --check`; the changed text files contain no embedded private-key or credential patterns found by the scoped scan. These checks do not substitute for an independent security audit.

## Release and account gates

Automatic approval review originally rejected public publication pending explicit authorization. On November 7, 2026, the user explicitly instructed that all reviewed changes be pushed and the signed release built. That authorization now permits publication to `Jclemente0411/snowglobe-overlay` and use of the existing release-signing workflow. The exact published commit, completed device runs and signed artifact must be recorded as they become available.

Run the prepared Android 15/17 workflow and sign with the existing upload key only after those jobs pass. Check the exact source commit, bundle identity, certificate and checksum before distributing an AAB.

The prior Play Console browser attempt was also blocked by automatic approval review of a redirect to the unapproved `https://google.play` origin. No Console release was submitted and account declarations/production access have not been inspected here.

Physical Pixel acceptance, a publicly accessible privacy policy, Data safety and foreground-service declarations, store setup and any account-specific production-testing requirements remain separate gates. The proposed $2.99 lifetime purchase and downloadable 4K wallpaper catalogue are not implemented and must not be advertised as available.

## Physical Pixel acceptance

Use the exact final candidate and record device/build and display/navigation settings. Check rapid two-thumb typing, first/last letters, cursor moves, selected deletion, correction undo, passwords/email/URL/numeric fields, Enter/Search/Send, landscape, navigation clearance and TalkBack. Compare with Gboard in the same text field and with the same keyboard-height preference. Check snowfall through the bottom quarter after rotation, keyboard open/dismiss, app return and lock/unlock. Confirm Pause still permits protected purchases and that no decorative work changes key hitboxes. Measure sustained frame behavior, latency and battery use before making numerical performance claims.

## First published CI run and runner correction

Published commit `5ae36dad01d4e777d5c0efc7462a46c2c71f9e60` started [run 42320322023](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42320322023). Both API jobs passed release-verifier regressions, JVM tests, lint and test compilation. Android 17 then failed before any app test executed: its system services repeatedly restarted during installation. The failed run was prevented from signing.

The failure matches the upstream old-avdmanager defect documented in [emulator-runner PR 494](https://github.com/ReactiveCircus/android-emulator-runner/pull/494) and [issue 482](https://github.com/ReactiveCircus/android-emulator-runner/issues/482). The follow-up pins verified Google command-line tools 23.0 at the path used by emulator-runner, validates AVD API metadata, requires stable unlocked system services, and captures full failure diagnostics. It passed all 31 release/readiness regressions plus an actual verified-tools installation fixture. Per-commit workflow concurrency preserves diagnostic runs while a corrected commit is tested. Actual device and signed-bundle results remain pending until the corrected workflow completes.
