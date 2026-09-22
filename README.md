# STUNMESH for Android

Local, owner-built fork pairing Android's `VpnService` with the locally built STUNMESH Go core and upstream WireGuard. WireGuard authenticates peers and encrypts tunnel traffic. STUN/OpenDHT supply **public, unauthenticated endpoint hints**; they cannot enroll peers or change keys/routes. There is no payload relay, Tailscale control service, analytics SDK, remote updater or executable plugin.

The VPN routes only explicitly selected server destination addresses/ranges (`AllowedIPs`); ordinary phone traffic and DNS keep using the phone's network. Prefer server `/32` and `/128` routes. Leave Android's **Block connections without VPN off**. Only one VPN can be active on Android. Direct NAT traversal is not guaranteed on every network, and public hints expose endpoint metadata/permit denial of service.

## Install and enroll

Use the signed release APK and certificate/checksums in `ARTIFACT_MANIFEST.json`, not an upstream APK or CI artifact. The application ID is **`dev.stunmesh.local`**, minimum Android 9, supported ABIs ARM64 and x86-64. Debug installs use `.debug` and synthetic identities only.

Follow [PROVISIONING.md](PROVISIONING.md). The phone generates its own private key after reviewing a public proposal; only its public reply is copied to the server. Optional PSKs use a protected separate input. No ordinary private-key import/display/export or secret QR exists. Profiles are read-only apart from rename/remove/selection; an encrypted OS restore can preserve full configuration without a lossy editor.

Configuration is atomically stored under hardware-backed AES-GCM wrapping. Software-only Keystore protection fails closed. Only the trusted OS backup agent can intentionally emit the logical configuration, and only to a transport reporting client-side encryption. Restore re-wraps under the new hardware key and leaves profiles inactive. Protect backup recovery secrets; never run old and restored copies of the same identity concurrently. Real GrapheneOS transport behavior remains a device gate.

## Operation and validation

One foreground service owns the backend. Underlay callbacks suspend network work while offline and coalesce handovers; no periodic app health poll, wakelock, WorkManager job or backup scheduler exists. Bounded discovery renews below record expiry; WireGuard's own keepalive remains independent. Doze, carrier NAT and proxy availability still affect connectivity. Measure before granting battery exemptions; no exemption is requested automatically.

[LOCAL_BUILD.md](LOCAL_BUILD.md) describes verified offline builds, mandatory local AAR input and isolated owner signing. [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md) records work and validation; [DEVICE_TESTS.md](DEVICE_TESTS.md) covers the later NAS/GrapheneOS tests. The original [SECURITY_AUDIT.md](SECURITY_AUDIT.md) describes the upstream baseline; [SECURITY_REMEDIATION_PLAN.md](SECURITY_REMEDIATION_PLAN.md) records decisions and acceptance criteria.

App code is [Apache-2.0](LICENSE); the Go core and WireGuard retain their own licenses. WireGuard is a registered trademark of Jason A. Donenfeld.
