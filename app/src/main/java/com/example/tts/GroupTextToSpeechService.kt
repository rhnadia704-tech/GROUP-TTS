package com.example.tts

import android.media.AudioFormat
import android.os.Bundle
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.VoiceSlot
import com.example.data.repository.ScriptParser
import com.example.data.repository.VoiceRepository
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GroupTextToSpeechService : TextToSpeechService() {

    private lateinit var voiceRepository: VoiceRepository
    private var underlyingTts: TextToSpeech? = null
    private var isTtsReady = false
    private val initLock = Any()

    companion object {
        private const val TAG = "GroupTtsService"
        private const val SAMPLE_RATE = 22050
        private const val CHANNELS = 1
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    override fun onCreate() {
        super.onCreate()
        voiceRepository = VoiceRepository(applicationContext)
        initUnderlyingTts()
    }

    private fun initUnderlyingTts() {
        try {
            // Find default or installed TTS engine (e.g., Google TTS)
            underlyingTts = TextToSpeech(applicationContext, { status ->
                synchronized(initLock) {
                    isTtsReady = (status == TextToSpeech.SUCCESS)
                    Log.d(TAG, "Moteur sous-jacent TTS initialisé. Statut: $isTtsReady")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Impossible d'initialiser le TTS sous-jacent", e)
        }
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val locale = try {
            when {
                lang != null && country != null -> Locale.Builder().setLanguage(lang).setRegion(country).build()
                lang != null -> Locale.Builder().setLanguage(lang).build()
                else -> Locale.getDefault()
            }
        } catch (_: Exception) {
            Locale.getDefault()
        }
        return underlyingTts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_AVAILABLE
    }

    override fun onGetLanguage(): Array<String> {
        val locale = Locale.getDefault()
        return arrayOf(locale.isO3Language, locale.isO3Country, "")
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        return onIsLanguageAvailable(lang, country, variant)
    }

    override fun onStop() {
        try {
            underlyingTts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur onStop", e)
        }
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null) return

        val text = request.charSequenceText?.toString() ?: ""
        if (text.isBlank()) {
            callback.start(SAMPLE_RATE, AUDIO_FORMAT, CHANNELS)
            callback.done()
            return
        }

        val voices = voiceRepository.getVoiceSlots()
        val dialogueLines = ScriptParser.parse(text, voices)

        // If underlying TTS is ready and available
        val ready = synchronized(initLock) { isTtsReady && underlyingTts != null }

        if (ready && dialogueLines.isNotEmpty()) {
            synthesizeWithUnderlyingTts(dialogueLines, voices, callback)
        } else {
            // Fallback PCM audio generation to avoid blocking caller
            synthesizeFallbackTone(callback, text.length)
        }
    }

    private fun synthesizeWithUnderlyingTts(
        dialogueLines: List<com.example.data.model.DialogueLine>,
        voices: List<VoiceSlot>,
        callback: SynthesisCallback
    ) {
        val tts = underlyingTts ?: return
        val voiceMap = voices.associateBy { it.id }
        val tempDir = File(cacheDir, "service_tts_${System.currentTimeMillis()}").apply { mkdirs() }

        try {
            var callbackStarted = false

            for (line in dialogueLines) {
                val voiceSlot = voiceMap[line.voiceSlotId] ?: voices.firstOrNull() ?: VoiceSlot(1, "Voix")
                val tempWav = File(tempDir, "service_chunk_${UUID.randomUUID()}.wav")

                val latch = CountDownLatch(1)
                val utteranceId = "svc_${UUID.randomUUID()}"

                tts.setPitch(voiceSlot.pitch)
                tts.setSpeechRate(voiceSlot.speechRate)
                if (voiceSlot.systemVoiceName.isNotBlank()) {
                    val matchingVoice = tts.voices?.firstOrNull { it.name == voiceSlot.systemVoiceName }
                    if (matchingVoice != null) {
                        tts.voice = matchingVoice
                    } else {
                        tts.language = Locale.forLanguageTag(voiceSlot.languageTag)
                    }
                } else {
                    tts.language = Locale.forLanguageTag(voiceSlot.languageTag)
                }

                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {
                        if (id == utteranceId) latch.countDown()
                    }
                    override fun onError(id: String?) {
                        if (id == utteranceId) latch.countDown()
                    }
                })

                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }

                val synthResult = tts.synthesizeToFile(line.text, params, tempWav, utteranceId)
                if (synthResult == TextToSpeech.SUCCESS) {
                    latch.await(5, TimeUnit.SECONDS)
                }

                // If file was generated, stream PCM bytes to callback
                if (tempWav.exists() && tempWav.length() > 44) {
                    FileInputStream(tempWav).use { fis ->
                        val header = ByteArray(44)
                        fis.read(header)

                        val chunkSampleRate = ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
                        val chunkChannels = ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

                        if (!callbackStarted) {
                            callback.start(chunkSampleRate, AUDIO_FORMAT, chunkChannels)
                            callbackStarted = true
                        }

                        val buffer = ByteArray(4096)
                        var readBytes: Int
                        while (fis.read(buffer).also { readBytes = it } != -1) {
                            callback.audioAvailable(buffer, 0, readBytes)
                        }
                    }
                    tempWav.delete()
                }
            }

            if (!callbackStarted) {
                callback.start(SAMPLE_RATE, AUDIO_FORMAT, CHANNELS)
            }
            callback.done()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans synthesizeWithUnderlyingTts", e)
            callback.error()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun synthesizeFallbackTone(callback: SynthesisCallback, textLength: Int) {
        callback.start(SAMPLE_RATE, AUDIO_FORMAT, CHANNELS)
        // Generate brief gentle indicator audio so caller knows service is active
        val numSamples = (SAMPLE_RATE * 0.15).toInt()
        val buffer = ByteArray(numSamples * 2)
        val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * i * 440.0 / SAMPLE_RATE
            val sample = (Math.sin(angle) * 4000.0).toInt().toShort()
            byteBuffer.putShort(sample)
        }

        callback.audioAvailable(buffer, 0, buffer.size)
        callback.done()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            underlyingTts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur onDestroy", e)
        }
    }
}
