package com.example.data.model

data class VoiceSlot(
    val id: Int, // 1 to 5
    val name: String,
    val systemVoiceName: String = "",
    val languageTag: String = "fr-FR",
    val pitch: Float = 1.0f,
    val speechRate: Float = 1.0f,
    val colorHex: Long = 0xFF3B82F6,
    val avatarEmoji: String = "🎙️"
) {
    val tag: String
        get() = name.trim()

    companion object {
        fun defaultVoices(): List<VoiceSlot> = listOf(
            VoiceSlot(
                id = 1,
                name = "Alice",
                languageTag = "fr-FR",
                pitch = 1.1f,
                speechRate = 1.0f,
                colorHex = 0xFF3B82F6,
                avatarEmoji = "👩"
            ),
            VoiceSlot(
                id = 2,
                name = "Bob",
                languageTag = "fr-FR",
                pitch = 0.88f,
                speechRate = 0.95f,
                colorHex = 0xFF10B981,
                avatarEmoji = "👨"
            ),
            VoiceSlot(
                id = 3,
                name = "Narrateur",
                languageTag = "fr-FR",
                pitch = 1.0f,
                speechRate = 0.92f,
                colorHex = 0xFFF59E0B,
                avatarEmoji = "📖"
            ),
            VoiceSlot(
                id = 4,
                name = "Clara",
                languageTag = "fr-FR",
                pitch = 1.25f,
                speechRate = 1.05f,
                colorHex = 0xFF8B5CF6,
                avatarEmoji = "✨"
            ),
            VoiceSlot(
                id = 5,
                name = "David",
                languageTag = "fr-FR",
                pitch = 0.75f,
                speechRate = 0.9f,
                colorHex = 0xFFEC4899,
                avatarEmoji = "🎭"
            )
        )
    }
}
