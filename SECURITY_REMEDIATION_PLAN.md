# STUNMESH: merged security remediation plan

**Updated:** 2026-09-22 · **Revision:** destination-only split tunnel, authorized encrypted identity backup, Android sandbox threat model, public-information QR provisioning. **Status:** local remediation implemented and validated; no NAS deployment or physical-phone validation performed. See `IMPLEMENTATION_PROGRESS.md`, `VALIDATION.md` and `ARTIFACT_MANIFEST.json`.

This is one plan for both Owner forks. An identical copy belongs at the root of each repository; update both copies together. The Go copy is canonical. Finding IDs refer to the original [Go audit](../stunmesh-go/SECURITY_AUDIT.md) and [Android audit](../stunmesh-android/SECURITY_AUDIT.md), which preserve evidence, prerequisites and limitations. Those reports remain historical records; record remediation outcomes separately. Relative cross-repository links assume sibling checkouts named `stunmesh-go` and `stunmesh-android`.

## 1. Project context and implementation contract

| Repository | Role | Audited production commit | Local audit commit |
| --- | --- | --- | --- |
| [snf/stunmesh-go](https://github.com/snf/stunmesh-go) | Desktop discovery daemon, optional local UDP proxy, and Go mobile core embedded in Android | `71a73228cd2bc001cdc5d485a16621a24bfae15a` | `934d4ef423a20498f3d9e579b6581ff5e3829a8c` |
| [snf/stunmesh-android](https://github.com/snf/stunmesh-android) | Kotlin UI, configuration/import/export, Android VPN lifecycle; consumes the Go core as an AAR | `e0cc30951e24ec018423bb113acfe7849f9836d5` | `6641b916be56f9dcdd298a1ba1ef9c69f2b0f48d` |

Implementation is on `security-remediation/2026-09-22`; the following audit revisions are the historical baseline. Both local audit branches are `security-audit/2026-09-13`. Public forks may not contain those branches: obtain the local audit commits and `security-audit-evidence/` before implementing. The audited Go mobile source matches `v1.15.1`; Android matches `v0.2.1`. Review intervening changes before any rebase/update.

**Architecture.** STUN finds a public UDP mapping. OpenDHT proxies exchange endpoint records. WireGuard then carries encrypted traffic directly between peers. An OpenDHT proxy is **not a tunnel-traffic relay**; it remains useful for discovery refresh and recovery. The optional Linux UDP proxy is local to the host. There is no TURN/DERP fallback, so some NAT combinations will fail. Android embeds official `wireguard-go`; the desktop daemon controls a separately established WireGuard interface.

**Why remediation is required.** The audit demonstrated that untrusted text can change Android's configured WireGuard peers, PSKs and routes. WireGuard then correctly authenticates an attacker-added peer. With valid keys, discovery exploitation required either participant's static private key, but not the PSK; a malicious imported profile is a separate route. An all-zero configured peer key permits an outsider to forge discovery records. This is a controller/configuration failure, not a break of WireGuard cryptography. The audited upstream Android build must not serve as the trusted network entrance. The remediated local release has passed the checks recorded in `VALIDATION.md`; production use still requires `DEVICE_TESTS.md`.

No deliberate backdoor or compromised downloaded dependency was identified. Server release binaries reproduced exactly; Android/native comparisons strongly supported source correspondence but were not wholly bit-identical. These are bounded findings, not a certification of dependencies, publishers or future updates. Several Android findings are source/platform-contract findings without phone reproduction.

**Requirements already decided by the owner:**

- WireGuard modules must perform peer authentication. Discovery must never enroll peers, remove PSKs, grant routes or authorize traffic. Do not introduce another peer-authentication protocol.
- Keep rootless **Podman** on `nas`; no Docker migration. Preserve the existing service identity and host UID/GID mappings; inspect them before eventual deployment. Android naturally runs as an Android app.
- Build Go/container and Android artifacts locally from reviewed Owner commits, using already available, verified, pinned dependencies. Only the owner installs the locally generated APK. Keep a stable local APK signing key; remove public release, hosted AAR, updater and Obtainium requirements. No new runtime dependency without a concrete need and review.
- The VPN is **destination-based split tunnel only**: route explicitly selected server IP addresses/ranges through WireGuard, for any app. All other traffic uses the phone's normal internet connection. No app selector, default routes, full-tunnel mode or system lockdown that blocks ordinary traffic.
- Android configuration and its WG identity **may be recovered through an owner-authorized encrypted backup**. F8 recommends the Android/GrapheneOS system backup mechanism, with no app-owned backup service or scheduler. A verified restore may retain the existing peer identity; without a usable backup, deliberately enroll a new peer and retire the previous one.
- Generate the phone private key locally; protect it at rest with the strongest verified hardware-backed Keystore wrapping key. Its presence in the app's memory for `wireguard-go` is **accepted**. No ordinary extraction, display, clipboard, secret provisioning or generic export path is allowed. The trusted OS backup/restore path is an explicit exception to the former ban on any transfer, including encrypted blobs; the hardware wrapping key itself remains non-exportable and non-backupable.
- F18's external-scanner/local-encoder QR approach remains selected: the local QR/file may include the optional WG PSK, and the phone returns only its public key. Treat a PSK-bearing proposal as confidential. Phone private keys and wrapped private-key blobs never enter the QR/import path; backup recovery remains separate.
- Review battery cost across the whole Android design. Use bounded, event-driven work; no extra polling services, permanent wake locks or automatic battery-optimization exemptions.
- Public STUN/OpenDHT dependencies are acceptable. No permanently managed VPS is desired. Direct connectivity must be tested; an external VPS is available for a later authorized trial, and the phone test will follow separately. The mobile carrier lacks IPv6.
- Keep existing encrypted-filesystem startup, Samba ownership/access, NFS service and configuration history. Restic remains configured but disabled until backup paths are chosen. Do not change firewall rules under this code-remediation plan. Any later necessary rule needs an exact explanation and approval.
- Keep changes under Git, in small reviewable commits. Do not rewrite existing configuration/credential history. New signing secrets belong outside source/build contexts; this does not authorize deleting historical credentials.

**Scope:** documentation only; implementation and NAS changes follow later. Keep work in local Git; no public disclosure/publication is authorized here. Fork Actions were disabled at audit completion and are unnecessary for local builds.

**Android threat model:** malicious apps may be installed but cannot escape Android's sandbox. Trust the OS/Keystore, the reviewed app and the owner-selected system backup transport; do not grant other apps a secret-reading IPC interface. The sandbox protects app-private files and process memory, including native code. A compromised app/OS, malicious signed update or separately granted elevated access exceeds this assumption. Hardware wrapping does not protect a key after trusted code has unwrapped it. Authorized backup deliberately extends the trust boundary to the OS backup component and whoever possesses the backup plus its recovery secret. [Android sandbox](https://source.android.com/docs/security/app-sandbox).

### Reading and prioritization

Design decisions **D1–D4 are settled**. Authorized encrypted backup and in-process WG key use are accepted, superseding the blanket no-backup requirement and unresolved hardware-confinement question. F8/N2 define the protection and recovery contract; F18 now allows a confidential PSK-bearing proposal and public-only response (owner revision 2026-09-22). **F1–F18 retain their IDs**. Section 7 lists device/backup validation and provisioning inputs, not previously settled choices.

Complexity is relative: **S** = narrow change with little new state; **M** = several boundaries or lifecycle state; **L** = protocol migration or broad redesign. Delete unused code before adding mechanisms. Keep discovery, WireGuard, Android lifecycle, secure storage and local signing responsible for separate tasks. “Settled” means selected, not implemented or verified.

## 2. Design decisions and researched requirements

### D1. Make discovery non-authorizing and stop reusing WireGuard keys

**SETTLED · Cross-codebase · High policy mismatch · G-02; governs G-01/G-03/G-14 and A-01/A-17 · Complexity M, coordinated protocol migration.**

**Risk/context:** `internal/crypto/endpoint.go` uses static-static NaCl `box` with WG private keys outside WireGuard. It excludes the WG PSK, permits reverse-slot reflection, and exposes recorded endpoint metadata after later static-key compromise. Desktop `internal/wg/client_ctrl.go` and `client_cli.go` read the WG private key into the daemon. This contradicts the literal authentication requirement even after injection is fixed. WireGuard's PSK participates in its own handshake, not this discovery codec. [WireGuard protocol](https://www.wireguard.com/protocol/).

**Decision:** remove NaCl discovery and private-key reads. Use bounded, unauthenticated typed endpoint hints; WireGuard alone authenticates peers and packets. The accepted tradeoffs are public endpoint metadata and a discovery service that can suppress, replace or replay hints. No separate discovery keys, signature protocol or legacy crypto fallback.

**Settled contract:** local configuration owns the peer public key, PSK and AllowedIPs. Discovery returns only a canonical numeric IP:port candidate associated with an already configured peer. The controller selects that peer from its trusted polling context, never from a peer identifier supplied by the record. No keys, routes, commands or configuration fragments are accepted from a store. Public-key-derived indices are identifiers, never authenticators. Public hints reveal endpoints to readers who locate the records; STUN/proxy operators already see some source metadata. HTTPS certificate verification remains mandatory: it authenticates the proxy transport, never a tunnel peer.

Use a versioned record schema and distinct protocol namespace; update both peers together. Reject incompatible records explicitly. Do not implement automatic legacy fallback. A small explicit configuration conversion and coordinated restart is preferable to two permanent protocols. Sender/slot labels and timestamps in unauthenticated records can prevent accidental confusion but cannot establish authenticity or freshness.

Remove private-key fields from desktop discovery interfaces and WG dump parsing that materializes private keys. This reduces accidental exposure; a daemon retaining unrestricted WG control may still retrieve/change keys. Do not add another privileged helper process without a demonstrated need. Android's embedded WG engine necessarily retains its private key in the app process; discovery must not consume it.

**Acceptance:** discovery never consumes a private key or PSK. Synthetic hostile records can change only a candidate endpoint. Wrong static keys and wrong/missing PSKs cannot complete a WG session where a PSK is configured. Record the schema/version change in the local configuration migration. F1 must precede testing unauthenticated hints.

### D2. Ship a minimal OpenDHT-only product, not executable plugins

**SETTLED · Cross-codebase · Medium conditional code-execution exposure · G-06, A-02; dependency portion of G-07/A-08 · Complexity S–M.**

**Risk/context:** Android imports can reach Go `exec`/`shell` constructors despite a built-ins-only contract. Execution was demonstrated in the Linux mobile build, not Android OS. Optional shell helpers evaluate shell input; a Cloudflare helper accepts its token in process arguments. Existing `builtin_opendht` tags remove Cloudflare but leave executable-plugin constructors linked.

**Decision:** retain built-in OpenDHT only. Delete executable-plugin constructors, unused Cloudflare integrations/helpers/UI, and their build/release paths from this fork; reject unsupported imported types. No plugin sandbox or separately maintained plugin product. Multiple named OpenDHT instances may remain where needed for existing configuration; F9 prevents silent editor changes.

**Files:** `internal/plugin/manager.go`, `exec.go`, `shell.go`, `contrib/` helpers, mobile construction, Android `TunnelYaml.kt`/`TunnelConfig.kt`/editor, build tags and recipes. Delete dependencies that become unused; do not replace working dependencies just to reduce a count.

**Acceptance:** both Android import and direct mobile JSON reject executable/unknown types before saving or starting. Tests cannot construct a process plugin in either home artifact. Inspect linked packages and image contents; a build-tag label alone is insufficient. See F9 for imported multi-store editing and N3 for further, optional dependency reduction.

### D3. Build and sign locally using the existing pinned inputs

**SETTLED · Cross-codebase · High release-trust risk · A-03; supply-chain portions of G-07/A-08 · Complexity S–M.**

**Risk/context:** Android release builds resolve an upstream Go AAR even when a local AAR exists. Repository ordering may permit the same coordinate from another repository; that substitution was not observed. Mutable tools/actions and build-time access to signing/publishing credentials let a compromised tool bypass source review. Pinning protects against unexpected changes, not malicious code already pinned.

**Decision:** one explicit local Go AAR → unsigned APK → local signing sequence. Remove remote AAR resolution, “latest” downloads, stub fallback, CI signing/publication and hosted feeds. Use the verified audit workspace inputs and existing dependency pins; inventories/checksums are under `security-audit-evidence/`. Any necessary version change must name the security or compatibility reason and remain separate from this simplification.

**Implementation:** consume one exact AAR path and expected checksum, failing if missing or wrong. Build without the signing key mounted/accessible, then use the already installed Android signing tools on the selected artifact in a separate minimal step. Record Go/Android commits, AAR/APK hashes, signing certificate, versionCode and OCI digest in a small local build manifest. No new provenance server or automation platform.

Use one stable fork application ID and APK signing identity, increasing versionCode on updates. Choose them before first installation; changing identity can prevent updates or OS restoration. A distinct ID avoids mixing upstream and local installations. Protect/back up the **workstation APK signing key** outside the build workspace; it is distinct from the phone WG identity, the phone's non-exportable wrapping key and the OS backup recovery secret. Local signing still trusts the tools and workstation.

**Acceptance:** missing/tampered AAR fails; the clean offline build uses the declared cached inputs. Signing runs no Gradle/plugin code. Verify the APK certificate/hash and container digest before manual installation. F13–F15 implement this path; no push or public artifact release is required.

### D4. Route only selected server IP addresses/ranges through the VPN

**SETTLED: former option 2 · Android/routing · A-09/A-21 remain deferred · Complexity S.**

**Verified online and against local source (2026-09-22):** Android supports destination routes through `VpnService.Builder.addRoute`. The official WireGuard Android app's `GoBackend` iterates each peer's `AllowedIPs` and calls that API. For its non-default-route configuration it also enables IPv4/IPv6 family fall-through. Thus narrow `AllowedIPs` can implement this split tunnel without application selection. [Official backend](https://git.zx2c4.com/wireguard-android/tree/tunnel/src/main/java/com/wireguard/android/backend/GoBackend.java?id=e7b3a3c118836e112620b1302a8ba1873ad4daac), [Android routing guidance](https://developer.android.com/develop/connectivity/vpn#service).

The existing STUNMESH `StunmeshVpnService.establishTun` already adds routes from `config.peers.flatMap { it.allowedIps }`. Retain and validate this design. **Do not add** app allowlists/exclusions, package enumeration, a packet filter or another VPN library. The official app demonstrates the route mechanism but does not implement this project's rendezvous protocol.

**Configuration contract:** phone-side `AllowedIPs` contains only the selected server tunnel/service IPs (`/32`, `/128`) or explicitly approved service ranges. Do not add `0.0.0.0/0`, `::/0`, broad private-network ranges “for convenience,” or equivalent catch-all prefix combinations. Keep default DNS/ordinary internet on the underlay. On the server, the phone peer's `AllowedIPs` normally identifies the phone's own tunnel address; do not copy the phone's destination-route list onto that peer. These lists have different roles in the two configurations.

For example, an illustrative phone peer route `AllowedIPs = 10.77.0.1/32` sends only that server address through WG; a public website uses the phone's normal connection. The actual service addresses remain a deployment input. Prefer dedicated tunnel/service addresses where roaming LAN ranges overlap. All apps can use these server routes: destination routing is not an app/port access-control policy. A route to one server IP includes its reachable services/ports; finer service isolation is a separate server decision, with no firewall changes authorized here.

**Android details:** add the selected routes and explicitly `allowFamily(AF_INET)`/`allowFamily(AF_INET6)` so an unused VPN family is not blocked. No default route or forced VPN DNS. Keep “Block connections without VPN” **off**; always-on startup is separate. Leave discovery/outer WG sockets protected from the VPN. Split routes do not require `allowBypass()`; that API allows applications to override VPN routing, which is a different feature. [Android builder contract](https://developer.android.com/reference/android/net/VpnService.Builder).

**DNS decision:** no new DNS policy, resolver feature or privacy hardening for now. Leave discovery fallback unchanged and record A-09/A-21 as accepted/deferred risks, not fixed. Do not add forced VPN DNS or DNS interception. Validate that ordinary name resolution still works in the split profile; use explicit server addresses for the initial trial. TLS/proxy validation and route safety fixes remain in scope.

**Acceptance:** test at least two apps: each reaches a selected server address through WG while public-IP checks, browsing and unselected destinations stay on the underlay. Test IPv4/IPv6 on Wi-Fi, cellular, DNS, route overlap, handover, tunnel failure and tunnel off. Confirm with routing/packet evidence; the VPN icon alone proves nothing. A failed tunnel must not block ordinary phone internet. F6 implements this scope; direct NAT connectivity remains separately tested.

## 3. Implementation fixes

### F1. Make all WireGuard configuration rendering typed and endpoint-only

**Cross-codebase · High deployment blocker · G-01/A-01 · Complexity M.**

**Risk:** multiline discovered/imported endpoints and AllowedIPs inject UAPI commands, create a hidden peer, remove a PSK and transfer routes. Later `IpcSet` errors do not roll back prior commands; a clean endpoint refresh does not remove the rogue peer.

**Agreed fix:** parse at the Go trust boundary into `netip.AddrPort`, `netip.Prefix` and fixed-size keys; reject controls, wrong lengths, ports outside 1–65535 and disallowed addresses. Render only canonical typed values. Initial configuration may set the trusted peer list; discovery's API accepts only an existing peer ID and typed endpoint. Validate Android imports as well, but never trust app-side validation alone. Numeric addresses are mandatory for discovery; if user-configured hostnames remain supported, resolve them through an explicit bounded path into typed addresses before UAPI rendering.

Untrusted public hints should reject unspecified, multicast, loopback, link-local and other locally forbidden destinations; permit LAN candidates only under an explicit locally configured policy for the intended subnet. Do not confuse “unicast” with “safe to contact.” This limits attacker-induced UDP probes without promising complete prevention. Keep scoped IPv6 handling explicit rather than accepting arbitrary zone strings.

**Settled approach:** typed rendering; no WireGuard fork or new cryptographic protocol. Check invariants when configuration mutates, not with a periodic background audit loop.

**Files:** Go `mobile/uapi.go`, `mobile/controller.go`, `mobile/config.go`, `internal/ctrl/establish.go`; Android `TunnelYaml.kt`, `WgQuickConf.kt`, `TunnelConfig.kt`.

**Acceptance/recovery:** invert the audit's injection reproductions into rejection tests; compare peer keys, PSKs and AllowedIPs before/after adversarial refreshes and verify no plaintext reaches an unconfigured peer. Keep single ownership of updates; compare security state after configuration mutations without logging secrets. Unexpected partial mutation must close/stop the compromised device and rebuild from validated local configuration before resuming. A validation failure before mutation leaves the prior good device intact. Never claim a successful clean refresh repairs pre-existing corruption.

### F2. Bound and validate configuration before storing or applying it

**Cross-codebase · Medium conditional/local risk, plus low availability defects · G-09/G-10/G-13/G-14, A-07/A-17 · Complexity M.**

**Risk:** oversized Android imports exhaust memory; duplicate fields mislead review; malformed Go YAML mapping keys panic; invalid intervals crash tickers; out-of-range ports wrap; low-order peer keys defeat the legacy discovery codec. The malformed peer itself still cannot authenticate with WireGuard.

**Agreed fix:** bounded read before parsing; strict typed schema and counts/length limits; reject non-string/null YAML keys, unnecessary aliases and duplicate keys. Reject repeated wg-quick singleton fields while preserving deliberately supported repeated list fields. Validate every refresh/ping/HTTP interval as positive and bounded before starting workers. Validate ports before narrowing to `uint16`. Reject low-order public keys using the standard X25519 API's error behavior, not custom curve arithmetic; this is input validation, not a new peer authenticator. Preserve WG's own handshake rejection.

Retain useful public configuration syntax, but reject private-key-bearing ordinary Android imports under F16; this deliberately narrows legacy profile compatibility. F8's OS restore callback is a separate authorized path and must apply the same bounded typed validation before changing local state. Internal Go/WG configuration still needs typed validation. Upgrading YAML/mapstructure alone is insufficient. Parse only on import, restore or configuration change.

**Starting limits for fixture review:** 256 KiB imported profile, 32 peers, 8 store instances, 64 routes/peer, 2 KiB URL, 256-byte numeric endpoint field; reject oversized input without truncation. These are proposed home-use bounds, not upstream protocol limits. Centralize them, validate existing fixtures and tune only with a documented requirement.

**Files/acceptance:** Go `internal/config/{config,device}.go`, `mobile/config.go`, plugin config and endpoint parser; Android import/model parsers. Test at/below/above limits, null-key fuzz reproducer, duplicates, low-order keys, zero/negative intervals, port 0/65536/65537, controls and malformed CIDRs. Errors must preserve the existing config and contain no source/secret snippets. See F11.

### F3. Bound DHT responses and make candidate failure recoverable

**Cross-codebase · Medium availability risk · G-03/G-05 · Complexity M; depends on D1.**

**Risk:** unbounded bodies can exhaust memory; zero/negative timeout disables HTTP deadlines. Selecting the largest unauthenticated timestamp lets public writers eclipse valid records. A successful malformed response prevents useful fallback; same-second publications can choose stale data.

**Agreed fix:** cap body bytes, entry count and individual decoded fields; use explicit request deadlines; validate status and bounded content before acceptance; try another configured proxy after invalid content. Start with a 256 KiB body, 64 scanned records and at most 4 distinct usable candidates per peer/cycle, subject to real proxy-fixture validation. Read limit+1 bytes or equivalent to detect overflow, including decompressed HTTP bodies. Bound decoding allocations too.

Treat timestamps as untrusted hints, never proof of freshness. Do not replace a demonstrably working WG endpoint on every unsolicited record. Try bounded alternate candidates when connectivity needs recovery, use actual WG handshake/receive evidence and avoid concurrent competing endpoint writers. Preserve a last-working candidate, bound retries and rate-limit probes. An attacker can still fill all returned slots or suppress values: **DoS resistance is best-effort, not solved**.

**Battery constraint:** one shared cycle/deadline per tunnel; sequential bounded fallback rather than racing every proxy/peer. Reuse HTTP connections where safe, stop failed-network retry loops, and use bounded backoff with jitter. Check WG counters during existing work; no continuous handshake polling, synthetic pings or separate watchdog. New authenticated-record/replay state is excluded by D1.

**Files/acceptance:** `internal/plugin/builtin/opendht/opendht.go`, plugin interface/controller selection; Cloudflare body limits too if retained. Test oversized/chunked/compressed responses, stalled reads, malformed HTTP 200, equal/future/stale timestamps, replay/reflection, all-invalid first proxy, deduplication and bounded retries. Verify forged candidates never change authorization. Do not flood a public DHT during testing.

### F4. Validate STUN replies and keep fallback readers alive

**Cross-codebase · Medium availability risk · G-04/G-16 · Complexity M.**

**Risk:** Linux raw discovery accepts replies without matching transaction/source; Android matches the transaction but not source. Linux consumes its one-shot reader after a bad first response, so a valid second server cannot recover. Darwin/BSD shares the unchecked helper, but was not network-tested.

**Agreed fix:** match transaction ID, expected server IP:port, response type/class, lengths and valid mapped endpoint; ignore invalid/mismatched packets until deadline. Keep the reader active for the operation and give attempts clear cancellation/ownership. Follow [RFC 8489 response validation](https://www.rfc-editor.org/rfc/rfc8489.html#section-7.3).

Use the existing local UDP proxy for the intended Linux/rootless build if its namespace trial succeeds; remove unused raw/pcap platform paths from that build instead of carrying extra privilege/dependencies. If such paths remain supported, fix and test them as above. Android source validation remains required. Keep the existing pinned parser/library inputs; no parser rewrite solely for uniformity. Probe only the configured/available address family; no repeated impossible cellular IPv6 probes.

**Files/acceptance:** `internal/stun/{helper_socket,stun_linux}.go`, platform variants, `mobile/transport.go` and response handlers. Test wrong sender/transaction, malformed first reply followed by a valid reply, two-server fallback, cancellation and late packets. Confirm WG packets sharing the socket remain functional. These checks improve discovery robustness; they do not authenticate a WG peer or prevent an on-path STUN server from lying.

### F5. Refresh below expiry and react to real Android underlay changes

**Cross-codebase · Medium availability risk · G-08/A-10/A-20 · Complexity M.**

**Risk:** the 600-second refresh default meets OpenDHT's approximately ten-minute value expiry; one delay can erase discoverability. Dedup can suppress renewal indefinitely. Android's default-network callback may observe the VPN rather than Wi-Fi/cellular changes, delaying recovery until the next timer.

**Agreed direction, revised for battery:** refresh comfortably below the measured store TTL, with OpenDHT dedup disabled and immediate coalesced discovery after a real underlay change. Start testing a **180-second stable cadence** (with bounded jitter, below 240 seconds for a confirmed 600-second TTL); retain 60 seconds only as a configurable diagnostic/short recovery setting. This replaces the earlier blanket one-minute default. Timers cannot guarantee publication during Android sleep; recover promptly when the OS permits execution rather than defeating Doze.

Observe non-VPN network availability/loss/capabilities using the existing underlay callback. Debounce duplicate callbacks; renew/rebind sockets/TUN only when actually necessary. Serialize lifecycle work, cancel obsolete attempts, suspend network work when no usable underlay exists, and stop everything when the tunnel is off. A bounded retry sequence rejoins the stable cadence; no second permanent timer. Check actual proxy TTL/rate limits before finalizing intervals. Shortening cadence alone is not a handover fix.

**Files/acceptance:** Go `internal/config/config.go`, `mobile/config.go`, `mobile/controller.go`; Android `StunmeshVpnService.kt`. Test multiple TTL periods, skipped publications, changed mappings, Wi-Fi↔cellular and sleep/wake. Measure cycles, bytes, wakeups and reconnection. A slower DHT refresh does not preserve a UDP NAT mapping; tune WG keepalive separately under section 3A.

### F6. Enforce split scope and prevent DHT redirect traversal

**Cross-codebase · Medium conditional network reachability risk plus routing requirement · G-11/A-12; D4 · Complexity S–M.**

**Risk:** a configured HTTPS proxy can redirect discovery requests to unrelated HTTP/LAN destinations; protected underlay sockets retain this risk even with split routing. Separately, broad routes can capture ordinary phone traffic. No router exploit or secret extraction was demonstrated.

**Agreed fix:** reject DHT redirects and require HTTPS, except isolated test fixtures. In Android, validate and install only D4's explicit server destination routes; discovery cannot change them. Add family fall-through and preserve protected outer sockets. Rebuild the TUN only when actual local settings change. No application selector, default route, port inspection or forced VPN DNS.

**Files:** Go OpenDHT HTTP client; Android `TunnelConfig.kt`, parsers/editor and `StunmeshVpnService.kt`. Keep one typed destination list feeding both WG `AllowedIPs` and Android routes; reject partial/invalid route application rather than silently skipping entries.

**Acceptance:** redirect tests cause no second request. Verify selected/unselected destination routing from multiple apps, both address families, overlapping LANs and VPN down. Normal internet remains direct. F1/F3 still bound hostile discovery output; enrollment and backup restoration must validate the same narrow route contract under F8/F18.

### F7. Make desktop UDP-proxy endpoint ownership consistent

**Go only · Low conditional availability risk · G-15 · Complexity S.**

**Risk:** two peers sharing a source endpoint overwrite the forward mapping; moving the first then deletes the second's mapping. Packets drop; WG authentication is not bypassed.

**Agreed fix:** reject ambiguous duplicate endpoint assignments atomically, preserve the prior good mapping and return a bounded diagnostic. Delete an old mapping only if the moving peer still owns it.

Do not add multi-peer packet demultiplexing for a topology this deployment does not need.

**Files/acceptance:** `internal/wgproxy/demux.go`. Test both assignment orders, failed duplicate update, subsequent move, removal and concurrent reprogramming under the race detector. Document the supported endpoint uniqueness constraint.

### F8. Preserve configuration and support authorized encrypted backup/restore

**SETTLED protection/recovery contract · Android only · Medium data-loss/secret-exposure risk · A-05/A-18, N2 · Complexity M; recommended implementation uses platform APIs only.**

**Risk:** read/decrypt errors currently become an empty store that can overwrite good data; failed replacement can delete the previous file, and stale UI state can overwrite active selection. Copying Keystore-wrapped ciphertext alone cannot recover the configuration on another installation. A recoverable backup instead creates another copy of the WG identity whose protection and ownership matter.

**Local persistence:** keep one repository/state flow, serialized updates and `AtomicFile` rollback. Distinguish absent data from corruption/Keystore failure; preserve unreadable data privately and stop with an explicit error, never silently overwrite it. Keep encrypted live data and atomic temporary/rollback files in app-private `noBackupFilesDir`, excluding them from generic file copying. The explicit backup adapter reads logical configuration; that directory placement does not prohibit this authorized path. Settings/status refresh must not repeatedly unwrap secrets.

**Key storage:** generate fresh enrollment keys locally with existing reviewed primitives. Wrap the WG key/configuration with AES-GCM under a non-exportable Android Keystore key: StrongBox where supported, otherwise verified TEE. Check `KeyInfo.getSecurityLevel()` or the appropriate older API; record only the security level. Do not silently use software-only/file-based wrapping. The current engine consumes the raw WG key in app memory through UAPI; **the owner accepts this under the intact-sandbox threat model**. No hardware-only WG engine, custom handshake or new crypto dependency is required. Minimize copies and clear mutable buffers where practical without promising complete memory erasure. [Keystore protection](https://developer.android.com/privacy-and-security/keystore), [security-level API](https://developer.android.com/reference/android/security/keystore/KeyInfo#getSecurityLevel()), [pinned WG key operations](https://git.zx2c4.com/wireguard-go/tree/device/noise-helpers.go?id=f333402bd9cb).

| Secret | Storage and recovery contract |
| --- | --- |
| Phone WG private key and configured PSKs | Encrypted locally; usable by the app/WG engine and recoverable through the authorized encrypted backup. Never public provisioning data; a PSK may enter through F18’s confidential proposal, never the public reply. |
| Device Keystore wrapping key | Hardware-backed, non-exportable and **never backed up**. A fresh installation creates its own wrapping key; old ciphertext alone is insufficient. |
| Backup recovery secret | Managed by the OS backup system/owner, independently of the device wrapping key. Keep recovery material separately from stored backup bytes; losing both usable device state and recovery access requires new enrollment. |

**Recommended implementation:** one small Android key-value `BackupAgent` carrying one bounded, versioned configuration entity. Use `android:allowBackup="true"` and declare the agent; do not enable a generic full-file fallback. This retains encrypted live storage without an app-owned backup UI, scheduler, transport, cryptographic format or plaintext staging file. GrapheneOS currently integrates Seedvault; the inspected transport implements key-value backup and advertises client-side encryption. This is source evidence, **not a successful restore test on the owner's phone**. No Google service or Seedvault SDK is required by the adapter. [Android key-value backup](https://developer.android.com/identity/data/keyvaluebackup), [GrapheneOS backup](https://grapheneos.org/features#encrypted-backups), [inspected transport](https://github.com/GrapheneOS/platform_packages_apps_Seedvault/blob/4b9bf6220b1b533dfd22180f3c22fce9d8310b88/app/src/main/java/com/stevesoltys/seedvault/transport/ConfigurableBackupTransport.kt).

**Backup boundary:** after the owner enables system backup and chooses its destination, the OS may invoke scheduled callbacks without a prompt each time. The agent unwraps a consistent configuration snapshot and supplies logical secrets to the **trusted OS backup component**, which encrypts them with independently recoverable protection. This intentionally relaxes the former prohibition on any transfer to another component; it does not expose a general app/USB extraction API. Require `FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED` before writing secret data; device-transfer mode alone is insufficient. Flags describe a trusted transport's promise, not proof against a malicious transport. If unavailable/locked/unsupported, report failure and do not emit an empty replacement or mark it successfully backed up. [Transport flags](https://developer.android.com/reference/android/app/backup/BackupAgent#FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED).

Keep raw ciphertext, legacy secret files, logs and caches excluded from default file backup/device transfer. Backup bookkeeping contains only nonsensitive revision data. Coalesce `BackupManager.dataChanged()` after durable configuration edits; never signal on endpoint refresh, status reads or handshakes. No app-owned timer, periodic job, wake lock or backup network client. Test OS foreground-backup policy with the VPN service, including `backupInForeground` if necessary; any process stop must recover through F10. Unverified transfer paths must not fall back to copying files. [Android backup lifecycle/exclusions](https://developer.android.com/identity/data/autobackup).

**Restore:** accept secrets only through the OS restore callback, separate from ordinary profile import. Bound/version-check and validate all fields under F1/F2/D4, then atomically re-encrypt/save using the receiving installation's hardware-backed wrapping key. A fresh installation generates a new wrapping key; an existing valid local key can be reused. Never invalidate the old key/file before a replacement commits. Preserve good state on failure; no silent overwrite or rotation. Keep restored tunnels inactive until the owner reviews routes/peer identity and any required system VPN consent. A valid backup can retain the same WG public identity if still authorized on the server. Do not run the original and restored device concurrently with that identity. For a lost/untrusted old device or suspected backup exposure, enroll a new key and revoke the old server peer instead; restoring a revoked key cannot restore authorization. Without a usable backup, deliberate new enrollment remains the fallback. Normal signed updates preserve local identity.

**Computer/USB boundary:**

| Situation | Expected access on the assumed non-rooted phone |
| --- | --- |
| Ordinary USB file transfer / file manager | No access to the app-private key or engine memory. Connecting a cable is not authorization to export secrets. |
| Authorized ADB, non-debuggable release | No automatic private-file access or `run-as` access to this app. Modern `adb backup` excludes non-debuggable app data for target API 31+. ADB remains powerful and can drive supported UI/system backup operations; authorize only a trusted host. |
| Computer stores the OS's encrypted backup | May hold/copy the backup; recovery additionally requires the backup system's recovery secret. Possession of both enables recovery of WG credentials. |
| Debuggable app, root or compromised app/OS | Potential key access; outside the agreed production assumptions. |

[ADB authorization](https://developer.android.com/tools/adb#Enabling), [AOSP `run-as` check](https://raw.githubusercontent.com/aosp-mirror/platform_system_core/master/run-as/run-as.cpp), [Android 12 backup restriction](https://developer.android.com/about/versions/12/behavior-changes-12#adb-backup-restrictions). Test the installed OS version; do not make release builds debuggable to enable backup.

**Alternative, only if OS recovery proves unsuitable:** a deliberate app-specific encrypted file export to the computer could use a separate recovery password/key and a reviewed authenticated-encryption format. It adds format/KDF, UI, parser and recovery responsibilities (M–L), so do not implement it alongside the recommended OS adapter. A computer without that secret would only store ciphertext; one given the recovery secret becomes a trusted recovery endpoint. Simply exporting the device-wrapped blob cannot work after loss of its Keystore key.

**Files/acceptance:** repository/model, minimal backup adapter, manifest/rules and lifecycle UI. Test atomic/stale-selection failures, hardware level and absence of ordinary extraction. On the actual GrapheneOS version, use synthetic peers to back up and restore into an installation without the original wrapping key: prove identity/PSK/route continuity and a WG connection after review. Test unsupported/unencrypted transport, malformed/oversized/old-schema data, locked Keystore, foreground backup, no automatic tunnel activation, and rejection of a revoked restored peer. Inspect the stored/transferred backup for absence of plaintext credentials; verify that authorized decryption recovers the WG configuration but contains no Keystore wrapping key. Copying only the old live ciphertext must fail to restore. A backup success icon is insufficient. Use a spare/test installation; no real phone reset or production peer revocation is authorized here.

### F9. Prevent the editor from rewriting undisplayed configuration

**Android only · Medium conditional metadata/availability risk · A-19 · Complexity S initially.**

**Risk:** editing an imported multi-store tunnel replaces its plugin list with one store and redirects every peer there; an unrelated edit also resets `logLevel`.

**Recommended:** make advanced/multi-store profiles read-only in the current single-store editor with a clear reason; preserve all fields during supported edits. Ordinary import must reject phone private keys; replies/export remain public-only. F18 permits PSK input in a confidential enrollment proposal; authorized OS recovery is separate under F8. This is the smallest safe behavior for an OpenDHT-only home setup.

Retain this small safe restriction; no full multi-store editor. Preserve selected destination routes, enrollment metadata and backup schema/revision during supported edits. Private-key ownership is controlled by F8, not the editor.

**Files/acceptance:** `TunnelEditorScreen.kt`, serialization/model. Import two distinct OpenDHT stores with separate peers and nondefault log level; either edit is blocked before mutation or name-only editing preserves the entire remaining model. Never silently downgrade an unsupported profile.

### F10. Make VPN lifecycle real and distinguish setup from connectivity

**Android only · High availability blocker plus misleading health · A-04/A-15 · Complexity M.**

**Risk:** `StubBackend` can create a real TUN and report UP while dropping traffic; genuine UP also precedes any handshake. The service never promotes itself to foreground, jeopardizing background/always-on operation.

**Agreed fix:** remove the stub from release builds and fail visibly when the real core cannot load. Implement prompt foreground promotion, a persistent status notification, appropriate permissions/type and orderly stop/revoke cleanup. Validate eligibility for the selected target SDK and actual device. Android explicitly requires foreground promotion for this VPN lifecycle. [VpnService reference](https://developer.android.com/reference/android/net/VpnService).

Display separate “starting/discovering,” “interface ready,” and per-peer “last authenticated activity” states; do not label device creation as proven connectivity. Stale handshake time alone is not proof of failure for an idle tunnel: combine handshake/receive data with an explicit reachability check when needed. Never use ICMP or STUN success as peer authentication.

Support always-on startup with split routing and lockdown off. A foreground notification is a lifecycle requirement, not a reason for a wake lock, CPU loop, permanent health polling or frequent notification refresh. Update status on events; poll counters only while the status screen is visible or during existing recovery work. Avoid duplicate Kotlin and Go reconnect supervisors.

**Files/acceptance:** `TunnelManager.kt`, `StubBackend.kt`, `StunmeshVpnService.kt`, `StatusScreen.kt`, manifest, `mobile/node.go`/status API. Test missing/corrupt native library, denial/revocation, start/stop, process death, reboot, background idle, notification behavior and upgrade on the target phone. Failures release descriptors and never show a fake connected state.

### F11. Generate safe diagnostics instead of exporting redacted raw configuration

**Cross-codebase · Medium conditional credential disclosure · G-17/A-06/A-14/A-22 · Complexity S–M.**

**Risk:** generic/nested plugin credentials, URL userinfo and malformed-import snippets survive log export. Go failure errors include credential-bearing proxy URLs. Disclosure requires such configuration and local logging or user sharing; no automatic exfiltration was found.

**Agreed fix:** allowlist diagnostic fields; do not serialize arbitrary plugin configuration into support logs. Reject URL userinfo at config load. Emit bounded error category, field and line number without raw parser snippets or exception text. Sanitize before insertion into rolling logs, with export-time defense in depth for older entries. Do not label arbitrary historical logs “secrets redacted.”

Delete generic plugin-config logging and unused credential handling under D2. Keep bounded in-memory diagnostics and format exports only on user action; no background file scan/redaction service. No credential-bearing proxy feature is needed.

**Files/acceptance:** Go OpenDHT errors/controller logging; Android `TunnelConfig.kt`, import exception handling, `StatusScreen.kt`. Canary-test private keys, PSKs, wrapped-key blobs, `api_key`, nested/list config, URL credentials and failed imports/requests. No canary may appear in diagnostics, logs or ordinary sharing surfaces. F16 removes generic secret export; F8 alone handles the authorized encrypted backup exception.

### F12. Remove the unauthenticated debug import receiver

**Android debug only · Medium conditional local configuration risk · A-11 · Complexity S.**

**Risk:** any installed app can replace/select a tunnel through the exported debug receiver. The audited release APK does not contain it.

**SETTLED:** delete the receiver and use ordinary test fixtures/instrumentation. Do not add a replacement IPC hook or keep debug APKs with real tunnel credentials.

**Files/acceptance:** `app/src/debug/AndroidManifest.xml`, `ConfigImportReceiver.kt`. Inspect merged manifests and built APKs; untrusted applications cannot import or select a profile. Do not treat a debug-only issue as evidence that the current release exposes this receiver.

### F13. Delete unnecessary CI/release authority and unsafe script entry points

**Cross-codebase · High conditional release compromise · G-12/A-16; workflow portions of G-07/A-08 · Complexity S–M.**

**Risk:** a tag capable of triggering release can inject shell syntax into workflow scripts and access artifact/signing authority. Mutable privileged CLA actions and broad job tokens create additional compromise paths. The audit demonstrated shell substitution locally, not a GitHub credential theft.

**SETTLED implementation:** delete inherited CLA/release/publication workflows and unused composite actions because D3 uses local builds only. Keep fork Actions disabled. Do not build a replacement CI signing/publishing system. Preserve necessary build commands in small reviewed local scripts without release-token or hosted-download plumbing.

For retained local scripts, pass names/versions as data, quote variable expansion and validate expected syntax. Review all input-to-shell paths, not just the demonstrated tag payload. Prefer explicit version arguments. Once necessary commands are available locally, remove the remaining unused workflow/action scaffolding rather than maintaining a second build path.

**Files/acceptance:** both `.github/workflows/` and Go `.github/actions/`; retained build scripts. Verify obsolete entry points are deleted and no remaining workflow can sign/publish. Test retained script arguments with harmless metacharacter canaries locally; no command executes. No compilation step has signing or repository-write credentials.

### F14. Pin and verify the complete build graph

**Cross-codebase · Supply-chain residual risk, not proven compromise · G-07/A-08 · Complexity M; implements D3.**

**SETTLED:** reuse available verified pins: audited Go 1.27.1, the recorded gomobile commit, JDK 21, NDK `29.0.14206865`, the existing Android platform/build-tools, Gradle 9.5.0 and the declared Maven graph. Exact archive versions/hashes are in the audit evidence; choose one already available build-tools version in the recipe. Pin the existing base-image digest. No `latest`, automatic toolchain download, remote AAR or convenience dependency upgrade. Remove Foojay auto-download and unused dependencies.

Keep Go checksums; add Gradle verification and locking where supported for both runtime and build-tool inputs. Reuse publisher comparisons from the audit to establish trusted hashes; cache presence alone is insufficient. A small local build manifest is enough; no vendored toolchain fleet, hosted repository or new supply-chain platform. [Gradle dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html).

**Files/acceptance:** `go.mod`/`go.sum`, Android settings/build scripts, verification/lock files, local recipe/manifest. Complete a clean offline build with verified cached inputs. A corrupted dependency/AAR must fail. Record module/artifact inventories and compare the OpenDHT-only output. Document reproducibility limits accurately. F17 handles necessary security updates; cosmetic wrapper refresh is deferred in N1.

### F15. Build a minimal image and validate rootless permissions explicitly

**Go/deployment · Conditional secret exposure and excess privilege · G-07 · Complexity S image, M deployment validation.**

**Risk:** unrestricted `COPY . .` can copy local secrets/history into builder layers; the image contains unused helpers and defaults to UID 0 inside the rootless namespace. That UID is not host root, but host access follows Podman's mappings and granted mounts.

**Agreed fix:** minimal allowlisted build context or explicit source copies plus `.dockerignore`; exclude `.git`, keys, audit scratch, exports and local config. Ship daemon, required trust roots and only actual runtime needs; omit unused shell/Cloudflare helpers. Prefer a read-only filesystem and explicit tested runtime identity. Use existing local proxy mode where suitable to avoid raw-socket privileges.

Use the least privilege supported by the tested namespace/interface arrangement; namespace UID 0 may be required and must be documented with its host mapping. Do not add arbitrary `USER 65532`, `--privileged`, host networking or ownership changes merely to silence a check. Keep configuration/QR generation in a local one-shot tool; no web provisioning server or QR/scanner dependency belongs in the running daemon/image.

**Files/acceptance:** `Dockerfile` remains a valid Podman build recipe; `.dockerignore`, release recipe and later container unit. Test using the actual service user and UID/GID map. Document WG interface/control-socket ownership, required namespace capabilities, exposed UDP ports and mount access. No NAS data directories need to be mounted into discovery. Use synthetic files to test denied/allowed access; inspect layers for excluded canaries. Preserve Samba permissions and the existing NFS/startup arrangement. Explain any necessary routing/firewall change separately before implementation.

### F16. Remove ordinary secret export/input; isolate authorized OS recovery

**SETTLED, supersedes the former plaintext-export recommendation · Android only · A-13/A-23 plus F8/N2 · Complexity S.**

**Risk:** the current full-profile export sends a WG private key/PSK to a document provider; the private-key form can expose it to an IME despite visual masking. The authorized backup exception must not become a generic sharing or import bypass.

**Fix:** delete full-secret profile export, private-key editor/reveal/copy controls and generic encrypted-blob sharing. Generate new identities locally; only F8's OS restore callback may recover one from backup. Reject private-key-bearing ordinary imports and provisioning payloads; do not accept a server-generated phone identity. No replacement exported receiver/provider/service may return secrets or invoke restore. Core validation remains mandatory for both internal configuration and backup recovery.

Retain only an allowlisted public peer description (public key, required tunnel addresses and non-secret enrollment metadata) for server enrollment. It must never serialize a full `TunnelConfig` and then try to redact secrets. Diagnostics remain separate under F11. Use password keyboard options/autocorrect off for any remaining shared-secret input; no private-key keyboard field remains. PSKs are not public data. The owner now permits an optional PSK in a confidential enrollment QR/file, eliminating the separate manual PSK input; this exception never permits importing/exporting the phone private key.

**Files/acceptance:** `MainActivity.kt`, `TunnelListScreen.kt`, `TunnelEditorScreen.kt`, public serializer, backup adapter and model boundaries. Enumerate every display/share/copy/provider/debug path: no phone private key or wrapped-key blob may leave through ordinary interfaces, logs, QR, IME or source Git. PSKs may enter through the deliberate confidential enrollment channel and trusted scanner/file provider; they must never appear in public replies, diagnostics or source-test evidence. Test that legacy private-key-bearing YAML/wg-quick profiles are rejected and public enrollment output contains only declared fields. Test OS backup/recovery separately under F8, including that another ordinary app cannot invoke it to obtain secrets. F18 keeps replies public-only and accepts a PSK-bearing local proposal.

### F17. Remediate reachable advisories without treating scanner counts as exploits

**Cross-codebase · Dependency maintenance/conditional exposure · G-07/A-08 · Complexity S–M per compatible update.**

**Risk/context:** the audit found advisories in module/build-tool graphs, not demonstrated dependency compromise. Android runtime graph had no reported OSV advisories at the audit date. Seven build-tool advisory entries covered six components; these were not shipped Android runtime dependencies. The Kotlin finding concerns KAPT incremental caches, and this project has no KAPT task; Jetifier's JDOM path was also inactive. A release compiler sharing signing credentials is still dangerous independently of any particular CVE.

**Plan:** start with the audited, available pins. Consult per-advisory evidence and rescan the exact reduced graph; classify runtime/build/test reachability. Delete unused affected paths/dependencies first. Keep KAPT/Jetifier absent. Do not upgrade broad dependency families or override AGP transitive JARs to clear scanner output. If a retained reachable vulnerability requires a version change, document why and propose the smallest compatible pinned update; existing-cache preference must not be described as a security fix.

Review the recorded upstream wireguard-go delta for this phone/TUN path before deciding whether an update is necessary. Document non-reachable accepted flags and review triggers instead of performing speculative upgrades. No exception for a demonstrated reachable authorization flaw. This preserves the local pinned build preference while keeping a route to necessary security fixes.

**Evidence/acceptance:** `security-audit-evidence/android-build-tool-advisory-triage.md`, Go direct-dependency and gosec triage, OSV snapshots and upstream delta notes. Maintain an inventory of advisory, resolved version, execution context, trigger, decision and next review. Preserve the short-XOR-STUN regression: the audited Pion version was already patched for that reported panic. Rebuild/retest after each coherent update. Publisher checksum agreement and a clean scan do not establish absence of malicious code.

### F18. Credential QR provisioning with local phone key generation

**SETTLED, owner revision 2026-09-22: external scanner/local encoder, optional PSK in the QR/file · Cross-codebase · Complexity S–M.**

**Decision:** keep the recommended small QR workflow: the local configuration tool generates a QR, GrapheneOS Camera scans it, and the user explicitly imports its text. The payload may contain an **optional PSK and is then confidential**. Separate manual entry is removed to avoid an impractical second transfer; the phone response remains public-only. The former variant containing a workstation-generated phone private key is prohibited. No in-app camera SDK, extra Android permission, enrollment server or new authentication protocol. [GrapheneOS Camera QR support](https://grapheneos.org/usage#camera).

**Minimal enrollment flow:**

1. The owner's local tool emits a compact versioned bootstrap record: server WG public key, intended server/tunnel addresses and narrow phone-side destination routes, plus required public STUN/OpenDHT settings. An optional canonical nonzero WG PSK may be included. No server/phone private key or encrypted private-key blob. Mark the proposal with an identifier so replies can be matched; it is not an authenticator.
2. The phone scans with the existing Camera and deliberately imports bounded text through F1/F2 validation. Show the server public key/fingerprint and selected routes for confirmation against the trusted local tool. Do not auto-open arbitrary provisioning links or enable a tunnel after an intent.
3. The phone creates and protects its own private key under F8, derives its public key, and keeps the configuration pending until enrollment is complete. Return only its public key and necessary public addressing/identifier to the owner (copy/share as text is sufficient; no second QR encoder in the phone is required).
4. The owner reviews that public response in the local configuration tool and applies the server peer entry as a normal Git-tracked configuration change. Server-side AllowedIPs bind the phone's tunnel address, while phone-side routes select the server services. On re-enrollment, explicitly retire the previous peer before relying on the new identity. The phone then tests an ordinary WireGuard handshake and transfer.

QR is trusted local configuration input, not peer authentication. Treat a substituted bootstrap/server public key as an enrollment attack; user review/out-of-band comparison is necessary. No DHT value or automatic enrollment endpoint can authorize a new peer. **PSK handling:** include a required PSK in the confidential local QR/file and configure the same PSK on the server. A trusted external scanner, clipboard/keyboard or selected document provider can see it; this is the owner-selected tradeoff. They never receive the phone private key. Keep temporary proposals private, remove them after import, and never place real credentials in public evidence. No separate manual PSK field or redundant requirement flag. Whether a new association uses a PSK remains explicit; never strip an existing requirement.

**Implementation:** one-shot configuration-tool encoder plus an explicit bounded enrollment importer reusing the existing parser/validation. Verify whether a reviewed pinned encoder is already available; if not, select one small local dependency and pin its version/hash. Keep it out of the long-running Go/Podman image and Android runtime. No online QR generation, multipart QR format, autonomous device registration or general-purpose exported import receiver. Require explicit review/save, cap payload size and leave failures unpaired.

**Acceptance:** offline scan/import of synthetic records with and without a PSK; private-key/blob fields, malformed/zero PSKs, unknown commands, oversized or broad-route payloads rejected; duplicate proposals cannot replace an existing private key silently. Verify that only the deliberate inbound credential channel carries a PSK, pasted input is protected, and replies, diagnostic logs and ordinary sharing remain public-only. Canary-test redaction and hardware-wrapped enrollment/reload. Fresh enrollment requires local key generation and an explicit server peer change. F8's validated OS restore may recover an already authorized identity, but never through the QR/import interface; explicit new enrollment always creates a different key. F17 remains dependency triage; this feature's changes get their own paired Git commits when implemented.

## 3A. Android battery and background-work contract — applies to every fix

| Area | Required behavior |
| --- | --- |
| Tunnel off | No STUN/DHT requests, status polling, wake locks or reconnect jobs. Cancel tunnel work/callbacks. An OS-requested backup may run independently; it must never start the VPN or discovery. |
| Discovery / F3–F5 | One scheduler per active tunnel, bounded attempts, coalesced handover events, HTTP connection reuse and backoff. Stable TTL refresh starts at 180 seconds for testing; no permanent minute-by-minute recovery loop. |
| NAT / WireGuard | The current Android model defaults to 25-second persistent keepalive. Do not multiply this with ICMP pings or a separate socket keepalive. Test whether it is needed for the real NAT pair; use 0 only if required reconnect/inbound behavior survives. Keep the smallest necessary set of peers active. |
| Validation / F1–F2, F6–F9 | Parse/validate on configuration changes or restore. No timed config scans, app enumeration or continuous rebuilds. Coalesce backup signals after durable semantic edits only; avoid secret unwraps on status refresh. |
| Backup / F8 | One bounded snapshot per OS callback; platform-managed scheduling/transport. No periodic WorkManager job, app-owned network client, wake lock or per-handshake backup. Handle unavailable Keystore without retry loops. |
| UI / F10–F12, F16/F18 | Event-driven status/notification; visible-screen-only counter refresh; bounded log ring. Credential import and public-only reply sharing occur only on user action. No telemetry or background scanner; key generation/Keystore work only when needed. |
| Platform power policy | No new permanent partial wake lock, exact wakeup alarm, periodic WorkManager job or automatic exemption request. A foreground service does not justify defeating Doze. If sleeping delays rendezvous, report/test recovery instead of promising uninterrupted reachability. |
| Metered traffic | Preserve the underlay's metered status and the sync app's cellular policy; test Wi-Fi/cellular transitions. Avoid making the VPN appear unmetered to trigger unintended bulk sync. Use the platform's inheritance semantics, not another polling mechanism. |
| Build/dependencies | No self-update checks, dependency fetches, release checks or background key rotations inside the installed app. |

WireGuard documents persistent keepalive as optional and useful for NAT mappings; it is distinct from DHT TTL refresh. Android can defer work/network during idle. Measure the resulting tradeoffs; do not promise zero wakeups and instant incoming connectivity. [WireGuard keepalive guidance](https://www.wireguard.com/quickstart/#nat-and-firewall-traversal-persistence), [Android Doze](https://developer.android.com/training/monitoring-device-state/doze-standby). For API 29+, `setMetered(false)` inherits the underlay's meteredness; it does not unconditionally force an unmetered network. [Metered VPN API](https://developer.android.com/reference/android/net/VpnService.Builder#setMetered(boolean)).

**Measurement gate:** on GrapheneOS compare VPN off, active-idle and actual sync over comparable Wi-Fi/cellular periods, including overnight idle and repeated handovers. Record app CPU, wakeups, transmitted bytes, radio activity, battery usage and reconnect delay using platform diagnostics. Reuse one test matrix for F5/F8/F10; investigate excessive retries before changing power exemptions. Final cadence/keepalive values remain test outcomes, not hard-coded promises.

## 4. Maintenance — deferred except mandatory key protection and safe recovery

| ID / sources | Risk/context | Recommended fix and alternatives | Complexity / acceptance |
| --- | --- | --- | --- |
| **N1 — A-08: wrapper mismatch** | Official hashes matched; wrapper/distribution mismatch was hygiene, not evidence of tampering. | **Deferred.** Keep the existing verified working pin unless the build needs a change. | No independent cleanup milestone. |
| **N2 — A-05: protected key with authorized encrypted recovery** | Ordinary disclosure or possession of a backup plus its recovery secret can clone the WG identity. Hardware wrapping protects storage; in-app key use is accepted. | **Required under F8/F16.** Generate locally; verified hardware-backed wrapping; no ordinary extraction. The **wrapping key** remains non-backupable; the **WG key/configuration** may pass through the trusted OS backup adapter and return under a new wrapping key. No WG-private-key/wrapping-key QR or generic secret export; F18’s PSK input is a separate scoped exception. | M; verify sandbox/export boundaries, hardware level, independent recovery encryption and real GrapheneOS restore. Avoid concurrent clones; revoke/re-enroll if the old device or backup is untrusted. |
| **N2 — remaining A-08/G-07 hygiene** | Lint style/unused resources, ICMP identifier quality and SHA-1 identifier naming are not the demonstrated authorization flaw. | **Deferred.** Do not add background health checks or perform cosmetic rewrites. Delete unused code incidentally under D2/F13; never label ICMP/STUN success as WG authentication. | No extra dependency upgrades. |
| **N3 — G-07/A-08: broad graph/UI refactor** | Some desktop packages enter mobile's import closure; Compose contributes many dependencies. Import presence alone does not establish retained vulnerable code. | **Deferred.** Delete dependencies made unused by agreed removals. Keep existing UI/framework and avoid large extraction work merely to reduce counts. | D2's removal of process plugins is still mandatory. |

## 5. Execution order and local installation gates

| Stage | Work | Completion gate |
| --- | --- | --- |
| **0 — Baseline and validation inputs** | Preserve audit commits/evidence; create local branches. Carry D1–D4, accepted app-memory key use, authorized backup and public-QR decisions forward. Record destinations, signing identity, hardware level and OS backup settings. | Section 7 inputs recorded; the hardware-only engine question is closed. Prefer the small platform backup adapter; no app selector or parallel backup product. |
| **1 — Trust boundary** | F1/F2, D2 plugin exclusion, then D1 protocol/private-key separation. F13 containment can happen immediately. | Injection tests become safety tests; unauthorized peers/PSK bypass fail; malformed config cannot partially apply; no process plugins in home artifacts. |
| **2 — Discovery/network correctness** | F3–F7 and D4 split routing; apply battery contract. | Bounded hostile-proxy/STUN tests pass; no broad routing/redirect traversal; both fork endpoints interoperate. DNS privacy policy remains deferred. |
| **3 — Android storage/lifecycle** | F8–F12/F16 and required N2, with F5/F10 lifecycle. Remove generic secret export/input; implement local storage and the minimal OS backup/restore adapter. | Durable configuration, hardware wrapping, no ordinary extraction, bounded encrypted recovery, inactive restored profiles, foreground service and safe diagnostics. |
| **4 — Local build trust** | D3, F13–F15/F17 using available pins. | Exact local AAR, offline build, minimal image, separate signer and artifact manifest. No release service or updater. N1/N3 remain deferred. |
| **5 — Provisioning/integration** | Implement F18 credential QR/public-reply flow; test synthetic peers, rootless namespace, VPS, actual Android carrier/home NAT and GrapheneOS recovery. | Split routes, encrypted backup with identity continuity, no phone private key in scanner output and no PSK in public replies/logs, fresh enrollment when recovery is unavailable/untrusted, handover/idle and battery tests. |
| **6 — Review and staged use** | Review diff, residual trust/recovery risks and hashes; agree exact NAS/peer commands and manually install the local APK. | LAN access preserved; public provisioning, recovery-key custody, restore review and peer retirement tested. No public publication step. |

Each issue commit references plan/audit IDs, behavior change, meaningful tests and limitations. Cross-repo changes need paired commit references and an exact AAR handoff. Install a compatible Android/core pair. Deleted vulnerable features are recorded as removed, not claimed to have been repaired for hypothetical upstream users.

### Verification instructions for the implementing agent

The original audit tests often **pass when a vulnerability is present**. Read their assertions; convert or replace them with safety assertions after fixing. Preserve exploit inputs and the historical evidence. Do not interpret an unchanged green audit suite as remediation.

Suggested starting commands, run from the relevant checkout with a pinned local toolchain:

```sh
# Go: ordinary tests, then mobile/audit coverage in an isolated test namespace.
go mod verify
go test -race ./...
go test -race -tags 'mobile security_audit builtin_all' ./...

# Android: exact fork-built AAR must already be selected by the fixed build recipe.
./gradlew --offline testDebugUnitTest assembleRelease lintDebug lintRelease
```

The Go raw-socket/network tests need the audit's isolated namespace setup, loopback enabled and its synthetic TEST-NET route; do not run them against the NAS LAN. Consult the commands/evidence in the existing reports and test prerequisites before invocation. The all-builtins command reflects the audit baseline; also test the actual OpenDHT-only home build, adapting tests/build tags when deliberately removing unsupported features. Never ship all plugins merely to make a legacy test pass. Missing namespace capabilities are an environment failure, not a reason to skip the security gate silently. Re-run targeted fuzzing for configuration, endpoint, UAPI and STUN parsing after changes, including the retained minimized crashes. Add an actual release-variant import/config test path; debug-only unit tests and lint alone do not prove release behavior.

**Required adversarial outcomes:** unknown/wrong-key peer and wrong-PSK traffic fails; forged discovery never changes peers/PSKs/routes; partial apply cannot leave a hidden peer; malformed/oversized records terminate within bounds; working sessions are not continuously disrupted by forged candidate updates; imported config cannot execute programs; credential canaries never appear in diagnostics; release inputs cannot execute shell syntax.

**Phone handoff:** local signed APK, hash/certificate, manual update instructions, confidential enrollment QR/file where a PSK is used, public reply and server routes. Document fresh enrollment versus OS recovery, backup destination/recovery-secret custody, USB/ADB boundaries and retirement of an untrusted old peer. Test split routing, both families, handover, idle, reboot, metered sync and backup lifecycle. Record OS/transport version and actual Keystore level. Demonstrate same-identity recovery under a fresh wrapping key with a synthetic peer, no automatic activation and no concurrent original instance; separately prove new enrollment/revocation. Do not reset the real phone without a separately agreed procedure.

**Rollback:** preserve server configuration history and exact build artifacts; use only the authorized encrypted backup for phone recovery, never a plaintext secret export. Verify schema compatibility before restoring an older APK; do not bypass signature/version or validation checks. If no known-safe Android version exists, stop the experimental tunnel and retain LAN administration. Normal updates and valid recovery can preserve identity; deliberate re-enrollment replaces it and retires the old server peer. Do not restore a key suspected compromised or already revoked. Other production keys are not rotated by this plan.

## 6. Complete finding-to-plan mapping

Every numbered finding is retained, including consciously deferred risks. A finding may span several implementation tasks without representing multiple vulnerabilities. F18 and the battery contract add requirements beyond the original findings.

| Go finding | Plan | Android finding | Plan |
| --- | --- | --- | --- |
| G-01 | D1, F1 | A-01 | D1, F1 |
| G-02 | D1 | A-02 | D2 |
| G-03 | D1, F3 | A-03 | D3, F14 |
| G-04 | F4 | A-04 | D3, F10 |
| G-05 | F2, F3 | A-05 | F8, F16, N2 |
| G-06 | D2 | A-06 | F11 |
| G-07 | D2, D3, F13–F15, F17, N2, N3 | A-07 | F2 |
| G-08 | F5 | A-08 | D2, D3, F13, F14, F17, N1–N3 |
| G-09 | F2 | A-09 | D4 — deferred |
| G-10 | F1, F2 | A-10 | F5 |
| G-11 | F6 | A-11 | F12 |
| G-12 | F13 | A-12 | F6 |
| G-13 | F2 | A-13 | F16 |
| G-14 | D1, F2 | A-14 | F11 |
| G-15 | F7 | A-15 | F10 |
| G-16 | F4 | A-16 | F13 |
| G-17 | F11 | A-17 | D1, F2 |
| — | — | A-18 | F8 |
| — | — | A-19 | F9 |
| — | — | A-20 | F5 |
| — | — | A-21 | D4 — deferred |
| — | — | A-22 | F11 |
| — | — | A-23 | F16 |

## 7. Remaining feasibility checks and provisioning inputs

| Item | Required next step / boundary |
| --- | --- |
| **Hardware verification (F8/N2)** | Verify StrongBox/TEE support on the phone; do not silently substitute software-only protection. In-process raw WG key use is accepted, so no hardware-only engine decision remains. |
| **Backup validation (F8/N2)** | Use the recommended small OS key-value adapter. Confirm the installed GrapheneOS transport/version, encrypted destination, recovery-secret custody, foreground behavior and a synthetic restore. Ordinary file transfer is not key extraction. Reconsider a separate manual encrypted export only if platform recovery is unsuitable; do not build both. |
| **Service routes (D4/F6)** | Record only the actual server/tunnel IPs or service ranges and verify roaming-LAN overlap. Destination-only routing is settled; no app list is needed. |
| **QR details / PSK (F18)** | Use an optional PSK-bearing confidential proposal + phone-generated private key + public-only return. Keep the pinned local encoder/external scanner, with no camera SDK or background work. Confirm whether each association uses a PSK; include it in the controlled QR/file and never weaken the server requirement. |
| **Installation identity and test inputs** | Record stable fork application ID, workstation signing-key location/fingerprint, phone OS/security level and synthetic restore/new-enrollment procedure. Distinguish APK signing key, WG identity, non-backupable wrapping key and backup recovery secret. |
| **Cadence/keepalive** | Finalize from phone/NAT/battery tests; proposed 180-second stable discovery and existing 25-second keepalive are test inputs, not guarantees. No extra always-on probes. |

**Settled removals:** application selection, generic secret profile export/private-key editing, and private-key-bearing QR/ordinary import. Authorized encrypted OS backup/restore is now in scope; the blanket no-backup rule is superseded. General N1/N3 and nonessential N2 hygiene stay deferred; N2 key protection and safe recovery are mandatory.

**Other deferred work:** A-09/A-21 DNS/privacy hardening. Existing local dependency pins remain unless a retained vulnerability or incompatibility requires a scoped change.

**Residual risks:** compromised endpoints/OS/WireGuard/toolchain, local signer or trusted backup transport; an attacker with both backup and recovery secret can recover the WG identity. App-memory key use is accepted, not eliminated by hardware wrapping. Other risks remain public metadata/discovery DoS, NAT/sleep recovery limits, deferred DNS and substituted enrollment records. Lost recovery access can require new enrollment; stale backups may restore obsolete configuration and never revive server-revoked authorization. Credential QR and encrypted backup do not change WireGuard's exclusive role in network peer authentication.
