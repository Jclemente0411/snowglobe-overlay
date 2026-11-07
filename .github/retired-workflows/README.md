# Historical workflows

These files are retained for reference outside GitHub Actions' active workflows directory. They used older version checks or only one Android test version and must not produce current release artifacts.

The active release pipeline is [release-verify.yml](../workflows/release-verify.yml). It permits the existing upload key to be restored only after both Android 15 and Android 17 device-test jobs succeed. The separate debug APK workflow produces a debug build, not a signed Google Play release.
