package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ExportedAudio
import com.example.data.model.VoiceSlot
import org.json.JSONArray
import org.json.JSONObject

class VoiceRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "group_tts_preferences"
        private const val KEY_VOICES = "key_voice_slots_v2"
        private const val KEY_SCRIPT_DRAFT = "key_script_draft"
        private const val KEY_EXPORTED_AUDIOS = "key_exported_audios"
        private const val KEY_SILENCE_GAP_MS = "key_silence_gap_ms"
        private const val KEY_AI_ENHANCE_ENABLED = "key_ai_enhance_enabled"

        const val DEFAULT_SILENCE_GAP_MS = 400

        val SAMPLE_SCRIPTS = listOf(
            SampleScript(
                title = "Débat Podcast Tech",
                content = """Alice: Bonjour à tous et bienvenue dans notre podcast Tech Pulse !
Bob: Salut Alice ! Aujourd'hui nous parlons de synthèse vocale multi-voix.
Narrateur: Dans le studio, les deux animateurs consultaient leurs tablettes interactives.
Alice: Exactement Bob. Imaginez pouvoir assigner plusieurs voix Android différentes au sein d'un même dialogue !
Bob: C'est une vraie révolution pour l'écoute de pièces de théâtre ou de livres audio.
Narrateur: Une démonstration qui impressionna l'ensemble des auditeurs."""
            ),
            SampleScript(
                title = "Conte & Aventure",
                content = """Narrateur: Il était une fois, aux confins d'une forêt enchantée, deux voyageurs égarés.
Alice: Regarde là-bas ! Vois-tu cette lueur dorée entre les grands chênes ?
David: Oui, c'est étrange... On dirait une antique porte de pierre gravée.
Narrateur: Le vent s'engouffra dans les ramures et une voix mystérieuse s'éleva.
Clara: Qui ose troubler le sommeil des Gardiens du Temps ?
Bob: N'ayez crainte, nous cherchons simplement notre chemin vers la vallée !"""
            ),
            SampleScript(
                title = "Interview Express",
                content = """Alice: Monsieur le Ministre, pouvez-vous résumer les avancées d'aujourd'hui ?
Bob: Avec plaisir. Nous avons simplifié l'accès aux technologies d'accessibilité pour tous.
Narrateur: Les journalistes prirent des notes avec empressement.
Alice: Et qu'en est-il du calendrier de déploiement ?
Bob: Dès la fin du trimestre, chaque utilisateur pourra en bénéficier directement."""
            )
        )
    }

    data class SampleScript(val title: String, val content: String)

    fun getVoiceSlots(): List<VoiceSlot> {
        val rawJson = prefs.getString(KEY_VOICES, null)
        if (rawJson.isNullOrBlank()) {
            val defaults = VoiceSlot.defaultVoices()
            saveVoiceSlots(defaults)
            return defaults
        }

        return try {
            val jsonArray = JSONArray(rawJson)
            val list = mutableListOf<VoiceSlot>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    VoiceSlot(
                        id = obj.getInt("id"),
                        name = obj.getString("name"),
                        systemVoiceName = obj.optString("systemVoiceName", ""),
                        languageTag = obj.optString("languageTag", "fr-FR"),
                        pitch = obj.optDouble("pitch", 1.0).toFloat(),
                        speechRate = obj.optDouble("speechRate", 1.0).toFloat(),
                        colorHex = obj.optLong("colorHex", 0xFF3B82F6),
                        avatarEmoji = obj.optString("avatarEmoji", "🎙️")
                    )
                )
            }
            if (list.size < 5) {
                // Ensure always exactly 5 voices
                val defaults = VoiceSlot.defaultVoices()
                val merged = (list + defaults.drop(list.size)).take(5)
                saveVoiceSlots(merged)
                merged
            } else {
                list.take(5)
            }
        } catch (e: Exception) {
            val defaults = VoiceSlot.defaultVoices()
            saveVoiceSlots(defaults)
            defaults
        }
    }

    fun saveVoiceSlots(voices: List<VoiceSlot>) {
        val jsonArray = JSONArray()
        for (v in voices) {
            val obj = JSONObject().apply {
                put("id", v.id)
                put("name", v.name)
                put("systemVoiceName", v.systemVoiceName)
                put("languageTag", v.languageTag)
                put("pitch", v.pitch.toDouble())
                put("speechRate", v.speechRate.toDouble())
                put("colorHex", v.colorHex)
                put("avatarEmoji", v.avatarEmoji)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_VOICES, jsonArray.toString()).apply()
    }

    fun updateVoiceSlot(updated: VoiceSlot) {
        val current = getVoiceSlots().toMutableList()
        val index = current.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            current[index] = updated
        } else {
            current.add(updated)
        }
        saveVoiceSlots(current.take(5))
    }

    fun getScriptDraft(): String {
        return prefs.getString(KEY_SCRIPT_DRAFT, SAMPLE_SCRIPTS[0].content) ?: SAMPLE_SCRIPTS[0].content
    }

    fun saveScriptDraft(script: String) {
        prefs.edit().putString(KEY_SCRIPT_DRAFT, script).apply()
    }

    fun getSilenceGapMs(): Int {
        return prefs.getInt(KEY_SILENCE_GAP_MS, DEFAULT_SILENCE_GAP_MS)
    }

    fun setSilenceGapMs(ms: Int) {
        prefs.edit().putInt(KEY_SILENCE_GAP_MS, ms).apply()
    }

    fun isAiEnhanceEnabled(): Boolean {
        return prefs.getBoolean(KEY_AI_ENHANCE_ENABLED, true)
    }

    fun setAiEnhanceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AI_ENHANCE_ENABLED, enabled).apply()
    }

    fun getExportedAudios(): List<ExportedAudio> {
        val raw = prefs.getString(KEY_EXPORTED_AUDIOS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<ExportedAudio>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                list.add(
                    ExportedAudio(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        filePath = o.getString("filePath"),
                        durationMs = o.getLong("durationMs"),
                        fileSizeBytes = o.getLong("fileSizeBytes"),
                        createdAt = o.getLong("createdAt"),
                        lineCount = o.getInt("lineCount")
                    )
                )
            }
            list.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addExportedAudio(audio: ExportedAudio) {
        val current = getExportedAudios().toMutableList()
        current.removeAll { it.id == audio.id }
        current.add(0, audio)
        saveExportedAudios(current)
    }

    fun removeExportedAudio(id: String) {
        val current = getExportedAudios().toMutableList()
        current.removeAll { it.id == id }
        saveExportedAudios(current)
    }

    private fun saveExportedAudios(list: List<ExportedAudio>) {
        val array = JSONArray()
        for (item in list) {
            val o = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("filePath", item.filePath)
                put("durationMs", item.durationMs)
                put("fileSizeBytes", item.fileSizeBytes)
                put("createdAt", item.createdAt)
                put("lineCount", item.lineCount)
            }
            array.put(o)
        }
        prefs.edit().putString(KEY_EXPORTED_AUDIOS, array.toString()).apply()
    }

    fun resetToDefaults(): List<VoiceSlot> {
        val defaults = VoiceSlot.defaultVoices()
        saveVoiceSlots(defaults)
        return defaults
    }
}
