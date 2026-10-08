package com.example.domain.model

data class DocArticle(
    val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val content: String,
    val tags: List<String>,
    val readingTimeMinutes: Int
)
