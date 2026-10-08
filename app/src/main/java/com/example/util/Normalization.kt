package com.example.util

import java.text.Normalizer
import java.util.Arrays

object Normalization {
    /**
     * Normalizes a search query or trigger phrase safely:
     * - Trims leading and trailing whitespace
     * - Collapses multiple whitespace sequences to a single space
     * - Applies Unicode NFKC normalization to handle all international characters and accents
     * - Converts to lower-case in a locale-independent manner for consistent matching
     */
    fun normalizePhrase(input: String): String {
        val trimmed = input.trim().replace(Regex("\\s+"), " ")
        val normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFKC)
        return normalized.lowercase(java.util.Locale.ROOT)
    }
}

object SecureMemory {
    /**
     * Overwrites sensitive byte arrays in memory with zeroes.
     */
    fun zeroize(bytes: ByteArray?) {
        if (bytes != null) {
            Arrays.fill(bytes, 0.toByte())
        }
    }

    /**
     * Overwrites sensitive char arrays (such as PIN characters) in memory with zeroes.
     */
    fun zeroize(chars: CharArray?) {
        if (chars != null) {
            Arrays.fill(chars, '0')
        }
    }
}
