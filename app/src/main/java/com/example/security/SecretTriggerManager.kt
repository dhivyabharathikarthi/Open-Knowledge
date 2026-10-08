package com.example.security

import com.example.util.Normalization
import com.example.util.SecureMemory
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SecretTriggerManager {

    private const val HMAC_ALGORITHM = "HmacSHA256"
    const val MIN_PHRASE_LENGTH = 3
    const val MAX_PHRASE_LENGTH = 128

    /**
     * Validates phrase length requirements.
     * Accepts any letters, numbers, symbols, spaces, or international Unicode characters.
     */
    fun validatePhrase(rawPhrase: String): Result<String> {
        val normalized = Normalization.normalizePhrase(rawPhrase)
        return when {
            normalized.length < MIN_PHRASE_LENGTH -> {
                Result.failure(IllegalArgumentException("Phrase must be at least $MIN_PHRASE_LENGTH characters"))
            }
            normalized.length > MAX_PHRASE_LENGTH -> {
                Result.failure(IllegalArgumentException("Phrase must be at most $MAX_PHRASE_LENGTH characters"))
            }
            else -> Result.success(normalized)
        }
    }

    /**
     * Generates a new random 32-byte verifier key for HMAC calculation.
     */
    fun generateVerifierKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        return key
    }

    /**
     * Computes the HMAC-SHA256 verifier for the normalized phrase.
     * The raw phrase is never stored or returned.
     */
    fun computeVerifier(normalizedPhrase: String, verifierKey: ByteArray): ByteArray {
        val phraseBytes = normalizedPhrase.toByteArray(Charsets.UTF_8)
        return try {
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            val keySpec = SecretKeySpec(verifierKey, HMAC_ALGORITHM)
            mac.init(keySpec)
            mac.doFinal(phraseBytes)
        } finally {
            SecureMemory.zeroize(phraseBytes)
        }
    }

    /**
     * Verifies whether the entered query matches the configured trigger in constant time.
     */
    fun matchesTrigger(
        enteredQuery: String,
        verifierKey: ByteArray,
        storedVerifier: ByteArray
    ): Boolean {
        if (enteredQuery.isBlank()) return false
        val normalized = Normalization.normalizePhrase(enteredQuery)
        val computed = computeVerifier(normalized, verifierKey)
        return MessageDigest.isEqual(computed, storedVerifier)
    }
}
