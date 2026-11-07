# SnowGlobe 0.21.6 release readiness and keyboard re-audit

Audit date: November 7, 2026 (UTC). Status: **The signed 0.21.6 / 100028 AAB is verified and ready for Google Play upload. All three C7 CI jobs passed, including the Android 15 and Android 17 gates and the signed-bundle checks. Physical Pixel acceptance and Google Play submission/approval remain unverified.**

## Candidate identity

| Item | Value |
| --- | --- |
| Package | `com.snowglobe.overlay` |
| Version | `0.21.6` / `100028` |
| Android | Minimum 26; compile and target 36 |
| Release source | `cdc1be36a049c807623babe08784b5ad362c4756` |
| Release workflow | [Run 42327417503](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42327417503) |
| Recovery source base | `e983369b297818e31d8ea1d0d4707609f9617c26` |
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

## Final typing and RGB corrections

Following the user's explicit publish instruction, a focused Samsung Keys Cafe comparison found that the short keypress sparkle had no frame-synchronized invalidation. It now uses one bounded Choreographer callback for the recent key only, with ambient palette work still throttled. The callback stops on expiry, editor replacement, reduced motion, power fallback and view shutdown. It does not change key geometry or defer text insertion.

The first Android 15 device run exposed a capitalization integration bug: Android returns a word-capitalization recommendation at the start of an empty field or paragraph, including when the editor requests sentence capitalization. Intersecting that result with the requested sentence flag incorrectly changed the keyboard to lowercase. Both the initial editor hint and the asynchronous result now accept Android's nonzero recommendation after the existing editor-type and caret checks. The uppercase-Q device assertion remains in place. See the [Android 15 capitalization implementation](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/text/TextUtils.java) and [Android's sample keyboard](https://android.googlesource.com/platform/development/+/master/samples/SoftKeyboard/src/com/example/android/softkeyboard/SoftKeyboard.java).

Three new service regressions use a real Android `EditText`/`InputConnection` to cover an empty field, a new paragraph, reopening the keyboard and moving inside a word. The empty-field and paragraph cases failed before the correction and passed afterward. Their observer waits for completed work without submitting an extra task to the service's bounded, coalescing queue. The final host suite includes these cases and all **33 keyboard-view regressions**, including the three animation/lifecycle cases.

## Current verification record

The table records the verified C7 evidence. C7 changes only test fixtures and CI setup/tooling; its production app code is unchanged from C4 source `bbfcb73be74c6a6570bee913923e30630fe6a486`, and it repeated the gated checks before producing the final deliverable. Its early preflight includes both keyboard editing and the exact discovery scenario, separately from the 50-test full suite. Earlier local results, C4 evidence and unsigned output are retained separately below; they do not identify the signed deliverable.

Both C7 OS jobs completed successfully, and their downloaded evidence archives identify the exact source. Each OS passed **both targeted preflight cases and all 50 full-suite device tests, with no failures, errors or skips**. The host suite passed **131 tests**, and **35 script regressions passed**. Debug lint reports **0 errors and 28 warnings**. The Android 17 archive is artifact `13555499817`, with verified SHA-256 `03396acf5c23ec19dfa4c57dc5acbcc2594bd026508fd660cd510cc02b1a8ba6`. The Android 15 archive is artifact `13556324548`, with verified SHA-256 `4f793cb4f1fdeee8e591a3c6c9ccd295da6184619ae981ba2b263b7c28023320`. The gated signed-bundle job also completed successfully, and the downloaded AAB was independently verified as recorded below.

| Verification | Observed result | Scope |
| --- | --- | --- |
| JVM and Android-simulation tests | **131 passed; 0 failed or skipped** | Includes 30 editing-engine regressions, 33 actual-view/touch/accessibility/native-render regressions, 3 real-editor service regressions, 4 dictionary cases and 2 thermal-failure cases. |
| Release/readiness script regressions | **35 passed** | Covers malformed/unsigned bundles, native-library metadata, required device evidence, release-path rules, AVD metadata and emulator readiness. Fixture tests are separate from actual device execution. |
| Debug lint | **0 errors; 28 warnings** | The current CI count includes seven additional online dependency-version recommendations relative to the earlier local snapshot. Existing warnings remain visible and were reviewed as described below. |
| Android 17 instrumentation | **2 targeted preflight cases and 50 full-suite tests passed; 0 failures or skips** | Separate preflight results do not inflate the full-suite count. The downloaded evidence archive identifies the exact C7 source and its SHA-256 was verified. |
| Android 15 instrumentation | **2 targeted preflight cases and 50 full-suite tests passed; 0 failures, errors or skips** | The downloaded evidence archive identifies the exact C7 source and its SHA-256 was verified. |
| Debug app, Android-test and tap-probe APKs | Built successfully | Used by the emulator jobs, including the cross-app keyboard smoke test. |
| C7 release build and lint | Passed | The completed job confirms successful release build and lint; the release warning count was not retained and is not asserted. |
| Final C7 signed release AAB | **Verified; ready for Play upload** | Signing followed both successful OS gates. Strict signature verification, the existing upload certificate, compiled manifest, official bundletool validation, native 16 KB checks and generated APK alignment all passed. Exact identity is below. |
| Portrait and constrained-landscape renders | Generated from the actual view through Robolectric native graphics and visually inspected | No clipped keys or footer overflow in these cases. These are synthetic Android-simulation renders, not phone captures. |
| Tap-probe companion editor | Requests sentence capitalization and runs under a separate application UID | Exercises actual cross-app insertion, deletion, correction and undo. The test companion is not packaged in SnowGlobe. |

The warning categories are existing style/API annotations, resource/icon/tool constructors, dependency advice, and two lifecycle/accessibility heuristics. `EffectsService.current` is cleared in `onDestroy`, and each committed touch calls `performClick` through `commitPress`, while virtual accessibility nodes send their own click events. The warnings have not been hidden. The device suite covers system-managed lifecycle cases; a complete physical TalkBack session remains an acceptance check.

The editing tests include 1,200 sequential typed characters with **zero synchronous text-read calls on that normal typing path**. This is a behavioral test, not a physical latency measurement. Selection-aware Backspace still uses one bounded editor read to avoid deleting the wrong selection or Unicode grapheme. Voice, glide, broad multilingual prediction and large-vocabulary suggestion quality are not implied by the result.

The Android 15/17 instrumentation ran on CI emulators, including the full-screen snow and cross-app keyboard scenarios. Host tests and native test renders ran separately. These results establish the tested behavior on those environments; they do not measure the owner's physical Pixel latency, battery use or complete TalkBack experience.

The C7 Android 15 emulator required one bounded launcher recovery before app installation, followed by 30 seconds of stable readiness; Android 17 required no recovery. This handled the emulator's startup environment before testing. No app assertion was retried or waived, and both full-suite evidence checks passed with six required screenshots per OS.

The C4 Android 15 visual review checked capture `23-autocorrect-and-rgb.png` and full-height snow captures 27–30, covering portrait, landscape, the open keyboard and dismissal. Capture 29 also shows the capitalized keyboard and active Shift. Capture 21 is taken before the smoke test's keyboard wait and contains an early probe view; it is not used as proof of the displayed keyboard. The subsequent uppercase-key and editing assertions passed.

## Final signed release artifact

The final output from [C7 run 42327417503](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42327417503) is a signed, verified AAB ready for Google Play upload. All three jobs completed successfully. The downloaded artifact archive and the extracted AAB were checked independently. Neither the historical unsigned checksum below nor the incomplete C4 packaging output identifies this deliverable.

| Item | Verified value |
| --- | --- |
| Deliverable | `SnowGlobe-0.21.6-build100028.aab` |
| AAB size | **7,450,877 bytes** |
| AAB SHA-256 | `bb5c88a8a55607c1b9083f03e32771c50094055df72b7ae4772a145f33742c77` |
| Source commit | `cdc1be36a049c807623babe08784b5ad362c4756` |
| GitHub workflow artifact | `13555594385`, from run `42327417503` |
| Artifact ZIP SHA-256 | `28c3c2797cafbca9f2dcd2b3299d8285e522b856f8eb80b03180a1ad2ccabc52` |
| Compiled manifest | `com.snowglobe.overlay`; version `0.21.6`; version code `100028`; target SDK `36` |
| Upload certificate SHA-256 | `73:45:EE:F8:97:0F:7F:8D:E6:20:BB:F7:C0:4B:28:03:0D:F9:C2:F7:A1:D6:8B:68:E5:E0:72:C1:04:8F:48:A4` |

Strict `jarsigner` verification passed, and the upload certificate matches the verified 0.21.5 baseline. Official bundletool validation and checks of the actual compiled manifest passed. Native ELF/RELRO and bundle 16 KB checks passed, and CI-generated APKs passed `zipalign`. The release build and lint passed; the release lint warning count was not retained and is not asserted. This establishes the artifact's tested build and signing identity, not Google Play approval or a physical Pixel performance benchmark.

## Historical local audit and unsigned inspection artifact

Before the final RGB and real-editor service follow-ups, the reconstructed local snapshot passed **125 JVM/Android-simulation tests**, **24 release-verifier tests**, and debug/release lint with **0 errors and 21 warnings in each variant**. Earlier compilation, optional thermal-listener and stale-capitalization-hint failures had been corrected. This is historical evidence for that earlier snapshot, not the final-run test count.

That local Gradle run completed successfully in 1 minute 12 seconds. Subsequent checks operated on its rebuilt unsigned AAB. Archive integrity, official bundletool 1.18.1 validation, package/version/service declarations and the native-library verifier all passed. Both 64-bit libraries met the ELF/RELRO 16 KB checks; both 32-bit libraries passed structural checks. The bundle requested 16 KB native packaging. The built debug APK also passed `zipalign -c -P 16 -v 4`.

- Artifact: `app/build/outputs/bundle/release/app-release.aab`
- Size: **7,440,148 bytes**
- SHA-256: `96a872d8a6b5bec2fb64fe85feafff36503d69cf1b7b7ebd0ad0526ea7a0966e`
- Package/version: `com.snowglobe.overlay` / `0.21.6` / `100028`; target SDK 36.
- Signature: **unsigned**, confirmed by `jarsigner` and the absence of JAR signing entries. This artifact is for inspection and cannot be offered as the signed Play release.

The release verifier's own second review found that an AndroidX library's harmless padded RELRO range was incorrectly rejected. The verifier now considers page-rounded protection and rejects writable data outside RELRO within either protected boundary page. Regressions cover both the valid bundled layout and genuinely unsafe adjacent writable data. No dependency or native-library replacement was necessary. Reference: [Android Bionic linker page-protection implementation](https://android.googlesource.com/platform/bionic/+/android16-qpr2-release/linker/linker_phdr.cpp).

Historical artifact-verification reports were retained in the adjacent workspace's `release-verification-final/`. Ordinary Gradle report directories are reused by later runs and are not an immutable record of that snapshot. Its source review also passed `git diff --check`; the scoped scan found no embedded private-key or credential patterns in the changed text files. These checks do not substitute for an independent security audit.

## Release and account gates

The user explicitly authorized publication and the signed release build on November 7, 2026. The reviewed source is published to `Jclemente0411/snowglobe-overlay`; the exact source and gated run are recorded above. The existing upload key is used, with no replacement signing identity introduced.

The candidate targets API 36, matching the current [Google Play target API requirement](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-gb) for new phone apps and updates from August 31, 2026. Native-library and packaging checks follow the current [Android 16 KB guidance](https://developer.android.com/guide/practices/page-sizes). These technical checks do not establish Google Play account acceptance.

The prior Play Console browser attempt was also blocked by automatic approval review of a redirect to the unapproved `https://google.play` origin. No Console release was submitted and account declarations/production access have not been inspected here.

The existing [published privacy policy](https://raw.githubusercontent.com/Jclemente0411/snowglobe-overlay/cdc1be36a049c807623babe08784b5ad362c4756/docs/PRIVACY.md) was retrieved without authentication on November 7, 2026 with HTTP 200. Its 4,410 bytes exactly matched `docs/PRIVACY.md` (SHA-256 `ec92327f5f5e67b2780b88781df6f2af58001245b9f08a04dc3079bbb4b46144`). It identifies the app, package and publisher; covers keyboard input, local settings/diagnostics, permissions, backup, retention and deletion; and provides the existing GitHub issues contact channel. No placeholder contact details were found. The policy URL configured in Play Console and Google's acceptance of it have not been verified; public retrieval does not establish policy approval or legal compliance.

Physical Pixel acceptance, Data safety and foreground-service declarations, store setup and any account-specific production-testing requirements remain separate gates. The proposed $2.99 lifetime purchase and downloadable 4K wallpaper catalogue are not implemented and must not be advertised as available.

## Physical Pixel acceptance

Use the exact final candidate and record device/build and display/navigation settings. Check rapid two-thumb typing, first/last letters, cursor moves, selected deletion, correction undo, passwords/email/URL/numeric fields, Enter/Search/Send, landscape, navigation clearance and TalkBack. Compare with Gboard in the same text field and with the same keyboard-height preference. Check snowfall through the bottom quarter after rotation, keyboard open/dismiss, app return and lock/unlock. Confirm Pause still permits protected purchases and that no decorative work changes key hitboxes. Measure sustained frame behavior, latency and battery use before making numerical performance claims.

## Earlier CI findings and corrections

Published commit `5ae36dad01d4e777d5c0efc7462a46c2c71f9e60` started [run 42320322023](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42320322023). Both API jobs passed release-verifier regressions, JVM tests, lint and test compilation. Android 17 then failed before any app test executed: its system services repeatedly restarted during installation. The failed run was prevented from signing.

The Android 17 startup failure matched the upstream old-avdmanager defect documented in [emulator-runner PR 494](https://github.com/ReactiveCircus/android-emulator-runner/pull/494) and [issue 482](https://github.com/ReactiveCircus/android-emulator-runner/issues/482). The correction pins verified Google command-line tools 23.0 at the path used by emulator-runner, validates AVD API metadata, requires stable unlocked system services, and captures bounded failure diagnostics. A verified-tools installation fixture also passed. Per-commit workflow concurrency preserves diagnostic runs while a corrected commit is tested.

The first Android 15 device run executed **50 tests: 49 passed and the uppercase-Q smoke assertion failed**. Both full-screen snow regressions passed. The real-editor capitalization defect identified above was reproduced and corrected; the assertion was retained.

[Run 42322775954](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42322775954) then exposed a race in a new host test: its observer task could evict the caps query from the capacity-one coalescing queue. Observing completed work without submitting another task corrected the test synchronization. C4 source `bbfcb73be74c6a6570bee913923e30630fe6a486` contains that test-only correction; it did not change production keyboard behavior. Both C4 OS gates, the signed build and release lint passed, but the subsequent packaging check stopped because `sdkmanager` was not on the bundle runner's path. C5 explicitly installs and addresses those tools without changing the app.

[C5 run 42324972216](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42324972216) then exposed two test/setup faults. An Android 17 launcher ANR, triggered during emulator startup before the app was installed, covered the successfully launched probe. On Android 15, a discovery test swiped past the already-visible Pixel setup section before checking it; the keyboard and full-screen snow cases passed. C6 corrected those setup conditions and checked discovery targets before bounded controlled scrolling. Both discovery assertions remain, and failure evidence is captured. C4's passing app evidence remains distinct from later artifact verification; these corrections did not change production app code.

The C6 discovery helper then treated UiAutomator's missing scroll-event response as proof that the list had ended, stopping after one gesture. C7 continues its bounded search using actual target visibility, scopes gestures to the app's main list, and captures live failure evidence before the activity closes. Both discovery assertions remain. This exact scenario now runs alongside keyboard editing in the early preflight; the separate full-suite gate remains in place. These test/tooling changes do not alter the production app or replace final artifact verification.
