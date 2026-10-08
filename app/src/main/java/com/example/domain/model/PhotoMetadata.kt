package com.example.domain.model

data class PhotoMetadata(
    val id: String,
    val originalFileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val importedAt: Long,
    val isFavorite: Boolean = false
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"id\":").append(escape(id)).append(",")
            append("\"originalFileName\":").append(escape(originalFileName)).append(",")
            append("\"mimeType\":").append(escape(mimeType)).append(",")
            append("\"sizeBytes\":").append(sizeBytes).append(",")
            append("\"width\":").append(width).append(",")
            append("\"height\":").append(height).append(",")
            append("\"importedAt\":").append(importedAt).append(",")
            append("\"isFavorite\":").append(isFavorite)
            append("}")
        }
    }

    companion object {
        private fun escape(s: String): String {
            val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"")
            return "\"$escaped\""
        }

        fun fromJson(jsonStr: String): PhotoMetadata {
            fun extractString(key: String, default: String = ""): String {
                val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
                return pattern.find(jsonStr)?.groupValues?.get(1) ?: default
            }

            fun extractLong(key: String, default: Long = 0L): Long {
                val pattern = Regex("\"$key\"\\s*:\\s*(\\d+)")
                return pattern.find(jsonStr)?.groupValues?.get(1)?.toLongOrNull() ?: default
            }

            fun extractInt(key: String, default: Int = 0): Int {
                val pattern = Regex("\"$key\"\\s*:\\s*(\\d+)")
                return pattern.find(jsonStr)?.groupValues?.get(1)?.toIntOrNull() ?: default
            }

            fun extractBoolean(key: String, default: Boolean = false): Boolean {
                val pattern = Regex("\"$key\"\\s*:\\s*(true|false)")
                return pattern.find(jsonStr)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: default
            }

            return PhotoMetadata(
                id = extractString("id"),
                originalFileName = extractString("originalFileName", "photo.jpg"),
                mimeType = extractString("mimeType", "image/jpeg"),
                sizeBytes = extractLong("sizeBytes", 0L),
                width = extractInt("width", 0),
                height = extractInt("height", 0),
                importedAt = extractLong("importedAt", System.currentTimeMillis()),
                isFavorite = extractBoolean("isFavorite", false)
            )
        }
    }
}
