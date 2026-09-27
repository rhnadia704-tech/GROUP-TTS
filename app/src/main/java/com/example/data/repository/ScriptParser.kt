package com.example.data.repository

import com.example.data.model.DialogueLine
import com.example.data.model.VoiceSlot

object ScriptParser {

    /**
     * Parses a script into a list of [DialogueLine] matching against the given [voices].
     * Supports formats like:
     * - "Alice: Hello there"
     * - "[Voice 1] Let's begin"
     * - "[Bob]: Sure thing"
     * - "Narrateur - Il était une fois"
     * - Alternating lines if no speaker tag is found.
     */
    fun parse(rawScript: String, voices: List<VoiceSlot>): List<DialogueLine> {
        val lines = rawScript.lines()
        val result = mutableListOf<DialogueLine>()
        var alternatingSlotIndex = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) {
                continue
            }

            val parsed = extractSpeakerAndText(trimmed, voices)
            if (parsed != null) {
                result.add(
                    DialogueLine(
                        index = result.size,
                        voiceSlotId = parsed.first.id,
                        speakerName = parsed.first.name,
                        text = parsed.second,
                        rawLine = trimmed
                    )
                )
            } else {
                // No recognized speaker tag: assign to next alternating voice slot among active voices
                val fallbackVoice = if (voices.isNotEmpty()) {
                    voices[alternatingSlotIndex % voices.size]
                } else {
                    VoiceSlot(1, "Voix 1")
                }
                alternatingSlotIndex++

                result.add(
                    DialogueLine(
                        index = result.size,
                        voiceSlotId = fallbackVoice.id,
                        speakerName = fallbackVoice.name,
                        text = trimmed,
                        rawLine = trimmed
                    )
                )
            }
        }

        return result
    }

    private fun extractSpeakerAndText(
        line: String,
        voices: List<VoiceSlot>
    ): Pair<VoiceSlot, String>? {
        // Pattern 1: [Speaker] or [Voice X] or [Voix X]: Text
        val bracketMatch = Regex("""^\[([^\]]+)\]\s*[:\-]?\s*(.*)$""").find(line)
        if (bracketMatch != null) {
            val speakerTag = bracketMatch.groupValues[1].trim()
            val text = bracketMatch.groupValues[2].trim()
            val matchedVoice = findMatchingVoice(speakerTag, voices)
            if (matchedVoice != null && text.isNotEmpty()) {
                return Pair(matchedVoice, text)
            }
        }

        // Pattern 2: Speaker: Text (e.g. Alice: Bonjour)
        val colonIndex = line.indexOf(':')
        if (colonIndex in 1..30) {
            val potentialSpeaker = line.substring(0, colonIndex).trim()
            val text = line.substring(colonIndex + 1).trim()
            val matchedVoice = findMatchingVoice(potentialSpeaker, voices)
            if (matchedVoice != null && text.isNotEmpty()) {
                return Pair(matchedVoice, text)
            }
        }

        // Pattern 3: Speaker - Text (e.g. Bob - Bonjour)
        val dashMatch = Regex("""^([A-Za-z0-9À-ÿ\s]{1,25})\s*[-—]\s*(.+)$""").find(line)
        if (dashMatch != null) {
            val potentialSpeaker = dashMatch.groupValues[1].trim()
            val text = dashMatch.groupValues[2].trim()
            val matchedVoice = findMatchingVoice(potentialSpeaker, voices)
            if (matchedVoice != null && text.isNotEmpty()) {
                return Pair(matchedVoice, text)
            }
        }

        return null
    }

    private fun findMatchingVoice(tag: String, voices: List<VoiceSlot>): VoiceSlot? {
        val cleanTag = tag.lowercase().trim()

        // 1. Direct name match
        for (v in voices) {
            if (v.name.lowercase().trim() == cleanTag) return v
        }

        // 2. "Voix 1" / "Voice 1" / "V1" / "1"
        for (v in voices) {
            if (cleanTag == "voix ${v.id}" ||
                cleanTag == "voice ${v.id}" ||
                cleanTag == "v${v.id}" ||
                cleanTag == "${v.id}" ||
                cleanTag == "voix${v.id}" ||
                cleanTag == "voice${v.id}"
            ) {
                return v
            }
        }

        // 3. Partial match
        for (v in voices) {
            if (cleanTag.contains(v.name.lowercase().trim())) return v
        }

        return null
    }
}
