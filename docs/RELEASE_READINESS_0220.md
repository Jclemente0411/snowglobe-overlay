# SnowGlobe 0.22.0 release audit

Audit date: November 7, 2026 (UTC).

## Release identity and decision

- Application: `com.snowglobe.overlay`.
- Version: **0.22.0**, version code **100029**.
- Minimum Android API: **26**. Target/compile API: **36**.
- Release source: [`914164b561dc761f501e59df026b4490c738ad2c`](https://github.com/Jclemente0411/snowglobe-overlay/commit/914164b561dc761f501e59df026b4490c738ad2c).
- Source tree: `b98fd5a8a5c980b38cf709b4b27cc999d09401df`.
- Branch: `fix/ship-readiness-0216`.
- Canonical verification: [run 42393453546, number 18, attempt 1](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42393453546).

**The canonical run completed successfully at 17:10:59 UTC. Both device jobs and the existing-key signing/verification job passed. The signed free-app AAB is available for download below. Holiday Plus is not commercially enabled in this build.**

The product is one free application with a **USD 2.99 one-time Holiday Plus upgrade**, product ID `holiday_plus_lifetime`. It is not a second paid package. Missing Play billing configuration leaves the free app usable and checkout disabled. A technical release pass does not establish Console product activation, a real purchase, production-access eligibility, or Google approval.

## Changes included

### Snow, presentation and controls

- Removed the entire SnowGlobe custom keyboard: production services, input-method declaration and metadata, keyboard setup/customization UI, and keyboard-only code/tests. Compiled-package verification rejects any IME service, even if renamed or disabled. The normal system keyboard remains available to users and is included in overlay tests.
- Snow starts distributed across the whole overlay. Full-height coverage and migration of the old coverage preference address the prior quarter-screen symptom. An explicitly selected top-quarter effect remains an intentional option.
- Added four bounded depth planes, varied cached crystal/star silhouettes, softer foreground flakes, time-based drift currents, and stable depth ordering. Density zero produces no particles. The renderer retains its 128-particle cap and one frame scheduler, with power, thermal and reduced-motion behavior.
- Refined the adaptive snow-globe launcher icon, including the Android 13+ monochrome layer, and the effects-focused Christmas Studio home screen with a live preview, clearer controls, and Free/Plus boundaries.
- Retained full-screen overlay, rotation, system-keyboard open/dismiss recovery, pause, unlock resume and live-wallpaper safeguards. Saved/copied presets cannot grant paid effects.
- Added four original 2160 × 3840 wallpaper designs: Winter Lodge and Moonlit Pines are free; Candlelight Village and Aurora Night require Plus. Export uses Android's selected document URI, renders off the UI thread, closes streams, recycles the bitmap, and handles allocation failure. Entitlement is checked before selection/export. No broad storage permission is requested.

### Purchase correctness and privacy

- Holiday Plus uses Play Billing 9.1.0 and a fresh localized permanent Buy offer. The implementation includes restore, pending/cancelled purchases, acknowledgement/retry, and revocation.
- Ownership requires a verified signed receipt with matching package, product, purchase token and purchased state. Proof is atomically stored in `noBackupFilesDir`; ordinary saved preferences cannot unlock Plus.
- Shared receipt-observation generations prevent an older empty query from undoing a new purchase and prevent a late acknowledgement from restoring an entitlement after a newer revocation.
- An unsuccessful ownership refresh preserves a previously valid offline proof; a successful authoritative empty query revokes it. This remains client-side verification: there is no server/RTDN implementation.
- With the genuine licensing public key absent, the production Play gateway/checkout remains disabled. The overlay is paused before configured checkout. Restore does not fabricate ownership.
- Retained the complete Billing/DataTransport class graph. Earlier class exclusions caused SDK startup failure and were corrected. Removed the inherited INTERNET/ACCESS_NETWORK_STATE permissions and the three diagnostic transport backend/upload manifest components together. Actual SDK startup, read-only product query, shutdown and transport rejection have host and ART coverage.
- This dependency boundary is a pinned implementation choice, not a documented public Google telemetry opt-out API. Billing permission, SDK metadata and Play proxy activities remain.
- Updated the in-app and repository privacy policy for keyboard removal, receipt verification and wallpaper export. The URL/content configured in Play Console was not independently inspected.

See [billing dependency isolation](BILLING_DEPENDENCY_ISOLATION.md), [Holiday Plus setup](HOLIDAY_PLUS_SETUP.md), and [Play release handoff](PLAY_RELEASE_0220.md).

## Canonical release gates

Both Android jobs completed successfully on the exact release source.

| Gate | Android 15 | Android 17 |
| --- | --- | --- |
| Job | [128465686092](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42393453546/job/128465686092) | [128465685872](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42393453546/job/128465685872) |
| Script regressions | 43 passed | 43 passed |
| JVM tests, debug lint and APK compilation | Passed | Passed |
| Preflight device suite | 5 completed, successful | 5 completed, successful |
| Full device suite | 38 passed | 38 passed |
| Required screenshot evidence | 11 accepted | 11 accepted |
| Exact emulator | 37.1.11 / build 15917651 | 37.2.13 / build 16740729 |
| Android API / architecture | 35 / x86_64 | 37 / x86_64 |

The five preflight cases are an early subset, not five additional unique features. The source-aware final verifier explicitly reported “Verified 38 passed device tests and 11 required screenshots” on each OS. No app assertion was waived, no app test was automatically retried, and neither canonical job required a rerun.

The JVM task executed and succeeded in both jobs. **91 host cases, with zero failures/errors/skips, were independently counted in C10's downloaded report for unchanged app/test sources.** C11's fresh report was not independently extracted during the workspace outage; its completed Gradle task is verified from the job log. Do not present the prior independent count as a fresh C11 HTML/XML inspection.

Signing is downstream of both complete quality jobs. The pipeline restores the existing upload key from existing secrets, builds the release, runs release lint, verifies the signed JAR strictly against that key, validates the bundle, checks compiled package/version/service declarations, and checks native and generated universal-APK 16 KB compatibility. Temporary signing material is removed and is excluded from the uploaded artifact.

The [signed-bundle job 128469777815](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42393453546/job/128469777815) completed successfully. Build/release lint, strict signature verification, bundle metadata validation, native-library checks, universal-APK alignment and artifact upload all passed. The completed log explicitly records the expected package, version 0.22.0/code 100029, target SDK 36, product ID and `commerceConfigured: False`. The temporary-key cleanup step also succeeded.

## Artifact provenance and delivery

- [Download the signed release ZIP](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42393453546/artifacts/13583285949).
- Artifact name: **SnowGlobe-0220-signed-AAB-18-1**.
- Artifact ID: **13583285949**.
- Upload file inside: **SnowGlobe-0.22.0-build100029.aab**.
- ZIP size reported by GitHub: **8,096,461 bytes**.
- ZIP SHA-256 reported by the upload log and artifact API: `e36763e4f1e05a04c138447078dad13dacf20f786c12ab44c69dfc51059e89c9`.
- Artifact source: `914164b561dc761f501e59df026b4490c738ad2c`, run 42393453546, attempt 1.
- Created November 7, 2026 at 17:10:39 UTC; expires November 21, 2026 at approximately 17:10 UTC.
- The authenticated artifact download completed and returned the original ZIP reference. Its bytes were not independently extracted/hashed after the workspace outage.

The ZIP digest above is **not** the standalone AAB digest. The AAB's generated checksum is inside `SHA256SUMS.txt`; no unobserved standalone byte size or digest is asserted here.

The Actions download is a ZIP containing `SnowGlobe-0.22.0-build100029.aab` at the release artifact root. Extract and upload that **AAB** in Play Console. The ZIP is a delivery container; it is not the Play upload format. Included records are `source-commit.txt`, `release-metadata.json`, `SHA256SUMS.txt`, `upload-certificate.txt`, `signature-check.txt`, `bundle-validation.txt`, `AndroidManifest.xml`, `bundle-config.json`, `native-alignment.txt`, `apk-alignment.txt`, and `commerce-status.txt`.

Evidence archives for the canonical run:

| Evidence | Artifact ID | ZIP bytes | GitHub-reported ZIP SHA-256 |
| --- | --- | --- | --- |
| Android 15 | 13582567182 | 15,210,712 | `b36982956e50b04b6b76bf3174d769da9fa123cf12b2c33dec9c9c4f59214880` |
| Android 17 | 13582134793 | 15,330,368 | `e3edaf3081a305470409a7bc331b9af7a6d6e3ac96170715865afc00e66244b2` |

Both metadata records identify source `914164b561dc761f501e59df026b4490c738ad2c`. The artifacts have 14-day retention, expiring November 21, 2026; save the release locally before expiry.

## Independent checks completed before the workspace outage

- The initial integrated host suite passed 90 cases. After the actual-SDK smoke test was added, the complete suite became 91; C10's final Android 17 archive independently confirmed all 91 host cases plus 5 preflight and 38 full device cases.
- All 43 script regression tests passed locally. These exercise release/readiness and installed-package safeguards, including removal of custom IMEs and diagnostic networking components.
- The C11 renderer setup additionally passed 21 focused control-flow fixtures: 13 identity-parser cases, five setup/install/cleanup cases, and three fatal device-gate cases. These fixtures are separate from the 43 repository regressions.
- Reviewed nine actual native renders: three portrait snow styles, landscape crystals, four wallpapers and the launcher icon. The snow reaches the bottom in both orientations; size/depth variations are visible; artwork is complete; the icon is centered and uncropped. The export test validates real 2160 × 3840 PNG output.
- C10's exact Android 17 screenshots were inspected, including the full-resolution large-text layout, full-height snow and snow with the normal system keyboard. An initial suspicion of crowded large-text navigation was withdrawn after full-resolution inspection showed adequate spacing. White flakes are subtle over a white test surface; quantitative coverage tests pass.
- GitHub's C10-to-C11 comparison contains exactly four CI/helper files: the workflow, Mesa preparation helper, renderer verifier and device-runner script. No application or tap-probe source changed. This supports using the earlier visual review as unchanged-app evidence; it is not a claim that C11 images were separately opened.
- The locally built C10 release bundle passed bundletool, compiled-manifest and native-library verification, and every DEX was checked for the four removed keyboard classes. It was **unsigned** and was never offered as the Play deliverable. Its 8,154,410-byte SHA-256 was `2f2cd062ce622dea6a7d3b47aea4c31e3bffa8c67d220a7f936187b33ad61136`.
- Local release lint completed with **0 errors and 18 reviewed warnings**: eight UseKtx, three ObsoleteSdkInt, three UseRequiresApi, and one each of SetTextI18n, ViewConstructor, MonochromeLauncherIcon and StaticFieldLeak. The v33 icon includes monochrome support; the v26 fallback triggers the hint. The service's static live-instance reference is cleared in onDestroy. These are retained findings, not a warning-free report.

C10's independently checked Android 17 evidence was artifact 13581019990, 14,964,100 bytes, SHA-256 `f9a318b504a0184e52b4617b6c1614faab2586dc691ab019e1fda1d2d149af09`, from source `e746db06f00a6c1ad0fcabd3b8218284d2fa5f42`, [run 42389851469](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42389851469), original attempt 1.

## CI failure diagnosis and resolution

Earlier failed attempts remain failures; they are not counted as release passes.

1. The initial Android 17 attempt exposed an incorrect disabled-control selector in the test and real inherited SDK networking permissions. The selector now resolves the actual clickable control, checks disabled checkout behavior and retains failure screenshots. The SDK privacy boundary was corrected while retaining required classes. The Billing 9.1 shutdown smoke test now checks ConnectionState.CLOSED; isReady is not a valid closure assertion when automatic reconnection is enabled.
2. Linux Android 15 failures retained host-native SIGSEGV records for the QEMU emulator. Captured cgroup OOM counters were zero. Later native triage found a fault in generated/unmapped-by-file code with a SIMD indexed load, and other threads involved gfxstream/GLES/SwiftShader. This does not prove a particular freed-memory mechanism, exact function, or a confirmed upstream bug.
3. A prior “Lavapipe selected” Vulkan message did **not** establish Mesa GLES. The actual GLES initialization in those retained Linux runs remained Google SwiftShader 4.0.0.1. The distinction motivated an actual-context check.
4. C10 moved only the Android 15 runner to macOS Intel. Two attempts failed before APK installation due to slow boot and SystemUI ANRs. Boot took approximately 536 and 624 seconds. The emulator logged a one-vCPU clamp on the four-core host, with severe guest CPU pressure. HVF was available; no OOM or host crash was demonstrated. No app case ran, and signing was correctly skipped. No further unchanged retry was made.
5. C11 uses Ubuntu 24.04/KVM with the documented `-gpu host -feature -Vulkan` path for API 35, a persistent Xvfb display, `LIBGL_ALWAYS_SOFTWARE=1` and `GALLIUM_DRIVER=llvmpipe`. Bounded setup verifies host glxinfo and, before APK installation, the actual guest SurfaceFlinger GLES identity. Missing output, timeout or SwiftShader/ANGLE fallback fails the job. Android 17's established graphics configuration remains unchanged.

The final Android 15 log proves host **llvmpipe (LLVM 20.1.2, 256 bits)** and guest **Google (Mesa), Android Emulator OpenGL ES Translator (llvmpipe (LLVM 20.1.2, 256 bits)), OpenGL ES 3.1**, backed by Mesa **25.2.8**. Boot completed in **34,062 ms**, strict readiness passed, and both complete device stages passed. This is evidence that the corrected runner works for the final source; it is not a universal guarantee against future emulator failures.

Relevant retained diagnostic runs: [C8](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42387720805), [C9 native triage](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42389492790), and [C10 / Mac attempts](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42389851469). The last-good 0.21.6 run used the same original Linux emulator/image combination as the first failing candidate, so the evidence does not support claiming an initial version drift.

Implementation references: [Android emulator acceleration/graphics](https://developer.android.com/studio/run/emulator-acceleration), [Mesa environment variables](https://docs.mesa3d.org/envvars.html), [Android 15 actual RenderEngine identity](https://android.googlesource.com/platform/frameworks/native/+/android-15.0.0_r1/libs/renderengine/skia/SkiaGLRenderEngine.cpp), and [emulator macOS CPU clamp source](https://android.googlesource.com/platform/external/qemu/+/emu-master-dev/android-qemu2-glue/main.cpp).

## Remaining release boundaries

### Workspace interruption

The execution workspace became unavailable around 17:04 UTC while the hosted CI jobs continued. Completed job logs, source comparisons and GitHub artifact metadata were checked through the repository connection. C11 archives were not locally extracted afterward, their byte hashes were not independently recomputed, and C11 screenshots were not separately opened. The signed artifact's standalone AAB checksum and certificate report are included inside the CI ZIP; no independent post-download comparison is claimed.

The previously established upload-certificate SHA-256 is `73:45:EE:F8:97:0F:7F:8D:E6:20:BB:F7:C0:4B:28:03:0D:F9:C2:F7:A1:D6:8B:68:E5:E0:72:C1:04:8F:48:A4`. The canonical pipeline uses the existing stored key and verifies against it. A separate local comparison of the final artifact against this historical fingerprint was planned but could not be executed during the outage.

### Holiday Plus / Play Console

The final signed-bundle log explicitly reports **`commerceConfigured: False`** and **`Holiday Plus: commerce-not-configured`**. Therefore the free functions are the available release scope. The $2.99 checkout is not enabled in this AAB, and activating a product alone cannot change its embedded configuration.

The Play Console navigation attempt was **rejected by automatic approval review because it redirected to the unapproved `google.play` origin**. That block was not bypassed. No product/price/payment-profile/account declaration was changed, no real Play purchase was completed, and no production submission occurred.

To enable the prepared paid upgrade:

1. Obtain the genuine **public RSA licensing key** for `com.snowglobe.overlay` from Play Console. Do not supply a keystore or signing password.
2. Set repository Actions variable `PLAY_BILLING_PUBLIC_KEY` (or the documented Gradle property `playBillingPublicKey`) and rebuild. The key must correspond to this app; a syntactically valid key alone is not proof.
3. Activate the permanent Buy product `holiday_plus_lifetime`, USD 2.99, in the intended regions with the appropriate payments configuration.
4. Install the app from a Play test track using a license-tester account and exercise purchase, pending completion, acknowledgement, restore/reinstall and refund/revocation. An ordinary test-track account may still be charged.
5. If code 100029 has already been uploaded, use a greater version code for the configured rebuild. Activating a Console product cannot enable checkout in an already-built keyless AAB.

See [Holiday Plus setup](HOLIDAY_PLUS_SETUP.md) for the operational handoff.

### Store release and physical-device scope

Complete the account's actual Console checks, store listing/privacy/Data safety declarations, foreground-service specialUse justification and genuine demonstration video, content rating and any account-specific testing/production-access requirement. These cannot be inferred from CI.

No physical Pixel frame-time measurement, 120 Hz guarantee, battery benchmark, complete TalkBack session, live transaction, Google approval or same-day publication is claimed. The free technical build and the paid commercial launch have separate evidence requirements.
