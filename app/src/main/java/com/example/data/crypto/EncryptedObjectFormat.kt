package com.example.data.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Versioned binary container format for all encrypted objects:
 * [MAGIC: 4 bytes][VERSION: 1 byte][ALGO: 1 byte][KEY_VER: 2 bytes]
 * [OBJECT_UUID: 16 bytes][NONCE: 12 bytes][PAYLOAD_LEN: 4 bytes][CIPHERTEXT: variable]
 */
data class EncryptedObject(
    val version: Byte = CURRENT_VERSION,
    val algorithmId: Byte = ALGORITHM_AES_256_GCM,
    val keyVersion: Short = 1,
    val objectId: UUID,
    val nonce: ByteArray, // 12-byte GCM IV
    val ciphertext: ByteArray // Ciphertext including 16-byte GCM auth tag
) {
    companion object {
        val MAGIC_BYTES = byteArrayOf(0x4F, 0x4B, 0x56, 0x31) // "OKV1"
        const val CURRENT_VERSION: Byte = 1
        const val ALGORITHM_AES_256_GCM: Byte = 1
        const val NONCE_LENGTH = 12
        const val HEADER_MIN_SIZE = 4 + 1 + 1 + 2 + 16 + 12 + 4 // 40 bytes

        /**
         * Serializes an EncryptedObject to its canonical binary format.
         */
        fun serialize(obj: EncryptedObject): ByteArray {
            val baos = ByteArrayOutputStream(HEADER_MIN_SIZE + obj.ciphertext.size)
            val dos = DataOutputStream(baos)

            dos.write(MAGIC_BYTES)
            dos.writeByte(obj.version.toInt())
            dos.writeByte(obj.algorithmId.toInt())
            dos.writeShort(obj.keyVersion.toInt())

            val uuidBuffer = ByteBuffer.allocate(16)
            uuidBuffer.putLong(obj.objectId.mostSignificantBits)
            uuidBuffer.putLong(obj.objectId.leastSignificantBits)
            dos.write(uuidBuffer.array())

            require(obj.nonce.size == NONCE_LENGTH) { "Invalid nonce length: ${obj.nonce.size}" }
            dos.write(obj.nonce)

            dos.writeInt(obj.ciphertext.size)
            dos.write(obj.ciphertext)
            dos.flush()

            return baos.toByteArray()
        }

        /**
         * Deserializes and validates an encrypted object from binary data.
         * Throws IllegalArgumentException on invalid magic, unsupported version, or malformed data.
         */
        fun deserialize(data: ByteArray): EncryptedObject {
            if (data.size < HEADER_MIN_SIZE) {
                throw IllegalArgumentException("Data is too small for encrypted object header: ${data.size} bytes")
            }

            val bais = ByteArrayInputStream(data)
            val dis = DataInputStream(bais)

            val magic = ByteArray(4)
            dis.readFully(magic)
            if (!magic.contentEquals(MAGIC_BYTES)) {
                throw IllegalArgumentException("Invalid magic header for encrypted object")
            }

            val version = dis.readByte()
            if (version != CURRENT_VERSION) {
                throw IllegalArgumentException("Unsupported format version: $version")
            }

            val algorithmId = dis.readByte()
            if (algorithmId != ALGORITHM_AES_256_GCM) {
                throw IllegalArgumentException("Unsupported algorithm ID: $algorithmId")
            }

            val keyVersion = dis.readShort()

            val uuidBytes = ByteArray(16)
            dis.readFully(uuidBytes)
            val uuidBuf = ByteBuffer.wrap(uuidBytes)
            val mostSig = uuidBuf.long
            val leastSig = uuidBuf.long
            val objectId = UUID(mostSig, leastSig)

            val nonce = ByteArray(NONCE_LENGTH)
            dis.readFully(nonce)

            val payloadLength = dis.readInt()
            if (payloadLength < 16) { // Must at least contain GCM 16-byte auth tag
                throw IllegalArgumentException("Invalid payload length: $payloadLength")
            }
            if (data.size - HEADER_MIN_SIZE < payloadLength) {
                throw IllegalArgumentException("Truncated ciphertext: expected $payloadLength, remaining ${data.size - HEADER_MIN_SIZE}")
            }

            val ciphertext = ByteArray(payloadLength)
            dis.readFully(ciphertext)

            return EncryptedObject(
                version = version,
                algorithmId = algorithmId,
                keyVersion = keyVersion,
                objectId = objectId,
                nonce = nonce,
                ciphertext = ciphertext
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedObject
        if (version != other.version) return false
        if (algorithmId != other.algorithmId) return false
        if (keyVersion != other.keyVersion) return false
        if (objectId != other.objectId) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + algorithmId.toInt()
        result = 31 * result + keyVersion.toInt()
        result = 31 * result + objectId.hashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}
