# Security remediation progress

Started 2026-09-22. Implementing `SECURITY_REMEDIATION_PLAN.md` at Go `2b900a7` / Android `3dccad9` on `security-remediation/2026-09-22`. The original audit branches/evidence remain intact. This log is mirrored into the Android repository; the Go copy is canonical.

## Completion contract

Complete all locally possible implementation, tests, release APK and container builds before handoff. No deployment, firewall change, NAS ownership change, production key rotation or real phone reset. NAS/GrapheneOS access arrives later: hardware Keystore/backup, real NAT, battery measurements and rootless host integration must be identified as pending device checks, never claimed passed locally. No public push/release.

Security and simplicity take precedence over legacy features: OpenDHT hints cannot authorize peers; WireGuard remains the authenticator. Only destination split routes. Owner-authorized encrypted OS backup is allowed, ordinary secret export is not. Keep verified dependency pins, delete unused execution/release/debug paths, avoid additional background workers.

## Work checklist

| Stage / plan IDs | State | Evidence / next action |
| --- | --- | --- |
| 0: baseline and toolchain | In progress | Clean starting worktrees; inspect cached verified toolchains and source. |
| 1: typed config/UAPI, public discovery, OpenDHT only (D1/D2, F1/F2) | Pending | Convert exploit regressions into rejection tests; remove unused plugins/crypto. |
| 2: bounded discovery/STUN/proxy and power behavior (F3–F7) | Pending | Fake-network, race and fuzz tests; delete unused raw capture paths. |
| 3: Android storage/backup/import/lifecycle (F8–F12/F16, N2) | Pending | Unit and release tests, foreground VPN, serialized state, safe diagnostics. |
| 4: local artifacts/dependency/image trust (D3, F13–F15/F17) | Pending | Exact local AAR, verified offline inputs, unsigned build then isolated signing. |
| 5: public QR provisioning and local integration (F18) | Pending | Bounded public schema, no secret QR, WG authorization tests. |
| 6: final verification and device handoff | Pending | All local tests/builds; exact artifacts/checksums and documented pending device tests. |

## Decisions during implementation

- Preserve existing package namespaces unless a targeted change is needed; the installed fork gets a distinct stable application ID before its first release.
- Do not introduce a second backup mechanism: implement the small platform key-value adapter. Device-specific transport/StrongBox validation remains a real-phone gate.
- Synthetic test keys/configuration only in tracked tests and logs. Owner signing material stays outside both repositories and the build context.

## Validation and commits

- Baseline: both worktrees clean; identical plan and complete original audit evidence.
- Progress will be recorded here at each coherent implementation milestone, including failing checks and remaining work.
