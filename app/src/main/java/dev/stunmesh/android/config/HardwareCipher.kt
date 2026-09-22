package dev.stunmesh.android.config

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Non-exportable hardware wrapping key. The WG private key is deliberately usable in this process
 * and in the authorized OS backup snapshot.
 */
internal class HardwareCipher(private val context: Context) {
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        var key = store.getKey(ALIAS, null) as? SecretKey
        if (key == null) {
            check(create) { "Hardware wrapping key unavailable" }
            key =
                if (
                    context.packageManager.hasSystemFeature(
                        PackageManager.FEATURE_STRONGBOX_KEYSTORE
                    )
                ) {
                    try {
                        generate(true)
                    } catch (_: StrongBoxUnavailableException) {
                        generate(false)
                    }
                } else generate(false)
        }
        val info =
            SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
                .getKeySpec(key, KeyInfo::class.java) as KeyInfo
        val secure =
            if (Build.VERSION.SDK_INT >= 31)
                info.securityLevel in
                    setOf(
                        KeyProperties.SECURITY_LEVEL_STRONGBOX,
                        KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,
                    )
            else {
                @Suppress("DEPRECATION") info.isInsideSecureHardware
            }
        check(secure) { "Hardware-backed key storage is required" }
        return key
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec =
            KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setIsStrongBoxBacked(strongBox)
        // Android 15+ fixes earlier unlocked-device-required defects. On older
        // supported releases credential-encrypted app storage still gates boot.
        if (Build.VERSION.SDK_INT >= 35) spec.setUnlockedDeviceRequired(true)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec.build()) }
            .generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        require(plain.size <= StrictDocument.MAX_BYTES)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(true))
        c.updateAAD(HEADER)
        check(c.iv.size == 12) { "Unsupported hardware GCM nonce" }
        return HEADER + c.iv + c.doFinal(plain)
    }

    fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size in (HEADER.size + 12 + 16)..(StrictDocument.MAX_BYTES + HEADER.size + 28))
        require(blob.copyOfRange(0, HEADER.size).contentEquals(HEADER))
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, blob, HEADER.size, 12))
        c.updateAAD(HEADER)
        return c.doFinal(blob, HEADER.size + 12, blob.size - HEADER.size - 12)
    }

    private companion object {
        const val ALIAS = "stunmesh_config_v1"
        val HEADER = byteArrayOf(0x53, 0x4e, 0x46, 1)
    }
}
