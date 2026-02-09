package com.example.opsisfacerecognition.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class DatabasePassphraseProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // SharedPreferences where the encrypted passphrase is stored
        private const val PREFS_NAME = "encrypted_db_prefs"
        private const val PREF_KEY_ENCRYPTED_PASSPHRASE = "encrypted_passphrase_b64"
        private const val PREF_KEY_ENCRYPTED_PASSPHRASE_IV = "encrypted_passphrase_iv_b64"

        // Android Keystore configuration
        // Where to store and how we will obtain it
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "opsis_db_passphrase_key"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"

        private const val KEY_SIZE_BITS = 256
        private const val PASSPHRASE_SIZE_BYTES = 32
        private const val GCM_TAG_BITS = 128
    }

    data class PassphraseResult(
        val passphrase: ByteArray,
        val isNewlyCreated: Boolean
    ) {
        // ByteArray uses reference equality by default
        // We override so tests/debugging compare passphrase content
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PassphraseResult

            if (isNewlyCreated != other.isNewlyCreated) return false
            if (!passphrase.contentEquals(other.passphrase)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = isNewlyCreated.hashCode()
            result = 31 * result + passphrase.contentHashCode()
            return result
        }
    }

    // Returns the existing database passphrase or creates a new one if missing
    // Οr if decryption fails (e.g. Keystore was cleared).
    fun getOrCreate(): PassphraseResult {
        // Read from SharedPreferences the encrypted passphrase and IV
        val prefs = getPrefs()
        val encryptedPassphraseB64 = prefs.getString(PREF_KEY_ENCRYPTED_PASSPHRASE, null)
        val ivB64 = prefs.getString(PREF_KEY_ENCRYPTED_PASSPHRASE_IV, null)

        // If both found try decrypting
        if (encryptedPassphraseB64 != null && ivB64 != null) {
            // If it's okay we will get the result
            return try {
                PassphraseResult(
                    passphrase = decryptPassphrase(encryptedPassphraseB64, ivB64),
                    isNewlyCreated = false
                )
            } catch (_: Exception) {
                // A scenario would be for the keystore/key to change value or got lost but the shared pref didn't get lost
                // Clear the prefs
                prefs.edit {
                    remove(PREF_KEY_ENCRYPTED_PASSPHRASE)
                        .remove(PREF_KEY_ENCRYPTED_PASSPHRASE_IV)
                }

                // Create new passphrase
                createAndPersistPassphrase(prefs)
            }
        }
        // If there is no passphrase or IV create a new one
        return createAndPersistPassphrase(prefs)
    }

    // Generates a new random passphrase and stores it encrypted
    private fun createAndPersistPassphrase(prefs: SharedPreferences): PassphraseResult {
        // Create a new random passphrase
        val passphrase = ByteArray(PASSPHRASE_SIZE_BYTES).also { SecureRandom().nextBytes(it) }

        // Encrypt it with AES/GCM key which lives in Android Keystore
        val encryptedPayload = encryptPassphrase(passphrase)

        // We save
        prefs.edit {
            putString(PREF_KEY_ENCRYPTED_PASSPHRASE, encryptedPayload.ciphertextB64)
                .putString(PREF_KEY_ENCRYPTED_PASSPHRASE_IV, encryptedPayload.ivB64)
        }

        return PassphraseResult(passphrase = passphrase, isNewlyCreated = true)
    }

    // Encrypts the passphrase using an AES key stored in Android Keystore
    private fun encryptPassphrase(passphrase: ByteArray): EncryptedPayload {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        // This key never leaves the Android Keystore
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())

        val encrypted = cipher.doFinal(passphrase)
        val encryptedB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        val ivB64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)

        return EncryptedPayload(ciphertextB64 = encryptedB64, ivB64 = ivB64)
    }

    // Decrypts the stored passphrase using the Keystore key
    private fun decryptPassphrase(encryptedPassphraseB64: String, ivB64: String): ByteArray {
        val encrypted = Base64.decode(encryptedPassphraseB64, Base64.NO_WRAP)
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )

        return cipher.doFinal(encrypted)
    }

    // Retrieves an existing AES key from Android Keystore or generates one if missing

    private fun getOrCreateSecretKey(): SecretKey {
        // Load AndroidKeyStore and look for an existing key by alias
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        // First-time setup: create a 256-bit AES/GCM key dedicated to DB passphrase encryption
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .build()

        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    // Helper for reading/writing encrypted passphrase metadata.
    private fun getPrefs(): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Encrypted passphrase payload. IV is required for AES/GCM decryption.
    private data class EncryptedPayload(
        val ciphertextB64: String,
        val ivB64: String
    )
}
