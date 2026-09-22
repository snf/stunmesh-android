# Local signed release

[`stunmesh-0.3.0-local.4.apk`](stunmesh-0.3.0-local.4.apk) is the exact APK tested on the Android 13 phone, including the successful mobile-data-hotspot and return-home test. It is committed locally at the owner's request; this does not publish a GitHub release.

| Property | Value |
| --- | --- |
| Package / version | `dev.stunmesh.local` / `0.3.0-local.4` (code 4) |
| Platform | Android 9/API 28 or later; ARM64 and x86-64 |
| Size | 36,825,882 bytes |
| APK SHA-256 | `8bed3a5ec0196e6aa28ff4a7de1118e073a8e80f2399d0a58f8f8860f51e409e` |
| Signing certificate SHA-256 | `1e7a77c5f73ccb78259d60677e37147d7e50c5a50644e2ecb57cc6b58b1cef82` |
| Android build source | `3cfcb6116a1d8f10baa76eb16aed44df70731e49` |
| Go/core build source | `d4d12e9f2e17f6259fbb496854b7117ac300e6c8` in `snf/stunmesh-go` |

Run `sha256sum -c SHA256SUMS` in this directory. Android `apksigner verify --verbose --print-certs` verifies the v3 signature with the certificate above. The checksum identifies these bytes; installation trust also depends on obtaining this repository and the signing certificate through a trusted channel.

This is a non-debuggable release. Existing installations signed by this owner key can update in place. A new installation still needs confidential enrollment and explicit server authorization; the APK contains no owner configuration or peer credentials. Keep the signing key/password outside Git so future updates retain the same signer.

[Artifact provenance](../ARTIFACT_MANIFEST.json), [test results and remaining acceptance gates](../DEVICE_TEST_RESULTS.md), and [the precommit credential review](../SECRET_REVIEW.md) document the scope. Source, tests and binary are unchanged by this packaging commit. Other builds, debug/test APKs, signing material and confidential enrollment files remain outside Git.
