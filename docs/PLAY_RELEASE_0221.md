# SnowGlobe 0.22.1 upload retry

## Purpose

The owner requested a fresh Google Play upload after 0.22.0 failed to update and a subsequent clean installation stalled. This release changes the version identity only; no cause has been established and successful physical-device installation is still unverified.

- Package: `com.snowglobe.overlay`.
- Version: `0.22.1`.
- Version code: `100030`, increased from `100029` so Play can accept a fresh upload.
- Expected filename: `SnowGlobe-0.22.1-build100030.aab`.
- Application implementation, dependencies, resources, manifest and existing upload key remain those audited for 0.22.0.
- Custom keyboard remains removed.
- Holiday Plus checkout remains disabled until its genuine Play configuration is provided and tested.

## Validation status

[Release run 42426089463](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42426089463), run 19 attempt 1, completed successfully on November 7, 2026 at 23:56:11 UTC (6:56 p.m. Eastern). Its exact source is `ef5a2450cbc90c6d8b652395235eaea7e03a1ed3`. No rerun was required.

Both Android 15 and Android 17 passed 43 script tests, JVM regressions/lint/compilation, 5 preflight device tests, the full 38-test device suite, and verification of 11 required screenshots. The preflight cases are a subset of the full suite. Exact JVM test-case totals were not independently extracted from fresh report artifacts.

The signed-bundle job 128568250103 passed existing-key restoration, release build/lint, strict signing verification, bundle/compiled-manifest checks, native 16 KB checks, generated universal-APK alignment, artifact upload, and temporary-key cleanup.

## Verified delivery

- [Signed artifact 13595633967](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42426089463/artifacts/13595633967): `SnowGlobe-0221-signed-AAB-19-1`.
- Artifact ZIP: 8,096,443 bytes; SHA-256 `0e07fa287b3110c970668b2e5ce9659076b91c8593c75f31d462c95e91560859`.
- Standalone AAB: **`SnowGlobe-0.22.1-build100030.aab`**, 8,167,416 bytes.
- Standalone AAB SHA-256: `76d54b78187221480a03f9ff6814f1e6c2291e605d04a8fdf93b5b36a1f99f33`.
- Actual embedded upload certificate SHA-256: `7345eef8970f7f8de620bbf7c04b28030df9c2f7a1d68b68e5e072c1048f48a4`, unchanged.
- The downloaded ZIP matched GitHub's recorded digest and passed integrity/path checks. The extracted AAB matched CI's checksum, release/source metadata and embedded certificate; all 197 non-signature payload digests matched its JAR manifest. Its compiled manifest contains no custom keyboard service.
- Actual release metadata confirms `commerceConfigured: false`.
- Machine-readable checks: [DELIVERY_0221.json](evidence/DELIVERY_0221.json).
- GitHub artifact retention ends November 21, 2026 at approximately 23:55 UTC. The standalone AAB was also saved and provided directly in the project chat.

These are build and delivery checks. The owner's physical Pixel installation, the exact APKs/signing delivered by Google Play, and the original 97% stall remain unverified. This release changes the version identity; it is not a confirmed repair.

## Upload handoff

Upload the resulting `.aab` to the existing app's internal-testing track and roll out the new `100030` release. An uploaded draft alone does not make the release available to the tester. Use the same tester account on the phone, then confirm the installed app reports `0.22.1`.

A successful new upload is not a confirmed repair of the previously reported 97% installation stall. If the new release also stalls, retain the actual phone installation error; the read-only Mac diagnostic helper prepared in the chat can capture that evidence.

References: [0.22.0 audit and unresolved installation status](RELEASE_READINESS_0220.md), [Google Play bundle versioning](https://developer.android.com/studio/publish/upload-bundle), and [release preparation and rollout](https://support.google.com/googleplay/android-developer/answer/9859348).
