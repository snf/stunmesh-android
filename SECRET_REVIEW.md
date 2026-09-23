# Publication security and privacy review — 2026-09-23

**No real credential was detected in the reviewed public histories or release assets.** This finding does not clear earlier credentials exposed through local diagnostic output or provisioning logs: affected Samba passwords, the NAS WireGuard private key and existing peer PSKs still require coordinated rotation. A previously exposed unused laptop identity was replaced. Secret values and identifying incident records are retained privately, never reproduced here.

## Scope and result

- Scanned both complete local Git object stores, historical files, commit/tag messages and current publication changes. Private-key/token/password patterns and canonical WireGuard-key candidates were reviewed rather than blanket-excluding tests.
- Compared known current and retired enrollment, signing, VPN and file-sharing credentials in memory, including common encoded forms. No matched values were printed, put in reports or sent to credential-validation services. No Android private key was extracted.
- Read every entry in the signed APK, including DEX/resources/native libraries, and the metadata and complete layers of the rebuilt OCI image. The APK has 139 entries; each image contains exactly five regular files and no runtime configuration or credentials.
- Reviewed identifying paths, LAN/public endpoints, host/account names, public peer keys and device/deployment records. Removed private operational evidence from public history and replaced identifying examples. Generic protocol addresses, reserved test networks, public dependency/service endpoints, upstream authorship and APK signing-certificate metadata remain intentionally public.
- Candidate credential strings were synthetic test vectors/canaries, README placeholders, variable concatenation and official key-generation command names. Base64 candidates were dependency checksums or synthetic/public test keys. Synthetic keys must never be used for real peers.

## History and artifact handling

The Android APK and other release binaries are absent from every branch/tag history prepared for publication, not merely deleted in the latest commit. Release binaries belong only in GitHub Releases. Fork-owned histories and affected published feature refs were sanitized; superseded branded releases and tags were withdrawn; upstream histories and signatures are preserved. Original private operational records and provenance are held in restricted local backups, outside the public repositories.

The Android APK, shared core and OCI image were rebuilt from cleaned source. The new APK signature, certificate, package, label and non-debuggable manifest were verified. Android debug/release unit tests (12 each), release lint/build, Go race tests/vet, 15 Python helper checks and isolated image/kernel authentication tests passed. The current signer was generated in a separate private directory, outside build/cache/source/release assets. Both old and new signing material were included in the in-memory credential comparison. [RELEASES.md](RELEASES.md) identifies the rebuilt assets and their fresh-install/migration requirements; earlier physical-device results do not validate this release.

No claim is made that patterns/exact comparisons prove absence of an unknown or deliberately obfuscated secret. Rewriting refs cannot erase prior clones, downloaded archives, GitHub caches or third-party copies. Known previous credential exposures require rotation regardless of the publication result. Any newly discovered real credential in public history must likewise be treated as compromised, not repaired solely by deleting its file.
