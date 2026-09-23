> **Historical evidence:** The device results below concern earlier builds. Names were normalized during history cleanup. Current build/checksum evidence is in [ARTIFACT_MANIFEST.json](ARTIFACT_MANIFEST.json); the rebuilt release still requires the physical-device migration checks in [PUBLICATION_TRANSITION.md](PUBLICATION_TRANSITION.md).

# Device and service validation summary

Identifying device captures and deployment records are retained privately and removed from public Git history. Addresses and paths elsewhere in this repository are generic examples, not a live inventory.

| Check | Result and limit |
| --- | --- |
| Android release | Version `0.3.0-local.4`, non-debuggable, owner-signed; installed bytes matched the release checksum. |
| Protected configuration | Hardware-backed wrapping, tamper rejection, non-exportable wrapping key and actual encrypted-disk parsing passed on Android 13. |
| Split routing | Selected server destinations passed a separate-app 32 KiB transfer; ordinary HTTPS retained the underlay internet path, with no VPN default route or DNS override. |
| External connection | One mobile-data hotspot path authenticated through WireGuard and transferred traffic. Return to home Wi-Fi recovered in approximately 10 seconds, as estimated by the tester. No universal NAT or instant-roaming guarantee. |
| Go authentication and image | Native userspace/kernel WireGuard positive and negative authorization checks passed. OCI rootfs contains exactly five reviewed regular files. |
| Linux helpers and services | Namespace tests cover direct routes, interface ownership and cleanup. Disposable service tests cover two-way synchronization and encrypted local backup/restore; these are not cloud-backup or completed production-acceptance claims. |
| Outstanding | Encrypted Android OS restore, IPv6, reboot/Doze, long-running service integration, reliability and measured battery use. |

The original VPN was restored after interactive testing and temporary wireless debugging was retired. No phone interaction or production service change is part of publication. See [DEVICE_TESTS.md](DEVICE_TESTS.md) for the remaining test plan and [RELEASES.md](RELEASES.md) for artifacts.
