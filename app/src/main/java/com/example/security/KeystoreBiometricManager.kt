package com.example.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreBiometricManager(
    private val keyAlias: String = KEY_ALIAS_DEFAULT,
    private val requireUserAuth: Boolean = true
) {

    companion object {
        const val KEY_ALIAS_DEFAULT = "OpenKnowledge_BioKey_v1"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128

        fun canAuthenticate(context: Context): Int {
            val biometricManager = BiometricManager.from(context)
            return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        }
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }

    /**
     * Checks if the biometric-bound key already exists in Android Keystore.
     */
    fun hasKey(): Boolean = keyStore.containsAlias(keyAlias)

    /**
     * Deletes the biometric key from Android Keystore.
     */
    fun deleteKey() {
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
    }

    /**
     * Generates a new hardware-backed AES-256 key in Android Keystore.
     * When user authentication is supported and enrolled, protects key usage with BIOMETRIC_STRONG.
     * If device has no enrolled biometrics, creates hardware key without user authentication flag
     * to prevent InvalidAlgorithmParameterException while preserving hardware Keystore protection.
     */
    fun generateBiometricKey(requireUserAuthForThisKey: Boolean = requireUserAuth) {
        deleteKey()
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )

        fun createSpec(withUserAuth: Boolean): KeyGenParameterSpec {
            val builder = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)

            if (withUserAuth) {
                builder.setUserAuthenticationRequired(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setUserAuthenticationParameters(
                        0, // 0 = every use of the key requires authentication
                        KeyProperties.AUTH_BIOMETRIC_STRONG
                    )
                } else {
                    @Suppress("DEPRECATION")
                    builder.setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            return builder.build()
        }

        try {
            keyGenerator.init(createSpec(requireUserAuthForThisKey))
            keyGenerator.generateKey()
        } catch (e: Exception) {
            if (requireUserAuthForThisKey) {
                // Fall back to hardware Keystore key without user authentication requirement
                deleteKey()
                keyGenerator.init(createSpec(false))
                keyGenerator.generateKey()
            } else {
                throw e
            }
        }
    }

    /**
     * Retrieves the secret key from Android Keystore.
     */
    private fun getSecretKey(): SecretKey {
        return keyStore.getKey(keyAlias, null) as? SecretKey
            ?: throw IllegalStateException("Keystore key not found for alias: $keyAlias")
    }

    /**
     * Creates and initializes a Cipher for encryption.
     * The returned cipher is wrapped in BiometricPrompt.CryptoObject.
     */
    fun createEncryptCipher(): Cipher {
        val key = getSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher
    }

    /**
     * Creates and initializes a Cipher for decryption using the provided IV.
     * The returned cipher is wrapped in BiometricPrompt.CryptoObject.
     */
    fun createDecryptCipher(iv: ByteArray): Cipher {
        val key = getSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher
    }

    /**
     * Helper to wrap a cipher into a BiometricPrompt.CryptoObject
     */
    fun getCryptoObject(cipher: Cipher): BiometricPrompt.CryptoObject {
        return BiometricPrompt.CryptoObject(cipher)
    }
}
