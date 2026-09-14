# STUNMESH: merged security remediation plan

**Date:** 2026-09-14 · **Status:** proposal; no remediation or deployment performed.

This is one plan for both Owner forks. An identical copy belongs at the root of each repository; update both copies together. The Go copy is canonical. Finding IDs refer to the original [Go audit](../stunmesh-go/SECURITY_AUDIT.md) and [Android audit](../stunmesh-android/SECURITY_AUDIT.md), which preserve evidence, prerequisites and limitations. Those reports remain historical records; record remediation outcomes separately. Relative cross-repository links assume sibling checkouts named `stunmesh-go` and `stunmesh-android`.

## 1. Project context and implementation contract

| Repository | Role | Audited production commit | Local audit commit |
| --- | --- | --- | --- |
| [snf/stunmesh-go](https://github.com/snf/stunmesh-go) | Desktop discovery daemon, optional local UDP proxy, and Go mobile core embedded in Android | `71a73228cd2bc001cdc5d485a16621a24bfae15a` | `934d4ef423a20498f3d9e579b6581ff5e3829a8c` |
| [snf/stunmesh-android](https://github.com/snf/stunmesh-android) | Kotlin UI, configuration/import/export, Android VPN lifecycle; consumes the Go core as an AAR | `e0cc30951e24ec018423bb113acfe7849f9836d5` | `6641b916be56f9dcdd298a1ba1ef9c69f2b0f48d` |

Both local audit branches are `security-audit/2026-09-13`. Public forks may not contain those branches: obtain the local audit commits and `security-audit-evidence/` before implementing. The Go mobile source matches release `v1.15.1`; Android matches `v0.2.1`. Rebase or dependency updates require reviewing the intervening changes.

**Architecture.** STUN finds a public UDP mapping. OpenDHT proxies exchange endpoint records. WireGuard then carries encrypted traffic directly between peers. An OpenDHT proxy is **not a tunnel-traffic relay**; it remains useful for discovery refresh and recovery. The optional Linux UDP proxy is local to the host. There is no TURN/DERP fallback, so some NAT combinations will fail. Android embeds official `wireguard-go`; the desktop daemon controls a separately established WireGuard interface.

**Why remediation is required.** The audit demonstrated that untrusted text can change Android's configured WireGuard peers, PSKs and routes. WireGuard then correctly authenticates an attacker-added peer. With valid keys, discovery exploitation required either participant's static private key, but not the PSK; a malicious imported profile is a separate route. An all-zero configured peer key permits an outsider to forge discovery records. This is a controller/configuration failure, not a break of WireGuard cryptography. The current Android build must not serve as the trusted network entrance.

No deliberate backdoor or compromised downloaded dependency was identified. Server release binaries reproduced exactly; Android/native comparisons strongly supported source correspondence but were not wholly bit-identical. These are bounded findings, not a certification of dependencies, publishers or future updates. Several Android findings are source/platform-contract findings without phone reproduction.

**Requirements already decided by the owner:**

- WireGuard modules must perform peer authentication. Discovery must never enroll peers, remove PSKs, grant routes or authorize traffic. Do not introduce another peer-authentication protocol.
- Keep rootless **Podman** on `nas`; no Docker migration. Preserve the existing service identity and host UID/GID mappings; inspect them before eventual deployment. Android naturally runs as an Android app.
- Use self-built Go/container and Android artifacts from reviewed Owner commits, pinned versions/digests, a self-controlled APK signing key, and the owner's GitHub release/Obtainium distribution. An official-store listing is not required.
- Public STUN/OpenDHT dependencies are acceptable. No permanently managed VPS is desired. Direct connectivity must be tested; an external VPS is available for a later authorized trial, and the phone test will follow separately. The mobile carrier lacks IPv6.
- Keep existing encrypted-filesystem startup, Samba ownership/access, NFS service and configuration history. Restic remains configured but disabled until backup paths are chosen. Do not change firewall rules under this code-remediation plan. Any later necessary rule needs an exact explanation and approval.
- Keep changes under Git, in small reviewable commits. Do not rewrite existing configuration/credential history. New signing secrets belong outside source/build contexts; this does not authorize deleting historical credentials.

**Scope of this document:** code, build/release and deployment prerequisites. It does not authorize applying NAS configuration, restarting production tunnels, publishing vulnerability details, enabling fork Actions, or releasing APKs. Actions were disabled on both public forks at audit completion; verify that state before eventual publication. Keep remediation work local until its disclosure/release scope is agreed.

### Reading and prioritization

Design decisions **D1–D4 come first**. Implementation work **F1–F17 follows**, then maintenance/nitpicks **N1–N3**. Cross-codebase items explicitly say so. Each item states its risk, recommendation, alternatives where useful, and acceptance criteria. There are at most three options per item; an omitted alternative means no comparably useful choice was identified.

Complexity is relative: **S** = narrow change with little new state; **M** = several boundaries or lifecycle state; **L** = protocol migration, new authority/infrastructure or broad redesign. Complexity compounds: avoid combining optional mechanisms, maintaining legacy and new protocols indefinitely, or adding a framework to solve a small validation problem. Recommendations are proposals, not previously approved design choices.

## 2. Design decisions — settle before dependent implementation

### D1. Make discovery non-authorizing and stop reusing WireGuard keys

**Cross-codebase · High policy mismatch · G-02; governs G-01/G-03/G-14 and A-01/A-17.**

**Risk/context:** `internal/crypto/endpoint.go` uses static-static NaCl `box` with WG private keys outside WireGuard. It excludes the WG PSK, permits reverse-slot reflection, and exposes recorded endpoint metadata after later static-key compromise. Desktop `internal/wg/client_ctrl.go` and `client_cli.go` read the WG private key into the daemon. This contradicts the literal authentication requirement even after injection is fixed. WireGuard's PSK participates in its own handshake, not this discovery codec. [WireGuard protocol](https://www.wireguard.com/protocol/).

| Option | Complexity | Assessment |
| --- | --- | --- |
| **1. Recommended: bounded, unauthenticated typed endpoint hints; WireGuard alone authenticates peers/packets. Remove NaCl discovery and private-key reads.** | M implementation; L coordinated migration | Smallest design meeting the requirement. Loses discovery-record confidentiality/origin authentication; retains public-service availability dependence. |
| 2. Keep NaCl solely as a metadata privacy layer, with the same strict endpoint-only boundary | M | Preserves encrypted metadata and more compatibility, but still performs authentication outside WG and retains key reuse. Requires an explicit relaxation of the owner's requirement; do not silently choose it. |
| 3. Authenticate records using separate discovery keys and bind sender, slot and sequence | L | Avoids WG-key reuse but adds enrollment, rotation and replay state; still violates the literal requirement. Not recommended for this deployment. |

**Recommended contract:** local configuration owns the peer public key, PSK and AllowedIPs. Discovery returns only a canonical numeric IP:port candidate associated with an already configured peer. The controller selects that peer from its trusted polling context, never from a peer identifier supplied by the record. No keys, routes, commands or configuration fragments are accepted from a store. Public-key-derived indices are identifiers, never authenticators. Public hints reveal endpoints to readers who locate the records; STUN/proxy operators already see some source metadata. HTTPS certificate verification remains mandatory: it authenticates the proxy transport, never a tunnel peer.

Use a versioned record schema and distinct protocol namespace; update both peers together. Reject incompatible records explicitly. Do not implement automatic legacy fallback. A small explicit configuration conversion and coordinated restart is preferable to two permanent protocols. Sender/slot labels and timestamps in unauthenticated records can prevent accidental confusion but cannot establish authenticity or freshness.

Remove private-key fields from desktop discovery interfaces and remove WG dump parsing that materializes private keys. This is reduced accidental exposure, **not privilege separation**: a daemon retaining unrestricted WG control may still retrieve/change keys. A separate restricted endpoint setter could reduce that privilege but adds another process/protocol and is deferred. Android's embedded WG engine necessarily retains its private key in the app process; discovery must not consume it.

**Acceptance:** source/data-flow review finds no private key or PSK in discovery; synthetic attacker-controlled records can change only a candidate endpoint. Neither wrong static keys nor wrong/missing PSKs complete a WG session where a PSK is configured. Publish the new metadata-privacy and availability tradeoffs before migration. F1 remains mandatory regardless of the option selected.

### D2. Ship a minimal OpenDHT-only product, not executable plugins

**Cross-codebase · Medium conditional code-execution exposure · G-06, A-02; dependency portion of G-07/A-08.**

**Risk/context:** Android imports can reach Go `exec`/`shell` constructors despite a built-ins-only contract. Execution was demonstrated in the Linux mobile build, not Android OS. Optional shell helpers evaluate shell input; a Cloudflare helper accepts its token in process arguments. Existing `builtin_opendht` tags remove Cloudflare but leave executable-plugin constructors linked.

| Option | Complexity | Assessment |
| --- | --- | --- |
| **1. Recommended: compile only built-in OpenDHT for the home daemon and Android; reject all other plugin types at import and core validation.** | S–M | Removes execution and unused credential/provider paths. Multiple named OpenDHT instances can remain; they are not executable plugins. |
| 2. Keep optional desktop plugins behind a separate explicit build target, never in Android/home artifacts | M | Useful only if maintaining general upstream compatibility; creates another supported/tested variant. |
| 3. Sandbox generic plugins and repair their protocol/secret transport | L | Adds IPC, permissions and lifecycle policy for a feature this deployment does not need. Not recommended. |

**Files/implementation:** `internal/plugin/manager.go`, `exec.go`, `shell.go`, mobile construction, Android `TunnelYaml.kt`/`TunnelConfig.kt`, build tags and release manifests. Remove unused helpers from the home image. If optional helpers remain distributed, replace shell evaluation with strict data parsing and move tokens out of argv; otherwise label them unsupported and exclude them from release outputs.

**Acceptance:** both Android import and direct mobile JSON reject executable/unknown types before saving or starting. Tests cannot construct a process plugin in either home artifact. Inspect linked packages and image contents; a build-tag label alone is insufficient. See F9 for imported multi-store editing and N3 for further, optional dependency reduction.

### D3. Establish one reviewed source-to-artifact path and a separate signer

**Cross-codebase · High release-trust risk · A-03; supply-chain portions of G-07/A-08.**

**Risk/context:** Android release builds resolve an upstream Go AAR even when a local AAR exists. Repository ordering may permit the same coordinate from another repository; that substitution was not observed. Mutable tools/actions and build-time access to signing/publishing credentials let a compromised tool bypass source review. Pinning protects against unexpected changes, not malicious code already pinned.

| Option | Complexity | Assessment |
| --- | --- | --- |
| **1. Recommended for this small deployment: verified local builds; sign the exact reviewed APK in a separate offline/minimal step; publish only the selected hashes.** | M | No CI signing authority to maintain. Best fit for infrequent manually approved releases. |
| 2. CI read-only build/test, followed by an independently gated minimal sign/publish job | M–L | Better automation, but more permissions, artifacts and workflow boundaries to maintain. |
| 3. Pinned fork AAR hosted in an exclusively scoped Maven repository | M–L | Supports a wider consumer base; unnecessary repository infrastructure for two local builds. Still needs signer isolation. |

**Required in every option:** Android release consumes the **exact fork-built AAR**, verifies its checksum and fails if absent/mismatched. No upstream or stub fallback. Prefer one explicit file dependency over competing resolution paths. If remote resolution remains, use exclusive repository content plus dependency verification. Keep a stable owner-controlled signing key and independent certificate fingerprint; choose a fork application ID before first distribution. A distinct ID permits a separate installation and avoids upstream-feed confusion; keeping the upstream ID requires explicit uninstall/migration because a different certificate cannot upgrade it.

One release manifest should bind Go commit, Android commit, AAR hash, toolchain/dependency pins, APK hash/certificate/versionCode, and OCI digest. Use monotonically increasing Android versionCodes. Obtainium must target the owner's selected feed; verify the first-install certificate independently. Updates remain manually reviewed/pinned. Keep a protected backup of the signing key and recovery instructions; loss can prevent normal updates. A signer compromise or compromised installed app can still expose tunnel secrets.

**Acceptance:** an absent/tampered local AAR fails the release build; a release manifest identifies every shipped input. Compilation has no signing key or write-capable publication token. The signing step verifies the selected artifact hash and executes no Gradle build or downloaded plugin. Verify APK signatures/certificate and container digest before installation. F13–F15 implement the remaining controls.

### D4. Make routing, DNS and direct-only availability explicit

**Cross-codebase · Conditional privacy/availability risk · A-09/A-21 and architecture caveats.**

**Risk/context:** the app silently uses public DNS for discovery if underlay DNS is unavailable. Separately, rejected configured VPN DNS can leave Android using default-network DNS. These are different paths. Discovery needs an underlay route before the tunnel works; application DNS must follow the chosen tunnel policy. Direct hole punching remains dependent on the actual NAT pair.

| Option | Complexity | Assessment |
| --- | --- | --- |
| **1. Recommended initial profile: split tunnel to explicit NAS/LAN destinations; use underlay DNS for discovery, with no hidden public fallback; explicitly configure application DNS behavior.** | S–M | Matches remote NAS access and avoids full-tunnel route recursion. When underlay DNS is absent, discovery waits/retries and explains why. |
| 2. Same split tunnel with an explicit approved public fallback resolver | S | Better discovery availability on some networks; exposes discovery hostnames to that resolver. Fits the owner's acceptance of public services if deliberately selected. |
| 3. Full-tunnel/lockdown profile with explicit VPN DNS and tested discovery escape | L testing/operations | Appropriate only if all phone traffic must traverse home. Requires separate IPv4/IPv6, DNS, protected-socket and rootless-route validation. |

**Required:** do not silently discard rejected DNS, routes or interface settings. Validate before creating the TUN and abort visibly if an explicitly required setting cannot be installed. An intentionally empty DNS list must explain underlay/default DNS behavior. Use HTTPS proxy hostnames with ordinary certificate verification; replacing URLs with literal IPs is not a universal DNS fix because TLS identity and provider addresses matter.

For direct access, expose “discovery unavailable”/“no authenticated handshake” status. Try a bounded list of approved STUN/proxy endpoints and the last working endpoint. If the actual NAT pair fails, document failure and revisit the connectivity design; do not silently add a relay or VPN provider. A VPS test proves only that pair, not the phone carrier. OpenDHT never becomes a data relay.

**Files/acceptance:** `mobile/node.go`, Android `StunmeshVpnService.kt`, `internal/wgproxy/escape_linux.go`, deployment profile. Test explicit valid/invalid/empty VPN DNS separately from missing discovery DNS; record actual resolver traffic. Test full-tunnel escape only if selected; existing `SO_MARK`/route-probe failures must not be treated as proof of a working outer route. No production firewall change is part of this decision.

## 3. Implementation fixes

### F1. Make all WireGuard configuration rendering typed and endpoint-only

**Cross-codebase · High deployment blocker · G-01/A-01 · Complexity M.**

**Risk:** multiline discovered/imported endpoints and AllowedIPs inject UAPI commands, create a hidden peer, remove a PSK and transfer routes. Later `IpcSet` errors do not roll back prior commands; a clean endpoint refresh does not remove the rogue peer.

**Recommended:** parse at the Go trust boundary into `netip.AddrPort`, `netip.Prefix` and fixed-size keys; reject controls, wrong lengths, ports outside 1–65535 and disallowed addresses. Render only canonical typed values. Initial configuration may set the trusted peer list; discovery's API accepts only an existing peer ID and typed endpoint. Validate Android imports as well, but never trust app-side validation alone. Numeric addresses are mandatory for discovery; if user-configured hostnames remain supported, resolve them through an explicit bounded path into typed addresses before UAPI rendering.

Untrusted public hints should reject unspecified, multicast, loopback, link-local and other locally forbidden destinations; permit LAN candidates only under an explicit locally configured policy for the intended subnet. Do not confuse “unicast” with “safe to contact.” This limits attacker-induced UDP probes without promising complete prevention. Keep scoped IPv6 handling explicit rather than accepting arbitrary zone strings.

**Alternative:** patch newline checks into every interpolated string (S), but this duplicates fragile checks and leaves later fields exposed; not recommended. No WireGuard fork or new cryptographic protocol is needed.

**Files:** Go `mobile/uapi.go`, `mobile/controller.go`, `mobile/config.go`, `internal/ctrl/establish.go`; Android `TunnelYaml.kt`, `WgQuickConf.kt`, `TunnelConfig.kt`.

**Acceptance/recovery:** invert the audit's injection reproductions into rejection tests; compare peer keys, PSKs and AllowedIPs before/after adversarial refreshes and verify no plaintext reaches an unconfigured peer. Keep single ownership of updates; compare security state after configuration mutations without logging secrets. Unexpected partial mutation must close/stop the compromised device and rebuild from validated local configuration before resuming. A validation failure before mutation leaves the prior good device intact. Never claim a successful clean refresh repairs pre-existing corruption.

### F2. Bound and validate configuration before storing or applying it

**Cross-codebase · Medium conditional/local risk, plus low availability defects · G-09/G-10/G-13/G-14, A-07/A-17 · Complexity M.**

**Risk:** oversized Android imports exhaust memory; duplicate fields mislead review; malformed Go YAML mapping keys panic; invalid intervals crash tickers; out-of-range ports wrap; low-order peer keys defeat the legacy discovery codec. The malformed peer itself still cannot authenticate with WireGuard.

**Recommended:** bounded read before parsing; strict typed schema and counts/length limits; reject non-string/null YAML keys, unnecessary aliases and duplicate keys. Reject repeated wg-quick singleton fields while preserving deliberately supported repeated list fields. Validate every refresh/ping/HTTP interval as positive and bounded before starting workers. Validate ports before narrowing to `uint16`. Reject low-order public keys using the standard X25519 API's error behavior, not custom curve arithmetic; this is input validation, not a new peer authenticator. Preserve WG's own handshake rejection.

**Alternative:** standardize imports on one format and remove the other (M migration), reducing parser maintenance but breaking existing exports. Keep both unless that compatibility loss is accepted. Merely upgrading YAML/mapstructure does not replace application validation.

**Starting limits for fixture review:** 256 KiB imported profile, 32 peers, 8 store instances, 64 routes/peer, 2 KiB URL, 256-byte numeric endpoint field; reject oversized input without truncation. These are proposed home-use bounds, not upstream protocol limits. Centralize them, validate existing fixtures and tune only with a documented requirement.

**Files/acceptance:** Go `internal/config/{config,device}.go`, `mobile/config.go`, plugin config and endpoint parser; Android import/model parsers. Test at/below/above limits, null-key fuzz reproducer, duplicates, low-order keys, zero/negative intervals, port 0/65536/65537, controls and malformed CIDRs. Errors must preserve the existing config and contain no source/secret snippets. See F11.

### F3. Bound DHT responses and make candidate failure recoverable

**Cross-codebase · Medium availability risk · G-03/G-05 · Complexity M; depends on D1.**

**Risk:** unbounded bodies can exhaust memory; zero/negative timeout disables HTTP deadlines. Selecting the largest unauthenticated timestamp lets public writers eclipse valid records. A successful malformed response prevents useful fallback; same-second publications can choose stale data.

**Recommended under D1 option 1:** cap body bytes, entry count and individual decoded fields; use explicit request deadlines; validate status and bounded content before acceptance; try another configured proxy after invalid content. Start with a 256 KiB body, 64 scanned records and at most 4 distinct usable candidates per peer/cycle, subject to real proxy-fixture validation. Read limit+1 bytes or equivalent to detect overflow, including decompressed HTTP bodies. Bound decoding allocations too.

Treat timestamps as untrusted hints, never proof of freshness. Do not replace a demonstrably working WG endpoint on every unsolicited record. Try bounded alternate candidates when connectivity needs recovery, use actual WG handshake/receive evidence and avoid concurrent competing endpoint writers. Preserve a last-working candidate, bound retries and rate-limit probes. An attacker can still fill all returned slots or suppress values: **DoS resistance is best-effort, not solved**.

**Alternative if D1 option 2/3 is expressly chosen:** validate/decrypt each candidate first and authenticate sender/slot/version plus a sequence or timestamp inside it; define persisted replay-floor/restart recovery (L). This adds state and still cannot force an uncooperative store to return records. It is not a substitute for F1.

**Files/acceptance:** `internal/plugin/builtin/opendht/opendht.go`, plugin interface/controller selection; Cloudflare body limits too if retained. Test oversized/chunked/compressed responses, stalled reads, malformed HTTP 200, equal/future/stale timestamps, replay/reflection, all-invalid first proxy, deduplication and bounded retries. Verify forged candidates never change authorization. Do not flood a public DHT during testing.

### F4. Validate STUN replies and keep fallback readers alive

**Cross-codebase · Medium availability risk · G-04/G-16 · Complexity M.**

**Risk:** Linux raw discovery accepts replies without matching transaction/source; Android matches the transaction but not source. Linux consumes its one-shot reader after a bad first response, so a valid second server cannot recover. Darwin/BSD shares the unchecked helper, but was not network-tested.

**Recommended:** match transaction ID, expected server IP:port, response type/class, lengths and valid mapped endpoint; ignore invalid/mismatched packets until deadline. Keep the reader active for the operation and give attempts clear cancellation/ownership. Follow [RFC 8489 response validation](https://www.rfc-editor.org/rfc/rfc8489.html#section-7.3).

**Alternative:** remove raw/pcap modes from the home build and standardize Linux on the existing local UDP proxy (S–M, fits rootless use). This removes those shipped paths, but Android source validation still needs fixing; retained general-purpose raw code remains unresolved until fixed or explicitly unsupported. Avoid replacing the mobile parser with a larger library solely for consistency unless it reduces measured maintenance.

**Files/acceptance:** `internal/stun/{helper_socket,stun_linux}.go`, platform variants, `mobile/transport.go` and response handlers. Test wrong sender/transaction, malformed first reply followed by a valid reply, two-server fallback, cancellation and late packets. Confirm WG packets sharing the socket remain functional. These checks improve discovery robustness; they do not authenticate a WG peer or prevent an on-path STUN server from lying.

### F5. Refresh below expiry and react to real Android underlay changes

**Cross-codebase · Medium availability risk · G-08/A-10/A-20 · Complexity M.**

**Risk:** the 600-second refresh default meets OpenDHT's approximately ten-minute value expiry; one delay can erase discoverability. Dedup can suppress renewal indefinitely. Android's default-network callback may observe the VPN rather than Wi-Fi/cellular changes, delaying recovery until the next timer.

**Recommended:** initial 60-second refresh, small bounded jitter, OpenDHT dedup disabled, and immediate coalesced discovery on actual underlay change. Observe non-VPN network availability/loss/capabilities, starting with the existing underlay-DNS callback. Renew/rebind sockets or TUN only where the selected underlay requires it; do not rebuild the TUN on every irrelevant callback. Serialize lifecycle work and cancel obsolete discovery attempts.

**Alternative:** shorter polling only (S), simpler but retains avoidable mobile handover delay and battery/network traffic. Not sufficient for reliable phone use. Refresh interval and proxy limits must be checked against the chosen service rather than assuming every proxy has identical TTL/rate policy.

**Files/acceptance:** Go `internal/config/config.go`, `mobile/config.go`, `mobile/controller.go`; Android `StunmeshVpnService.kt`. Run beyond multiple TTL periods, skip consecutive refreshes, change public mapping, switch Wi-Fi↔cellular and sleep/wake. Record reconnection time and query/battery overhead. Phone behavior remains a release gate.

### F6. Prevent DHT redirects across network boundaries

**Cross-codebase · Medium conditional network reachability risk · G-11/A-12 · Complexity S.**

**Risk:** a configured HTTPS proxy can redirect requests to unrelated HTTP/LAN destinations. Android discovery sockets bypass the VPN, making underlay resources reachable. No router exploit or secret extraction was demonstrated.

**Recommended:** reject all redirects and require HTTPS proxy URLs; HTTP allowed only in an explicit isolated test configuration. Enforce in the Go HTTP client, not solely Android manifest policy.

**Alternative:** allow a small number of same-origin HTTPS redirects (S–M) only if the chosen proxy needs them; compare parsed scheme/host/effective port, not string prefixes. Extra redirect support is unnecessary until justified.

**Files/acceptance:** OpenDHT HTTP client construction. Synthetic HTTPS→HTTP, HTTPS→other-host, redirect-loop and localhost redirects cause no second request. This does not prevent a malicious explicitly configured origin or compromised DNS/TLS trust from impairing discovery; F1/F3 still bound its output.

### F7. Make desktop UDP-proxy endpoint ownership consistent

**Go only · Low conditional availability risk · G-15 · Complexity S.**

**Risk:** two peers sharing a source endpoint overwrite the forward mapping; moving the first then deletes the second's mapping. Packets drop; WG authentication is not bypassed.

**Recommended:** reject ambiguous duplicate endpoint assignments atomically, preserve the prior good mapping and return a bounded diagnostic. Delete an old mapping only if the moving peer still owns it.

**Alternative:** redesign demultiplexing to support multiple peers at one source (L), requiring WG-aware handling and more state. Only justified by an actual deployment needing that topology.

**Files/acceptance:** `internal/wgproxy/demux.go`. Test both assignment orders, failed duplicate update, subsequent move, removal and concurrent reprogramming under the race detector. Document the supported endpoint uniqueness constraint.

### F8. Preserve encrypted configuration and use one authoritative state owner

**Android only · Medium data-loss/availability risk · A-05/A-18 · Complexity M.**

**Risk:** read/decrypt errors become an empty store that later overwrites existing data; replacement can delete the last good file. Auto Backup can restore ciphertext without its device-bound key. Stale activity state can overwrite the manager's active tunnel selection.

**Recommended:** one repository/state flow with serialized read-modify-write operations. Distinguish absent file from corrupt/unreadable data; expose errors and block overwrites until explicit resolution. Use Android's atomic-file primitive with rollback on failed writes. Exclude the device-bound encrypted file from both backup/transfer rule sets. Keep user-directed transfer separate from this local store.

**Alternative:** transactional database (M–L), justified only if the configuration grows beyond a small store. It does not itself solve Keystore portability and is unnecessary here.

**Files/acceptance:** `ConfigRepository.kt`, `TunnelStore.kt`, `TunnelManager.kt`, `MainActivity.kt`, backup XML/manifest. Fault-inject decrypt/parse/write/rename failures and preserve prior bytes. Connect B, edit/import/delete another profile, restart service and verify B remains active. Explicitly define deleting the active tunnel: stop and clear selection atomically, never silently select another. Exercise device restore without the original key; no empty-store overwrite.

### F9. Prevent the editor from rewriting undisplayed configuration

**Android only · Medium conditional metadata/availability risk · A-19 · Complexity S initially.**

**Risk:** editing an imported multi-store tunnel replaces its plugin list with one store and redirects every peer there; an unrelated edit also resets `logLevel`.

**Recommended:** make advanced/multi-store profiles read-only in the current single-store editor with a clear reason; retain import/export and preserve all fields for supported edits. This is the smallest safe behavior for an OpenDHT-only home setup.

**Alternatives:** (2) patch only edited fields into the original model, preserving every undisplayed field/mapping (M; preferable if advanced editing is needed); (3) full multi-store editor (L; unnecessary now).

**Files/acceptance:** `TunnelEditorScreen.kt`, serialization/model. Import two distinct OpenDHT stores with separate peers and nondefault log level; either edit is blocked before mutation or name-only editing preserves the entire remaining model. Never silently downgrade an unsupported profile.

### F10. Make VPN lifecycle real and distinguish setup from connectivity

**Android only · High availability blocker plus misleading health · A-04/A-15 · Complexity M.**

**Risk:** `StubBackend` can create a real TUN and report UP while dropping traffic; genuine UP also precedes any handshake. The service never promotes itself to foreground, jeopardizing background/always-on operation.

**Recommended:** remove the stub from release builds and fail visibly when the real core cannot load. Implement prompt foreground promotion, a persistent status notification, appropriate permissions/type and orderly stop/revoke cleanup. Validate eligibility for the selected target SDK and actual device. Android explicitly requires foreground promotion for this VPN lifecycle. [VpnService reference](https://developer.android.com/reference/android/net/VpnService).

Display separate “starting/discovering,” “interface ready,” and per-peer “last authenticated activity” states; do not label device creation as proven connectivity. Stale handshake time alone is not proof of failure for an idle tunnel: combine handshake/receive data with an explicit reachability check when needed. Never use ICMP or STUN success as peer authentication.

**Alternative:** explicitly opt out of always-on initially (S scope reduction), but a user-started long-running VPN still needs a correct foreground lifecycle; this is not a workaround for omitting it.

**Files/acceptance:** `TunnelManager.kt`, `StubBackend.kt`, `StunmeshVpnService.kt`, `StatusScreen.kt`, manifest, `mobile/node.go`/status API. Test missing/corrupt native library, denial/revocation, start/stop, process death, reboot, background idle, notification behavior and upgrade on the target phone. Failures release descriptors and never show a fake connected state.

### F11. Generate safe diagnostics instead of exporting redacted raw configuration

**Cross-codebase · Medium conditional credential disclosure · G-17/A-06/A-14/A-22 · Complexity S–M.**

**Risk:** generic/nested plugin credentials, URL userinfo and malformed-import snippets survive log export. Go failure errors include credential-bearing proxy URLs. Disclosure requires such configuration and local logging or user sharing; no automatic exfiltration was found.

**Recommended:** allowlist diagnostic fields; do not serialize arbitrary plugin configuration into support logs. Reject URL userinfo at config load. Emit bounded error category, field and line number without raw parser snippets or exception text. Sanitize before insertion into rolling logs, with export-time defense in depth for older entries. Do not label arbitrary historical logs “secrets redacted.”

**Alternative:** recursive redaction with a growing secret-name list (M ongoing), but unknown fields and embedded secrets are easy to miss. A separately supported proxy-credential feature would require separate storage/display and request-error sanitization; unnecessary for public OpenDHT.

**Files/acceptance:** Go OpenDHT errors/controller logging; Android `TunnelConfig.kt`, import exception handling, `StatusScreen.kt`. Canary-test private keys, PSKs, `api_key`, nested/list config, single/list URL credentials, malformed lines and failed/fallback requests. No canary may appear in UI diagnostics, stored rolling logs or exported diagnostics. Preserve intentional sensitive configuration export only under F16.

### F12. Remove the unauthenticated debug import receiver

**Android debug only · Medium conditional local configuration risk · A-11 · Complexity S.**

**Risk:** any installed app can replace/select a tunnel through the exported debug receiver. The audited release APK does not contain it.

**Recommended:** remove the receiver and use test fixtures/instrumentation. **Alternative:** a dedicated test variant with a signature-protected, bounded receiver (S–M) if external test injection is necessary. Neither variant should carry production tunnel credentials.

**Files/acceptance:** `app/src/debug/AndroidManifest.xml`, `ConfigImportReceiver.kt`. Inspect merged manifests and built APKs; untrusted applications cannot import or select a profile. Do not treat a debug-only issue as evidence that the current release exposes this receiver.

### F13. Treat release tags and action inputs as data; reduce workflow authority

**Cross-codebase · High conditional release compromise · G-12/A-16; workflow portions of G-07/A-08 · Complexity S–M.**

**Risk:** a tag capable of triggering release can inject shell syntax into workflow scripts and access artifact/signing authority. Mutable privileged CLA actions and broad job tokens create additional compromise paths. The audit demonstrated shell substitution locally, not a GitHub credential theft.

**Recommended:** pass external inputs through `env`, expand quoted shell variables, and validate a narrow release-tag pattern before use. Audit downstream outputs and composite callers too; do not merely escape the demonstrated payload. Remove the personal forks' unused CLA workflows; pin external actions to reviewed full commits. Use read-only build jobs, `persist-credentials: false`, minimal publication permissions and protected release refs. Implement D3 signer separation before any release.

**Alternative:** retain Actions disabled and release manually (S immediate containment, recommended while fixing). Mark workflow defects unresolved until repaired or obsolete workflows removed; disabled CI is not a source fix. Keeping the CLA workflow offers no deployment benefit.

**Files/acceptance:** both `.github/workflows/`; Go `.github/actions/{build,build-aar,archive-plugins}/action.yml` and all other input-to-shell paths. Local tests pass shell metacharacters/newlines as inert data or reject them; no execution marker is created. Re-run workflow analysis and review reachable callers. Do not test by pushing a malicious release tag. No compilation step has signing or repository-write credentials.

### F14. Pin and verify the complete build graph

**Cross-codebase · Supply-chain residual risk, not proven compromise · G-07/A-08 · Complexity M; implements D3.**

**Recommended:** pin Go, gomobile commit, JDK, Android SDK/NDK/build tools, Gradle, base-image digest and all build dependencies. Keep Go checksum verification; add Gradle dependency locking where supported and verification metadata across runtime/build tooling. Bootstrap hashes from independently checked publisher artifacts, not blindly from the existing cache. Remove Foojay auto-download if supplying JDK explicitly. Use the exact local AAR and record its hash in the release manifest.

**Alternative:** vendor all sources/toolchains (L storage/maintenance) where offline rebuild requirements justify it. A cache alone is not provenance. Strict locking plus verified archives is smaller for this deployment. [Gradle dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html).

**Files/acceptance:** `go.mod`/`go.sum`, Android settings/build scripts, Gradle verification/lock files, build recipe and manifest. Build from a clean isolated workspace; after verified dependency acquisition, complete an offline build. Deliberately corrupt a dependency/AAR and require failure. Generate SBOM/module inventories and compare shipped closure against the intended OpenDHT-only scope. Reproduce own artifacts where feasible; document any build-ID/packaging differences rather than claiming byte identity from semantic similarity. F17 handles advisory triage; N1 handles the wrapper mismatch.

### F15. Build a minimal image and validate rootless permissions explicitly

**Go/deployment · Conditional secret exposure and excess privilege · G-07 · Complexity S image, M deployment validation.**

**Risk:** unrestricted `COPY . .` can copy local secrets/history into builder layers; the image contains unused helpers and defaults to UID 0 inside the rootless namespace. That UID is not host root, but host access follows Podman's mappings and granted mounts.

**Recommended:** minimal allowlisted build context or explicit source copies plus `.dockerignore`; exclude `.git`, keys, audit scratch, exports and local config. Ship daemon, required trust roots and only actual runtime needs; omit unused shell/Cloudflare helpers. Prefer a read-only filesystem and explicit tested runtime identity. Use existing local proxy mode where suitable to avoid raw-socket privileges.

**Alternative:** namespace UID 0 with narrowly granted mounts/capabilities if the existing WG/interface arrangement requires it (M validation). Do not add arbitrary `USER 65532`, `--privileged`, host networking or ownership changes merely to silence a check. A separate helper for privileged interface operations is L complexity and needs a demonstrated need.

**Files/acceptance:** `Dockerfile` remains a valid Podman build recipe; `.dockerignore`, release recipe and later container unit. Test using the actual service user and UID/GID map. Document WG interface/control-socket ownership, required namespace capabilities, exposed UDP ports and mount access. No NAS data directories need to be mounted into discovery. Use synthetic files to test denied/allowed access; inspect layers for excluded canaries. Preserve Samba permissions and the existing NFS/startup arrangement. Explain any necessary routing/firewall change separately before implementation.

### F16. Label plaintext export and request secure keyboard behavior

**Android only · Medium conditional user-action/privacy risks · A-13/A-23 · Complexity S.**

**Risk:** full profile export intentionally writes private keys/PSKs to a selected document provider without an explicit warning. Masked secret fields use normal text keyboard options, potentially enabling suggestions/learning. A malicious keyboard can observe input even with password hints.

**Recommended:** label export “plaintext configuration — contains private keys,” explain the destination receives secrets, and require a deliberate export confirmation. Keep support-log export distinct. Set password keyboard type with autocorrect disabled for private key, PSK and any retained token field; retain visual masking. Verify actual `EditorInfo.inputType` on device.

**Alternatives:** for export, remove full-profile export (S but impairs migration), or add portable encrypted export (L key/password/format/recovery design; defer). A compatible secure-text-field API may replace manual keyboard options, but avoid a Compose upgrade solely for this small fix.

**Files/acceptance:** `MainActivity.kt`, `TunnelListScreen.kt`, `TunnelEditorScreen.kt`. Cancelled export writes nothing; confirmed output is explicitly sensitive. Verify all secret fields, including retained advanced configuration. Do not promise protection from a compromised IME or introduce a new backup cryptosystem as incidental UI work.

### F17. Remediate reachable advisories without treating scanner counts as exploits

**Cross-codebase · Dependency maintenance/conditional exposure · G-07/A-08 · Complexity S–M per compatible update.**

**Risk/context:** the audit found advisories in module/build-tool graphs, not demonstrated dependency compromise. Android runtime graph had no reported OSV advisories at the audit date. Seven build-tool advisory entries covered six components; these were not shipped Android runtime dependencies. The Kotlin finding concerns KAPT incremental caches, and this project has no KAPT task; Jetifier's JDOM path was also inactive. A release compiler sharing signing credentials is still dangerous independently of any particular CVE.

**Recommended:** consult the checked-in per-advisory evidence, rescan the exact new graphs, classify runtime/build/test reachability, then update supported direct dependencies/toolchains to compatible patched versions in separate commits. Review the official wireguard-go delta identified by the audit and retest the pinned replacement. Keep KAPT/Jetifier absent unless deliberately needed. Do not override arbitrary AGP transitive JAR versions without compatibility testing.

**Alternatives:** (2) remove an unused affected component/path (S, preferred whenever genuinely unused); (3) document a time-bounded exception for a non-reachable advisory if upgrading breaks the supported build (S now, continuing review burden). No exception for a demonstrated reachable authorization flaw.

**Evidence/acceptance:** `security-audit-evidence/android-build-tool-advisory-triage.md`, Go direct-dependency and gosec triage, OSV snapshots and upstream delta notes. Maintain an inventory of advisory, resolved version, execution context, trigger, decision and next review. Preserve the short-XOR-STUN regression: the audited Pion version was already patched for that reported panic. Rebuild/retest after each coherent update. Publisher checksum agreement and a clean scan do not establish absence of malicious code.

## 4. Maintenance and nitpicks — after security and lifecycle fixes

| ID / sources | Risk/context | Recommended fix and alternatives | Complexity / acceptance |
| --- | --- | --- | --- |
| **N1 — A-08: wrapper mismatch** | Wrapper JAR/scripts lag the pinned distribution; official hashes matched, so this was hygiene, not tampering. | Regenerate wrapper from the chosen pinned Gradle release and independently verify distribution/JAR checksums. Alternatively retain the known-compatible old wrapper with a documented hash, but avoid the confusing mismatch. | S; clean/offline build and checksum verification. |
| **N2 — A-05/A-08/G-07: claims and low-impact findings** | Hardware-backed Keystore claim is unsupported; lint has unused resources/style warnings. Weak ICMP identifiers concern a health signal, not WG authentication. SHA-1 DHT indices are identifiers, not signatures. | Correct the hardware claim to “Android Keystore”; inspect security level only if reporting it. Remove verified-unused resources. Use standard cryptographic randomness for ICMP IDs if those health checks remain, but never treat ICMP success as authenticated health. Keep SHA-1 indexing unless D1's new namespace benefits from an intentionally versioned change; a hash swap does not fix DHT forgery. | S; docs match observed behavior, lint reviewed, health spoofing cannot grant access. No UI redesign to eliminate style warnings. |
| **N3 — G-07/A-08: further graph reduction** | Shared Go imports pull desktop WG control, YAML/DI wiring and related modules into mobile's import closure; import presence is not proof all code is retained. Compose is the largest Android dependency family, with no identified telemetry SDK. | Move shared endpoint types/interfaces into a small package and isolate desktop wiring if this yields a clear closure reduction. Alternative: retain verified dependencies when extracting them needs broad restructuring. Replacing Compose with a new UI stack is L complexity and not recommended for this audit. | M; compare before/after package/module/native closure and all behavior tests. D2's executable-plugin removal is mandatory, not deferred here. |

## 5. Execution order and release gates

| Stage | Work | Completion gate |
| --- | --- | --- |
| **0 — Decisions and baseline** | Record chosen D1–D4 options, schema/address policy, application ID, signer location and supported platforms. Preserve audit commits/evidence; create local remediation branches. | Explicit decisions include privacy loss, legacy incompatibility and direct-only failure behavior. No code/deployment assumptions hidden in defaults. |
| **1 — Trust boundary** | F1/F2, D2 plugin exclusion, then D1 protocol/private-key separation. F13 containment can happen immediately. | Injection tests become safety tests; unauthorized peers/PSK bypass fail; malformed config cannot partially apply; no process plugins in home artifacts. |
| **2 — Discovery/network correctness** | F3–F7 and D4 DNS/routing behavior. | Bounded hostile-proxy/STUN tests pass; refresh/fallback works; no redirect traversal; both fork endpoints interoperate on the selected new schema. |
| **3 — Android correctness** | F8–F12/F16, with F5/F10 lifecycle integration. | Durable config, preserved active selection, honest status, foreground lifecycle and safe diagnostics. Source/unit checks pass; unresolved device checks clearly tracked. |
| **4 — Build/release trust** | D3, F13–F15/F17 and N1/N2; N3 only if its small extraction is worthwhile. | Exact local AAR, verified dependencies, minimal image, signer isolation and complete artifact manifest. Fresh build produces installable owner-signed APK. |
| **5 — Isolated integration and phone** | Synthetic peers; real rootless namespace; external VPS pair; then actual Android carrier and home NAT, without disturbing existing access. | WG handshakes/data, routing/DNS, handover, restart, idle and failure recovery measured. No guarantee inferred from one successful NAT pair. |
| **6 — Review and staged deployment** | Review final diff, unresolved exceptions and release hashes; approve publication and exact NAS commands separately. | Local access preserved, rollback artifact/config available, owner accepts residual risks. Release only supported/verified features. |

For each issue commit: reference its plan ID and original audit IDs, describe the behavior change, add meaningful regression coverage, record results and any remaining limitation. Cross-repo changes need paired commit references and an exact AAR handoff. Do not publish a temporarily incompatible Android/core combination.

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

**Phone handoff:** provide the owner-signed release APK, SHA-256 and independently recorded signer fingerprint; installation/migration and Obtainium-feed instructions; synthetic or explicitly chosen test profile; expected split routes/DNS; then steps for mobile-data connection, authenticated handshake/data transfer, Wi-Fi↔cellular switch, idle/reboot and recovery after discovery loss. Inspect actual DNS paths and foreground notification. An emulator/JVM pass cannot close phone-dependent findings.

**Rollback:** preserve prior configuration and exact artifacts without overwriting keys. Rollback must not mean reinstating the vulnerable Android core as a trusted entrance; if no known-safe version exists, stop the experimental tunnel and retain existing LAN administration. No blanket key rotation is prescribed by this audit, because compromise was not observed; confirmed exposure would require a separate scoped incident response.

## 6. Complete finding-to-plan mapping

Every numbered finding is covered below. A finding may span design, implementation and maintenance; those references are parts of one remediation, not duplicate vulnerabilities.

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
| G-09 | F2 | A-09 | D4 |
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
| — | — | A-21 | D4 |
| — | — | A-22 | F11 |
| — | — | A-23 | F16 |

**Residual risks after the recommended fixes:** compromised phone/NAS/kernel/WireGuard implementation; a malicious reviewed dependency or toolchain; signing-key/build-account compromise; public endpoint metadata; discovery censorship/replay/flooding; unsupported NAT pairs; and configuration mistakes outside the tested route/permission policy. Pins and self-hosted source improve control, but do not eliminate these risks. The recommended scope deliberately avoids creating another authenticator, relay network or plugin sandbox to pursue guarantees the deployment does not require.
