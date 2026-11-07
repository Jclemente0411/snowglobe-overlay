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

The existing complete release workflow must pass Android 15 and Android 17 checks before it builds and signs the AAB with the existing upload key. The resulting artifact must pass signature, bundle/manifest, checksum and native/APK alignment verification before delivery. Build and delivery details will be recorded here after that run completes.

## Upload handoff

Upload the resulting `.aab` to the existing app's internal-testing track and roll out the new `100030` release. An uploaded draft alone does not make the release available to the tester. Use the same tester account on the phone, then confirm the installed app reports `0.22.1`.

A successful new upload is not a confirmed repair of the previously reported 97% installation stall. If the new release also stalls, retain the actual phone installation error; the read-only Mac diagnostic helper prepared in the chat can capture that evidence.

References: [0.22.0 audit and unresolved installation status](RELEASE_READINESS_0220.md), [Google Play bundle versioning](https://developer.android.com/studio/publish/upload-bundle), and [release preparation and rollout](https://support.google.com/googleplay/android-developer/answer/9859348).
