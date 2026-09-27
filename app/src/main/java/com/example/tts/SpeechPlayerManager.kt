package com.example.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.data.model.DialogueLine
import com.example.data.model.VoiceSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

sealed class PlaybackState {
    data object Idle : PlaybackState()
    data class Playing(val currentLineIndex: Int, val totalLines: Int) : PlaybackState()
    data class Paused(val currentLineIndex: Int, val totalLines: Int) : PlaybackState()
}

class SpeechPlayerManager(
    private val context: Context,
    private val scope: CoroutineScope
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoices: StateFlow<List<Voice>> = _availableVoices.asStateFlow()

    private var currentDialogue: List<DialogueLine> = emptyList()
    private var currentVoices: Map<Int, VoiceSlot> = emptyMap()
    private var currentIndex = 0
    private var silenceGapMs = 400
    private var playJob: Job? = null

    companion object {
        private const val TAG = "SpeechPlayerManager"
    }

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupUtteranceListener()
            refreshAvailableVoices()
            Log.d(TAG, "SpeechPlayerManager TTS initialisé avec succès")
        } else {
            Log.e(TAG, "Échec de l'initialisation TTS: $status")
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Handled in playCurrentLine
            }

            override fun onDone(utteranceId: String?) {
                handleUtteranceDone(utteranceId)
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "Erreur TTS sur utteranceId: $utteranceId")
                handleUtteranceDone(utteranceId)
            }
        })
    }

    fun refreshAvailableVoices() {
        val voiceSet = tts?.voices
        if (voiceSet != null) {
            val list = voiceSet.sortedBy { it.name }
            _availableVoices.value = list
        }
    }

    fun getTtsInstance(): TextToSpeech? = tts

    fun playDialogue(
        dialogue: List<DialogueLine>,
        voices: List<VoiceSlot>,
        silenceGap: Int,
        startIndex: Int = 0
    ) {
        if (!isInitialized || dialogue.isEmpty()) return

        currentDialogue = dialogue
        currentVoices = voices.associateBy { it.id }
        currentIndex = startIndex.coerceIn(0, dialogue.size - 1)
        silenceGapMs = silenceGap

        playCurrentLine()
    }

    private fun playCurrentLine() {
        if (currentIndex !in currentDialogue.indices) {
            stop()
            return
        }

        val line = currentDialogue[currentIndex]
        val voice = currentVoices[line.voiceSlotId] ?: VoiceSlot(1, "Voix")

        _playbackState.value = PlaybackState.Playing(currentIndex, currentDialogue.size)

        applyVoiceConfig(voice)

        val utteranceId = "line_${currentIndex}_${UUID.randomUUID()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        tts?.speak(line.text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    private fun handleUtteranceDone(utteranceId: String?) {
        if (utteranceId?.startsWith("line_") == true) {
            playJob?.cancel()
            playJob = scope.launch(Dispatchers.Main) {
                if (_playbackState.value is PlaybackState.Playing) {
                    delay(silenceGapMs.toLong())
                    if (currentIndex + 1 < currentDialogue.size) {
                        currentIndex++
                        playCurrentLine()
                    } else {
                        _playbackState.value = PlaybackState.Idle
                    }
                }
            }
        } else if (utteranceId?.startsWith("test_") == true) {
            _playbackState.value = PlaybackState.Idle
        }
    }

    fun pause() {
        if (_playbackState.value is PlaybackState.Playing) {
            tts?.stop()
            playJob?.cancel()
            _playbackState.value = PlaybackState.Paused(currentIndex, currentDialogue.size)
        }
    }

    fun resume() {
        if (_playbackState.value is PlaybackState.Paused) {
            playCurrentLine()
        }
    }

    fun stop() {
        playJob?.cancel()
        tts?.stop()
        currentIndex = 0
        _playbackState.value = PlaybackState.Idle
    }

    fun nextLine() {
        if (currentIndex + 1 < currentDialogue.size) {
            tts?.stop()
            playJob?.cancel()
            currentIndex++
            playCurrentLine()
        }
    }

    fun previousLine() {
        if (currentIndex > 0) {
            tts?.stop()
            playJob?.cancel()
            currentIndex--
            playCurrentLine()
        }
    }

    fun playSingleLine(index: Int, dialogue: List<DialogueLine>, voices: List<VoiceSlot>) {
        if (index in dialogue.indices) {
            currentDialogue = dialogue
            currentVoices = voices.associateBy { it.id }
            currentIndex = index
            playCurrentLine()
        }
    }

    fun testVoice(voice: VoiceSlot, sampleText: String) {
        if (!isInitialized) return
        stop()

        applyVoiceConfig(voice)
        val utteranceId = "test_${UUID.randomUUID()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        tts?.speak(sampleText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    private fun applyVoiceConfig(voiceSlot: VoiceSlot) {
        val engine = tts ?: return
        engine.setPitch(voiceSlot.pitch)
        engine.setSpeechRate(voiceSlot.speechRate)

        if (voiceSlot.systemVoiceName.isNotBlank()) {
            val matched = engine.voices?.firstOrNull { it.name == voiceSlot.systemVoiceName }
            if (matched != null) {
                engine.voice = matched
                return
            }
        }

        try {
            engine.language = Locale.forLanguageTag(voiceSlot.languageTag)
        } catch (_: Exception) {
            engine.language = Locale.getDefault()
        }
    }

    fun destroy() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur destroy TTS", e)
        }
    }
}
