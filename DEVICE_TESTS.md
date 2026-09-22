# Later NAS / GrapheneOS acceptance gate

Local evidence covers source tests, release compilation, artifact checks and isolated namespace tests. It does **not** establish real carrier NAT success, NAS service access, hardware Keystore/Seedvault recovery or battery consumption. Those tests require the server/phone access the owner will provide later.

## Before changing the server

Record the existing rootless service user, process UID/GID maps, image/config Git commit, container networks/ports and filesystem ownership. Preserve encrypted-mount startup, Samba/NFS, credentials/history and disabled Restic. Load only the verified OCI archive; compare its SHA-256 and recorded image config/manifest digests. Review the dedicated trial compose config and the WG public peer entries before starting it. Mount no share/data directory into discovery.

Use the selected proxy UDP port `51820` and a different unexposed kernel WG port `51822`. Confirm namespace `NET_ADMIN` suffices; do not add `NET_RAW`/privileged/host networking or a broad ownership change if a check fails. Diagnose the actual failure. The later service integration must explicitly connect selected Samba/Syncthing service addresses; a successful isolated `.1` ping does not prove either service is reachable. Explain any necessary routing/firewall change before making it.

## Phone / network matrix

Install the final signed release APK, verify package `dev.stunmesh.local`, version and signing certificate against ARTIFACT_MANIFEST.json. This is a separate installation from upstream. Use public enrollment and an explicit server peer addition; no server-generated phone private key or legacy private profile import. Keep Android **Block connections without VPN disabled**.

| Check | Required result |
| --- | --- |
| Wi-Fi then actual carrier connection | Real WG handshake and bidirectional service traffic; STUN/publish success alone is insufficient. |
| Selected server routes from two apps | Both apps reach only the intended server IPs through WG. Confirm with traffic/routing evidence. |
| Ordinary IPv4/IPv6 internet and DNS | Public-IP checks/browser/unselected destinations continue over the normal underlay. No default/equivalent broad route or VPN DNS takeover. |
| VPN off / peer unavailable / DHT unavailable | Ordinary internet remains usable. Selected services may be unavailable; UI must not claim authenticated connectivity from the VPN icon. |
| Wi-Fi ↔ cellular, changed IP, airplane mode | Sockets bind/protect the new physical network; no TUN restart storm, stale request backlog or offline discovery timer. Record reconnection time; public-hint recovery can take multiple refresh cycles, especially with the server proxy. |
| IPv4-only and IPv6/CLAT underlays | Skip unavailable families; confirm actual supported-family connectivity. Carrier IPv4 is the owner's expected path. |
| Wrong public key / wrong PSK / unknown peer | No authenticated traffic; hints cannot enroll the unknown peer or disable PSK. |
| App backgrounded / screen off / process killed / VPN revoked | Foreground notification is present, resources are released on stop/revoke, sticky restart uses the durably selected profile, and failures are visible without secret logs. |
| Device reboot then unlock | Credential-encrypted/hardware-key availability is handled without replacing unreadable state. Confirm the owner's chosen always-on behavior. |
| Diagnostics/clipboard/scanner/files/logcat | Only public enrollment/status appears; no private key, PSK, raw config, blob, URL credentials or exception excerpt. |

The debug APK/instrumentation target has a different ID. `HardwareStorageTest` uses synthetic bytes, verifies hardware wrapping/non-exportability and GCM tamper rejection without replacing the app's store. Compile success is recorded locally; execution requires the phone and must not be claimed until done. Test the **release** UI/OS lifecycle separately; instrumentation is not a substitute.

## Authorized encrypted Android backup

Use the installed GrapheneOS backup transport and a disposable synthetic peer first. Record OS/transport versions and recovery-secret custody. Verify that it advertises client-side encryption; the app refuses backup otherwise, including a device-transfer-only capability. No app fallback/export should appear.

1. Enroll, authorize and connect the synthetic profile; record its public identity and routes.
2. Ask the OS for a backup while the VPN is active and while stopped. Verify existing valid backup state survives locked/unavailable-key failures and unchanged snapshots are coalesced.
3. Restore on a clean authorized installation/device. Verify fresh hardware wrapping, same restored WG public identity/routes, **inactive profile**, explicit review and VPN consent before activation. Confirm no raw local ciphertext/wrapping key was transported as a generic file backup.
4. Keep the original peer device off, then verify the restored identity connects. Never run clones simultaneously. For a lost/untrusted original or exposed backup, enroll a fresh key and revoke the old one instead.
5. Test malformed/oversized/future-schema restore and interrupted writes with synthetic data: no empty-store replacement, partial profile or silent key rotation. Retry safely with the original valid state.

Ordinary USB/MTP/non-debug ADB is not a private-key export path. Possession of an authorized backup plus its recovery secret deliberately enables recovery; privileged debugging, a malicious OS/transport or a sandbox escape are outside the ordinary-app threat model.

## Battery and reliability

Compare VPN off, active-idle and actual Syncthing sync over comparable Wi-Fi/cellular periods, including overnight idle and repeated handovers. Record CPU, wakeups, network bytes/radio activity, app battery use and reconnect delay. Expect one discovery scheduler (healthy ~180 seconds, TTL 600 seconds), bounded backoff and separate WG keepalive (initially 25 seconds). Keepalive itself has a radio cost; tune it only after NAT measurements, never by adding another health poll/job/wake lock or an automatic battery exemption.

Only after these checks should the trial be considered for boot startup or production service routing. Commit each reviewed server configuration change; retain the prior image/config for scoped rollback. No NFS, Samba, Restic or host-firewall change is implied by installing this fork.
