# SnowGlobe 0.22.0 release audit

Audit date: November 7, 2026 (UTC).

**Post-upload status, November 7 at 6:25 p.m. Eastern:** the owner reports that the Play Store update from installed 0.21.6 fails and that, after uninstalling, a fresh installation stalls at 97% / 6.22 MB. The physical-device/Play installation problem remains unresolved. An archived-release upgrade test passed on an Android 17 x86_64 emulator; a fresh source/AAB comparison and representative ARM64 split-packaging check also found no installer blocker. These checks do not establish successful delivery or installation on the owner's phone. See the post-upload diagnostics below.

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
- After workspace access returned, the authenticated artifact download was safely extracted and its ZIP digest independently matched the GitHub record. The standalone AAB checksum matched SHA256SUMS.txt, its source/metadata matched the released commit, and its embedded PKCS7 certificate matched the existing upload-key fingerprint.

The ZIP digest above is **not** the standalone AAB digest. The independently checked AAB is **8,167,436 bytes**, SHA-256 `226e92d2121d5a3cd6934b22e00fec7449643d11e50d298d4e26ef8ac126681b`. Its generated checksum is also inside `SHA256SUMS.txt`.

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

The execution workspace became unavailable around 17:04 UTC while the hosted CI jobs continued. Completed job logs, source comparisons and GitHub artifact metadata were initially checked through the repository connection. After access returned, the final signed release archive and standalone AAB were independently hashed/extracted and the embedded upload certificate was compared successfully, as recorded above. The two C11 device-evidence archives were not newly extracted for a fresh host-report count or screenshot review during that delivery check.

The previously established upload-certificate SHA-256 is `73:45:EE:F8:97:0F:7F:8D:E6:20:BB:F7:C0:4B:28:03:0D:F9:C2:F7:A1:D6:8B:68:E5:E0:72:C1:04:8F:48:A4`. The canonical pipeline uses the existing stored key and verifies against it. The separate local comparison could not run during the outage; it was completed after access returned and the final AAB's embedded certificate matched this historical fingerprint.

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

## Post-upload update diagnostic: 0.21.6 to 0.22.0

The owner reported a generic Play Store “Can't update” dialog, then confirmed that the phone still runs 0.21.6 after the initial troubleshooting steps. The dialog does not identify the version Play is serving or an underlying PackageInstaller error. The actual uploaded release artifacts were compared: both use package `com.snowglobe.overlay`, minimum SDK 26, target SDK 36, and the same upload certificate; version increases from 100028 to 100029. Provider authority and the custom signature-permission namespace remain unchanged. No installation blocker was identified by that comparison.

The previous device matrix exercised fresh debug installations. It did not prove an in-place upgrade of the archived signed releases. A separate diagnostic was added on `diagnostics/upgrade-100028-100029-20261107`, source `730034d5b99e41ea9686132b5903ee5f5ebe9c47`. **Neither application was rebuilt or changed.**

[Diagnostic run 42421616927](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42421616927), job 128551059587, completed successfully on November 7 at 5:42 p.m. Eastern. All 15 job steps passed and all 30 captured commands returned exit code zero.

| Check | Old release | Updated release |
| --- | --- | --- |
| Version | 0.21.6 / 100028 | 0.22.0 / 100029 |
| AAB SHA-256 | `bb5c88a8a55607c1b9083f03e32771c50094055df72b7ae4772a145f33742c77` | `226e92d2121d5a3cd6934b22e00fec7449643d11e50d298d4e26ef8ac126681b` |
| Installer result | Success, exit 0 | Success, exit 0 |
| Package UID | 10234 | 10234 |
| First-install timestamp, retained verbatim from the UTC emulator | 2026-11-07 22:41:29 | 2026-11-07 22:41:29 |
| Studio launch | Status: ok | Status: ok |

The test used the emulator's actual device specification and four APK splits per version: base, English, x86_64 and xxhdpi. It installed the old release, selected `ChristmasKeyboardService`, then performed one replacement installation without uninstalling, clearing data or permitting a downgrade. After replacement, StudioActivity was resumed, the old keyboard was absent, and Android selected `com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME`. No matching Java fatal exception or native crash was found in the captured interval.

[Evidence artifact 13594191410](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42421616927/artifacts/13594191410), `SnowGlobe-upgrade-100028-to-100029-1-1`, contains 113 files and is 1,582,340 bytes. Its independently verified ZIP SHA-256 is `e0c61ea14037a9a78a4d238e521d1a71bb4c59d8aeed02aec90f42452acff3bb`. The archive passed integrity, path and symlink checks; the report, installer outputs, exit codes and before/after package records were inspected.

**Limits:** this was Android API 37 on x86_64 with 4,096-byte pages. It does not establish behavior on the owner's physical phone or on an ARM64 device using 16 KB pages; the phone's actual page size has not been measured. Generated APKs shared the existing upload key, so the test does not validate Google's app-signing certificate/lineage or Play delivery. Unchanged UID and firstInstallTime show package continuity; no claim is made that individual settings, saved scenes or purchase records were inspected for retention.

The next required account-side evidence is the internal-testing release status for **0.22.0 / 100029**. If that release is active for the tester and the update still fails, obtain the actual PackageInstaller/PackageManager failure from the phone. A new app version or key replacement is not justified by the generic popup alone.


## Fresh-install and keyboard-removal forensic audit

On November 7, the owner reported uninstalling 0.21.6 and then a fresh Play install stalling at 97% / 6.22 MB. A progress percentage does not supply an Android installer failure code. This broadens the unresolved issue beyond a routine replacement-install failure.

A second, independent source and actual-artifact comparison covered baseline `cdc1be36a049c807623babe08784b5ad362c4756` and release `914164b561dc761f501e59df026b4490c738ad2c`. Both original signed artifacts were downloaded again, checked against their recorded ZIP/AAB digests, and inspected. No application source or release version was changed during this investigation.

### Keyboard removal and concurrent changes

- The IME service declaration, metadata/XML, four implementation classes, keyboard-specific UI, and callbacks were removed together. All 34 remaining production Java/Kotlin files were scanned; no dangling references to the removed classes or IME registration were found.
- One legacy `EffectsService.setKeyboardTyping` callback remains unused. Its flag defaults to false and has no remaining production caller; it is cleanup debt, not an established installation defect. The generic `WindowInsets.Type.ime()` path in `SnowView` remains useful for reducing rendering work while Gboard or another normal keyboard is visible.
- Keyboard preference deletion executes during `StudioActivity.onCreate`. It affects only private keys beginning with `keyboard`; it is not a package-install hook and does not change Android's selected keyboard.
- The startup receiver and overlay service are unchanged from 0.21.6. Android documents `MY_PACKAGE_REPLACED` as a broadcast after the new version has been installed.
- The same feature commit also added Billing 9.1.0. Its new manifest permission, queries, proxy activities and metadata were checked alongside the IME removal. No extra required hardware feature, provider-authority conflict, changed custom-permission namespace, debug/test-only flag, SDK restriction, or changed application ID was found.
- The configured billing key is absent in the delivered AAB. Its production gateway is disabled and refresh returns without opening a Billing connection. This is a runtime check, not proof about Play delivery.

### Actual bundle and native-code comparison

- AGP 8.10.1, Kotlin 2.1.20, signing configuration, release minification setting, minimum/target SDK and ABI/split configuration are unchanged.
- The embedded upload certificates match. This is not a verification of the separate Google Play app-signing certificate on the phone.
- Both archives are CRC-clean and have no duplicate entries. Every non-signature payload entry's SHA-256 digest matches its JAR manifest: 168 entries in 0.21.6 and 197 in 0.22.0.
- All four ABI copies of `libandroidx.graphics.path.so` are byte-for-byte identical between releases. The ARM64 library SHA-256 is `41e9a793c43a0f4fddb19e33f346bace464f30f888ba7b9eaf96294ea115bfb6`; native LOAD alignments remain 16,384.
- Raw `BundleConfig.pb` and `base/native.pb` are identical. Native libraries remain uncompressed with `PAGE_ALIGNMENT_16K`.
- Both releases' two DEX files pass length, SHA-1 and Adler32 checks and retain DEX format 038.
- The streaming JAR-manifest-order warning is present in both the working baseline and 0.22.0. It was not introduced by keyboard removal.

### Newly completed ARM64 split-packaging check

The previous release gate inspected a universal APK and the upgrade test installed x86_64 splits. This audit additionally generated ARM64 splits directly from each archived AAB, without rebuilding either application.

Google bundletool 1.18.1 was downloaded from its official release and its SHA-256 matched `675786493983787ffa11550bdb7c0715679a44e1643f3ff980a529e9c822595c`. A manually constructed, representative specification used API 37, ARM64-only, English and density 560; it was **not** a device specification captured from the owner's Pixel.

Both bundles generated four splits successfully: base, ARM64, English and xxxhdpi. All eight APKs passed archive integrity and AAPT2 parsing. Each split carries the expected application ID and the appropriate release's version code. All stored non-library payloads were 4-byte aligned. The uncompressed ARM64 library in each ABI split begins at offset 16,384 and satisfies 16 KB ZIP alignment.

Machine-readable results are in [the ARM64 packaging evidence](evidence/INSTALL_AUDIT_0220_ARM64.json).

**Limits:** these generated APKs were intentionally unsigned, were used only for static packaging inspection, and were never installed or offered to the owner. No signing key was generated or replaced. This does not verify Google's APK signing, Play's exact delivered splits, the owner's actual phone configuration, or a physical ARM64 installation. Do not represent the audit as a confirmed repair or a newly built release.

### Evidence still required to identify the failure

The remaining useful evidence is the actual phone's PackageInstaller/PackageManager result around one failed Play attempt and/or the Play-generated device-specific APKs for version 100029. Read-only package state can also identify whether another Android user/profile retains a package after a current-user uninstall; no such retained copy has been observed on this phone.

References: [Android IME package-removal handling](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/inputmethod/InputMethodManagerService.java), [post-install replacement broadcast](https://developer.android.com/reference/android/content/Intent#ACTION_MY_PACKAGE_REPLACED), [bundletool](https://developer.android.com/tools/bundletool), [16 KB packaging](https://developer.android.com/guide/practices/page-sizes), and [Play-generated APK downloads](https://support.google.com/googleplay/android-developer/answer/9844279?hl=en).
