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
