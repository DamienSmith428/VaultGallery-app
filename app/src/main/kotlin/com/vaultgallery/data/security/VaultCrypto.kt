package com.vaultgallery.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultCrypto @Inject constructor() {

    companion object {
        private const val KEY_ALIAS = "vault_gallery_master_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
    }

    private fun getOrCreateKey(): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    /**
     * Encrypts data from [inputStream] into [outputStream].
     * Prepends 12-byte IV to the output stream.
     */
    fun encrypt(inputStream: InputStream, outputStream: OutputStream) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        outputStream.use { out ->
            out.write(iv) // Prepend IV
            CipherOutputStream(out, cipher).use { cos ->
                inputStream.use { input ->
                    input.copyTo(cos)
                }
            }
        }
    }

    /**
     * Decrypts data from [inputStream] (which must start with 12-byte IV).
     * Returns a [CipherInputStream] ready to read decrypted bytes.
     */
    fun decryptStream(inputStream: InputStream): CipherInputStream {
        val iv = ByteArray(IV_LENGTH)
        inputStream.read(iv)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        return CipherInputStream(inputStream, cipher)
    }

    /**
     * Decrypts entire file into a byte array (for in-memory viewing).
     */
    fun decryptToBytes(inputStream: InputStream): ByteArray {
        return inputStream.use { input ->
            decryptStream(input).use { cipherInput ->
                cipherInput.readBytes()
            }
        }
    }

    /**
     * Wipes the keystore key. Call only on full vault reset.
     */
    fun deleteKey() {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }
}
