package dev.stunmesh.android.config

import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

class RemediationTest {
    private val publicKey = Base64.getEncoder().encodeToString(ByteArray(32) { 2 })
    private val privateKey = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })

    private fun tunnel() =
        TunnelConfig(
            iface = InterfaceConfig(privateKey = privateKey, addresses = listOf("10.89.0.2/32")),
            peers =
                listOf(
                    PeerConfig(
                        publicKey = publicKey,
                        allowedIps = listOf("10.89.0.1/32"),
                        endpoint = "198.51.100.1:51820",
                    )
                ),
        )

    private fun reject(work: () -> Any?) {
        try {
            work()
            fail("Unsafe input accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun sharedPublicProposalMatchesGoTool() {
        val text =
            javaClass.getResourceAsStream("/enrollment-public.json")!!.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        val p = Provisioning.decode(text)
        assertTrue(p.presharedKey.isEmpty())
        assertEquals("10.77.0.1/32", p.publicConfig.peers.single().allowedIps.single())
        assertTrue(p.publicConfig.iface.privateKey.isEmpty())
    }

    @Test
    fun narrowDestinationRoutesOnly() {
        val t = tunnel()
        ConfigPolicy.validate(t)
        for (route in
            listOf(
                "0.0.0.0/0",
                "::/0",
                "128.0.0.0/1",
                "10.0.0.0/8",
                "10.89.0.1/32\npublic_key=rogue",
                "example.com/32",
                "127.0.0.1/32",
                "[fe80::1%wlan0]/128",
            )) reject {
            ConfigPolicy.validate(
                t.copy(peers = t.peers.map { it.copy(allowedIps = listOf(route)) })
            )
        }
        assertEquals("10.89.0.0/24", ConfigPolicy.cidr("10.89.0.7/24", true).text)
        reject {
            ConfigPolicy.validate(t.copy(iface = t.iface.copy(dnsServers = listOf("1.1.1.1"))))
        }
        for (endpoint in
            listOf(
                "198.51.100.1:65537",
                "127.0.0.1:80",
                "198.51.100.1:9\nremove=true",
                "example.com:9",
            )) reject { ConfigPolicy.endpoint(endpoint) }
    }

    @Test
    fun strictDocumentsRejectAmbiguityAndUnboundedInput() {
        for (text in
            listOf(
                "name: a\nname: b",
                "{\"name\":\"a\",\"NAME\":\"b\"}",
                "name: null",
                "? [a,b]\n: value",
                "name: &x abc\nother: *x",
                "name: !!java.net.URL https://example.com",
                "name: a\n---\nname: b",
                "x: " + "[".repeat(30) + "0" + "]".repeat(30),
                "x".repeat(StrictDocument.MAX_BYTES + 1),
            )) reject { StrictDocument.parse(text) }
        reject {
            TunnelConfig.fromJson(tunnel().toJson().dropLast(1) + ",\"shell\":\"secret-canary\"}")
        }
    }

    @Test
    fun invalidIntervalsAndKeysAreRejected() {
        val t = tunnel()
        for (n in listOf(-1, 0, 1, 59, 241, Int.MAX_VALUE)) reject {
            ConfigPolicy.validate(t.copy(refreshIntervalSeconds = n))
        }
        for (port in listOf(-1, 65536)) reject {
            ConfigPolicy.validate(t.copy(iface = t.iface.copy(listenPort = port)))
        }
        for (key in
            listOf(
                "A".repeat(44),
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                publicKey + "\n",
            )) reject { ConfigPolicy.key(key) }
    }

    @Test
    fun httpsOriginsAndOpenDhtOnly() {
        for (origin in
            listOf(
                "http://example.com",
                "https://user:secret@example.com",
                "https://example.com/a",
                "https://example.com?token=secret",
                "https://example.com:65536",
            )) reject { ConfigPolicy.origin(origin) }
        ConfigPolicy.origin("https://example.com")
        val t = tunnel()
        for (kind in listOf("exec", "shell")) reject {
            ConfigPolicy.validate(
                t.copy(
                    plugins =
                        listOf(
                            PluginDefinition(
                                type = kind,
                                config = mapOf("endpoint" to "https://example.com"),
                            )
                        )
                )
            )
        }
        reject {
            ConfigPolicy.validate(t.copy(plugins = listOf(PluginDefinition(name = "cloudflare"))))
        }
        reject {
            ConfigPolicy.validate(
                t.copy(
                    plugins =
                        listOf(
                            PluginDefinition(
                                config = mapOf("endpoint" to "https://example.com", "dedup" to true)
                            )
                        )
                )
            )
        }
    }

    @Test
    fun publicWgQuickRejectsSecretsDuplicatesAndScripts() {
        val public =
            """[Interface]
Address = 10.89.0.2/32
[Peer]
PublicKey = $publicKey
AllowedIPs = 10.89.0.1/32
AllowedIPs = 192.168.0.5/32
Endpoint = 198.51.100.1:51820
"""
        val parsed = WgQuickConf.decode(public, "Server")
        assertEquals(2, parsed.peers.single().allowedIps.size)
        assertEquals("", parsed.iface.privateKey)
        for (extra in
            listOf(
                "PrivateKey = $privateKey\n",
                "PostUp = echo secret\n",
                "MTU = 1420\nMTU = 1280\n",
            )) reject {
            WgQuickConf.decode(public.replace("Address =", extra + "Address ="), "Server")
        }
        reject { WgQuickConf.decode(public + "PublicKey = $publicKey\n", "Server") }
        reject { WgQuickConf.decode(public + "PresharedKey = $privateKey\n", "Server") }
    }

    @Test
    fun yamlMultiStorePreservesFieldsAndHasNoExport() {
        val other = Base64.getEncoder().encodeToString(ByteArray(32) { 3 })
        val yaml =
            """schema: 1
name: Server
wireguard:
  addresses: [10.89.0.2/32]
  peers:
    - public_key: '$publicKey'
      allowed_ips: [10.89.0.1/32]
    - public_key: '$other'
      allowed_ips: [10.89.0.3/32]
stunmesh:
  plugins:
    - instance: first
      type: builtin
      name: opendht
      config: {endpoint: 'https://one.example.com'}
    - instance: second
      type: builtin
      name: opendht
      config: {endpoint: 'https://two.example.com'}
  log: {level: error}
  peers:
    - {public_key: '$publicKey', plugin: first}
    - {public_key: '$other', plugin: second}
"""
        val parsed = TunnelYaml.decode(yaml)
        val renamed = parsed.copy(name = "Renamed")
        assertEquals(parsed.plugins, renamed.plugins)
        assertEquals(parsed.peers, renamed.peers)
        assertEquals("error", renamed.logLevel)
        reject {
            TunnelYaml.decode(
                yaml.replace("addresses:", "private_key: '$privateKey'\n  addresses:")
            )
        }
    }

    @Test
    fun enrollmentMayCarryPskButNeverPhoneIdentity() {
        val text =
            javaClass.getResourceAsStream("/enrollment-with-psk.json")!!.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        val psk = Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
        val proposal = Provisioning.decode(text)
        assertEquals(psk, proposal.presharedKey)
        assertEquals("", proposal.publicConfig.iface.privateKey)
        assertTrue(proposal.publicConfig.peers.all { it.presharedKey.isEmpty() })
        assertFalse(proposal.toString().contains(psk))
        for (field in listOf("private_key", "encrypted_key", "command", "psk_required")) reject {
            Provisioning.decode(text.trim().dropLast(1) + ",\"$field\":\"$privateKey\"}")
        }
        for (bad in
            listOf(
                "",
                "canary",
                Base64.getEncoder().encodeToString(ByteArray(32)),
                psk + "\\n",
            )) reject { Provisioning.decode(text.replace(psk, bad)) }
        for (bad in listOf("null", "true", "[]")) reject {
            Provisioning.decode(text.replace("\"$psk\"", bad))
        }
        reject { Provisioning.decode(text.replace("10.77.0.1/32", "0.0.0.0/0")) }
        reject { Provisioning.decode(text.replace(Provisioning.SCHEMA, "stunmesh-enroll-v1")) }
        val reply =
            Provisioning.response(
                PublicTunnel(
                    "id",
                    "Server",
                    publicKey,
                    listOf(publicKey),
                    listOf("10.77.0.2/32"),
                    listOf("10.77.0.1/32"),
                    "proposal",
                )
            )
        assertFalse(reply.contains(psk))
        assertFalse(reply.contains("preshared_key"))
        assertFalse(reply.contains("private_key"))
    }

    @Test
    fun androidJsonSolidusEscapesRoundTripWithoutChangingLiteralBackslashes() {
        val t = tunnel().copy(name = "Folder \\ / name")
        val store = TunnelStore(listOf(t), t.id)
        // Android's writer escapes all slashes; the JVM JSON implementation does not.
        val androidJson = store.toJson().replace("/", "\\/")
        assertEquals(store, TunnelStore.fromJson(androidJson))
        val escaped =
            StrictDocument.parse("""{"path":"a\\/b", "literal":"a\\\\/b", "odd":"a\\\\\\/b"}""")
        assertEquals("a/b", escaped.text("path"))
        assertEquals("a\\/b", escaped.text("literal"))
        assertEquals("a\\/b", escaped.text("odd"))
        reject { StrictDocument.parse("""{"a/b":1,"a\\/b":2}""") }
        reject { StrictDocument.parse("""{"x":"\\q"}""") }
    }

    @Test
    fun storeRestoreVersionAndInactiveReview() {
        val t = tunnel()
        val stored = TunnelStore(listOf(t), t.id)
        assertEquals(stored, TunnelStore.fromJson(stored.toJson()))
        val restored = TunnelStore.fromJson(stored.toJson()).copy(activeId = "")
        assertEquals(privateKey, restored.tunnels.single().iface.privateKey)
        assertNull(restored.active)
        reject { TunnelStore.fromJson(stored.toJson().replace("\"schema\":1", "\"schema\":2")) }
        reject { TunnelStore.fromJson(stored.toJson().replace("10.89.0.1/32", "0.0.0.0/0")) }
        assertFalse(t.toString().contains(privateKey))
        assertFalse(t.iface.toString().contains(privateKey))
        assertFalse(stored.toString().contains(privateKey))
    }

    @Test
    fun failedWritesAndUnreadableDataNeverBecomeEmptyStore() {
        val t = tunnel()
        val old = TunnelStore(listOf(t))
        var disk = old
        var failWrite = true
        val c =
            StoreCoordinator(
                { disk },
                {
                    if (failWrite) throw java.io.IOException()
                    disk = it
                },
            )
        try {
            c.update { it.copy(activeId = t.id) }
            fail()
        } catch (_: java.io.IOException) {}
        assertEquals(old, c.snapshot())
        assertEquals(old, disk)
        failWrite = false
        c.update { it.copy(activeId = t.id) }
        assertEquals(t.id, disk.activeId)
        var writes = 0
        val corrupt = StoreCoordinator({ throw java.io.IOException() }, { writes++ })
        repeat(2) {
            try {
                corrupt.update { TunnelStore() }
                fail()
            } catch (_: java.io.IOException) {}
        }
        assertEquals(0, writes)
    }

    @Test
    fun serializedChangesPreserveSelectionAndUndisplayedFields() {
        val t = tunnel()
        var disk = TunnelStore(listOf(t))
        val c = StoreCoordinator({ disk }, { disk = it })
        val gate = CountDownLatch(1)
        val done = CountDownLatch(2)
        val workers = Executors.newFixedThreadPool(2)
        workers.execute {
            gate.await()
            c.update { it.copy(activeId = t.id) }
            done.countDown()
        }
        workers.execute {
            gate.await()
            c.update { old -> old.copy(tunnels = old.tunnels.map { it.copy(name = "Renamed") }) }
            done.countDown()
        }
        gate.countDown()
        assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS))
        workers.shutdownNow()
        assertEquals(t.id, c.snapshot().activeId)
        assertEquals("Renamed", c.snapshot().tunnels.single().name)
        assertEquals(t.peers, c.snapshot().tunnels.single().peers)
    }
}
