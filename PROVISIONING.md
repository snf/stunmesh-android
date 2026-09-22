# Enrollment and QR provisioning

**Owner decision, 2026-09-22:** the optional WireGuard PSK belongs in the local enrollment QR/file. Separate manual PSK entry was impractical and encouraged extra transfers. A PSK-bearing proposal is **confidential**, while the phone's response remains **public**. No server/phone private key or wrapped private-key blob is accepted in enrollment. The phone generates and protects its own identity.

WireGuard mixes an optional PSK into its existing authenticated key exchange. Possessing the PSK alone does not supply an authorized peer's private key; leaking it loses that extra shared-secret protection. This changes provisioning, not WireGuard's exclusive authentication role. [WireGuard protocol](https://www.wireguard.com/protocol/).

## Owner tool

Use the local `provision` binary and **qrencode 4.1.1**, already pinned in the build inputs. Neither belongs in the running service image. No new Android camera dependency, network enrollment service or background task is added.

```sh
umask 077
./provision new --name nas --server-key "$SERVER_PUBLIC_KEY" \
  --address 10.77.0.2/32 --routes 10.77.0.1/32 \
  --stun "$STUN_SERVERS" --opendht "$HTTPS_PROXY_ORIGINS" \
  --psk-file /private/path/phone-psk --out phone-enrollment.json
qrencode -t SVG -l M -o phone-enrollment.svg < phone-enrollment.json
```

`--psk-file -` reads the PSK from stdin, avoiding a secret command-line argument. Omit the option only for a deliberately PSK-free peer. The tool validates a canonical nonzero 32-byte PSK, bounds input, creates the proposal exclusively with mode 0600 and never prints the PSK in a reply or error. Configure the identical PSK on the server; never remove an existing requirement just to make enrollment succeed. Keep complete server credentials/configuration in the owner's private NAS Git repository as requested; do not commit real credential QR/files to either source fork or public test evidence.

The schema is **stunmesh-enroll-v2** with an optional `preshared_key`; there is no separate `psk_required` flag or manual PSK field. Missing means deliberately no additional PSK; a present empty, malformed or all-zero key is rejected. The earlier v1 proposal is rejected: regenerate it using this tool. Private-key/blob fields, unknown fields, duplicate keys, broad/default routes and payloads over 2048 bytes are rejected. Replies retain `stunmesh-peer-v1` because their public-only schema is unchanged.

## Phone flow

1. Install the locally signed **STUNMESH** APK (`dev.stunmesh.local`). Scan with a trusted local scanner such as GrapheneOS Camera and deliberately paste the result, or use **Open enrollment file**. No automatic link/intent handler exists.
2. Review the server public key, phone address and exact service routes against the owner's trusted display. The UI states whether a shared key is included without displaying it. Pasted enrollment text is masked and uses password keyboard options; the window retains `FLAG_SECURE`.
3. **Confirm & create identity** generates the phone private key locally and wraps the complete configuration using the hardware-backed store. The VPN stays off. A duplicate proposal cannot replace an existing identity.
4. **Copy public reply** returns only the proposal ID, phone public key and addresses. Validate it locally:

```sh
./provision reply --proposal phone-enrollment.json --reply phone-public-reply.json
```

The tool checks the matching proposal/address and valid public key; it never echoes the proposal's PSK. This matches paperwork, not authentication or automatic authorization. Compare the public key through the trusted channel, then explicitly add it to the server WG configuration and public discovery overlay under Git.

5. After server authorization, choose **Review & connect** and grant Android's VPN/notification consent. Leave **Block connections without VPN off**, so ordinary phone traffic keeps its normal internet route.

**Transfer boundary:** a trusted external scanner, clipboard/keyboard or selected file provider can see an embedded PSK. This is the accepted usability tradeoff; none receives the phone-generated private key. Display QR locally, avoid online QR generators/messengers/cloud-synced screenshots, and remove temporary credential QR/files and clipboard copies after enrollment. Deleting a file is not a promise of forensic erasure. For this controlled device session a secret file may travel directly over paired TLS ADB, then be removed after confirmed import; do not mistake a shared-storage download for app-private storage.

## Recovery

The app stores AES-GCM-wrapped configuration in `noBackupFilesDir` with atomic replacement. The wrapping key is non-exportable StrongBox where available, otherwise verified TEE; software-only protection is rejected. Wireguard-go needs the WG private key in process memory, accepted under the intact-Android-sandbox threat model.

Only the system-bound key-value BackupAgent may emit a logical configuration snapshot, and only when the OS transport asserts **client-side encryption**. Device-to-device capability alone is insufficient. No generic secret export, private-key QR, backup UI, app-owned scheduler or network backup client exists. The wrapping key/raw live ciphertext are excluded; restore validates and re-wraps under the destination hardware key and leaves profiles inactive.

An authorized backup plus its recovery secret can clone the WG identity. Keep the original copy off when restoring; if the original or backup is untrusted, create a fresh identity and revoke the old peer. Ordinary USB/MTP does not expose app-private WG keys. Actual encrypted recovery remains a device/transport acceptance gate, not a claim from local builds or QR provisioning.
