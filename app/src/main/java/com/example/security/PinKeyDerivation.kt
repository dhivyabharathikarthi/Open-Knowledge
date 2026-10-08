package com.example.security

import com.example.util.SecureMemory
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom

object PinKeyDerivation {

    const val MIN_PIN_LENGTH = 6
    const val MAX_PIN_LENGTH = 12
    const val KEY_LENGTH_BYTES = 32 // 256 bits for AES-256
    const val SALT_LENGTH_BYTES = 32

    // Memory-hard parameters suitable for modern Android mobile devices
    private const val MEMORY_KIB = 32768 // 32 MB
    private const val ITERATIONS = 3
    private const val PARALLELISM = 1

    /**
     * Validates PIN criteria (numeric digits only, length between 6 and 12).
     */
    fun validatePin(pin: String): Result<Unit> {
        return when {
            pin.length < MIN_PIN_LENGTH -> {
                Result.failure(IllegalArgumentException("PIN must be at least $MIN_PIN_LENGTH digits"))
            }
            pin.length > MAX_PIN_LENGTH -> {
                Result.failure(IllegalArgumentException("PIN must be at most $MAX_PIN_LENGTH digits"))
            }
            !pin.all { it.isDigit() } -> {
                Result.failure(IllegalArgumentException("PIN must contain only numeric digits"))
            }
            else -> Result.success(Unit)
        }
    }

    /**
     * Generates a fresh random 32-byte salt for PIN derivation.
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        return salt
    }

    /**
     * Derives a 256-bit Key Encryption Key (KEK) from a numeric PIN using Argon2id.
     * The input PIN bytes and intermediate buffers are carefully managed.
     */
    fun deriveKek(pinChars: CharArray, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(ITERATIONS)
            .withMemoryAsKB(MEMORY_KIB)
            .withParallelism(PARALLELISM)
            .withSalt(salt)
            .build()

        val generator = Argon2BytesGenerator()
        generator.init(params)

        val resultKey = ByteArray(KEY_LENGTH_BYTES)
        // Convert char array to UTF-8 bytes securely without creating String in memory
        val pinBytes = ByteArray(pinChars.size)
        try {
            for (i in pinChars.indices) {
                pinBytes[i] = pinChars[i].code.toByte()
            }
            generator.generateBytes(pinBytes, resultKey, 0, resultKey.size)
            return resultKey
        } finally {
            SecureMemory.zeroize(pinBytes)
        }
    }
}
