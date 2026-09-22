# Public enrollment and QR

Run the local `provision` binary on the owner's workstation. It generates a proposal ID and validates the server public key, exact phone address, narrow service routes, numeric optional endpoint, STUN servers and HTTPS OpenDHT origins. It never generates or accepts a phone private key or PSK. Inputs below are public; substitute the actual verified values.

```sh
./provision new --name nas --server-key "$SERVER_PUBLIC_KEY" \
  --address 10.77.0.2/32 --routes 10.77.0.1/32 \
  --stun "$STUN_SERVERS" --opendht "$HTTPS_PROXY_ORIGINS" \
  --out phone-enrollment.json
qrencode -t SVG -l M -o phone-enrollment.svg < phone-enrollment.json
```

The encoder is **qrencode 4.1.1**, a one-shot workstation tool (local validation used Debian `4.1.1-2` and matching `libqrencode4`). Its package hashes are recorded in the artifact inputs. It is absent from Android and the service image. Payloads are capped at 2048 bytes; there is no multipart format, online QR generator, web enrollment service or phone camera dependency.

If a PSK is required, add `--psk-required`. The QR then requires a separately supplied PSK; it never contains one. Supply it through the phone's protected password field using a trusted keyboard/local channel, and configure the same PSK in WG on the server. Do not weaken an existing PSK requirement to make enrollment easier. The new profile stays off after saving.

1. Install the signed **STUNMESH** APK (`dev.stunmesh.local`); its ID/certificate differ from upstream.
2. Scan with GrapheneOS Camera, copy the public JSON text and deliberately paste it into the app, or select the public JSON file. The app has no automatic link/intent import handler.
3. Review the server public key, selected routes and phone address against the owner's trusted display. Confirm to generate a fresh phone private key and protect it locally. Duplicate proposal IDs cannot silently replace an identity.
4. Copy **Public reply** from the phone to a public text file. Check it locally:

```sh
./provision reply --proposal phone-enrollment.json --reply phone-public-reply.json
```

The tool checks the matching proposal/address and canonical non-low-order public key. This matches paperwork; it is **not authentication or automatic authorization**. Compare the public key over the trusted local channel, then explicitly add it to both the server WG peer configuration and public discovery overlay under Git. Only WG will authenticate subsequent traffic.

5. Choose **Review & connect**, confirm server authorization, and grant Android's VPN consent. Leave **Block connections without VPN off**; it would defeat the normal-internet requirement. The interface-ready status is distinct from a WG-authenticated session observation.

## Recovery

The app stores an AES-GCM-wrapped configuration in `noBackupFilesDir` with AtomicFile rollback. The wrapping key is non-exportable, StrongBox where available, otherwise verified TEE; software-only protection is rejected. Raw WG key use inside the app process is necessary for wireguard-go and accepted under the intact-Android-sandbox threat model.

Only the system-bound key-value backup agent may emit a bounded logical snapshot, and only when the OS transport asserts **client-side encryption**. Device-to-device capability alone is insufficient. No generic file/blob export, backup UI, app-owned scheduler or network backup client exists. The wrapping key/raw local ciphertext are excluded; authorized restore validates the logical config and wraps it under the destination installation's hardware key. Restored profiles remain inactive until reviewed.

An authorized backup plus its recovery secret can recover/clone the WG identity. Keep the original device off when restoring that identity. If the original device or backup is lost/untrusted, generate a fresh identity and revoke the old peer instead. Without a usable encrypted backup, re-enroll. Ordinary USB/MTP/non-debug ADB does not expose app-private keys; trusted debugging/admin facilities or a compromised OS are outside this ordinary-app threat model. Actual GrapheneOS transport behavior is a device test, not inferred from a successful APK build.
