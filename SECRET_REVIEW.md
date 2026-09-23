# Publication security and privacy review — 2026-09-23

**No real credential was detected in the reviewed public histories or release assets.** This finding does not clear earlier credentials exposed through local diagnostic output or provisioning logs: affected Samba passwords, the NAS WireGuard private key and existing peer PSKs still require coordinated rotation. A previously exposed unused laptop identity was replaced. Secret values and identifying incident records are retained privately, never reproduced here.

## Scope and result

- Scanned both complete local Git object stores, historical files, commit/tag messages and current publication changes. Private-key/token/password patterns and canonical WireGuard-key candidates were reviewed rather than blanket-excluding tests.
- Compared known current and retired enrollment, signing, VPN and file-sharing credentials in memory, including common encoded forms. No matched values were printed, put in reports or sent to credential-validation services. No Android private key was extracted.
- Read every entry in the signed APK, including DEX/resources/native libraries, and the metadata and complete layers of both published OCI images. The APK has 139 entries; each image contains exactly five regular files and no runtime configuration or credentials.
- Reviewed identifying paths, LAN/public endpoints, host/account names, public peer keys and device/deployment records. Removed private operational evidence from public history and replaced identifying examples. Generic protocol addresses, reserved test networks, public dependency/service endpoints, upstream authorship and APK signing-certificate metadata remain intentionally public.
- Candidate credential strings were synthetic test vectors/canaries, README placeholders, variable concatenation and official key-generation command names. Base64 candidates were dependency checksums or synthetic/public test keys. Synthetic keys must never be used for real peers.

## History and artifact handling

The Android APK and other release binaries are absent from every branch/tag history prepared for publication, not merely deleted in the latest commit. Release binaries belong only in GitHub Releases. Fork-owned histories and affected published feature/tag refs were sanitized; upstream histories and signatures are preserved. Original private operational records and provenance are held in restricted local backups, outside the public repositories.

The exact Android APK signature and checksum were reverified. The release's runtime source and dependency files are byte-identical to their privacy-rewritten source equivalents. Android release build/unit tests/lint, Go race tests/vet and Python helper tests were rerun; packaging does not replace the existing signed APK or OCI bytes. Asset provenance is attached to each release; [RELEASES.md](RELEASES.md) gives hashes and installation instructions.

No claim is made that patterns/exact comparisons prove absence of an unknown or deliberately obfuscated secret. Rewriting refs cannot erase prior clones, downloaded archives, GitHub caches or third-party copies. Known previous credential exposures require rotation regardless of the publication result. Any newly discovered real credential in public history must likewise be treated as compromised, not repaired solely by deleting its file.
