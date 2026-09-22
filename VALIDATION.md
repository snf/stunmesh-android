# Local remediation result — 2026-09-22

**Implemented and locally validated. Not deployed to NAS or tested on a physical Android device.** Both repositories use `security-remediation/2026-09-22`; original audit branches/history remain. `ARTIFACT_MANIFEST.json` records the exact commits used to build each artifact; later test/documentation commits do not change those already built binaries. Artifacts are local under `../stunmesh-build/artifacts/`, not pushed or published.

## Acceptance results

| Gate | Result / evidence |
| --- | --- |
| Go race suite, static checks, dependencies | Full `go test -race -tags 'mobile security_audit' ./...`, `go vet`, and `go mod verify` pass. Historical opt-in host/netns shell harnesses are skipped; the new image integration test is executed separately below. |
| Parser/discovery fuzzing | Seven boundary targets passed 30-second runs: mobile configuration, shared STUN response, server YAML, DHT candidates, public discovery record, JSON admission and public enrollment. Final strict-YAML rerun passed 132,157 executions. These are bounded fuzz runs, not exhaustive proofs. |
| WG authorization | Actual userspace WG/shared-bind tests pass for authorized traffic, unknown identity, wrong PSK, forbidden source and offline/resume. No custom authenticator participates. |
| Actual image/kernel WG | Final OCI rootfs, kernel WG, daemon/public-metadata reader, proxy, mobile bind and synthetic HTTPS DHT fixture pass bidirectional traffic. Unknown key, wrong PSK and unauthorized source cases pass rejection checks. Only namespace `NET_ADMIN`; no host network/firewall/configuration. Clean daemon SIGTERM passes. |
| Android offline source build | Clean committed snapshot, fresh output/project-cache directories, no build cache/network: **135 tasks executed**, release/debug APKs plus instrumentation APK built. Debug **11/11**, release **11/11** JVM tests pass; release lint passes. |
| Build input integrity | **839/839** Gradle artifact hashes matched earlier audit or current publisher data; strict resolution locks enabled. Deliberately changed AAR and cached Maven JAR both rejected; original cached bytes restored before final build. Go modules verified. |
| Real release APK | Owner-signed `dev.stunmesh.local`, version `0.3.0-local.2` / code `2`; non-debuggable, not test-only, no stub. Signature v3 verified with Android apksigner. ARM64/x86-64 `libgojni.so` exactly matches the local AAR; all native LOAD segments and APK ZIP layout support 16 KiB pages. |
| Image contents | OCI digests checked; exactly five regular files: daemon, official `wg`, BusyBox, musl loader, reviewed entrypoint. No config/key/Git/source/package manager/compiler. WG source signature verified; retained Alpine utilities meet the matching security database's listed fixes. |
| Dependency advisories | Full graph scan and package/artifact scope reviewed in `security-remediation-evidence/ADVISORY_TRIAGE.md`. Flagged Android host libraries are absent from release runtime/DEX; newly flagged Go packages are absent from daemon/core/gomobile imports. No blanket upgrade. |

Machine logs, unit-test XML, input provenance, package inventories and APK/image inspection results are under `security-remediation-evidence/`. Lint retains warnings for pinned dependency versions, template resources and the deliberate application-context singleton; it reports no blocking errors. A narrow documented suppression covers prepared-VPN foreground-service eligibility; no alarm permission was added.

## Plan coverage

| Plan | Implemented behavior |
| --- | --- |
| D1/D2, F1 | Public version-2 hints, typed endpoint-only WG updates, update-existing-peer semantics, no private discovery crypto or executable/cloud plugins. Discovery cannot add identities, change PSKs or grant routes. |
| F2–F4 | Bounded strict JSON/YAML/configuration, canonical keys/routes/endpoints, low-order rejection; HTTPS-origin/no-redirect DHT with request/aggregate/body/record/candidate bounds and fallback; STUN source/transaction/class/type/full-length validation without consuming a waiter for invalid packets. |
| F5–F7, D4 | Destination-only narrow split routes, unchanged phone DNS, protected/bound underlay sockets, one cancellable/coalescing mobile scheduler, offline suspension, bounded refresh/backoff, authenticated endpoint preservation and collision-safe server mapping. |
| F8/F9/F16, N2 | Verified StrongBox/TEE wrapping, serialized atomic durable store and fail-closed errors; remove lossy editor/ordinary secret import/export; minimal encrypted OS key-value backup, no generic file transfer, validated inactive restore. Wrapping key stays non-exportable/non-backupable; authorized OS recovery of logical WG identity is intentional. |
| F10–F12 | Single foreground service/backend owner, consent and safe teardown, setup distinguished from observed WG authentication, public allowlisted diagnostics, removed debug import receiver/stub backend. |
| D3, F13–F15/F17 | Removed hosted CI/release/install authority, mandatory hash-checked local AAR, offline pinned build, separate local signer, strict Gradle locks/verification, minimal rootless-compatible image and reachability-based advisory handling. |
| F18 | Versioned credential proposal with optional PSK and public-only reply, shared synthetic fixtures, 2048-byte QR bound, external scanner/local encoder and local phone-key generation. Removed manual PSK entry; no private-key/blob QR or automatic authorization. |
| N1/N3 and nonessential N2 hygiene | Remain deferred as agreed; no cosmetic upgrade/platform rewrite/new health worker. |

## Residual risks and later device gate

- WireGuard provides identity authentication, not availability of STUN/OpenDHT. Public hints expose endpoints and can be poisoned; candidate failure is bounded but connections can still be denied. There is no relay fallback for difficult NATs. The server's local UDP proxy remains on its packet path; OpenDHT is not a VPN traffic relay.
- Android code, Go/WG, kernel WG, OS, compiler/dependency publishers and the owner signer remain trusted. Hashes/pins constrain change; they cannot prove absence of backdoors. The namespace daemon's `NET_ADMIN` authority can alter its WG configuration if the daemon itself is compromised.
- WireGuard keys exist in the app's memory. Hardware wrapping protects storage against ordinary extraction; the authorized encrypted OS backup deliberately permits identity recovery/cloning. A trusted backup transport/OS and protected recovery secret are required. An unavailable/unencrypted transport gets no backup fallback.
- Battery cost was reduced structurally (deleted polls/duplicate work, offline suspension); **physical battery savings are not measured**. WG keepalive still costs radio activity. Handovers, especially through the server proxy, can need multiple discovery cycles. No instant-roaming claim.
- `DEVICE_TESTS.md` remains mandatory for hardware Keystore, actual GrapheneOS backup/restore, foreground/reboot behavior, IPv4/IPv6 split routing, carrier NAT, handover and overnight battery measurements. `deploy/README.md` requires checking actual rootless `operator` UID/GID maps and explicitly connecting the selected NAS services. A tunnel into a standalone container does not automatically expose Samba/Syncthing.

No NFS, Samba, encrypted-mount startup, disabled Restic, NAS ownership, production peer keys or host firewall was changed. Keep the original deployment until the later scoped trial passes. Keep `/workspace/stunmesh-signing/` private and make an owner-controlled encrypted backup of its key/password before relying on future APK updates; neither file is in Git or the build workspace.

## Physical session and QR PSK update — 2026-09-22

- New CLI/parser race tests and vet pass. Bounded enrollment fuzz run: 135,452 executions in 32 seconds, no failure. Negative cases include malformed/zero PSKs, private-key/blob injection, redaction, no output overwrite and mode-0600 credential files. Public replies never contain PSKs.
- Android rebuild from `abcf4cd`: all 135 tasks executed offline with the unchanged pinned AAR/dependencies; 11/11 debug and 11/11 release unit tests, release lint and all three APK builds pass. Owner signature unchanged; signed release code 2 installed as an update without clearing data. Installed hash matches `ARTIFACT_MANIFEST.json`; native libraries match the previous AAR and ZIP alignment passes for 16 KiB pages. Subsequent Kotlin changes are formatter-only.
- Pixel 4a / Android 13: both instrumentation tests pass (0.837 s), including hardware wrapping/tamper rejection and a synthetic PSK-bearing enrollment, locally generated identity, encrypted write/reload and public-only reply. Only the test-created profile is removed; no production app state cleared. Release `run-as` is denied as non-debuggable.
- Actual NAS rootless Podman starts the pinned image with NET_ADMIN only, correct UID/GID map, read-only config/rootfs, proxy 51820 and kernel WG 51822. Dedicated LAN publication is UDP 51824; existing upstream trial/services remain running. Owner raised exhausted kernel key-count quota to 512; byte quota remains 20000. Phone peer/authenticated traffic is still pending.
- No claim of carrier NAT, service integration, encrypted Seedvault restore or battery/endurance success. See `DEVICE_TEST_RESULTS.md`. No new runtime dependency or background activity was introduced by credential provisioning.
