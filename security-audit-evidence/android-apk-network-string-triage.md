# Bounded official APK network-string cross-check

The locally downloaded, signature-verified `v0.2.1` APK was opened as ZIP
data inside the isolated audit sandbox. This check did **not** execute the
APK. A bounded ASCII pattern scan covered both DEX files, ARM64 `libgojni.so`
and `libandroidx.graphics.path.so`; raw candidates and entry sizes are in
`android-apk-network-string-inventory.json`.

The app's second DEX contains the expected editable OpenDHT default
`https://dhtproxy2.jami.net` and its user-opened GitHub About link. The Go
library contains the configured/default Cloudflare and Google STUN host
strings, plus Go/toolchain documentation and package-path strings. The first
DEX contains Android/Compose/JetBrains documentation and issue URLs. No
additional obvious analytics, crash-upload or remote-code-loading endpoint
was identified among these string candidates.

The scan is **not** a network trace or proof of absence. Binary substrings
can join adjacent constants into false URLs (for example, a Cloudflare URL
followed by the word `opendht`), and dynamically built, compressed, encoded,
or non-ASCII endpoints can escape this pattern. The source-level
`network-and-execution-surface.md` inventory and exact DEX/source-build
comparison are stronger evidence about the Kotlin app; the Go report's AAR
section addresses the native code separately. The presence of the
Cloudflare URL in the official all-builtins Go AAR is one reason to build a
smaller OpenDHT-only fork artifact before deployment.
