package com.example.security

import com.example.data.crypto.CryptoEngine
import com.example.util.SecureMemory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import javax.crypto.Cipher

/**
 * Manages the multi-layered Vault Master Key (VMK) envelope:
 *
 * [Outer Layer: Biometric Keystore AES-GCM]
 *     ↓ (unlocked via BiometricPrompt authenticated Cipher)
 * [Inner Layer: PIN KEK derived via Argon2id AES-GCM]
 *     ↓ (unlocked via verified numeric PIN)
 * [Vault Master Key (VMK)]
 */
object VaultKeyManager {

    /**
     * Creates the complete two-factor envelope for a newly generated Vault Master Key (VMK).
     *
     * @param vmk The 32-byte Vault Master Key
     * @param pinKek The 32-byte key derived from user's PIN via Argon2id
     * @param biometricEncryptCipher An initialized Android Keystore Cipher (ENCRYPT_MODE)
     * @return Outer envelope bytes containing [outerNonceLen: 2][outerNonce][outerCiphertext]
     */
    fun createEnvelope(
        vmk: ByteArray,
        pinKek: ByteArray,
        biometricEncryptCipher: Cipher
    ): ByteArray {
        // Step 1: Wrap VMK with PIN KEK (AES-256-GCM)
        val (innerNonce, innerCiphertext) = CryptoEngine.encrypt(vmk, pinKek)

        // Pack inner bundle: [innerNonce: 12][innerCiphertext]
        val innerBaos = ByteArrayOutputStream()
        val innerDos = DataOutputStream(innerBaos)
        innerDos.write(innerNonce)
        innerDos.write(innerCiphertext)
        innerDos.flush()
        val innerBundle = innerBaos.toByteArray()

        try {
            // Step 2: Wrap inner bundle with Biometric Keystore Cipher
            val outerCiphertext = biometricEncryptCipher.doFinal(innerBundle)
            val outerNonce = biometricEncryptCipher.iv

            val outerBaos = ByteArrayOutputStream()
            val outerDos = DataOutputStream(outerBaos)
            outerDos.writeShort(outerNonce.size)
            outerDos.write(outerNonce)
            outerDos.writeInt(outerCiphertext.size)
            outerDos.write(outerCiphertext)
            outerDos.flush()

            return outerBaos.toByteArray()
        } finally {
            SecureMemory.zeroize(innerBundle)
        }
    }

    /**
     * Extracts outer envelope components (outerNonce and outerCiphertext)
     * so that the biometric decrypt cipher can be initialized with the IV.
     */
    fun parseOuterEnvelope(envelopeBytes: ByteArray): Pair<ByteArray, ByteArray> {
        val bais = ByteArrayInputStream(envelopeBytes)
        val dis = DataInputStream(bais)

        val nonceLen = dis.readShort().toInt()
        val outerNonce = ByteArray(nonceLen)
        dis.readFully(outerNonce)

        val cipherLen = dis.readInt()
        val outerCiphertext = ByteArray(cipherLen)
        dis.readFully(outerCiphertext)

        return Pair(outerNonce, outerCiphertext)
    }

    /**
     * Decrypts the outer envelope using the authenticated Biometric Cipher,
     * then decrypts the inner envelope using the PIN-derived KEK to recover the VMK.
     */
    fun unwrapVmk(
        outerCiphertext: ByteArray,
        biometricDecryptCipher: Cipher,
        pinKek: ByteArray
    ): ByteArray {
        // Step 1: Decrypt outer layer using the hardware-authenticated Biometric Cipher
        val innerBundle = biometricDecryptCipher.doFinal(outerCiphertext)
        try {
            val bais = ByteArrayInputStream(innerBundle)
            val dis = DataInputStream(bais)

            val innerNonce = ByteArray(CryptoEngine.GCM_NONCE_LENGTH)
            dis.readFully(innerNonce)

            val innerCiphertext = ByteArray(innerBundle.size - CryptoEngine.GCM_NONCE_LENGTH)
            dis.readFully(innerCiphertext)

            // Step 2: Decrypt inner layer using PIN KEK (AES-256-GCM)
            // Throws AEADBadTagException if PIN is incorrect
            return CryptoEngine.decrypt(innerCiphertext, pinKek, innerNonce)
        } finally {
            SecureMemory.zeroize(innerBundle)
        }
    }
}
