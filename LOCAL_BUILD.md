# Local build and artifact trust

Use sibling checkouts `stunmesh-go` and `stunmesh-android`, and a separate `stunmesh-build` directory containing `tools`, `cache`, `work`, `artifacts`, and `logs`. Override the latter with `STUNMESH_BUILD_ROOT`. Do not put owner signing material in any of these directories or a container context. Publishing is an explicit owner action using GitHub Releases; there is no automatic release uploader or in-app updater.

`scripts/sandbox.py` needs Linux, Python 3.12+, bubblewrap and the pinned toolchain below. Source builds have no host home, credentials or signing directory; tools/source are read-only, cache/output writable, and networking is off. `--network` is only for an intentional fetch of reviewed pins. `--net-admin` is only for isolated synthetic WG tests. No production interfaces/routes are visible. Gradle needs a writable project directory: `--snapshot` builds an archive of a **clean committed HEAD**, without making the original checkout writable.

## Inputs

The original publisher checksum records remain in `security-audit-evidence/build-toolchain-publisher-checksums.json`. Tool archives were restored at those exact pins, not upgraded. The final artifact manifest records outputs separately.

| Input | Pin / location under `tools` |
| --- | --- |
| Go | 1.27.1, `go` |
| Temurin JDK | 21.0.12.1+1, `jdk` |
| Gradle | 9.5.0, `gradle-9.5.0` |
| Android command-line tools | 16111833; extract verified SDK archives directly; do not invoke the mutable installer wrapper |
| Android SDK / build tools | platform 36.1 / 36.1.0, `android-sdk` |
| Android NDK | 29.0.14206865 |
| gomobile / gobind | Both from `golang.org/x/mobile` in this repository's `go.mod`; install those exact commands, never `gomobile init` / `@latest` |
| WG command-line tool | Signed upstream 1.0.20260223 archive, signature fingerprint and SHA in `build/inputs.json` |
| Image utilities | Only BusyBox and musl extracted from the digest-pinned Alpine image in `build/inputs.json` |

`go.sum` and `go mod verify` cover the Go modules. Android locks all resolved configurations in strict mode, verifies plugin/transitive artifact hashes, and requires the exact locally built AAR SHA for **every** variant. `stunmesh-android/scripts/verify-dependencies.py` checks metadata against publisher bytes/hashes; its committed evidence covers the complete resolved graph, not merely direct dependencies. Do not regenerate verification metadata to bypass a mismatch. Clean builds use the verified local caches offline.

## Test and build

Run in the Go checkout, with pinned inputs already populated:

```sh
python3 scripts/sandbox.py --net-admin -- make test
python3 scripts/sandbox.py -- make vet
python3 scripts/sandbox.py -- go mod verify
python3 scripts/sandbox.py -- make core build provision APP_VERSION="$(git rev-parse HEAD)"
sha256sum ../stunmesh-build/artifacts/stunmesh-core.aar
```

Then, from the Android checkout, supply that reviewed hash (not an unrelated upstream AAR):

```sh
python3 ../stunmesh-go/scripts/sandbox.py --repo android --snapshot -- \
  /tools/gradle-9.5.0/bin/gradle --offline --no-daemon --no-build-cache \
  --project-cache-dir /cache/gradle-project-final \
  -PbuildRoot=/work/android-final \
  -PcoreAar=/artifacts/stunmesh-core.aar -PcoreAarSha256=REVIEWED_SHA256 \
  :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleRelease \
  :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest
```

`assembleRelease` intentionally emits **unsigned** `stunmesh-release-unsigned.apk`. Supported ABIs are ARM64 and x86-64; minimum Android 9/API 28. Build configuration never sees signing material. An Android device is required to execute instrumented hardware/OS tests; merely building their test APK is not a pass.

## Separate owner signing

In the Android checkout, run `scripts/sign-release.py --help`. Pass the unsigned APK's SHA, tool directory, an owner-private key directory outside source/build, and a new output filename. First use additionally requires `--initialize-key`; subsequent releases must reuse the existing key and increment Android's versionCode. The script runs only the pinned JDK/keytool and Android apksigner inside a separate offline namespace: no Gradle, plugins, source or build cache. It verifies the resulting APK signature. This API-28+ release uses v3; apksigner omits the redundant v2 block for this minimum SDK.

Keep `owner-release.p12` and `password.txt` together in an encrypted offline backup accessible only to the owner. Both are necessary to issue compatible updates; never commit, publish, or copy them to NAS service containers. A malicious signer/compiler remains a supply-chain risk: hash verification establishes artifact identity, not proof of benign code. This local owner certificate differs from upstream; application ID `dev.stunmesh.local` prevents accidental replacement.

## Minimal rootless image

Build static `wg` from the signature-verified archive (`make -C src LDFLAGS=-static` in the offline build sandbox); copy the output to `artifacts/wg`. Retrieve the source utilities using Skopeo's digest-qualified pull and the restricted policy:

```sh
skopeo --policy build/alpine-policy.json copy \
  docker://docker.io/library/alpine@sha256:1beb0dc0a51de7ff38e3b5274078a2e0b81113ba5c7535e1a03d5913a5edbda3 \
  oci:../stunmesh-build/artifacts/alpine-base:pinned
python3 scripts/prepare-image.py --artifacts ../stunmesh-build/artifacts \
  --context ../stunmesh-build/work/image-context-final
podman build --network=none --timestamp=0 \
  -t localhost/stunmesh:local ../stunmesh-build/work/image-context-final
podman save --format oci-archive -o ../stunmesh-build/artifacts/stunmesh-linux-amd64.oci.tar \
  localhost/stunmesh:local
```

The context builder verifies every OCI blob and both retained utility hashes. Seven allowlisted context files produce five regular image files; no source tree, Git, production config, keys, provisioner, compiler or package manager is copied. The Dockerfile executes **no RUN commands**. Local validation used rootless Buildah 1.39.3 to assemble this standard OCI image because Podman is not installed in the audit workspace; the exact image rootfs was smoke-tested in an isolated user/network namespace with only `NET_ADMIN`. Actual Podman service-user mapping is a later NAS test, not inferred from that smoke test. See `deploy/README.md`.

The stronger image integration gate uses real kernel WG, the daemon/proxy, the mobile shared-socket WG bind and a synthetic HTTPS discovery proxy. It tests authorized bidirectional traffic plus unknown keys, incorrect PSKs and unauthorized tunnel source addresses. Every interface/route and test CA stays in the disposable namespace:

```sh
python3 scripts/sandbox.py -- env CGO_ENABLED=0 go test -c \
  -tags 'mobile security_audit' -o /artifacts/kernel-integration.test ./test/e2e/kernel
python3 scripts/test-image.py \
  --image ../stunmesh-build/artifacts/stunmesh-linux-amd64.oci.tar \
  --test-binary ../stunmesh-build/artifacts/kernel-integration.test
```

This is not a carrier/NAT test and does not mount production configuration or change the host firewall. The fake-TUN helper's packet checksums are repaired for the kernel stack; a mock byte-for-byte echo alone would not test real IP delivery.

## Verification limits

The final manifest associates source commits, tools, local AAR, APK, certificate and OCI digests. Offline builds and signatures passed locally; this is **not** a claim that two independent builds are bit-for-bit reproducible. ZIP metadata, native toolchains, build paths and signing affect bytes. Publisher verification and advisory triage do not prove absence of hidden backdoors. Actual NAT, hardware key storage, GrapheneOS backup, background behavior and battery measurements are gated by `DEVICE_TESTS.md`.
