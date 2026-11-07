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
| Word/sentence boundaries | Requested capitalization gets an immediate local boundary hint; the service also obtains editor cursor-capitalization state. | Type the next letter immediately after a space or sentence boundary, before the asynchronous reply. |
| Delete | Initial press deletes once; named repeat callbacks stop on cancellation, leaving the key, hiding the keyboard or editor replacement. | Hold, drag away, cancel, hide, and press again; no old timer may affect the new press. |
| Space-bar cursor movement | A deliberate horizontal drag moves by code points and suppresses space insertion. Small tap drift still types a space. | Verify directions, drag cancellation, selections and supplementary Unicode in a real editor. |
| Editor types/actions | Numeric and phone inputs use a dedicated keypad; email/URL punctuation and Search/Send/Next/Done actions follow editor metadata. Password fields begin without automatic capitalization. | Check text, multiline, email, URL, PIN, decimal, signed-number and phone fields in multiple applications. |
| Symbols and emoji | Two symbol pages avoid duplicate digit rows. The emoji key opens a local palette with a return to letters. A keyboard-switch key remains available. | Reach brackets, currency, punctuation and emoji; confirm one insertion per action and safe return to the current editor. |
| Accessibility | Canvas keys expose virtual nodes with useful action labels, bounds, click actions, accessibility focus and exploration hover events. | Run TalkBack and Switch Access on the actual IME, including suggestions and layout changes. Local node tests do not certify a complete assistive-technology session. |
| RGB rendering | Settings, labels, text metrics and palette buffers are cached. Wave colors and twinkle brightness interpolate using elapsed time. Idle updates are bounded; decoration yields while typing or holding a key. Reduced motion and available power/thermal state limit animation. | Inspect motion and measure input/render timing on the physical device with effects enabled; record observed results before making latency or battery claims. |

Android recommends touch targets of at least 48 by 48 dp. A ten-column portrait keyboard cannot provide that width on every phone. This layout uses full-cell targets, normal portrait row heights above that vertical recommendation, and virtual accessibility controls; narrower screens and strongly constrained landscape heights still require ergonomic review. See [Android accessibility guidance](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views).

## Verification evidence

`FastKeyboardViewRegressionTest` exercises actual Android measurement, MotionEvents, handler timing, accessibility nodes and native Canvas rendering under Robolectric. Its synthetic portrait and constrained-landscape images are written to `app/build/reports/keyboard-visuals/` when the tests pass. `FastKeyboardGeometryTest` and `FastKeyboardTouchTest` retain Android instrumented checks for the device/emulator path.

The completed current run passed all 125 JVM/Android-simulation tests, including 30 view regressions and 30 editing/service regressions. The portrait and constrained-landscape native renders were visually inspected. See `RELEASE_READINESS_0216.md` for the complete evidence and remaining gates. Native test renders verify this view's drawing; they do not show the full system IME window, device navigation behavior, physical typing latency or Google Play approval.

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
