# STUNMESH: merged security remediation plan

**Updated:** 2026-09-22 · **Status:** agreed direction plus explicitly identified open choices; documentation only, no remediation or deployment performed.

This is one plan for both Owner forks. An identical copy belongs at the root of each repository; update both copies together. The Go copy is canonical. Finding IDs refer to the original [Go audit](../stunmesh-go/SECURITY_AUDIT.md) and [Android audit](../stunmesh-android/SECURITY_AUDIT.md), which preserve evidence, prerequisites and limitations. Those reports remain historical records; record remediation outcomes separately. Relative cross-repository links assume sibling checkouts named `stunmesh-go` and `stunmesh-android`.

## 1. Project context and implementation contract

| Repository | Role | Audited production commit | Local audit commit |
| --- | --- | --- | --- |
| [snf/stunmesh-go](https://github.com/snf/stunmesh-go) | Desktop discovery daemon, optional local UDP proxy, and Go mobile core embedded in Android | `71a73228cd2bc001cdc5d485a16621a24bfae15a` | `934d4ef423a20498f3d9e579b6581ff5e3829a8c` |
| [snf/stunmesh-android](https://github.com/snf/stunmesh-android) | Kotlin UI, configuration/import/export, Android VPN lifecycle; consumes the Go core as an AAR | `e0cc30951e24ec018423bb113acfe7849f9836d5` | `6641b916be56f9dcdd298a1ba1ef9c69f2b0f48d` |

Both local audit branches are `security-audit/2026-09-13`. Public forks may not contain those branches: obtain the local audit commits and `security-audit-evidence/` before implementing. The audited Go mobile source matches `v1.15.1`; Android matches `v0.2.1`. Review intervening changes before any rebase/update.

**Architecture.** STUN finds a public UDP mapping. OpenDHT proxies exchange endpoint records. WireGuard then carries encrypted traffic directly between peers. An OpenDHT proxy is **not a tunnel-traffic relay**; it remains useful for discovery refresh and recovery. The optional Linux UDP proxy is local to the host. There is no TURN/DERP fallback, so some NAT combinations will fail. Android embeds official `wireguard-go`; the desktop daemon controls a separately established WireGuard interface.

**Why remediation is required.** The audit demonstrated that untrusted text can change Android's configured WireGuard peers, PSKs and routes. WireGuard then correctly authenticates an attacker-added peer. With valid keys, discovery exploitation required either participant's static private key, but not the PSK; a malicious imported profile is a separate route. An all-zero configured peer key permits an outsider to forge discovery records. This is a controller/configuration failure, not a break of WireGuard cryptography. The current Android build must not serve as the trusted network entrance.

No deliberate backdoor or compromised downloaded dependency was identified. Server release binaries reproduced exactly; Android/native comparisons strongly supported source correspondence but were not wholly bit-identical. These are bounded findings, not a certification of dependencies, publishers or future updates. Several Android findings are source/platform-contract findings without phone reproduction.

**Requirements already decided by the owner:**

- WireGuard modules must perform peer authentication. Discovery must never enroll peers, remove PSKs, grant routes or authorize traffic. Do not introduce another peer-authentication protocol.
- Keep rootless **Podman** on `nas`; no Docker migration. Preserve the existing service identity and host UID/GID mappings; inspect them before eventual deployment. Android naturally runs as an Android app.
- Build Go/container and Android artifacts locally from reviewed Owner commits, using already available, verified, pinned dependencies. Only the owner installs the locally generated APK. Keep a stable local APK signing key; remove public release, hosted AAR, updater and Obtainium requirements. No new runtime dependency without a concrete need and review.
- The VPN is **split-tunnel only**: selected server destinations, optionally further restricted to selected Android apps. Ordinary phone traffic uses its ordinary internet connection. No default routes, full-tunnel mode or system lockdown that blocks bypass traffic.
- Configuration must survive Android/GrapheneOS system backup and restore while remaining protected against ordinary local extraction. The OS backup service handles scheduling, encryption, transport and recovery; the app may supply a small standard Android backup adapter, not its own backup product.
- Review battery cost across the whole Android design. Use bounded, event-driven work; no extra polling services, permanent wake locks or automatic battery-optimization exemptions.
- Public STUN/OpenDHT dependencies are acceptable. No permanently managed VPS is desired. Direct connectivity must be tested; an external VPS is available for a later authorized trial, and the phone test will follow separately. The mobile carrier lacks IPv6.
- Keep existing encrypted-filesystem startup, Samba ownership/access, NFS service and configuration history. Restic remains configured but disabled until backup paths are chosen. Do not change firewall rules under this code-remediation plan. Any later necessary rule needs an exact explanation and approval.
- Keep changes under Git, in small reviewable commits. Do not rewrite existing configuration/credential history. New signing secrets belong outside source/build contexts; this does not authorize deleting historical credentials.

**Scope:** documentation only; implementation and NAS changes follow later. Keep work in local Git; no public disclosure/publication is authorized here. Fork Actions were disabled at audit completion and are unnecessary for local builds.

### Reading and prioritization

Design decisions **D1–D3 are settled**. D4 records the mandatory split tunnel and the new app-selection recommendation; its prior DNS/privacy work is deferred. **F1–F17 retain their IDs**; optional QR provisioning is F18 so existing references do not change. Rejected alternatives have been removed. Open choices appear only where investigation or the latest requirements introduced a real choice; see section 7.

Complexity is relative: **S** = narrow change with little new state; **M** = several boundaries or lifecycle state; **L** = protocol migration or broad redesign. Delete unused code before adding mechanisms. Keep discovery, WireGuard, Android lifecycle, backup adaptation and local signing responsible for separate tasks. “Settled” means the owner has selected the approach, not that it has been implemented or verified.

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

Use one stable fork application ID and signing identity, increasing versionCode on updates. Choose and record them before the first installation/backup; changing them later affects installation and restore identity. A distinct ID is recommended to avoid mixing upstream and local installations. Protect and back up the signing key outside the build workspace. Local signing still trusts the signing tools and workstation; it is not protection from their compromise.

**Acceptance:** missing/tampered AAR fails; the clean offline build uses the declared cached inputs. Signing runs no Gradle/plugin code. Verify the APK certificate/hash and container digest before manual installation. F13–F15 implement this path; no push or public artifact release is required.

### D4. Keep a true split tunnel; add app selection using Android's existing API

**Split tunnel SETTLED; app-selection detail proposed · Android/routing · A-09/A-21 explicitly deferred · Complexity S–M.**

**Online verification (2026-09-22):** the official WireGuard Android app has included/excluded application selection. Its `GoBackend` passes those lists to `VpnService.Builder.addAllowedApplication`/`addDisallowedApplication`, and separately adds routes from peer `AllowedIPs`. This is OS routing by application identity, not packet inspection or a custom WireGuard extension. The app's selector and config parser support both lists, but prohibit using both together. [Official backend](https://git.zx2c4.com/wireguard-android/tree/tunnel/src/main/java/com/wireguard/android/backend/GoBackend.java?id=e7b3a3c118836e112620b1302a8ba1873ad4daac), [official app selector](https://git.zx2c4.com/wireguard-android/tree/ui/src/main/java/com/wireguard/android/fragment/AppListDialogFragment.kt?id=e7b3a3c118836e112620b1302a8ba1873ad4daac).

The current STUNMESH service adds `AllowedIPs` routes but has no app list. Retain that routing approach and extend it; do not switch to a second VPN app or import another entire VPN/UI library. STUNMESH's discovery integration still needs its own app; Android permits only one active VPN per user/profile. The official app alone does not supply this project's rendezvous protocol.

| Remaining choice | Complexity | Recommendation/tradeoff |
| --- | --- | --- |
| **1. Selected-app allowlist AND selected server IP routes** | M | **Recommended.** Only chosen apps can use the tunnel, and only for server destinations; their unrelated internet traffic still bypasses it. One inclusion list is enough; omit an exclusion-list mode. |
| 2. Server IP routes only, available to any app | S | Smaller UI/config change; meets destination-only splitting but any app can contact those destinations. Use only if the owner decides app restriction is unnecessary. |

**Required for either choice:** use explicit server `/32` routes (and `/128` only where needed), or a deliberately selected small subnet. Reject default/effectively catch-all route sets, including imported profiles that approximate them with multiple broad prefixes. Application selection alone does **not** justify `0.0.0.0/0` or `::/0`. WireGuard/Android routes select IP destinations, not TCP ports or individual background services within an app. Access to only particular server ports would require a separate server-side policy; no firewall or userspace packet-filter expansion is planned.

With an allowlist, an empty list or missing selected package must never fall back to “all apps.” Preserve restored package names and require explicit resolution before activation if unavailable. Add selections only at TUN establishment/configuration change. Preserve ordinary IPv4/IPv6 connectivity: Android otherwise blocks an address family absent from the VPN; explicitly allow family fall-through as the official split configuration does. Keep “Block connections without VPN” **off**; always-on startup is separate and can remain supported. [Android builder contract](https://developer.android.com/reference/android/net/VpnService.Builder), [Android per-app VPN guidance](https://developer.android.com/develop/connectivity/vpn#per-app).

**DNS decision:** no new DNS policy, resolver feature or privacy hardening for now. Leave discovery fallback unchanged and record A-09/A-21 as accepted/deferred risks, not fixed. Do not add forced VPN DNS or DNS interception. Validate that ordinary name resolution still works in the split profile; use explicit server addresses for the initial trial. TLS/proxy validation and route safety fixes remain in scope.

**Acceptance:** selected app → server uses WG; selected app → internet stays direct; nonselected app stays direct. Test both families on Wi-Fi even though the carrier lacks IPv6. A disconnected tunnel must not turn ordinary phone networking into a blocked/full-tunnel path. F6 contains the concrete service changes. NAT failure remains possible; a VPS test does not prove phone-carrier success.

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

Keep existing import compatibility; upgrading YAML/mapstructure alone does not replace application validation. Parse once per user import/configuration update, never on a background timer.

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

**Risk:** a configured HTTPS proxy can redirect discovery requests to unrelated HTTP/LAN destinations. Split tunneling does not remove this risk: discovery intentionally uses protected underlay sockets. Separately, broad routes or an empty app allowlist can accidentally widen traffic capture. No router exploit or secret extraction was demonstrated.

**Agreed fix:** reject all DHT redirects and require HTTPS, except isolated test fixtures. In Android, implement D4's selected server routes and proposed inclusion list with platform APIs. Keep app-selection fields local to Android; neither DHT nor the Go discovery record can set them. Preserve them through import/edit/backup/restore. Rebuild the TUN only when those settings actually change. No default route, catch-all prefix combination, port-inspection engine or automatic VPN DNS.

**Files:** Go OpenDHT HTTP client; Android `TunnelConfig.kt`, parsers/editor and `StunmeshVpnService.kt`. Store explicit package names; a picker can use platform APIs when opened. Manual entry suffices for nonvisible packages; no broad scanning SDK or background package polling. Explain missing packages after restore instead of broadening access.

**Acceptance:** redirect tests cause no second request. Capture routing for selected/nonselected apps and selected/unselected destinations, including IPv4/IPv6, route overlap, unavailable packages, VPN down and after restore. Verify that normal phone internet remains direct. Application/DNS APIs are platform policy, not an additional WG authenticator; F1/F3 still bound hostile discovery output.

### F7. Make desktop UDP-proxy endpoint ownership consistent

**Go only · Low conditional availability risk · G-15 · Complexity S.**

**Risk:** two peers sharing a source endpoint overwrite the forward mapping; moving the first then deletes the second's mapping. Packets drop; WG authentication is not bypassed.

**Agreed fix:** reject ambiguous duplicate endpoint assignments atomically, preserve the prior good mapping and return a bounded diagnostic. Delete an old mapping only if the moving peer still owns it.

Do not add multi-peer packet demultiplexing for a topology this deployment does not need.

**Files/acceptance:** `internal/wgproxy/demux.go`. Test both assignment orders, failed duplicate update, subsequent move, removal and concurrent reprogramming under the race detector. Document the supported endpoint uniqueness constraint.

### F8. Preserve configuration and integrate with Android/GrapheneOS system backup

**Android only · Medium data-loss/availability risk · A-05/A-18, N2 · Durable storage agreed; backup adapter recommended · Complexity M.**

**Risk:** read/decrypt errors become an empty store that later overwrites existing data; replacement can delete the last good file. Auto Backup can restore ciphertext without its device-bound key. Stale activity state can overwrite the manager's active tunnel selection.

**Settled storage fix:** one repository/state flow with serialized read-modify-write operations. Distinguish absent file from corrupt/unreadable data; surface errors and block overwrites until explicit resolution. Use `AtomicFile` with rollback, retaining AES-GCM encryption under a local Android Keystore wrapping key. Preserve active selection and all configuration fields, including app restrictions. Do not add a database.

**Research finding:** a hardware-backed Keystore wrapping key is not a portable backup secret. Restoring its ciphertext alone can produce a successful-looking but unusable backup; Seedvault documents this exact problem. The WG private key is different: the app already decrypts it into memory for `wireguard-go`, so it can supply it to an authorized system backup without exporting the wrapping key. [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [Seedvault known issues](https://github.com/seedvault-app/seedvault/wiki/Known-Issues#apps-that-use-keystore-may-fail-to-open-after-restore).

GrapheneOS currently integrates encrypted Seedvault backup. Its inspected transport supports key-value backup and advertises client-side encryption, as well as device-transfer mode. These are source capabilities, not proof of behavior on the owner's installed version. Use standard Android backup APIs so the app does not depend on a Seedvault-specific SDK. [GrapheneOS backup support](https://grapheneos.org/features#encrypted-backups), [inspected transport at `4b9bf622`](https://github.com/GrapheneOS/platform_packages_apps_Seedvault/blob/4b9bf6220b1b533dfd22180f3c22fce9d8310b88/app/src/main/java/com/stevesoltys/seedvault/transport/ConfigurableBackupTransport.kt).

| Backup implementation choice | Complexity | Tradeoff |
| --- | --- | --- |
| **1. Recommended: small key-value `BackupAgent`; retain encrypted live file** | M, no external dependency | Supplies one bounded versioned configuration entity to the OS and re-encrypts it on restore. No durable plaintext export/staging file, backup UI, scheduler or transport in this app. |
| 2. App-private credential-encrypted file, relying on OS file encryption/sandbox and ordinary Auto Backup | S | Least code, but a copied decrypted app-data file contains secrets. Does not retain the extra protection against raw file extraction; not the default given the owner's requirement. |
| 3. Custom full-backup adapter retaining encrypted live storage | M–L | Fallback only if the target OS cannot use option 1. File-based backup callbacks require careful temporary plaintext handling/cleanup; greater audit burden. |

**Option 1 contract:** declare `android:backupAgent` and use its platform callbacks; do not add an exported intent/service for apps to request backup. Load/validate the encrypted config through the repository and write only a bounded, versioned configuration entity to `BackupDataOutput`. Require `FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED`; device-transfer mode alone is insufficient. Report an unsupported transport as failure, preserving the last good backup. The inspected Seedvault transport sets both flags; verify on the actual phone. [Android key-value backup](https://developer.android.com/identity/data/keyvaluebackup), [BackupAgent transport flags](https://developer.android.com/reference/android/app/backup/BackupAgent#FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED).

On restore, bound/validate the entity, create a new local wrapping key if needed, and atomically encrypt/write the restored configuration. Never restore/export the old wrapping key. Exclude raw ciphertext, logs, caches and temporary exports from default file backup/transfer; explicitly prevent a full-backup fallback from copying unusable ciphertext. Signal `BackupManager.dataChanged()` after durable semantic edits, coalescing changes; the OS owns when/how backup runs. No app-owned timers or network backup calls. If Keystore is unavailable before unlock, fail/defer without resetting data.

Configure and test backup while the VPN foreground service is active, including `backupInForeground` if required. Backup may pause/stop the process; F10 must recover safely. Restore selected tunnel data, but obtain any required system VPN permission again and do not automatically activate an incomplete profile. The same stable application ID/signing identity must be retained. Treat restoration as moving a peer identity: do not leave two simultaneously active devices using its same WG key. [Android backup lifecycle](https://developer.android.com/identity/data/autobackup).

**Protection boundary (also N2):** ordinary apps, file managers, shared storage, diagnostics and a copied encrypted config must not reveal WG keys/PSKs. An authorized encrypted OS backup intentionally includes recoverable configuration; possession of that backup and its recovery secret grants recovery of WG secrets. A compromised OS/app process, an intentional sensitive export or the running WG engine is outside “ordinary extraction” protection. Do not claim the WG private key never leaves hardware; only the wrapping key may be hardware-backed.

**Files/acceptance:** repository/model/manager/activity, new minimal backup adapter, manifest/backup rules. Test failed decrypt/parse/write without overwriting good data; connect B then edit/import/restart and preserve B. Delete-active must stop and clear it atomically. On the actual GrapheneOS build/profile, back up via system settings, restore into a fresh installation/device with no old Keystore key, and prove key/config/app-selection continuity plus a successful WG connection. Test missing packages, corruption, old schema, unencrypted transport rejection, foreground backup and locked-device behavior. A Seedvault checkmark alone does not close this requirement. No real phone reset is authorized by this plan; use a spare/test profile or later agreed restore procedure.

### F9. Prevent the editor from rewriting undisplayed configuration

**Android only · Medium conditional metadata/availability risk · A-19 · Complexity S initially.**

**Risk:** editing an imported multi-store tunnel replaces its plugin list with one store and redirects every peer there; an unrelated edit also resets `logLevel`.

**Recommended:** make advanced/multi-store profiles read-only in the current single-store editor with a clear reason; retain import/export and preserve all fields for supported edits. This is the smallest safe behavior for an OpenDHT-only home setup.

Retain this small safe restriction; no full multi-store editor. Preserve new Android app restrictions and backup metadata during supported edits. This item was not changed by the latest decisions.

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

**Files/acceptance:** Go OpenDHT errors/controller logging; Android `TunnelConfig.kt`, import exception handling, `StatusScreen.kt`. Canary-test private keys, PSKs, `api_key`, nested/list config, single/list URL credentials, malformed lines and failed/fallback requests. No canary may appear in UI diagnostics, stored rolling logs or exported diagnostics. Preserve intentional sensitive configuration export only under F16.

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

Use the least privilege supported by the tested namespace/interface arrangement; namespace UID 0 may be required and must be documented with its host mapping. Do not add arbitrary `USER 65532`, `--privileged`, host networking or ownership changes merely to silence a check. Keep configuration/optional QR generation in a local one-shot tool; no web provisioning server or QR/scanner dependency belongs in the running daemon/image.

**Files/acceptance:** `Dockerfile` remains a valid Podman build recipe; `.dockerignore`, release recipe and later container unit. Test using the actual service user and UID/GID map. Document WG interface/control-socket ownership, required namespace capabilities, exposed UDP ports and mount access. No NAS data directories need to be mounted into discovery. Use synthetic files to test denied/allowed access; inspect layers for excluded canaries. Preserve Samba permissions and the existing NFS/startup arrangement. Explain any necessary routing/firewall change separately before implementation.

### F16. Label plaintext export and request secure keyboard behavior

**Android only · Medium conditional user-action/privacy risks · A-13/A-23 · Complexity S.**

**Risk:** full profile export intentionally writes private keys/PSKs to a selected document provider without an explicit warning. Masked secret fields use normal text keyboard options, potentially enabling suggestions/learning. A malicious keyboard can observe input even with password hints.

**Agreed fix:** label export “plaintext configuration — contains private keys,” explain the destination receives secrets, and require a deliberate export confirmation. Keep support-log export distinct. Set password keyboard type with autocorrect disabled for private key, PSK and any retained token field; retain visual masking. Verify actual `EditorInfo.inputType` on device.

**SETTLED:** retain the warned, deliberate plaintext configuration export and set password keyboard options using existing Compose APIs. System backup is F8; do not add a separate encrypted-backup format or backup UI. Optional QR transfer follows F18 and carries the same secret warnings. No library upgrade solely for a secure-field convenience API.

**Files/acceptance:** `MainActivity.kt`, `TunnelListScreen.kt`, `TunnelEditorScreen.kt`. Cancelled export writes nothing; confirmed output is explicitly sensitive. Verify all secret fields, including retained advanced configuration. Do not promise protection from a compromised IME or introduce a new backup cryptosystem as incidental UI work.

### F17. Remediate reachable advisories without treating scanner counts as exploits

**Cross-codebase · Dependency maintenance/conditional exposure · G-07/A-08 · Complexity S–M per compatible update.**

**Risk/context:** the audit found advisories in module/build-tool graphs, not demonstrated dependency compromise. Android runtime graph had no reported OSV advisories at the audit date. Seven build-tool advisory entries covered six components; these were not shipped Android runtime dependencies. The Kotlin finding concerns KAPT incremental caches, and this project has no KAPT task; Jetifier's JDOM path was also inactive. A release compiler sharing signing credentials is still dangerous independently of any particular CVE.

**Plan:** start with the audited, available pins. Consult per-advisory evidence and rescan the exact reduced graph; classify runtime/build/test reachability. Delete unused affected paths/dependencies first. Keep KAPT/Jetifier absent. Do not upgrade broad dependency families or override AGP transitive JARs to clear scanner output. If a retained reachable vulnerability requires a version change, document why and propose the smallest compatible pinned update; existing-cache preference must not be described as a security fix.

Review the recorded upstream wireguard-go delta for this phone/TUN path before deciding whether an update is necessary. Document non-reachable accepted flags and review triggers instead of performing speculative upgrades. No exception for a demonstrated reachable authorization flaw. This preserves the local pinned build preference while keeping a route to necessary security fixes.

**Evidence/acceptance:** `security-audit-evidence/android-build-tool-advisory-triage.md`, Go direct-dependency and gosec triage, OSV snapshots and upstream delta notes. Maintain an inventory of advisory, resolved version, execution context, trigger, decision and next review. Preserve the short-XOR-STUN regression: the audited Pion version was already patched for that reported panic. Rebuild/retest after each coherent update. Publisher checksum agreement and a clean scan do not establish absence of malicious code.

### F18. Optional QR provisioning through the existing configuration importer

**New cross-codebase usability item · Optional, not a security blocker · Secret-transfer risk · Complexity S–M for the smallest path.**

**Goal:** make initial peer setup less error-prone without adding an enrollment server, network pairing protocol, custom authentication or another background service. QR carries configuration; the owner authorizes the peer locally and WireGuard performs tunnel authentication. A copied QR is not an additional login factor and has no automatic expiry/revocation.

| Option | Complexity | Assessment |
| --- | --- | --- |
| **1. Recommended if QR is wanted: local configuration tool emits one phone profile as QR; existing GrapheneOS Camera scans it; user explicitly imports bounded text** | S–M | No camera SDK/permission in STUNMESH. Reuse the F1/F2 parser and preview; add only a user-triggered text-import entry point if needed. A pinned local encoder may be required; it is not currently an audited Android dependency. |
| 2. Generate each private key on its own device and exchange public-key/address QR payloads in two steps | M–L | Avoids moving private keys, but requires local key-generation/provisioning flows and more coordination; a PSK still needs protected sharing. Defer unless that extra separation is desired. |
| 3. Keep file import and omit QR initially | S / no new implementation | Smallest overall scope and compatible with the available-dependencies requirement. Recommended first milestone; QR can follow once the importer and backup are fixed. |

GrapheneOS Camera already supports offline QR scanning; test its actual text-transfer workflow on the owner's build. Prefer deliberate paste/import to a new exported auto-enrollment handler. Do not add an in-app scanner, Google service dependency, online QR generator, multipart QR protocol or updater. [GrapheneOS Camera usage](https://grapheneos.org/usage#camera).

**If option 1 proceeds:** the trusted local tool creates a distinct phone key/profile and the matching server peer entry; a phone QR must **never** contain the server private key. Include only the phone's configuration, selected host routes and needed public rendezvous settings; app selections are confirmed on the phone. A profile containing the phone private key/PSK is a plaintext secret even when drawn as QR. Warn before display; avoid logs, screenshots, shell history and persistent QR images; clipboard/scanner exposure is a deliberate tradeoff. Keep the payload compact and versioned; reject oversize instead of inventing chunking. The server entry remains a reviewed Git configuration change, not automatic remote enrollment.

**Acceptance:** scan without network access; preview expected peer key/routes before saving; no automatic activation; malformed/oversized payload rejected; a duplicate import cannot silently replace an existing key. Retain F16's warning and F11's no-secret diagnostics. No QR work until the extra encoder dependency and transfer workflow are selected; verify available tools first. F17 remains dependency triage so existing issue IDs remain stable.

## 3A. Android battery and background-work contract — applies to every fix

| Area | Required behavior |
| --- | --- |
| Tunnel off | No STUN/DHT requests, status polling, wake locks or reconnect jobs. Unregister callbacks and cancel Go/Kotlin work. OS-scheduled configuration backup remains available. |
| Discovery / F3–F5 | One scheduler per active tunnel, bounded attempts, coalesced handover events, HTTP connection reuse and backoff. Stable TTL refresh starts at 180 seconds for testing; no permanent minute-by-minute recovery loop. |
| NAT / WireGuard | The current Android model defaults to 25-second persistent keepalive. Do not multiply this with ICMP pings or a separate socket keepalive. Test whether it is needed for the real NAT pair; use 0 only if required reconnect/inbound behavior survives. Keep the smallest necessary set of peers active. |
| Validation / F1–F2, F6–F9 | Parse/validate and compare authorization state on configuration mutations. No timed config scans, app enumeration or continuous full-device rebuilds. Back up only durable configuration changes, not packet counters or transient discovery state. |
| UI / F10–F12, F16/F18 | Event-driven status/notification; visible-screen-only counter refresh; bounded log ring. Imports, exports and QR scanning happen only on user action. No telemetry or background scanner. |
| Platform power policy | No new permanent partial wake lock, exact wakeup alarm, periodic WorkManager job or automatic exemption request. A foreground service does not justify defeating Doze. If sleeping delays rendezvous, report/test recovery instead of promising uninterrupted reachability. |
| Metered traffic | Preserve the underlay's metered status and the sync app's cellular policy; test Wi-Fi/cellular transitions. Avoid making the VPN appear unmetered to trigger unintended bulk sync. Use the platform's inheritance semantics, not another polling mechanism. |
| Build/dependencies | No self-update checks, dependency fetches, release checks or background key rotations inside the installed app. |

WireGuard documents persistent keepalive as optional and useful for NAT mappings; it is distinct from DHT TTL refresh. Android can defer work/network during idle. Measure the resulting tradeoffs; do not promise zero wakeups and instant incoming connectivity. [WireGuard keepalive guidance](https://www.wireguard.com/quickstart/#nat-and-firewall-traversal-persistence), [Android Doze](https://developer.android.com/training/monitoring-device-state/doze-standby). For API 29+, `setMetered(false)` inherits the underlay's meteredness; it does not unconditionally force an unmetered network. [Metered VPN API](https://developer.android.com/reference/android/net/VpnService.Builder#setMetered(boolean)).

**Measurement gate:** on GrapheneOS compare VPN off, active-idle and actual sync over comparable Wi-Fi/cellular periods, including overnight idle and repeated handovers. Record app CPU, wakeups, transmitted bytes, radio activity, battery usage and reconnect delay using platform diagnostics. Reuse one test matrix for F5/F8/F10; investigate excessive retries before changing power exemptions. Final cadence/keepalive values remain test outcomes, not hard-coded promises.

## 4. Maintenance — deferred except the explicit secret/backup requirement

| ID / sources | Risk/context | Recommended fix and alternatives | Complexity / acceptance |
| --- | --- | --- | --- |
| **N1 — A-08: wrapper mismatch** | Official hashes matched; wrapper/distribution mismatch was hygiene, not evidence of tampering. | **Deferred.** Keep the existing verified working pin unless the build needs a change. | No independent cleanup milestone. |
| **N2 — A-05: secret protection/backup** | A non-exportable wrapping key cannot itself be the portable backup. WG secrets must remain recoverable by authorized OS backup, without ordinary file/log extraction. | **Required, implemented in F8.** Retain encrypted local storage; platform backup decrypts logical configuration through the app's standard callback and restore rewraps it under a new key. Verify hardware backing only if making that claim; never claim WG key bytes remain permanently in hardware. | M with F8; prove encrypted-file-copy protection and fresh-device restore. This overrides treating all of section 4 as optional. |
| **N2 — remaining A-08/G-07 hygiene** | Lint style/unused resources, ICMP identifier quality and SHA-1 identifier naming are not the demonstrated authorization flaw. | **Deferred.** Do not add background health checks or perform cosmetic rewrites. Delete unused code incidentally under D2/F13; never label ICMP/STUN success as WG authentication. | No extra dependency upgrades. |
| **N3 — G-07/A-08: broad graph/UI refactor** | Some desktop packages enter mobile's import closure; Compose contributes many dependencies. Import presence alone does not establish retained vulnerable code. | **Deferred.** Delete dependencies made unused by agreed removals. Keep existing UI/framework and avoid large extraction work merely to reduce counts. | D2's removal of process plugins is still mandatory. |

## 5. Execution order and local installation gates

| Stage | Work | Completion gate |
| --- | --- | --- |
| **0 — Baseline and remaining choices** | Preserve audit commits/evidence; create local branches. Carry D1–D3 forward as settled. Resolve section 7 where needed and record schema, destinations, package list, app identity and signer location. | No reopening settled crypto/plugin/local-build decisions. Backup adapter feasibility and app scope are explicit. |
| **1 — Trust boundary** | F1/F2, D2 plugin exclusion, then D1 protocol/private-key separation. F13 containment can happen immediately. | Injection tests become safety tests; unauthorized peers/PSK bypass fail; malformed config cannot partially apply; no process plugins in home artifacts. |
| **2 — Discovery/network correctness** | F3–F7 and D4 split routing; apply battery contract. | Bounded hostile-proxy/STUN tests pass; no broad routing/redirect traversal; both fork endpoints interoperate. DNS privacy policy remains deferred. |
| **3 — Android correctness/backup** | F8–F12/F16 and required N2, with F5/F10 lifecycle. Prototype OS backup early to validate the chosen adapter before committing to its schema. | Durable config, real fresh-key restore, preserved app selection, foreground service, honest status and safe diagnostics. Phone gates still tracked. |
| **4 — Local build trust** | D3, F13–F15/F17 using available pins. | Exact local AAR, offline build, minimal image, separate signer and artifact manifest. No release service or updater. N1/N3 remain deferred. |
| **5 — Isolated integration and phone** | Synthetic peers; rootless namespace; external VPS pair; then actual Android carrier/home NAT. | Split routing, native backup/restore, handover/idle recovery and battery measurements pass. No guarantee inferred from one NAT pair or a backup success icon. |
| **6 — Review and staged use** | Review diff, deferred risks and hashes; agree exact NAS commands and manually install the local APK. F18 follows only if selected. | Existing LAN access and configuration preserved; rollback available. No public publication step. |

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

**Phone handoff:** local signed APK, SHA-256/certificate fingerprint, manual install/update instructions, test profile and selected apps/server routes. Include steps for direct internet versus WG traffic, authenticated transfer, handover, overnight idle, reboot, discovery failure and GrapheneOS settings backup/restore. Record phone OS/Seedvault versions and package identity. Test metered sync policy and foreground notification. No destructive reset without a separately agreed procedure; JVM/emulator tests do not close phone-dependent findings.

**Rollback:** preserve prior configuration and exact artifacts without overwriting keys. Rollback must not mean reinstating the vulnerable Android core as a trusted entrance; if no known-safe version exists, stop the experimental tunnel and retain existing LAN administration. No blanket key rotation is prescribed by this audit, because compromise was not observed; confirmed exposure would require a separate scoped incident response.

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

## 7. Remaining choices and verification inputs

| Item | Recommended next decision / boundary |
| --- | --- |
| **App scope (D4/F6)** | Choose the combined app allowlist + server routes; destination-only is the smaller alternative. Supply actual package names and server IPs during provisioning. Full-tunnel routing is already ruled out. |
| **Native backup adapter (F8)** | Use the small key-value `BackupAgent`, retaining encrypted local storage. Verify the owner's GrapheneOS version/profile invokes it and can restore under a fresh key. If incompatible, choose the full-backup adapter; do not silently weaken local storage to option 2 or exclude configuration from backup. |
| **QR (F18)** | Defer for the first working build. If desired afterward, select the external-scanner/local-encoder path and explicitly review any encoder dependency not already available. No in-app scanner by default. |
| **Installation identity and test inputs** | Record stable fork application ID, signing-key location/fingerprint, phone OS/Seedvault version and target Android packages. These are implementation/provisioning inputs, not reasons to revisit D1–D3. |
| **Cadence/keepalive** | Finalize from phone/NAT/battery tests; proposed 180-second stable discovery and existing 25-second keepalive are test inputs, not performance guarantees. No extra always-on probes. |

**Deferred by choice:** A-09/A-21 DNS/privacy hardening, general N1/N3 and nonessential N2 hygiene. Native backup and N2's secret protection are required. Local dependency pins remain unless a specific retained vulnerability or incompatibility requires a scoped change.

**Residual risks:** compromised endpoints/OS/WireGuard/toolchain or local signer; public metadata and discovery DoS/replay; NAT failure and sleep-delayed recovery; the deferred DNS behavior; and disclosure through authorized backup recovery or intentional configuration/QR transfer. The plan reduces code and authority while keeping those limits explicit.
