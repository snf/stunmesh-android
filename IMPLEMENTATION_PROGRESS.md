# Security remediation progress

Started 2026-09-22. Implementing `SECURITY_REMEDIATION_PLAN.md` at Go `2b900a7` / Android `3dccad9` on `security-remediation/2026-09-22`. The original audit branches/evidence remain intact. This log is mirrored into the Android repository; the Go copy is canonical.

## Completion contract

Complete all locally possible implementation, tests, release APK and container builds before handoff. No deployment, firewall change, NAS ownership change, production key rotation or real phone reset. NAS/GrapheneOS access arrives later: hardware Keystore/backup, real NAT, battery measurements and rootless host integration must be identified as pending device checks, never claimed passed locally. No public push/release.

Security and simplicity take precedence over legacy features: OpenDHT hints cannot authorize peers; WireGuard remains the authenticator. Only destination split routes. Owner-authorized encrypted OS backup is allowed, ordinary secret export is not. Keep verified dependency pins, delete unused execution/release/debug paths, avoid additional background workers.

## Work checklist

| Stage / plan IDs | State | Evidence |
| --- | --- | --- |
| 0: baseline and toolchain | Complete locally | Exact restored publisher pins, committed snapshots, offline builds and inventories. |
| 1: typed config/UAPI, public discovery, OpenDHT only (D1/D2, F1/F2) | Complete | Typed endpoint-only updates, strict bounded admission, no custom discovery crypto/process plugins. Race/fuzz/WG authorization tests pass. |
| 2: bounded discovery/STUN/proxy and power behavior (F3–F7) | Complete locally | Bounded/cancellable/coalesced work, offline suspension, split routes, STUN validation/collision ownership; real userspace and kernel WG tests pass. Actual carrier/battery measurements are pending hardware. |
| 3: Android storage/backup/import/lifecycle (F8–F12/F16, N2) | Complete locally | Hardware wrapping/atomic store, encrypted OS adapter/inactive restore, public enrollment, real foreground lifecycle; 11 debug + 11 release JVM tests and lint pass. Hardware/transport execution pending. |
| 4: local artifacts/dependency/image trust (D3, F13–F15/F17) | Complete | 839 publisher artifact hashes, strict locks, corrupt-input rejection, clean offline APK/AAR/image, isolated owner signature and manifests. |
| 5: public QR provisioning and local integration (F18) | Complete locally | Shared public schema/fixture/provisioner; kernel WG rejects wrong keys/PSKs/sources. Actual camera scan remains a device check. |
| 6: final verification and device handoff | Complete locally | `VALIDATION.md`, `ARTIFACT_MANIFEST.json`, `LOCAL_BUILD.md`, `PROVISIONING.md`, `deploy/README.md`, `DEVICE_TESTS.md`. No production deployment claimed. |

## Decisions during implementation

- Preserve existing package namespaces unless a targeted change is needed; the installed fork gets a distinct stable application ID before its first release.
- Do not introduce a second backup mechanism: implement the small platform key-value adapter. Device-specific transport/StrongBox validation remains a real-phone gate.
- Synthetic test keys/configuration only in tracked tests and logs. Owner signing material stays outside both repositories and the build context.
- Service routes are limited to IPv4 /24 or narrower and IPv6 /64 or narrower, with the plan's count limits. This prevents equivalent default routes assembled from broad prefixes; actual server /32-/128 routes remain the intended deployment. Legacy broad profiles must be narrowed deliberately.
- The old audit download/installed-tool caches had been removed. Fetch the same recorded versions from publishers and verify the historical hashes; this is restoration of pins, not an upgrade. Build/test sandbox excludes host home, credentials and signer, and defaults to no network.

## Validation and commits

- Baseline: both worktrees clean; identical plan and complete original audit evidence.
- F1/F2 first increment: `go test -race -tags mobile ./internal/validation ./mobile ./internal/config ./internal/wg` passes inside `scripts/sandbox.py`. New tests assert malicious endpoints/routes never mutate real in-memory WG peer keys, PSKs or AllowedIPs. YAML nil-key/duplicate and bad cadence/port fixtures reject without panics. See `security-remediation-evidence/stage1-boundary-tests.txt`.
- Some historical `security_audit` tests still intentionally assert vulnerabilities; they will be replaced alongside D1/D2/F3/F4 rather than treated as a passing security gate. Full suites, fuzzing and builds remain pending.

- Public discovery increment: removed custom NaCl endpoint encryption, private keys from discovery configuration, exec/shell/Cloudflare stores, registry and DI generator. New `stunmesh-hints-v2` namespace is deliberately incompatible with upstream encrypted records. No legacy fallback.
- Server metadata reads use public-only `wg show` fields; the daemon no longer requests dumps/private keys/PSKs. WireGuard retains authentication. Candidate health comes only from WG; recent authenticated sessions survive hostile hints.
- OpenDHT now requires HTTPS origins, rejects redirects, caps decompressed responses (256 KiB), records (64) and candidates (4), ignores publisher timestamp authority and tries another proxy for unusable successes. TTL renewal is never suppressed by unchanged values.
- Removed raw/pcap STUN paths and their production dependencies; the server now requires the shared UDP proxy. Android/server use the same bounded binding parser; reply source, transaction, class/type and full lengths must match. Duplicate proxy endpoints cannot steal/delete another peer's mapping.
- Mobile scheduling uses one cancellable loop with underlay events, no disconnected timer and no unsupported-family probes. Existing-cycle WG health sampling replaces an extra health timer. Android callback wiring/device power tests remain.
- Validation: full `go test -race -tags 'mobile security_audit builtin_all' ./...` passed after converting historical vulnerability demonstrations into rejection regressions. Evidence: `security-remediation-evidence/discovery-network-tests.txt`. Tests run only in an isolated network namespace with synthetic keys.
- Build note: Google's pinned SDK wrapper attempts to download a new Android CLI. That download was confined to the build sandbox and failed before installation; do not use that mutable bootstrap for the artifact. The already hash-verified SDK archives are extracted; package metadata will be restored from official repository XML instead.

- Mobile lifecycle increment: removed renewable TUN wrapper; serialized lifecycle keeps the TUN during underlay rebinding, closes WG sockets/timers offline, cancels in-flight discovery and coalesces network changes. OpenDHT failures participate in backoff; static-only profiles have no discovery timer. Discovery package no longer imports desktop controllers.
- Real WireGuard/UDP integration tests pass: correct identities/PSK decrypt, unknown keys and wrong PSKs cannot, unauthorized tunnel source addresses are dropped; offline/resume recovers. Immediate resume respects WG handshake retry timing (test deadline eight seconds), rather than introducing an app retry timer. Invalid STUN source/type/transaction/length packets do not consume the valid response waiter. Evidence: `security-remediation-evidence/wg-auth-tests.txt`.
- Android adapter rejects nonstandard TUN descriptors with virtio offload flags; standard VpnService uses TUN + NO_PI. This excludes the audited WG pin's unused virtio GRO path. Backend diagnostics now receive fixed WG error categories, never raw formatting.
- Local AAR build succeeded with both supported ABIs. It will be rebuilt after the final Go changes. Android no longer has a remote AAR/stub fallback, secret editor/export, or debug import receiver; release/debug use the same hash-checked local core.

- Android implementation milestone: debug **and release** unit variants pass (10 tests each), release APK packaging and release lint pass. The unsigned APK is a validation intermediate; final rebuild/signature/inventory still pending. Narrow `ForegroundServicePermission` lint suppression documents Android's prepared-VPN eligibility for `systemExempted`; no alarm permission or scheduler was added. Other lint warnings are retained pins/unused template resources plus the intentional application-context singleton.
- Implemented single-authority atomic encrypted storage and StrongBox/verified-TEE wrapping, fail-closed reads/writes, OS key-value backup requiring client-side transport encryption, inactive validated restore, and no generic file-transfer fallback. Real StrongBox/GrapheneOS transport checks remain the later device gate.
- VPN service owns its backend on one executor; prepared consent, foreground notification, protected/bound physical-network sockets, both address families allowed, explicit narrow routes and unchanged phone DNS. No app selectors/default routes, private-key field, ordinary secret export, debug import receiver, stub backend or multi-store editor remain.

- Local integration/build milestone: both Android unit variants now pass 11 tests, release lint/package and hardware-test APK compilation pass after the atomic-write hardening. Strict publisher verification covers 839 Gradle artifact hashes (181 reused from the earlier verified audit). Full Go race tests and seven 30-second boundary fuzz runs pass; strict YAML scalar types reject coercion. Rechecking final committed builds remains.
- Public provisioning now uses matching Go/Android schema and shared synthetic fixture, 2048-byte public-only QR limit, locally generated phone keys and explicit server authorization. Removed obsolete CI/release/install paths. Minimal image context excludes source/Git/config/keys; kernel WireGuard startup and clean SIGTERM passed in an isolated rootless network namespace with NET_ADMIN only. Actual Podman host mappings remain pending.
- Advisory refresh: 551 full-graph coordinate queries. Newly reported Go advisories affect packages absent from daemon/core/gomobile imports; the compiler is patched Go 1.27.1. Android host-tool advisories are separately triaged from release-runtime dependencies. No cosmetic upgrades or scanner-count-based claims. Final inventory/signature and device handoff are in progress.

- Final Go race/vet/module checks pass. The first final fuzz command matched three similarly named historical targets and correctly refused to run; rerunning with an anchored exact target passed (132,157 executions). Strict Android dependency-lock enforcement also passes.

## Final local acceptance

- Production Go/core artifacts built offline from `ca7f90144dfc944900769c5764a1eb5779f5d479`; Android from `0bd8fe0` (full hash in artifact manifest). Final Android build executed all 135 tasks from fresh output/project-cache directories, with network/build cache off. Debug 11/11 and release 11/11 JVM tests, release lint, release/debug APKs and instrumentation APK compilation pass.
- Separate owner release signature verifies (v3 for API 28+). Release is non-debuggable/non-test-only; ARM64 and x86-64 core libraries exactly match the locally built AAR, with 16 KiB ELF/ZIP alignment. Owner key/password are only in `/workspace/stunmesh-signing/`, outside source/build/Git; preserve an encrypted private backup for future updates.
- Kernel/image integration test commit `9baf28f` exercises the actual final daemon/proxy image, public-only WG metadata reader, a synthetic HTTPS DHT and real mobile shared-socket WG bind. Authorized kernel echo passes; unknown-key, wrong-PSK and unauthorized-source rejection pass. All work occurs in a disposable rootless user/network namespace with NET_ADMIN only. The fixture needs valid IP checksums (the upstream fake-TUN echo helper does not supply them); production code did not need changing for this test.
- Final whole Go race suite (including compilation of the new test package), vet and module verification pass; see `go-acceptance.txt`. Legacy opt-in host/netns harnesses remain skipped, with the isolated image gate executed separately. Deliberately tampered Maven/AAR inputs fail, then original bytes are restored before final builds.
- Artifacts/checksums and signed APK are in `../stunmesh-build/artifacts/`. All changed source, decisions, evidence and progress are committed locally; no push, release publication, NAS/phone deployment, host firewall or production-configuration change.
- Remaining work explicitly requires the later provided devices: actual rootless Podman mappings/service bindings, NAT/carrier access, GrapheneOS hardware wrapping/encrypted OS restore, real route/lifecycle/handover behavior and battery measurements. `DEVICE_TESTS.md` is the acceptance checklist. Structural battery reductions are implemented; measured savings are not claimed.

## Implementation commit map

| Go | Android | Increment |
| --- | --- | --- |
| `f0f08bb` | `18c5666` | Progress/baseline |
| `9f889da` | `3769bbe` | Typed configuration / WG admission |
| `8acb543` | `26e54e0` | Public discovery, OpenDHT-only, deleted crypto/plugins/raw paths |
| `1b3a6bd` | `e08af6d` | Mobile lifecycle/WG auth; Android protected store/backup/service |
| `ca7f901` | `0bd8fe0` | Public enrollment, bounded daemon, minimal image / strict verified build |
| `9baf28f` | — | Actual image/kernel WG authorization integration gate |

Final documentation/evidence commits follow these build/test commits. See Git history for their IDs; artifacts deliberately retain the exact build commits rather than claiming they were built from a later documentation-only revision.

## Device-test planning update — 2026-09-22

- Owner selected **this working container → phone over Wi-Fi** for ADB; `nas` is only for the server side, always in rootless Podman. No NAS ADB service or additional phone agent. Documented native TLS pairing with no initial USB, explicit phone ports, private diagnostic credentials separate from build/signing inputs, and optional USB only for uninterrupted live debugging.
- Expanded `DEVICE_TESTS.md` with staged inventory, actual existing hardware-test invocation, release routing/service tests, carrier/handover failures, encrypted OS recovery, disconnected battery measurements and evidence/cleanup gates. Wireless ADB loss during Wi-Fi-off is expected and must not be confused with VPN failure.
- Verified current Android/AOSP/GrapheneOS documentation and the instrumentation package in the already built APK. These edits are a test plan only: no ADB installation/pairing, device access, NAS/container deployment or firewall change was performed. App binaries and recorded artifact hashes are unchanged.

## Device session preparation — 2026-09-22

- Owner provided Android 13 at `192.168.0.20`; two ICMP probes pass from this working container. Installed official pinned ADB 37.0.1 in the separate private diagnostic workspace, verified archive metadata/hashes and loopback-only server binding. Release/debug/test APK checksums match the existing manifest.
- Native TLS pairing succeeded using the owner-provided code entered interactively. Connection awaits the main Wireless debugging port because mDNS discovery returned no services; no APK has been installed and no hardware/device gate is claimed passed. `DEVICE_TEST_RESULTS.md` tracks execution separately from the plan. Diagnostic credentials remain outside Git/build/signing material.
- Read-only NAS inspection confirms rootless Podman as `operator`, with different trial/Samba UID mappings preserved. The earlier upstream trial and Samba run; Syncthing is absent, NFS active. Recorded the existing dirty `/srv` worktree without staging or modifying it. The audited fork is not yet deployed; no NAS/service/firewall change was made.

## First physical-device results — 2026-09-22

- Native TLS Wi-Fi ADB connected to Pixel 4a / Android 13 / owner profile 0. All three verified APKs installed, signed release launched, and its installed APK hash matches the artifact manifest. HardwareStorageTest passes 1/1 (0.651 s), establishing actual hardware wrapping/tamper/non-exportability behavior on this phone. Public evidence is in `device-test-evidence/2026-09-22/`.
- Owner approved temporarily pausing official WireGuard always-on for connectivity tests; no pause has occurred yet. Public enrollment file is on the phone, waiting for protected PSK entry and its generated public reply. Seedvault is installed/selected but Backup Manager is disabled; recovery is not tested.
- Loaded verified OCI image on nas; prepared and committed only new isolated trial files at NAS `8613fb0`, preserving existing dirty work and services. Podman/crun cannot create a container because operator's kernel keyring count is exhausted (200/200). Exact temporary admin remedy is documented and requested; no keyring deletion, isolation bypass, sysctl/firewall or production-service change was performed.

- Owner applied the temporary kernel key-count allowance; verified 512/20000 and the pinned container image now starts successfully with its normal private keyring. No isolation bypass was needed.
- Owner changed provisioning: include the PSK in a confidential local QR/file instead of separate manual entry. Implementing a narrow new enrollment schema plus public-only reply, deleting the extra PSK UI, retaining local phone-key generation and unchanged runtime/network/battery behavior. No new dependencies.

## PSK enrollment simplification — implementation in progress

- Owner selected credential QR/file transfer over separate manual PSK entry. New `stunmesh-enroll-v2` adds optional canonical nonzero `preshared_key`; public response is unchanged. Removed the redundant requirement flag and custom manual secret-entry widget. Pasted enrollment is masked; no private-key/blob import or new dependency/background work.
- Provisioner reads the PSK from a private file or stdin, writes mode-0600/exclusive output and keeps public replies/error formatting free of the credential. Go/Android share public and synthetic-PSK fixtures. Go race tests for parser/provisioner pass, including invalid PSKs, redaction, output permissions and no overwrite. Android tests/build/device rerun are pending.

- QR PSK implementation validated: Go race/vet and 135,452 fuzz executions pass; Android clean offline build executes all 135 tasks with 11 debug + 11 release unit tests and release lint passing. Signed version 0.3.0-local.2/code 2 installed with the same owner certificate. Both device storage/enrollment tests pass (0.837 s). Native AAR/image are unchanged. `ARTIFACT_MANIFEST.json` identifies the actual build commits, including the separate provisioner; later Android edits are formatter-only.
- Confidential v2 enrollment JSON and QR generated locally from the existing PSK without printing it. JSON transferred over paired TLS ADB; superseded public proposal removed from Downloads. Waiting for owner review/create and the public reply; temporary credential download will be removed after successful import.
- NAS trial now starts with zero authorized peers, NET_ADMIN only, expected UID mapping and separate proxy/WG ports. Runtime configs committed at NAS `ac5a7e7`. No service shares, host firewall or startup changes.

- Current interactive gate is phone unlock: ADB remains connected, but the lock screen hides the app controls. Requested unlock to attempt normal UI enrollment/public-reply retrieval. Existing WireGuard remains active. Verified actual NAS process capability mask NET_ADMIN only, NoNewPrivs and seccomp; new trial has zero peers pending enrollment. No authenticated device VPN traffic is claimed yet.

- Phone enrollment completed by the owner. Validated its public reply against the confidential v2 proposal; authorized only its phone-generated public key/address while preserving the NAS-local PSK. Explicit trial configs committed at NAS `e139b7d`; only the new container restarted. Removed and verified absence of the temporary credential download. Official WireGuard remains active pending the authorized VPN switch; latest handshake is still zero before activation.

- Real LAN acceptance now passes: WG-authenticated phone key/PSK, exact 32 KiB TCP echo from shell and a distinct Android app UID, narrow routes/no VPN DNS, ordinary HTTPS, and automatic recovery in one ~21-second NAS restart test. Fixed the deployment-only proxy/LAN mismatch using separate LAN kernel-WG (51824) and discovery-proxy (51826) listeners at NAS `7023229`; no WG authentication change, new release dependency/background task or host firewall edit. Added opt-in live instrumentation (Android `c96b8e0`), built all 71 debug/test tasks offline and passed 1/1 on device. Release/native/image artifacts remain unchanged. Public evidence is mirrored in both repositories.

- Hotspot acceptance prepared: test handset has no SIM, so owner will use a separate phone’s mobile-data hotspot. Temporary tunnel-only HTTP canary verified locally, server public-metadata monitor active, exact steps supplied. This is an external NAT/Wi-Fi handover gate, not direct-cellular modem/battery testing. Await owner switch/result; keep STUNMESH active for the test, then restore original WireGuard and remove fixtures. VPS inspected read-only and available if needed.

- External hotspot test passes after slow automatic recovery: DHT exposed the new phone endpoint; NAS switched to it at 15:18:07 UTC and WG authenticated at 15:18:17. Owner confirmed tunnel-only HTTP content and ordinary internet. Exact Wi-Fi switch time was not observed. Returning home timed out. Identified stale handshake preservation and missing retry of the configured LAN bootstrap. First fix: health requires new WG handshake/receive progress after the initial snapshot, with a counter baseline on underlay change. Three focused race suites (discovery/controller/mobile) pass; no new timer, background service or dependency. LAN fallback fix and rebuilt artifact/device acceptance still pending.

- LAN recovery fix: retain the owner-reviewed bootstrap as the first bounded candidate after a network change, including during DHT failure; untrusted private/CGNAT hints remain rejected. Endpoint assignment now uses the existing recovery backoff until WireGuard reports progress. Focused race suites pass, including real WG recovery after a stale endpoint with DHT unavailable, candidate bounding/deduplication and rejection of unsolicited LAN hints. No extra polling loop, timer, dependency or authentication mechanism. New release/container build and physical retest follow.

- Recovery artifacts built from Go d4d12e9 / Android dc882f3: full Go race/vet, real image/kernel WG positive and negative cases, 11 debug + 11 release unit tests, release lint and 135-task clean offline build pass. Same owner signer; ABI contents and 16 KiB alignment verified. NAS scoped image deployment committed 18826ce. Compatible phone update installed code 3, but a process restart exposed unavailable persisted configuration; encrypted data is retained. Strengthened hardware test to bypass the process cache and exercise synthetic disk decryption, parsing and native validation before diagnosing the owner profile. Do not clear/re-enroll or claim updated physical acceptance yet.
