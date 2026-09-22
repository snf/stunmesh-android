# Working on this fork

Read `SECURITY_REMEDIATION_PLAN.md`, `IMPLEMENTATION_PROGRESS.md`, `LOCAL_BUILD.md` and `DEVICE_TESTS.md`. Keep the original audit/evidence as historical baseline. This is an owner-built Android app, `dev.stunmesh.local`, paired with the sibling Go repository. Every variant requires its exact locally built AAR hash; there is no stub/remote fallback or public CI release workflow.

WireGuard alone authorizes VPN traffic. OpenDHT records are bounded public hints, never configuration authority. Keep route-only split tunneling, no VPN DNS/default routes/app selectors. Do not add executable plugins, custom discovery crypto or private-key import/export. Phone-generated identity uses verified StrongBox/TEE wrapping; only the system-bound encrypted key-value backup adapter may deliberately recover a logical secret snapshot. Never put secrets in diagnostics, QR, clipboard, intents or ordinary files.

`ConfigRepository` owns serialized, durable store changes. `StunmeshVpnService` serializes backend lifecycle; physical network callbacks drive one cancellable Go scheduler. Avoid polling, wakelocks, jobs and redundant UI/background work. No lossy multi-store editor. The only secret entry is optional PSK under the protected password UI.

Build/test offline with the pinned inputs and strict verification/locks. Run debug and release unit tests, release lint, release packaging and hardware-test APK compilation. Real StrongBox/TEE, GrapheneOS backup, mobile handovers, NAS UID/GID mappings and battery measurements require the actual devices; never label APK compilation as those tests passing. Source builds cannot read owner signing keys. Sign only the reviewed unsigned APK in the separate signer; never regenerate an existing owner key or publish automatically.
