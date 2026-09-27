package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GeminiDialogueOptimizer
import com.example.data.model.DialogueLine
import com.example.data.model.ExportedAudio
import com.example.data.model.VoiceSlot
import com.example.data.repository.AudioExportManager
import com.example.data.repository.PdfExtractor
import com.example.data.repository.ScriptParser
import com.example.data.repository.VoiceRepository
import com.example.tts.PlaybackState
import com.example.tts.SpeechPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRepository(application)
    val playerManager = SpeechPlayerManager(application, viewModelScope)

    private val _voiceSlots = MutableStateFlow<List<VoiceSlot>>(repository.getVoiceSlots())
    val voiceSlots: StateFlow<List<VoiceSlot>> = _voiceSlots.asStateFlow()

    private val _scriptText = MutableStateFlow<String>(repository.getScriptDraft())
    val scriptText: StateFlow<String> = _scriptText.asStateFlow()

    val dialogueLines: StateFlow<List<DialogueLine>> = combine(_scriptText, _voiceSlots) { script, voices ->
        ScriptParser.parse(script, voices)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState

    val availableSystemVoices = playerManager.availableVoices

    private val _silenceGapMs = MutableStateFlow<Int>(repository.getSilenceGapMs())
    val silenceGapMs: StateFlow<Int> = _silenceGapMs.asStateFlow()

    private val _isAiOptimizing = MutableStateFlow(false)
    val isAiOptimizing: StateFlow<Boolean> = _isAiOptimizing.asStateFlow()

    private val _isReadingPdf = MutableStateFlow(false)
    val isReadingPdf: StateFlow<Boolean> = _isReadingPdf.asStateFlow()

    private val _exportProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val exportProgress: StateFlow<Pair<Int, Int>?> = _exportProgress.asStateFlow()

    private val _exportedAudios = MutableStateFlow<List<ExportedAudio>>(repository.getExportedAudios())
    val exportedAudios: StateFlow<List<ExportedAudio>> = _exportedAudios.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun updateScript(text: String) {
        _scriptText.value = text
        repository.saveScriptDraft(text)
    }

    fun insertSpeakerTag(voice: VoiceSlot) {
        val current = _scriptText.value
        val insertion = "\n${voice.name}: "
        val updated = if (current.isBlank()) "${voice.name}: " else current + insertion
        updateScript(updated)
    }

    fun loadSample(index: Int) {
        val samples = VoiceRepository.SAMPLE_SCRIPTS
        if (index in samples.indices) {
            updateScript(samples[index].content)
            _statusMessage.value = "Exemple chargé : ${samples[index].title}"
        }
    }

    fun updateVoiceSlot(voice: VoiceSlot) {
        repository.updateVoiceSlot(voice)
        _voiceSlots.value = repository.getVoiceSlots()
    }

    fun renameVoice(id: Int, newName: String) {
        val voice = _voiceSlots.value.firstOrNull { it.id == id } ?: return
        val updated = voice.copy(name = newName.trim())
        updateVoiceSlot(updated)
    }

    fun updateVoicePitch(id: Int, pitch: Float) {
        val voice = _voiceSlots.value.firstOrNull { it.id == id } ?: return
        updateVoiceSlot(voice.copy(pitch = pitch))
    }

    fun updateVoiceSpeed(id: Int, speed: Float) {
        val voice = _voiceSlots.value.firstOrNull { it.id == id } ?: return
        updateVoiceSlot(voice.copy(speechRate = speed))
    }

    fun setSystemVoice(id: Int, systemVoiceName: String, localeTag: String) {
        val voice = _voiceSlots.value.firstOrNull { it.id == id } ?: return
        updateVoiceSlot(voice.copy(systemVoiceName = systemVoiceName, languageTag = localeTag))
    }

    fun resetVoicesToDefaults() {
        _voiceSlots.value = repository.resetToDefaults()
        _statusMessage.value = "Voix réinitialisées par défaut"
    }

    fun testVoice(voice: VoiceSlot) {
        val phrase = "Bonjour ! Je suis ${voice.name}, la voix ${voice.id} de Group TTS."
        playerManager.testVoice(voice, phrase)
    }

    fun playDialogue(startIndex: Int = 0) {
        val lines = dialogueLines.value
        if (lines.isEmpty()) {
            _statusMessage.value = "Aucun texte ou dialogue à lire."
            return
        }
        playerManager.playDialogue(lines, _voiceSlots.value, _silenceGapMs.value, startIndex)
    }

    fun pausePlayback() {
        playerManager.pause()
    }

    fun resumePlayback() {
        playerManager.resume()
    }

    fun stopPlayback() {
        playerManager.stop()
    }

    fun nextLine() {
        playerManager.nextLine()
    }

    fun previousLine() {
        playerManager.previousLine()
    }

    fun playSingleLine(index: Int) {
        playerManager.playSingleLine(index, dialogueLines.value, _voiceSlots.value)
    }

    fun setSilenceGap(ms: Int) {
        _silenceGapMs.value = ms
        repository.setSilenceGapMs(ms)
    }

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            _isReadingPdf.value = true
            val result = PdfExtractor.extractTextFromUri(getApplication(), uri)
            _isReadingPdf.value = false
            result.onSuccess { extracted ->
                updateScript(extracted)
                _statusMessage.value = "Fichier importé avec succès (${extracted.length} caractères) !"
            }.onFailure { err ->
                _statusMessage.value = "Erreur import : ${err.message}"
            }
        }
    }

    fun optimizeScriptWithAi() {
        val currentText = _scriptText.value
        if (currentText.isBlank()) {
            _statusMessage.value = "Veuillez d'abord écrire ou importer un texte à optimiser."
            return
        }

        viewModelScope.launch {
            _isAiOptimizing.value = true
            val result = GeminiDialogueOptimizer.optimizeScript(currentText, _voiceSlots.value)
            _isAiOptimizing.value = false
            result.onSuccess { optimized ->
                updateScript(optimized)
                _statusMessage.value = "Dialogue optimisé par l'IA avec succès !"
            }.onFailure { err ->
                _statusMessage.value = "Optimisation : ${err.message}"
            }
        }
    }

    fun exportToAudio(onSuccess: (ExportedAudio) -> Unit) {
        val lines = dialogueLines.value
        if (lines.isEmpty()) {
            _statusMessage.value = "Le script est vide. Impossible d'exporter."
            return
        }

        val tts = playerManager.getTtsInstance()
        if (tts == null) {
            _statusMessage.value = "Moteur TTS non disponible pour l'export."
            return
        }

        viewModelScope.launch {
            _exportProgress.value = Pair(0, lines.size)
            val exportManager = AudioExportManager(getApplication(), tts, repository)

            val result = exportManager.exportDialogueToWav(
                dialogueLines = lines,
                voices = _voiceSlots.value,
                silenceGapMs = _silenceGapMs.value,
                onProgress = { current, total ->
                    _exportProgress.value = Pair(current, total)
                }
            )

            _exportProgress.value = null
            result.onSuccess { exported ->
                _exportedAudios.value = repository.getExportedAudios()
                _statusMessage.value = "Fichier WAV généré : ${exported.title}"
                onSuccess(exported)
            }.onFailure { err ->
                _statusMessage.value = "Erreur export : ${err.message}"
            }
        }
    }

    fun deleteExportedAudio(id: String) {
        repository.removeExportedAudio(id)
        _exportedAudios.value = repository.getExportedAudios()
        _statusMessage.value = "Enregistrement supprimé"
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.destroy()
    }
}
