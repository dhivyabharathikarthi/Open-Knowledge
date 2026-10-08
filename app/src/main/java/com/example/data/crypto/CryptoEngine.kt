package com.example.data.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {

    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AES_ALGO = "AES"
    const val GCM_TAG_LENGTH_BITS = 128
    const val GCM_NONCE_LENGTH = 12
    const val KEY_LENGTH_BYTES = 32 // 256 bits

    private val secureRandom = SecureRandom()

    fun generateRandomKey(): ByteArray {
        val key = ByteArray(KEY_LENGTH_BYTES)
        secureRandom.nextBytes(key)
        return key
    }

    fun generateNonce(): ByteArray {
        val nonce = ByteArray(GCM_NONCE_LENGTH)
        secureRandom.nextBytes(nonce)
        return nonce
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM.
     * Returns Pair(nonce, ciphertextWithAuthTag).
     */
    fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        customNonce: ByteArray? = null
    ): Pair<ByteArray, ByteArray> {
        require(key.size == KEY_LENGTH_BYTES) { "Invalid key length: ${key.size}" }
        val nonce = customNonce ?: generateNonce()
        require(nonce.size == GCM_NONCE_LENGTH) { "Invalid nonce length: ${nonce.size}" }

        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val keySpec = SecretKeySpec(key, AES_ALGO)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val ciphertext = cipher.doFinal(plaintext)
        return Pair(nonce, ciphertext)
    }

    /**
     * Decrypts ciphertext bytes with AES-256-GCM authentication.
     * Throws AEADBadTagException / GeneralSecurityException if ciphertext has been tampered with or key is wrong.
     */
    fun decrypt(
        ciphertext: ByteArray,
        key: ByteArray,
        nonce: ByteArray
    ): ByteArray {
        require(key.size == KEY_LENGTH_BYTES) { "Invalid key length: ${key.size}" }
        require(nonce.size == GCM_NONCE_LENGTH) { "Invalid nonce length: ${nonce.size}" }

        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val keySpec = SecretKeySpec(key, AES_ALGO)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        return cipher.doFinal(ciphertext)
    }
}
