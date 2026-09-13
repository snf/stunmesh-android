package dev.stunmesh.android.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Baseline behavior checks for the 2026-09-13 security audit. Synthetic data only. */
class SecurityAuditTest {
    @Test
    fun importedTunnelCanKeepDistinctStoresForDifferentPeers() {
        val yaml = """
            schema: 1
            wireguard:
              private_key: synthetic-private-key
              peers:
                - public_key: synthetic-peer-a
                - public_key: synthetic-peer-b
            stunmesh:
              plugins:
                - instance: first
                  type: builtin
                  name: opendht
                  config:
                    endpoint: https://first.example.test
                - instance: second
                  type: builtin
                  name: opendht
                  config:
                    endpoint: https://second.example.test
              peers:
                - public_key: synthetic-peer-a
                  plugin: first
                - public_key: synthetic-peer-b
                  plugin: second
        """.trimIndent()
        val tunnel = TunnelYaml.decode(yaml)
        assertEquals(listOf("first", "second"), tunnel.plugins.map { it.instance })
        assertEquals(listOf("first", "second"), tunnel.peers.map { it.plugin })
    }

    @Test
    fun importedYamlPreservesExecutablePluginTypeAcrossGoBoundary() {
        val yaml = """
            schema: 1
            wireguard:
              private_key: synthetic-private-key
            stunmesh:
              plugins:
                - instance: probe
                  type: exec
                  name: ignored
                  config:
                    command: /system/bin/sh
        """.trimIndent()
        val tunnel = TunnelYaml.decode(yaml)
        assertEquals("exec", tunnel.plugins.single().type)
        assertEquals("exec", TunnelConfig.fromJson(tunnel.toJson()).plugins.single().type)
    }

    @Test
    fun importedYamlPreservesLowOrderPeerPublicKeyAcrossGoBoundary() {
        val zeroKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        val yaml = """
            schema: 1
            wireguard:
              private_key: synthetic-private-key
              peers:
                - public_key: $zeroKey
                  allowed_ips: [10.89.0.2/32]
            stunmesh: {}
        """.trimIndent()
        val tunnel = TunnelYaml.decode(yaml)
        assertEquals(zeroKey, tunnel.peers.single().publicKey)
        assertEquals(zeroKey, TunnelConfig.fromJson(tunnel.toJson()).peers.single().publicKey)
    }

    @Test
    fun importedYamlPreservesHiddenUapiLinesInAllowedIpAcrossGoBoundary() {
        val privateKey = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA="
        val peerKey = "ISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0A="
        val rogueHex = "4142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f60"
        val yaml = """
            schema: 1
            wireguard:
              private_key: $privateKey
              peers:
                - public_key: $peerKey
                  allowed_ips: ["10.89.0.2/32\npublic_key=$rogueHex\nallowed_ip=10.89.0.3/32"]
            stunmesh: {}
        """.trimIndent()
        val tunnel = TunnelYaml.decode(yaml)
        val hidden = tunnel.peers.single().allowedIps.single()
        assertTrue(hidden.contains("\npublic_key=$rogueHex\n"))
        assertEquals(hidden, TunnelConfig.fromJson(tunnel.toJson()).peers.single().allowedIps.single())
    }

    @Test
    fun genericApiKeyIsNotRedactedFromLogExportConfig() {
        val tunnel = TunnelConfig(
            iface = InterfaceConfig(privateKey = "synthetic-private-key"),
            plugins = listOf(PluginDefinition(config = mapOf("api_key" to "AUDIT-CANARY"))),
        )
        val exported = TunnelYaml.encode(tunnel.redactSecrets())
        assertTrue("current generic-key redaction gap", exported.contains("AUDIT-CANARY"))
    }

    @Test
    fun snakeYamlDefaultRejectsArbitraryJavaObjectTag() {
        val yaml = """
            schema: 1
            wireguard:
              private_key: synthetic-private-key
            stunmesh:
              plugins: !!java.lang.ProcessBuilder []
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) { TunnelYaml.decode(yaml) }
    }

    @Test
    fun snakeYamlRejectsCollectionAliasExpansion() {
        val aliases = List(100) { "*audit_anchor" }.joinToString(", ")
        val yaml = """
            schema: 1
            anchor: &audit_anchor [one, two]
            expansion: [$aliases]
            wireguard:
              private_key: synthetic-private-key
            stunmesh: {}
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) { TunnelYaml.decode(yaml) }
    }

    @Test
    fun malformedImportErrorCanIncludePrivateKeyText() {
        val malformed = "wireguard:\n  private_key: AUDIT-CANARY: extra\n"
        val error = assertThrows(IllegalArgumentException::class.java) {
            TunnelYaml.decode(malformed)
        }
        assertTrue("parser error contains imported secret-like text", error.message.orEmpty().contains("AUDIT-CANARY"))
    }

    @Test
    fun malformedWgQuickImportErrorCanIncludePrivateKeyText() {
        val malformed = "[Interface]\nPrivateKey AUDIT-CANARY\n"
        val error = assertThrows(IllegalArgumentException::class.java) {
            WgQuickConf.decode(malformed, "synthetic")
        }
        assertTrue("parser error contains imported secret-like text", error.message.orEmpty().contains("AUDIT-CANARY"))
    }
}
