# Credential review before the release commit — 2026-09-22

**Result: no real credential detected in either repository, its available local Git history, or the signed release APK. No compromised credential was identified for rotation.** Synthetic test keys are intentionally public and must never be used for real peers. This is a bounded review, not proof that arbitrary encoded or unknown secrets cannot exist.

## Reviewed material

| Repository snapshot before packaging | Commits | Blobs | Trees | Tags | Current files |
| --- | ---: | ---: | ---: | ---: | ---: |
| `snf/stunmesh-go` at `89d55b1f919c42bed3861ab0946c7d4d1d7943ed` | 628 | 2,065 | 1,872 | 14 | 369 |
| `snf/stunmesh-android` at `79c51ba0e834d418c82c804a9d6c5b589cab238e` | 148 | 479 | 742 | 7 | 184 |

- Read every available local Git object using `git cat-file --batch-all-objects` and `--batch`, including objects outside current branches, commit/tag messages and historical deleted files. Mapped paths through all refs/reflogs and inspected tracked/nonignored current files. These counts describe the baseline, before this documentation/artifact commit; the final staged changes were checked separately.
- Inspected the APK container and **all 139 decompressed entries**, including DEX, resources and both native libraries: 36,825,882 archive bytes / 36,854,304 decompressed bytes. Also inspected the historical Gradle wrapper JAR's entries. Historical image paths contain launcher icons, with no enrollment QR artifact found.
- Compared the actual local enrollment PSK, signing password, encrypted signing container and serialized signing private key against the reviewed bytes, with common encodings where applicable. **Zero matches.** Comparisons stayed in memory; values were not emitted, sent to a credential-validation service or included in this report. No phone private key or NAS private key was extracted for comparison.
- Applied private-key header, cloud/source-hosting/API token, credential URL/assignment, WireGuard hex/base64 and sensitive-filename checks; reviewed matches rather than excluding entire test directories. ASCII and UTF-16 representations were checked. No private signing/access/configuration file was found in the tracked or mapped historical paths.

## Candidate disposition

| Match class | Disposition |
| --- | --- |
| 29 credential-pattern groups | All reviewed: README placeholders, synthetic URL/redaction/parser canaries, generated-value concatenation, and signing-property variable references. No real token, password or private-key block found. |
| 2,745 distinct 32-byte base64 groups | Go dependency hashes, with their `go.sum` row syntax checked. Nine also appear in the APK's native Go build metadata. These are integrity metadata. |
| Eight other 32-byte base64 groups | Synthetic fixtures: five repeated-byte values, one single-nonzero-byte value and two ascending-byte sequences. Includes the public test PSK shared by Go and Android provisioning tests; it differs from the actual enrollment PSK. |
| Five other 32-byte base64 groups | Two documented owner **public** peer keys and three test **public** keys. They do not authorize a peer without its private key. |
| APK contents | No credential-pattern or known-credential matches. Its nine canonical 32-byte base64 groups are the dependency hashes above. The signing certificate/public key is expected public information. |

Public IP addresses, peer public keys, proposal identifiers, certificate fingerprints and artifact hashes in test reports are metadata, not credentials. Existing private signing files and confidential enrollment JSON/QR remain outside both repositories; their legitimate presence in the private workspace is not evidence of a leak into Git or the APK.

## Release and commit checks

- Versioned only the owner-signed `snf/stunmesh-android/releases/stunmesh-0.3.0-local.4.apk`, its checksum and provenance. SHA-256: `8bed3a5ec0196e6aa28ff4a7de1118e073a8e80f2399d0a58f8f8860f51e409e`.
- Android `apksigner` verifies its v3 signature with certificate SHA-256 `1e7a77c5f73ccb78259d60677e37147d7e50c5a50644e2ecb57cc6b58b1cef82`. Copied bytes match the artifact tested on the phone; no rebuild or source change.
- Added and checked ignore rules for local signing keys/passwords, ADB credentials, environment files and confidential enrollment files. Android permits only the named release APK; synthetic public fixtures remain trackable. Ignore rules reduce accidental additions and do not replace review.
- Explicitly staged reviewed paths; checked the staged content for credential matches, APK checksum/signature identity and `git diff --cached --check`. No history rewrite, push or GitHub release publication.

The review covers the local objects available at the listed snapshots and these packaging changes. It does not examine inaccessible remote history, prove absence of steganographic/obfuscated secrets, or replace the separate source/dependency audit. Any future real credential discovered in a commit must be treated as compromised and rotated; deleting the current file alone is insufficient.
