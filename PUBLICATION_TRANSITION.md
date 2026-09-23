# Publication and installation transition

The project is named **STUNMESH**. Fork-owned history has been rewritten to remove owner branding from source, filenames and commit messages. Upstream ancestry is preserved. Historical logs and reports have normalized names and are not byte-exact evidence of previous distributed artifacts. Only the current `ARTIFACT_MANIFEST.json` and release provenance describe the rebuilt downloads. Prior releases have been withdrawn; do not use historical artifact hashes as current installation instructions.

Android now uses `dev.stunmesh.local`, version 0.3.1 / code 5, and a new certificate with subject `CN=STUNMESH`. This is a **new installation**, not an update of the previous locally installed application. Configure Obtainium with the current release, enroll the new app as a new peer, and revoke the old peer only after verifying the new connection. Do not export the old private key or bypass Android sandbox/Keystore protection. Preserve the previous installation until migration is successful; Android permits only one active VPN per profile.

The server and Android core now use discovery namespace `stunmesh-hints-v2`; enrollment/reply schemas are `stunmesh-enroll-v2` and `stunmesh-peer-v1`. The Linux profile schema is `stunmesh-linux-v1`. Older discovery namespaces will not interoperate. Upgrade participating endpoints together and generate fresh enrollment with the matching provisioner. Authentication remains WireGuard's responsibility; this naming transition adds no authentication layer, network polling or battery work.

Linux deployment paths, systemd units, interface names and container names also lose the owner prefix. Published templates are for a reviewed migration or fresh installation: do not run them alongside existing instances, blindly rename active interfaces, or assume they migrate current host state. Preserve service-user ownership, UID/GID mappings and mounted configuration. No NAS, phone, firewall, NFS, Samba or live credentials were changed during this publication update.

The rebuilt application and container require physical-device and NAS acceptance tests. Earlier LAN/hotspot/hardware results concern previous builds; they are not proof that the new application identity, signer and enrollment have passed those tests. See `DEVICE_TESTS.md`.

The hosting account remains unchanged. Its name in actual GitHub repository/download URLs is an ownership identifier, not project branding. Existing clones should be archived privately and replaced with fresh clones; merging old history would reintroduce removed content. Historical commit IDs in normalized reports may no longer be reachable.

Android application identity and signing requirements: [application ID](https://developer.android.com/build/configure-app-module#set-application-id), [app signing](https://developer.android.com/studio/publish/app-signing).
