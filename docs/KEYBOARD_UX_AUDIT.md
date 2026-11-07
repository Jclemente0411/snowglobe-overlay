# SnowGlobe keyboard UX audit

Reviewed November 7, 2026. This checklist separates implemented typing behavior from device acceptance and from the broader Gboard feature set. The comparison uses official Google help and Android guidance; it does not claim access to Gboard's private implementation or performance measurements.

## Corrected behavior and acceptance checks

| Area | Current implementation | Acceptance check |
| --- | --- | --- |
| Portrait geometry | Default letter rows are 58 dp and the footer is 62 dp. The height preference still adjusts them. A separate 48 dp bottom-clearance region preserves the requested raised position. | Inspect the native portrait render and confirm the actual IME window on the target Pixel; system navigation adds its own insets. |
| Constrained size | Row geometry scales to the measured height, including landscape. Controls remain above the reserved navigation region. | Inspect every virtual-key bound and the constrained landscape render; test rotation and display/font-size changes on-device. |
| Touch coverage | Visual key gaps remain inside touch targets. Staggered home-row edge keys accept the adjacent outer margin. | Dispatch presses at cell edges and inspect real-device typing accuracy. |
| Two-thumb typing | Pending characters commit in press order when the later thumb lifts first, with no second commit on the older thumb's release. | Exercise nonsequential pointer IDs and reversed release order. |
| Shift and capitals | One-shot Shift, held-Shift chords, double-tap caps lock and manual overrides are supported. Capitalize-all-character fields retain their case. | Check both Shift release orders, cancelled drags, repeated Shift taps and asynchronous editor replies. |
| Word/sentence boundaries | Requested capitalization gets an immediate local boundary hint; the service also obtains editor cursor-capitalization state. Android's nonzero recommendation is honored even when its returned word-boundary flag differs from the requested sentence flag. | Real `EditText` service regressions cover empty fields, new paragraphs, reopening and moving inside a word. The cross-app device smoke test still requires an uppercase first letter. |
| Delete | Initial press deletes once; named repeat callbacks stop on cancellation, leaving the key, hiding the keyboard or editor replacement. | Hold, drag away, cancel, hide, and press again; no old timer may affect the new press. |
| Space-bar cursor movement | A deliberate horizontal drag moves by code points and suppresses space insertion. Small tap drift still types a space. | Verify directions, drag cancellation, selections and supplementary Unicode in a real editor. |
| Editor types/actions | Numeric and phone inputs use a dedicated keypad; email/URL punctuation and Search/Send/Next/Done actions follow editor metadata. Password fields begin without automatic capitalization. | Check text, multiline, email, URL, PIN, decimal, signed-number and phone fields in multiple applications. |
| Symbols and emoji | Two symbol pages avoid duplicate digit rows. The emoji key opens a local palette with a return to letters. A keyboard-switch key remains available. | Reach brackets, currency, punctuation and emoji; confirm one insertion per action and safe return to the current editor. |
| Accessibility | Canvas keys expose virtual nodes with useful action labels, bounds, click actions, accessibility focus and exploration hover events. | Run TalkBack and Switch Access on the actual IME, including suggestions and layout changes. Local node tests do not certify a complete assistive-technology session. |
| RGB rendering | Settings, labels, text metrics and palette buffers are cached. Wave colors and twinkle brightness interpolate using elapsed time. A short keypress effect uses a bounded frame-synchronized callback for the recent key. Idle updates are throttled; ambient decoration yields while typing or holding a key. Reduced motion and available power/thermal state limit animation. | Animation/lifecycle regressions verify callback cleanup and fixed key geometry. Inspect motion and measure input/render timing on the physical device before making latency or battery claims. |

Android recommends touch targets of at least 48 by 48 dp. A ten-column portrait keyboard cannot provide that width on every phone. This layout uses full-cell targets, normal portrait row heights above that vertical recommendation, and virtual accessibility controls; narrower screens and strongly constrained landscape heights still require ergonomic review. See [Android accessibility guidance](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views).

## Verification evidence

`FastKeyboardViewRegressionTest` exercises actual Android measurement, MotionEvents, handler timing, accessibility nodes and native Canvas rendering under Robolectric. Its synthetic portrait and constrained-landscape images are written to `app/build/reports/keyboard-visuals/` when the tests pass. `FastKeyboardGeometryTest` and `FastKeyboardTouchTest` retain Android instrumented checks for the device/emulator path.

The final release source is `cdc1be36a049c807623babe08784b5ad362c4756` in [C7 release run 42327417503](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42327417503). All three CI jobs passed, both OS evidence archives were verified, and the signed `SnowGlobe-0.21.6-build100028.aab` is verified and ready for Google Play upload. C7 changes only test fixtures and CI setup/tooling; the production app code is unchanged from C4 source `bbfcb73be74c6a6570bee913923e30630fe6a486`.

The C7 Android 15 and Android 17 evidence archives have been downloaded, their SHA-256 values verified and their exact source confirmed. Each OS passed **both targeted preflight cases and all 50 full-suite device tests, with no failures, errors or skips**. The host suite passed **131 tests**, including **33 view regressions, 30 editing-engine regressions and 3 real-editor service regressions**; **35 script regressions passed**. Debug lint reports **0 errors and 28 warnings**. Preflight results are separate from the full-suite evidence and do not inflate its count.

The earlier [C4 run 42323195414](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42323195414) also passed the host suite and both OS device gates, but stopped at a later packaging-tool setup step. C7 repeats the full-suite gates for the final artifact's exact source and adds the exact discovery scenario to the early preflight. Earlier evidence does not substitute for this final-run verification.

The portrait and constrained-landscape native renders were visually inspected. CI instrumentation additionally exercises the actual system IME and separate-app editing path, including first-letter capitalization, insertion, deletion, correction undo and navigation clearance. The C4 Android 15 visual review used keyboard captures 23 and 29, plus snow captures 27–30 for rotation and keyboard open/dismiss; early capture 21 precedes the keyboard wait and is not keyboard evidence. See [release readiness](RELEASE_READINESS_0216.md) for artifact identity, verified results and remaining acceptance checks. Emulator results do not measure the owner's physical typing latency or establish Google Play approval.

## Gboard features that are outside this implementation

Gboard's documented feature set also includes long-press accent choices, glide typing, voice input, multilingual layouts, personal dictionary controls, broader emoji/GIF content and advanced suggestions. SnowGlobe does not implement all of these. Its current suggestions use the bundled local English dictionary; the emoji palette is a small local selection. The release should describe the implemented controls and appearance without claiming complete Gboard parity.

## Official sources

- [Gboard: use your keyboard](https://support.google.com/gboard/answer/2842292?co=GENIE.Platform%3DAndroid&hl=en) — space-bar cursor movement, caps lock, long-press alternatives, emoji and language switching.
- [Gboard: theme, sound and vibration](https://support.google.com/gboard/answer/6102154?hl=en) — independent appearance and feedback settings.
- [Gboard: suggestions and corrections](https://support.google.com/gboard/answer/7068415?co=GENIE.Platform%3DAndroid&hl=en) — candidate selection and correction undo.
- [Gboard: glide typing](https://support.google.com/gboard/answer/2811346?hl=en) — an additional input method, beyond ordinary key tapping.
- [Android: create an input method](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method) — editor types, IME lifecycle, input connections and settings.
- [Android: accessible custom views](https://developer.android.com/guide/topics/ui/accessibility/views/custom-views) — virtual control hierarchies, accessibility events and actions.
- [Android: optimize a custom view](https://developer.android.com/develop/ui/views/layout/custom-views/optimizing-view) — allocation, redraw and layout work on frequently executed paths.
- [Samsung: Good Lock and Keys Cafe](https://www.samsung.com/levant/support/mobile-devices/good-lock-elevate-your-samsung-experience/) — key, background and font colors, plus color and motion customization. The comparison concerns documented behavior; Samsung's proprietary implementation is not available here.
- [Android 15: capitalization behavior](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/text/TextUtils.java) and [Android sample keyboard](https://android.googlesource.com/platform/development/+/master/samples/SoftKeyboard/src/com/example/android/softkeyboard/SoftKeyboard.java) — the actual caps recommendation can use the word-boundary flag at the beginning of a sentence editor or paragraph.
