package com.example.security

import com.example.data.crypto.CryptoEngine
import com.example.data.crypto.EncryptedObject
import com.example.data.crypto.PhotoCipher
import com.example.data.docs.DocumentationRepository
import com.example.domain.model.PhotoMetadata
import com.example.util.Normalization
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID
import javax.crypto.AEADBadTagException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CryptoAndSecurityTest {

    // ------------------------------------------------------------------------
    // 1. Secret Phrase Normalization & Verifier Tests
    // ------------------------------------------------------------------------
    @Test
    fun testPhraseNormalization() {
        val raw1 = "   Quantum Computing Notes   "
        val raw2 = "quantum computing notes"
        val raw3 = "QUANTUM COMPUTING NOTES"

        assertEquals(
            Normalization.normalizePhrase(raw1),
            Normalization.normalizePhrase(raw2)
        )
        assertEquals(
            Normalization.normalizePhrase(raw2),
            Normalization.normalizePhrase(raw3)
        )
    }

    @Test
    fun testSecretTriggerVerification() {
        val testPhrase = "distributed consensus algorithms"
        val wrongPhrase = "distributed consensus protocol"

        val key = SecretTriggerManager.generateVerifierKey()
        assertEquals(32, key.size)

        val verifier = SecretTriggerManager.computeVerifier(
            Normalization.normalizePhrase(testPhrase),
            key
        )
        assertNotNull(verifier)
        assertEquals(32, verifier.size)

        // Matching phrase should return true
        assertTrue(
            SecretTriggerManager.matchesTrigger(testPhrase, key, verifier)
        )
        // Leading/trailing whitespace variation should still match due to normalization
        assertTrue(
            SecretTriggerManager.matchesTrigger("  $testPhrase  ", key, verifier)
        )
        // Incorrect phrase must return false
        assertFalse(
            SecretTriggerManager.matchesTrigger(wrongPhrase, key, verifier)
        )
        // Empty query must return false
        assertFalse(
            SecretTriggerManager.matchesTrigger("", key, verifier)
        )
    }

    @Test
    fun testPhraseLengthValidation() {
        // Too short (< 3 chars)
        val shortResult = SecretTriggerManager.validatePhrase("ab")
        assertTrue(shortResult.isFailure)

        // Valid length
        val validResult = SecretTriggerManager.validatePhrase("abc")
        assertTrue(validResult.isSuccess)

        // Too long (> 128 chars)
        val longPhrase = "a".repeat(129)
        val longResult = SecretTriggerManager.validatePhrase(longPhrase)
        assertTrue(longResult.isFailure)
    }

    // ------------------------------------------------------------------------
    // 2. PIN Derivation Tests (Argon2id)
    // ------------------------------------------------------------------------
    @Test
    fun testPinValidation() {
        assertTrue(PinKeyDerivation.validatePin("123456").isSuccess)
        assertTrue(PinKeyDerivation.validatePin("123456789012").isSuccess)

        // Less than 6 digits
        assertTrue(PinKeyDerivation.validatePin("12345").isFailure)
        // More than 12 digits
        assertTrue(PinKeyDerivation.validatePin("1234567890123").isFailure)
        // Non-digits
        assertTrue(PinKeyDerivation.validatePin("12345a").isFailure)
    }

    @Test
    fun testArgon2idPinDerivation() {
        val pin = "849201"
        val salt = PinKeyDerivation.generateSalt()
        assertEquals(32, salt.size)

        val key1 = PinKeyDerivation.deriveKek(pin.toCharArray(), salt)
        val key2 = PinKeyDerivation.deriveKek(pin.toCharArray(), salt)

        assertEquals(32, key1.size)
        // Deterministic derivation for identical PIN and salt
        assertArrayEquals(key1, key2)

        // Different salt must yield different key
        val differentSalt = PinKeyDerivation.generateSalt()
        val keyWithDifferentSalt = PinKeyDerivation.deriveKek(pin.toCharArray(), differentSalt)
        assertFalse(key1.contentEquals(keyWithDifferentSalt))

        // Different PIN must yield different key
        val wrongPin = "849202"
        val keyWithWrongPin = PinKeyDerivation.deriveKek(wrongPin.toCharArray(), salt)
        assertFalse(key1.contentEquals(keyWithWrongPin))
    }

    // ------------------------------------------------------------------------
    // 3. Authenticated Encryption Tests (AES-256-GCM)
    // ------------------------------------------------------------------------
    @Test
    fun testAesGcmEncryptionDecryptionRoundTrip() {
        val key = CryptoEngine.generateRandomKey()
        val plaintext = "Sensitive private photo payload bytes".toByteArray(Charsets.UTF_8)

        val (nonce, ciphertext) = CryptoEngine.encrypt(plaintext, key)
        assertEquals(12, nonce.size)
        assertTrue(ciphertext.size >= plaintext.size + 16) // Plaintext + 16-byte auth tag

        val decrypted = CryptoEngine.decrypt(ciphertext, key, nonce)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun testNonceUniqueness() {
        val nonce1 = CryptoEngine.generateNonce()
        val nonce2 = CryptoEngine.generateNonce()
        assertFalse(nonce1.contentEquals(nonce2))
    }

    @Test
    fun testTamperedCiphertextFails() {
        val key = CryptoEngine.generateRandomKey()
        val plaintext = "Tamper detection test".toByteArray(Charsets.UTF_8)
        val (nonce, ciphertext) = CryptoEngine.encrypt(plaintext, key)

        // Tamper with 1 bit of ciphertext
        val tamperedCiphertext = ciphertext.clone()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0x01).toByte()

        try {
            CryptoEngine.decrypt(tamperedCiphertext, key, nonce)
            fail("Decryption must fail when ciphertext is modified")
        } catch (_: AEADBadTagException) {
            // Expected
        } catch (_: Exception) {
            // Also acceptable if wrapped
        }
    }

    @Test
    fun testWrongKeyDecryptionFails() {
        val key1 = CryptoEngine.generateRandomKey()
        val key2 = CryptoEngine.generateRandomKey()
        val plaintext = "Confidential image data".toByteArray(Charsets.UTF_8)

        val (nonce, ciphertext) = CryptoEngine.encrypt(plaintext, key1)

        try {
            CryptoEngine.decrypt(ciphertext, key2, nonce)
            fail("Decryption must fail when decrypted with incorrect key")
        } catch (_: AEADBadTagException) {
            // Expected
        } catch (_: Exception) {
            // Also acceptable if wrapped
        }
    }

    // ------------------------------------------------------------------------
    // 4. Encrypted Object Format Binary Serialization
    // ------------------------------------------------------------------------
    @Test
    fun testEncryptedObjectFormatSerialization() {
        val id = UUID.randomUUID()
        val nonce = CryptoEngine.generateNonce()
        val dummyCiphertext = ByteArray(32) { it.toByte() }

        val obj = EncryptedObject(
            objectId = id,
            nonce = nonce,
            ciphertext = dummyCiphertext
        )

        val serialized = EncryptedObject.serialize(obj)
        val deserialized = EncryptedObject.deserialize(serialized)

        assertEquals(obj.version, deserialized.version)
        assertEquals(obj.algorithmId, deserialized.algorithmId)
        assertEquals(obj.keyVersion, deserialized.keyVersion)
        assertEquals(obj.objectId, deserialized.objectId)
        assertArrayEquals(obj.nonce, deserialized.nonce)
        assertArrayEquals(obj.ciphertext, deserialized.ciphertext)
    }

    @Test
    fun testEncryptedObjectRejectsCorruptedHeader() {
        val badData = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        try {
            EncryptedObject.deserialize(badData)
            fail("Must reject data smaller than minimum header size")
        } catch (_: IllegalArgumentException) {
            // Expected
        }

        val fakeData = ByteArray(60) // Wrong magic bytes
        try {
            EncryptedObject.deserialize(fakeData)
            fail("Must reject data with invalid magic bytes")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }

    // ------------------------------------------------------------------------
    // 5. Photo Cipher Full Pipeline (Photo, Thumbnail, Metadata)
    // ------------------------------------------------------------------------
    @Test
    fun testPhotoCipherCompleteRoundTrip() {
        val vmk = CryptoEngine.generateRandomKey()
        val photoId = UUID.randomUUID()
        val fakePhotoBytes = ByteArray(1024) { (it % 256).toByte() }
        val fakeThumbBytes = ByteArray(256) { (it % 256).toByte() }
        val metadata = PhotoMetadata(
            id = photoId.toString(),
            originalFileName = "IMG_9921.JPG",
            mimeType = "image/jpeg",
            sizeBytes = fakePhotoBytes.size.toLong(),
            width = 1920,
            height = 1080,
            importedAt = 1700000000000L,
            isFavorite = true
        )

        val bundle = PhotoCipher.encryptPhoto(
            photoId = photoId,
            photoBytes = fakePhotoBytes,
            thumbnailBytes = fakeThumbBytes,
            metadata = metadata,
            vmk = vmk
        )

        // Decrypt photo
        val decryptedPhoto = PhotoCipher.decryptPayload(
            encryptedSerializedData = bundle.encryptedPhotoData,
            wrappedKey = bundle.wrappedPhotoKey,
            keyNonce = bundle.photoKeyNonce,
            vmk = vmk
        )
        assertArrayEquals(fakePhotoBytes, decryptedPhoto)

        // Decrypt thumbnail
        val decryptedThumb = PhotoCipher.decryptPayload(
            encryptedSerializedData = bundle.encryptedThumbnailData,
            wrappedKey = bundle.wrappedPhotoKey,
            keyNonce = bundle.photoKeyNonce,
            vmk = vmk
        )
        assertArrayEquals(fakeThumbBytes, decryptedThumb)

        // Decrypt metadata
        val decryptedMeta = PhotoCipher.decryptMetadata(
            encryptedMetadata = bundle.encryptedMetadata,
            metadataNonce = bundle.metadataNonce,
            wrappedKey = bundle.wrappedPhotoKey,
            keyNonce = bundle.photoKeyNonce,
            vmk = vmk
        )
        assertEquals(metadata.id, decryptedMeta.id)
        assertEquals(metadata.originalFileName, decryptedMeta.originalFileName)
        assertEquals(metadata.mimeType, decryptedMeta.mimeType)
        assertEquals(metadata.width, decryptedMeta.width)
        assertEquals(metadata.height, decryptedMeta.height)
        assertEquals(metadata.isFavorite, decryptedMeta.isFavorite)
    }

    // ------------------------------------------------------------------------
    // 6. Documentation Repository & Search Engine
    // ------------------------------------------------------------------------
    @Test
    fun testDocumentationRepositorySearch() {
        val all = DocumentationRepository.getAllArticles()
        assertTrue(all.isNotEmpty())

        val linuxMatches = DocumentationRepository.search("kernel")
        assertTrue(linuxMatches.isNotEmpty())
        assertTrue(linuxMatches.any { it.id == "linux-kernel-arch" })

        val tlsMatches = DocumentationRepository.search("TLS handshake")
        assertTrue(tlsMatches.isNotEmpty())
        assertTrue(tlsMatches.any { it.id == "tls-handshake-protocol" })

        val emptyMatches = DocumentationRepository.search("")
        assertTrue(emptyMatches.isEmpty())
    }
}
