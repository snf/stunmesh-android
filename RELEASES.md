# Android releases and Obtainium

Download [STUNMESH Android 0.3.1](https://github.com/snf/stunmesh-android/releases/tag/v0.3.1). Assets: `stunmesh-0.3.1.apk`, `SHA256SUMS`, `PROVENANCE.json`. Binaries are never ordinary committed files.

**Fresh installation and enrollment required.** The package and signer changed. Read [PUBLICATION_TRANSITION.md](PUBLICATION_TRANSITION.md) before installing. The server must use the matching `stunmesh-hints-v2` discovery namespace; the new app will not discover a server still running the retired namespace.

| Property | Value |
| --- | --- |
| Package | `dev.stunmesh.local` |
| Version / code | `0.3.1` / `5` |
| Platforms | Android 9/API 28+, ARM64 and x86-64 |
| APK SHA-256 | `88e3ffc4e55cc01961c4e5dd63ce934ca66d4d15ff538fdeb3e5515af0bbf223` |
| Certificate subject | `CN=STUNMESH` |
| Certificate SHA-256 | `4fe0a475cdc290f2b5f45379e796d948983ca6b813367fb8ab4f2c5f883e8524` |

In Obtainium, add `https://github.com/snf/stunmesh-android` as a GitHub source. This normal release contains one APK, so no architecture/filename filter or prerelease option is needed. Replace any existing Obtainium entry tied to the retired application identity; do not expect an in-place update. See [Obtainium source documentation](https://wiki.obtainium.imranr.dev/sources/).

Download the APK and `SHA256SUMS` together, then run `sha256sum -c SHA256SUMS`. Verify its certificate with Android `apksigner verify --print-certs` or a trusted installer. A checksum served beside an artifact detects corruption, but is not an independent trust anchor. Future releases must retain this signer/package and increase the version code. Signing material stays outside source, build/cache and release directories; preserve an encrypted offline backup.

The release was rebuilt from committed source with the locally rebuilt core and pinned dependencies. Debug/release unit tests, release lint and builds passed; the signed APK is non-debuggable. **This release has not been tested on a physical phone.** Migration, LAN/hotspot recovery, hardware storage, OS recovery, Doze/reboot, IPv6 and battery remain acceptance checks. Historical tests are summarized separately in [DEVICE_TEST_RESULTS.md](DEVICE_TEST_RESULTS.md).

See [artifact provenance](ARTIFACT_MANIFEST.json), [security review](SECRET_REVIEW.md) and [build instructions](LOCAL_BUILD.md).
