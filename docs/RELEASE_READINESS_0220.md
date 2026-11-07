# SnowGlobe 0.22.0 release audit

Audit date: November 7, 2026. Candidate: `com.snowglobe.overlay`, version **0.22.0 / 100029**, minimum API 26 and target API 36. Local compilation and host tests have passed; the corrected device candidate and final signing remain in progress. The exact successful release source and artifact identity will be recorded after the gates complete.

## Scope delivered in source

- Removed all SnowGlobe keyboard services, input-method metadata, keyboard UI and keyboard-only code/tests. The compiled-package gate explicitly rejects renamed or disabled IMEs.
- Layered snowfall: four bounded depth planes, multiple cached crystal silhouettes, softer foreground flakes, two time-based drift currents, stable depth ordering and full-height initial distribution. The 128-particle limit and single frame scheduler are preserved.
- Existing full-screen overlay, rotation, system-keyboard open/dismiss, pause, unlock resume, power/thermal response and lock-screen wallpaper safety remain under regression coverage.
- Refined adaptive launcher icon and an effects-focused home screen with clear Free/Plus boundaries.
- Four original, locally rendered 2160 × 3840 wallpaper designs; two free and two Plus. Export uses Android's selected document destination, runs off the UI thread, and closes streams/recycles the bitmap. No broad storage permission.
- Real one-time Holiday Plus purchase/restore implementation, signed-receipt ownership, pending/acknowledgement/revocation handling and fail-closed checkout when the genuine verification key/product are unavailable.
- Updated in-app and published privacy policy to match keyboard removal, billing proof and wallpaper export.

## Release gates

The workflow runs every currently declared device test, including full snow coverage, installed-package scope, Plus/Restore discovery and the artwork gallery. Required screenshots include the actual Home/Snow/Lights/Decor UI, large-text layout, Plus, wallpapers and snow with rotation/system keyboard. App failures remain fatal. A bounded launcher recovery is allowed only before app installation, as retained from the previously verified runner.

Signing uses the existing upload key only after both Android 15 and Android 17 jobs pass. The final AAB must pass strict signature verification, compiled package/version/service checks, bundletool validation and native/APK 16 KB alignment. The bundle includes exact source and checksum records plus a separate `commerce-status.txt`.

## Completed local verification

- **90 JVM/Android-simulation tests passed** on the initial integrated app, with zero failures, errors or skips. These include 19 billing-controller cases, four premium-policy cases, six native snow-renderer cases and three artwork cases. After the dependency correction, all **24 focused billing/policy/actual-SDK cases passed**, and the updated instrumentation APK compiled. The new SDK case brings the complete declared host suite to 91; its full rerun remains part of the next workflow.
- **43 release/readiness script tests passed.** Their package checks reject any input-method service, including disabled or renamed declarations, and inherited networking/diagnostic-transport components.
- Kotlin/Java compilation, debug lint, instrumentation APK packaging and the separate tap-probe APK build all completed successfully with JDK 17 and Gradle 8.11.1.
- Debug lint reported **zero errors and 18 warnings**. The warnings comprise eight `UseKtx`, three `ObsoleteSdkInt`, three `UseRequiresApi`, and one each of `SetTextI18n`, `ViewConstructor`, `MonochromeLauncherIcon` and `StaticFieldLeak`. The v33 adaptive icon includes its monochrome layer; the v26 fallback triggers the icon hint. The service's existing static live-instance reference is cleared in `onDestroy`. These are retained findings, not a claim of a warning-free lint report.
- Reviewed all nine actual native renders: three portrait snow styles, landscape crystals, all four wallpapers and the launcher icon. Snow extends to the bottom edge in every rendered orientation, with visible size/depth differences. Artwork is complete and the icon remains centered and uncropped. The 4K export test verifies real 2160 × 3840 PNG output.
- A second independent purchase review found and corrected two cross-controller ordering cases before this source was frozen: a stale empty query after a new purchase, and a late acknowledgement after a newer revocation. Shared receipt-observation generations now guard both; the corresponding regressions are included in the passing billing tests.

## Paid-release limitation

The prepared product is `holiday_plus_lifetime`, USD 2.99 once, in the existing app. Build metadata records whether a valid-format RSA licensing public key was supplied; the flag alone cannot prove that the key belongs to this app's Console listing. App/key correspondence, product activation and a real Play test transaction require separate verification. Missing configuration does not create a purchasable product or grant a local entitlement. It preserves a usable free app, with checkout disabled. See [Holiday Plus setup](HOLIDAY_PLUS_SETUP.md).

The Cloud browser's attempted Play Console access was rejected by automatic approval review because of an unapproved `google.play` redirect. No Console state or real test purchase has been verified, and the block has not been bypassed. Play activation, account declarations, Google review and any account-specific production-access requirements are distinct from an AAB passing technical checks.

## Initial device attempt

The [first 0.22.0 candidate](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42383243986), source `2ac86d07bb15747e7b8686569c04acd3dd6bb534`, passed all three Android 15 preliminary cases, then lost the entire emulator during the full suite after three completed cases. ADB reported that the device was absent and the emulator console port refused connections. The interrupted case had no assertion message or stack trace; captured guest logs did not show an app fatal exception at the interruption. This is an **incomplete, failed run**, not a passing app suite.

That attempt did not retain host kernel, cgroup or emulator-crash records, so the evidence cannot establish whether host memory pressure or a native emulator failure caused the loss. The next runner revision lowers only the instrumentation Gradle heap/worker budget and AVD RAM, and retains bounded host diagnostics before and after execution. These are resource-risk reduction and diagnostic corrections, not proof of an identified OOM defect. App tests, screenshot requirements and failure gates remain unchanged, and no app assertion is automatically retried.

Android 17 completed all 37 original device cases in that first attempt: **35 passed, two failed, zero skipped**. Full-height snow, rotation, the actual system keyboard and wallpaper-gallery cases passed. The two failures exposed (1) a Plus UI test that inspected an enabled Text descendant rather than its disabled Button ancestor, and (2) real network permissions inherited from Billing's DataTransport dependencies. The corrected UI test examines and touches the actual disabled control, checks that checkout does not open, and captures failures while the sheet is still present. The privacy correction retains SDK classes and removes the diagnostic backend/upload components and their network permissions together; actual SDK and scheduler tests are required. See [dependency isolation](BILLING_DEPENDENCY_ISOLATION.md).

## Emulator diagnosis and corrected gates

The [second candidate](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42384417082), source `f592905f4ac095ea4db505dc7a53ba78507f4d59`, retained the missing host diagnostics. Android 15 again lost the emulator during preliminary scene discovery. This time `coredumpctl` recorded **SIGSEGV in qemu-system-x86_64-headless** (Android Emulator 37.2.13.0). Host available memory exceeded 11 GiB before execution and all retained cgroup OOM counters were zero. Memory exhaustion is not supported as the cause of that run. The captured record proves a host native emulator crash but does not identify its crashing subsystem.

The workflow now uses the current `-gpu software` selector instead of `swiftshader_indirect`, which Google documents as deprecated. AVD memory is restored to 4 GiB, the required minimum for API 37 phone images. Bounded coredump metadata/stack capture is retained without copying the 1.1 GiB core. This is a supported runner configuration correction; a fresh complete suite is still required to establish that it resolves the observed failure. References: [graphics modes](https://developer.android.com/studio/run/emulator-acceleration), [emulator release notes](https://developer.android.com/studio/releases/emulator).

The corrected device suite has **38 cases, four preliminary cases and 11 required screenshots per Android version**. The installed-package permission check and actual Billing SDK startup/read-only-query/telemetry-rejection smoke now run during preflight, before the long UI suite. Fresh app data is restored before running the complete suite. No purchase is launched, no assertion is waived, and signing remains blocked until both full suites pass.

## Evidence to record after completion

- Exact release source and successful workflow run.
- JVM, script and both device-suite results; lint errors and warnings.
- Visual review of actual native snow/artwork renders and emulator captures.
- Signed artifact filename, byte size, AAB SHA-256 and archive digest.
- Existing upload certificate comparison and local bundle validation.
- Actual `commerceConfigured` result and any unverified physical-device or Console steps.

No physical Pixel frame-time, 120 Hz guarantee, battery benchmark, complete TalkBack session or Google approval is claimed by emulator/host results.
