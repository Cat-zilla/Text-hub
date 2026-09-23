package com.texthub.app.keys

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.texthub.core.keys.VaultCipher
import com.texthub.core.keys.VaultStorage
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The device half of the saved-key vault: an **Android Keystore** cipher and the app's private
 * file.
 *
 * The AES-256-GCM key is generated *inside* the Android Keystore and is not extractable - not to
 * this app, not to a backup, not to another installation. The sealed records on disk can only be
 * opened by this app on this device with this key. Uninstalling the app destroys the key and with
 * it every saved private key; that is the trade-off of keeping the material device-bound, and it
 * is stated in the documentation.
 *
 * Everything the collection *does* with these two objects is tested on the JVM against the
 * [VaultCipher]/[VaultStorage] interfaces (`RsaKeyVaultTest`); the Keystore itself is platform
 * behaviour that needs a device to exercise.
 */
object AndroidRsaKeyVault {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "texthub_rsa_vault_key"
    private const val FILE_NAME = "saved_rsa_keys.vault"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    /**
     * The cipher over the vault's records. `unseal` returns null-shaped failures as exceptions so
     * the collection can tell a wrong key from damaged data; both surface as a friendly message.
     */
    class KeystoreCipher : VaultCipher {

        private fun key(): SecretKey {
            val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return generator.generateKey()
        }

        override fun seal(plain: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val sealed = cipher.doFinal(plain)
            return cipher.iv + sealed
        }

        override fun unseal(blob: ByteArray): ByteArray {
            if (blob.size <= IV_LEN) throw GeneralSecurityException("sealed record is too short")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob, 0, IV_LEN))
            return cipher.doFinal(blob, IV_LEN, blob.size - IV_LEN)
        }
    }

    /** The sealed collection file in the app's private storage - not a preference. */
    class FileStorage(context: Context) : VaultStorage {
        private val file = java.io.File(context.filesDir, FILE_NAME)

        override fun read(): ByteArray? = if (file.isFile) file.readBytes() else null

        override fun write(bytes: ByteArray) {
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
    }
}
