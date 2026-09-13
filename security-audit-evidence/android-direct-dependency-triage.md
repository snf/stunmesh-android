# Android direct dependency and build-tool triage

This maps every main `app/build.gradle.kts` release dependency at source commit
`e0cc30951e24ec018423bb113acfe7849f9836d5`. The resolved release-runtime
graph contained 106 coordinates, including metadata/BOM entries and 73 cached
content-bearing AAR/JAR artifacts outside the Go AAR. All 73 matched official
Google Maven/Maven Central artifact bytes or published SHA-256 metadata
(`android-runtime-artifact-published-sha256.json`). The OSV batch query found
no reported runtime advisory (`android-maven-osv-scan.json`). Neither result
proves that an upstream publisher or build account was never compromised.

| Direct declaration | Resolved input | Purpose and audit disposition |
| --- | --- | --- |
| `dev.stunmesh:stunmesh-android` | `v1.15.1` GitHub release AAR | Go core and wireguard-go; highest-impact binary. A-03: fetched from upstream at release build without an artifact hash. Rebuild from the reviewed fork and pin its digest. |
| `androidx.compose:compose-bom` | `2026.02.01` | Version constraints, not a standalone shipped engine. Pin the complete resolved graph because declarations alone do not determine all versions. |
| `androidx.activity:activity-compose` | Declared `1.8.0`, resolved `1.8.2` | Activity/Compose integration for UI and VPN consent flow. Official Google Maven artifact matched. |
| `androidx.compose.material3:material3` | Android variant `1.4.0` | Material UI components. Official Google Maven artifact matched. |
| `androidx.compose.material:material-icons-core` | `1.7.8` | Icon support. Official Google Maven artifact matched. |
| `androidx.compose.ui:ui` | `1.10.4` | Compose rendering. Official Google Maven artifact matched. |
| `androidx.compose.ui:ui-graphics` | `1.10.4` | Compose graphics. The APK also contains AndroidX's native path library; the direct artifact matched Google Maven. |
| `androidx.compose.ui:ui-tooling-preview` | Android variant `1.10.4` | Preview annotations/tools; evaluate whether release needs the declared dependency when making a minimal fork build. |
| `androidx.core:core-ktx` | Declared `1.10.1`, resolved `1.16.0` | Android core helpers. Official Google Maven artifact matched. |
| `androidx.lifecycle:lifecycle-runtime-ktx` | Declared `2.6.1`, resolved `2.9.4` | Lifecycle/coroutine integration. Official Google Maven artifact matched. |
| `org.yaml:snakeyaml` | `2.5` | YAML import/export. Exact Maven Central JAR matched; the inspected loader rejects arbitrary Java tags and limits aliases/nesting/document code points, while duplicate keys remain allowed and the app's file read is unbounded (A-07). |

AndroidX packages account for 92 of the 106 resolved runtime
coordinates; those are not 92 independent shipped binaries. A leaner UI would
require architectural work. Removing a redundant direct declaration may not
reduce APK size because Gradle can retain it transitively; verify any proposed
reduction against a rebuilt APK and resolved graph. The practical high-value
reduction is the locally built OpenDHT-only Go AAR without executable plugins
or desktop-only imports.

Build-time dependencies are a separate boundary. The wrapper JAR matches
official Gradle 9.0/9.1 hashes but downloads checksum-pinned Gradle 9.5;
the build declares Android Gradle Plugin `9.3.1`, Kotlin Compose plugin
`2.2.10`, and Foojay toolchain resolver `1.0.0`. All 111 cached
content-bearing build-tool JARs matched their publishers at audit time
(`android-build-tool-artifact-published-sha256.json`). Seven advisory entries
were reported in six build-tool components, including a Kotlin build-cache
deserialization advisory, but none was shown exploitable in this project
(`android-maven-osv-scan.json`). Because the release workflow runs the
compiler/plugins in a job with the decoded signing keystore and write-capable
token, publisher consistency alone is insufficient. The fork should build
with a fresh controlled cache, verify the artifact hash, then sign in a
separate narrowly scoped job.
