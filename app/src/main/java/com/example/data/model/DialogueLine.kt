package com.example.data.model

data class DialogueLine(
    val index: Int,
    val voiceSlotId: Int, // 1 to 5
    val speakerName: String,
    val text: String,
    val rawLine: String = ""
)
