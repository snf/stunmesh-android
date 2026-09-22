# Security remediation progress

Started 2026-09-22. Implementing `SECURITY_REMEDIATION_PLAN.md` at Go `2b900a7` / Android `3dccad9` on `security-remediation/2026-09-22`. The original audit branches/evidence remain intact. This log is mirrored into the Android repository; the Go copy is canonical.

## Completion contract

Complete all locally possible implementation, tests, release APK and container builds before handoff. No deployment, firewall change, NAS ownership change, production key rotation or real phone reset. NAS/GrapheneOS access arrives later: hardware Keystore/backup, real NAT, battery measurements and rootless host integration must be identified as pending device checks, never claimed passed locally. No public push/release.

Security and simplicity take precedence over legacy features: OpenDHT hints cannot authorize peers; WireGuard remains the authenticator. Only destination split routes. Owner-authorized encrypted OS backup is allowed, ordinary secret export is not. Keep verified dependency pins, delete unused execution/release/debug paths, avoid additional background workers.

## Work checklist

| Stage / plan IDs | State | Evidence / next action |
| --- | --- | --- |
| 0: baseline and toolchain | In progress | Pinned Go 1.27.1/JDK 21/Gradle 9.5/SDK installed in isolated workspace. ARM64/x86-64 AAR built; full graph verification/offline final builds continue. |
| 1: typed config/UAPI, public discovery, OpenDHT only (D1/D2, F1/F2) | In progress | Typed mobile UAPI, bounded JSON/YAML, low-order key/port/route checks pass targeted race tests. D1/D2 public hints and OpenDHT-only path implemented; Android bounded public-only admission implemented; release validation continues. |
| 2: bounded discovery/STUN/proxy and power behavior (F3–F7) | In progress | Shared STUN parser/source checks, proxy collision ownership, bounded HTTPS discovery and mobile event loop implemented. Race suites pass; additional lifecycle/fuzz/real WG tests continue. |
| 3: Android storage/backup/import/lifecycle (F8–F12/F16, N2) | In progress | Hardware wrapping, atomic store, encrypted OS-only backup, public enrollment and foreground VPN implemented; compiling/testing both variants. |
| 4: local artifacts/dependency/image trust (D3, F13–F15/F17) | In progress | Exact local AAR, verified offline inputs, unsigned build then isolated signing. |
| 5: public QR provisioning and local integration (F18) | Implemented | Shared public schema/fixtures; WG authorization and negative-peer tests pass. Real device scan remains a device gate. |
| 6: final verification and device handoff | Pending | All local tests/builds; exact artifacts/checksums and documented pending device tests. |

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
