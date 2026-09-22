package dev.stunmesh.android.config

import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import org.junit.Assert.*
import org.junit.Test

/**
 * Run later on the separate debug application ID on GrapheneOS. These tests use synthetic data and
 * never replace the configuration or wrapping key.
 */
class HardwareStorageTest {
    @Test
    fun enrollmentWrapsIncludedPskAndReturnsOnlyPublicIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".debug"))
        val psk = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
        val server = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 2 })
        val text =
            """{"schema":"${Provisioning.SCHEMA}","proposal_id":"${java.util.UUID.randomUUID()}","name":"Synthetic enrollment test","address":"10.77.0.2/32","server_public_key":"$server","allowed_ips":["10.77.0.1/32"],"stun_servers":["stun.example.com:3478"],"opendht":["https://proxy.example.com"],"preshared_key":"$psk"}"""
        val enrollment = Provisioning.decode(text)
        val repository = ConfigRepository.get(context)
        repository.load()
        val before = repository.state.value as RepositoryState.Ready
        val peer = repository.enroll(enrollment.publicConfig, enrollment.presharedKey)
        try {
            val reply = Provisioning.response(peer)
            assertTrue(peer.publicKey.isNotEmpty())
            assertNotEquals(server, peer.publicKey)
            assertFalse(reply.contains(psk))
            assertFalse(reply.contains("private_key"))
            assertFalse(reply.contains("preshared_key"))
            val bytes = java.io.File(context.noBackupFilesDir, "configuration.v1.bin").readBytes()
            assertFalse(bytes.toString(Charsets.ISO_8859_1).contains(psk))
            // load() can reuse the process cache. Exercise actual persisted bytes
            // with Android's JSON implementation and the native admission path.
            val plain = HardwareCipher(context).decrypt(bytes)
            try {
                val persisted = TunnelStore.fromJson(plain.toString(Charsets.UTF_8))
                val profile = persisted.tunnels.single { it.id == peer.id }
                mobile.Mobile.validateConfig(profile.toJson())
                assertEquals(peer.publicKey, mobile.Mobile.publicKey(profile.iface.privateKey))
                assertEquals(psk, profile.peers.single().presharedKey)
            } finally {
                plain.fill(0)
                bytes.fill(0)
            }
            repository.load()
            val reloaded = repository.state.value as RepositoryState.Ready
            assertEquals(before.selectedId, reloaded.selectedId)
            assertEquals(peer, reloaded.profiles.single { it.id == peer.id })
        } finally {
            repository.remove(peer.id) // only this synthetic profile; never clear app state
        }
    }

    @Test
    fun verifiedHardwareWrappingRejectsTamperingAndNeverExportsKey() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".debug"))
        val cipher = HardwareCipher(context)
        val canary = "synthetic-wireguard-private-key-canary".toByteArray()
        val first = cipher.encrypt(canary)
        val second = cipher.encrypt(canary)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(canary, cipher.decrypt(first))
        assertFalse(first.toString(Charsets.ISO_8859_1).contains(canary.toString(Charsets.UTF_8)))
        val damaged = first.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        try {
            cipher.decrypt(damaged)
            fail("Tampered ciphertext accepted")
        } catch (_: javax.crypto.AEADBadTagException) {
            // The original blob/key must still work after a rejected write/read.
        }
        assertArrayEquals(canary, cipher.decrypt(first))
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey("stunmesh_config_v1", null)
        assertNotNull(key)
        assertNull(key.encoded)
    }
}
