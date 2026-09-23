# Android releases and Obtainium

Download [0.3.0-local.4](https://github.com/snf/stunmesh-android/releases/tag/v0.3.0-local.4). The signed APK, `SHA256SUMS` and provenance JSON are GitHub Release assets. No APK, AAR, image archive, private enrollment or signing key belongs in Git.

| Property | Value |
| --- | --- |
| Package | `dev.stunmesh.local` |
| Version / code | `0.3.0-local.4` / `4` |
| Platforms | Android 9/API 28+, ARM64 and x86-64 |
| APK SHA-256 | `8bed3a5ec0196e6aa28ff4a7de1118e073a8e80f2399d0a58f8f8860f51e409e` |
| Signing certificate SHA-256 | `1e7a77c5f73ccb78259d60677e37147d7e50c5a50644e2ecb57cc6b58b1cef82` |

In Obtainium, add `https://github.com/snf/stunmesh-android` as a GitHub source. The release has one APK, so no architecture or filename filter is needed. It is published as a normal release; prerelease inclusion is unnecessary. Obtainium supports GitHub release assets directly ([official source documentation](https://wiki.obtainium.imranr.dev/sources/)).

The same owner signer and package can update the existing local installation without replacing its enrolled identity. Installing this fork alongside upstream uses a different package and does not import upstream credentials. Future releases must retain the owner signer and increase Android's version code. Keep the signing key and password private and outside build workspaces.

Download the APK and `SHA256SUMS` into one directory, then run `sha256sum -c SHA256SUMS`. Verify the signing certificate using Android `apksigner verify --print-certs` or a trusted installer. A checksum delivered beside a file detects mismatched bytes; first-install trust still requires a trusted source/certificate.

This is the unchanged signed APK tested on Android 13: hardware-protected storage, narrow destination routes, ordinary internet outside the tunnel, and one external-hotspot/home-return path passed. Battery, Doze/reboot, IPv6 and encrypted OS recovery remain acceptance gates. See [test summary](DEVICE_TEST_RESULTS.md), [credential/privacy review](SECRET_REVIEW.md) and [build instructions](LOCAL_BUILD.md).
