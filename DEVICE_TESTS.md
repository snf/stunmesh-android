# NAS / Android acceptance tests

Local evidence covers source tests, release compilation, artifact checks and isolated namespace tests. It does **not** establish real carrier NAT success, NAS service access, hardware Keystore/Seedvault recovery or battery consumption. Current physical results are recorded in `DEVICE_TEST_RESULTS.md`: updated storage/LAN checks and the prior release’s external hotspot path pass; updated roaming and remaining device/service gates are tracked separately.

## Connection plan: wireless debugging first

**USB is optional.** Android 11+ supports installing and debugging over Wi-Fi after on-device pairing, without a first USB connection. GrapheneOS support has confirmed the same pairing workflow. Check the installed Android/GrapheneOS version at the start of the session. Sources checked 2026-09-22: [Android instructions](https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi), [GrapheneOS support](https://discuss.grapheneos.org/d/16143-not-possible-to-connect-via-wireless-debugging).

**Settled topology:** this working container communicates directly with the phone over its Wi-Fi/LAN address. `nas` is used only for the server side, always in rootless Podman. The owner will place the phone on a network reachable from this container.

```text
This working container --ADB with paired TLS over LAN--> GrapheneOS phone on Wi-Fi
This working container --existing SSH---------------> nas / operator
                                                     rootless Podman VPN/services
```

The phone and this container need LAN connectivity. Being on the same SSID is insufficient if guest/client isolation blocks peers. The container can use an ordinary routed/bridged network if outgoing connections reach the phone; it need not share multicast discovery or the host network. Verify reachability to the displayed phone ports at setup. Do not move ADB to `nas` or make debugging depend on the STUNMESH tunnel being tested.

| Connection | Use / tradeoff |
| --- | --- |
| **Wi-Fi ADB from this container — selected** | Push/install APKs, run device tests and collect diagnostics. Pairing gives this working container substantial phone-management authority while debugging is enabled. |
| Disconnected carrier test, then Wi-Fi reconnect — selected for mobility tests | No cable; prepare actions first, observe the phone and NAS, then collect available phone diagnostics after reconnection. Live ADB is unavailable during the disconnected interval. |
| USB debugging — optional fallback only | A separate session on a trusted computer, or deliberate USB access to this container, if continuous live logs prove necessary. Do not enable USB tethering, which would change the network being tested. |

Prepare ADB in this container when beginning the device session. Pin official SDK Platform-Tools and record its version and archive SHA-256. Google's current stable listing is **37.0.1**; these are diagnostic tools, not a change to the app's pinned build dependencies. No additional phone agent, container, host daemon, privileged mode, added capabilities, USB device mount or host-network switch is needed for the planned Wi-Fi path. Store ADB host credentials in a private per-session directory outside Git and compiler caches (proposed location: `/workspace/stunmesh-device-access/`); these are distinct from APK signing keys. Do not copy either credential set to `nas`. [Official standalone tools/release notes](https://developer.android.com/tools/releases/platform-tools).

Use explicit phone IP/ports instead of depending on multicast discovery across the container boundary. The ADB server remains on this container's loopback interface; do not publish port 5037 or enable all-interface listening. No router forwarding or firewall change is planned. If access fails, diagnose address, pairing port, network isolation and container routing before proposing any scoped change.

### One-time actions on the phone

1. Unlock the phone; enable Developer options by tapping **Build number** in About phone, if not already enabled.
2. Open **Settings → System → Developer options → Wireless debugging** on the trusted home Wi-Fi, then **Pair device with pairing code**.
3. Use the displayed pairing address/port and short-lived code for this container. After pairing, use the connection address/port on the main Wireless debugging page; the ports are distinct and can change.

In this container, with actual values substituted, run:

```sh
adb pair PHONE_IP:PAIRING_PORT
# Enter the short-lived pairing code at the prompt, not in a script or Git.
adb connect PHONE_IP:CONNECTION_PORT
adb devices -l
```

Save the exact target identifier from `adb devices -l` as `PHONE_SERIAL` for subsequent commands. Wireless pairing uses authenticated TLS; the legacy `adb tcpip 5555` flow is not part of this plan. No additional Android app, root or bootloader unlock is needed. [AOSP wireless ADB design and commands](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/dev/adb_wifi.md).

### What Wi-Fi debugging can and cannot cover

ADB can install the signed release, install/run the separate debug instrumentation package, and collect app-scoped logs/system diagnostics over this connection. The release remains non-debuggable; debugger attachment or deliberate fault injection belongs in the debug app with synthetic identities. If a native debugger is needed, inspect GrapheneOS's **per-app** native-debugging setting for the debug package rather than weakening global protections. [GrapheneOS native-debugging controls](https://grapheneos.org/features#attack-surface-reduction).

Turning off Wi-Fi interrupts this management path; Android also disables wireless debugging on Wi-Fi loss in the framework. Reboot/network changes can require unlocking, re-enabling debugging and reading the new connection port. For pure carrier tests, agree on short actions first, disconnect Wi-Fi, observe the server and phone, then reconnect and retrieve available diagnostics promptly. The log buffer is finite and reboot can lose it; no guarantee of a complete post-hoc trace. Use USB only if reproducing a failure needs continuous live capture. Do not route ADB through the VPN to work around this dependency. [AOSP network-loss handling](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/adb/AdbDebuggingManager.java).

The app deliberately sets `FLAG_SECURE`; screenshots/screen mirroring of its UI may be blank. Preserve that protection. The owner will handle OS consent/unlock, enrollment review and VPN consent locally. An emulator can supplement parser/UI checks, but cannot establish this phone's hardware key security, GrapheneOS backup behavior, carrier NAT or battery life.

## Execution order and evidence

The phases below define the acceptance plan. Current executed checks and pending prerequisites are recorded in [`DEVICE_TEST_RESULTS.md`](DEVICE_TEST_RESULTS.md). Run functional checks before the longer unattended tests. The detailed acceptance matrix and recovery checks below remain authoritative.

| Phase | Work / passing result | Owner involvement |
| --- | --- | --- |
| 0. Inventory and pairing | Record phone model, Android/GrapheneOS build, active user/profile, backup transport, current VPN/battery settings, artifact hashes and NAS UID/GID/network state. Pair this container; verify install/shell access. | Unlock and enable/pair wireless debugging; identify the intended test profile. |
| 1. Hardware/storage | Run the existing `HardwareStorageTest` in the separate debug app. Require hardware-backed wrapping, distinct GCM ciphertexts, tamper rejection, preserved valid ciphertext and non-exportable wrapping key. | Keep phone unlocked for initial key creation. No production configuration is replaced. |
| 2. Isolated server and enrollment | On `nas`, prepare only the dedicated Podman VPN trial; verify required permissions/ports and review explicit connections to the selected services before phase 3. Import the reviewed proposal (confidential when carrying a PSK), generate phone identity, review/add its public peer explicitly. No phone private-key QR or key copy. | Approve public peer details, VPN consent and notifications; use the PSK-bearing local enrollment record. |
| 3. Release connectivity and routing | On release APK, prove WG handshake plus real Samba/Syncthing service transfers using disposable test data, and ordinary internet/DNS bypass. Two apps targeting the same server address must follow the same route policy. Wrong key/PSK/source must fail. | A few phone interactions; existing sync data/shares remain untouched. |
| 4. Failure and mobility | Test Wi-Fi ↔ actual SIM data, short airplane-mode interval, inaccessible discovery, unavailable peer, screen lock/background, VPN revoke and restart/reboot. Record time from usable underlay to authenticated traffic and recovery. Distinguish loss of ADB from loss of VPN. | Toggle networks/reconnect debugging; unlock after reboot. Any reboot is scheduled with the owner. |
| 5. Encrypted recovery | Test the actual OS backup transport with a disposable identity. Restore validates/re-wraps and remains inactive; old device copy stays off. Missing/unencrypted transport or broken restore is a failed recovery gate, not a reason to add secret export. | Use OS backup/restore UI and retain recovery secret privately. Prefer a spare device or verified disposable-profile workflow; no personal-phone factory reset or production-data clearing. |
| 6. Battery and endurance | Start with short Wi-Fi/cellular idle and sync runs, then matched overnight VPN-off/on runs. Disconnect ADB and turn wireless debugging off during measurement. Record service reliability, radio/CPU/wakeup evidence where the OS exposes it, battery use and environmental differences. Repeat suspicious results. | Leave phone unplugged under agreed comparable conditions; reconnect only afterward. |
| 7. Closeout | Record pass/fail/blocked per case, fix failures in Git, rebuild/retest affected paths, verify final release hashes, and produce scoped deployment/rollback recommendation. Forget this container's pairing on the phone, disable wireless debugging, and retire its temporary ADB credentials. | Confirm that debugging access has been revoked. |

Allow one interactive session for phases 0–4, a separate recovery session if needed, and at least two comparable overnight measurements; these are planning allowances, not completion guarantees. Choose a tolerable handover delay before judging timing results; the present design can need several discovery cycles and does not promise instant roaming. No numerical battery-savings claim without a controlled comparison.

### Reviewed install and hardware-test commands

These commands describe the current reviewed artifacts; executed results are recorded separately. Use the existing signed APK in this container; no signing key is needed for installation. `install -r` preserves an existing compatible app's data. A signature conflict is a stop-and-review condition, not permission to uninstall/clear the app. Set `PHONE_USER_ID` to the agreed test profile's numeric ID after phase-0 inventory; installing APK code is device-wide even though enabling it and its data are per user, so a secondary profile is not a separate package-signing boundary.

```sh
STUNMESH_TEST_ARTIFACTS=/workspace/stunmesh-build/artifacts
adb -s "$PHONE_SERIAL" install --user "$PHONE_USER_ID" -r "$STUNMESH_TEST_ARTIFACTS/release/stunmesh-0.3.0-local.4.apk"
adb -s "$PHONE_SERIAL" install --user "$PHONE_USER_ID" -r "$STUNMESH_TEST_ARTIFACTS/handover4/stunmesh-debug.apk"
adb -s "$PHONE_SERIAL" install --user "$PHONE_USER_ID" -r -t "$STUNMESH_TEST_ARTIFACTS/handover4/stunmesh-debug-androidTest.apk"
adb -s "$PHONE_SERIAL" shell pm list instrumentation
adb -s "$PHONE_SERIAL" shell am instrument --user "$PHONE_USER_ID" -w -r \
  -e class dev.stunmesh.android.config.HardwareStorageTest \
  dev.stunmesh.local.debug.test/androidx.test.runner.AndroidJUnitRunner
adb -s "$PHONE_SERIAL" shell am start --user "$PHONE_USER_ID" -W \
  -n dev.stunmesh.local/dev.stunmesh.android.MainActivity
```

The runner package was checked against the built test APK; the revised suite contains **two hardware/enrollment tests**, not a complete automated VPN/backup test suite. Expect it to report two passed tests with no instrumentation errors. The enrollment test must decrypt and validate actual saved bytes, independently of the repository’s process cache. Check profile reload after a compatible release update/restart without clearing data or reenrolling. Use the release app for final acceptance and the debug app only for synthetic instrumentation/diagnosis. [Android instrumentation CLI](https://developer.android.com/studio/test/command-line#RunTestsDevice).

Keep scoped logcat, public WG handshake/counter evidence, instrumentation output and timing notes. Avoid private-key dumps, PSK-bearing arguments, unfiltered device-wide bugreports, heap dumps or screenshots containing unrelated personal data. Record the raw diagnostics privately; commit only reviewed summaries/public evidence under a new device-test session directory in both repositories. Record `not run`/`blocked` honestly for unavailable transport, address family or hardware, along with the next required check.

## Before changing the server

Record the existing rootless service user, process UID/GID maps, image/config Git commit, container networks/ports and filesystem ownership. Preserve encrypted-mount startup, Samba/NFS, credentials/history and disabled Restic. Load only the verified OCI archive; compare its SHA-256 and recorded image config/manifest digests. Review the dedicated trial compose config and the WG public peer entries before starting it. Mount no share/data directory into discovery.

Use separate same-port container/host mappings: kernel WG `51824` for explicit LAN bootstrap and proxy `51826` for public discovery, both bound only to the NAS LAN IP. The first real phone test showed that the proxy rejects LAN sources when its mapping contains only the public STUN endpoint; the direct listener lets WG authenticate/roam without adding untrusted source-learning. Confirm namespace `NET_ADMIN` suffices; do not add `NET_RAW`/privileged/host networking or a broad ownership change if a check fails. Diagnose the actual failure. The later service integration must explicitly connect selected Samba/Syncthing service addresses; a successful isolated `.1` ping does not prove either service is reachable. Explain any necessary routing/firewall change before making it.

## Phone / network matrix

Install the final signed release APK, verify package `dev.stunmesh.local`, version and signing certificate against ARTIFACT_MANIFEST.json. This is a separate installation from upstream. Use reviewed enrollment and an explicit server peer addition; no server-generated phone private key or legacy private profile import. Keep Android **Block connections without VPN disabled**.

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
| Diagnostics/clipboard/scanner/files/logcat | Only public reply/status appears in outputs; no private key, PSK, raw config, blob, URL credentials or exception excerpt. A trusted inbound scanner/file channel may see the explicitly included PSK; never the phone private key. |

The debug APK/instrumentation target has a different ID. `HardwareStorageTest` uses synthetic bytes, verifies hardware wrapping/non-exportability and GCM tamper rejection without replacing the app's store. Two tests have now passed on the provided Pixel 4a; consult the dated evidence rather than extending that result to other devices or untested OS recovery. Test the **release** UI/OS lifecycle separately; instrumentation is not a substitute.

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

## Live split-route application probe

`LiveSplitTunnelTest` is an opt-in debug instrumentation test. It uses ordinary sockets through the separately installed signed release VPN and never reads its identity/store. Start the temporary echo fixture **inside the dedicated trial**, bound only to its WG address (no host TCP publish):

```sh
podman exec -d stunmesh-audit-trial /bin/busybox timeout 600 \
  /bin/busybox nc -lk -s 10.77.0.1 -p 18080 -e /bin/busybox cat
adb -s "$PHONE_SERIAL" shell am instrument --user 0 -w -r \
  -e class dev.stunmesh.android.config.LiveSplitTunnelTest \
  -e live_split_tunnel 1 \
  dev.stunmesh.local.debug.test/androidx.test.runner.AndroidJUnitRunner
```

It asserts a selected `10.77.0.1/32` route, no VPN default routes/DNS override, exact 32768-byte bidirectional echo and ordinary HTTPS with normal certificate validation. The fixture expires after ten minutes; stop it sooner at closeout. No recurring job, permission or library is added to the release. This proves a real second UID/application path alongside the shell probe; it is not Samba/Syncthing integration or a carrier test.
