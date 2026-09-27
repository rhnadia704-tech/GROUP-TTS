package com.example.data.model

data class ExportedAudio(
    val id: String,
    val title: String,
    val filePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val createdAt: Long,
    val lineCount: Int
)
