package com.example.data.repository

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.data.model.DialogueLine
import com.example.data.model.ExportedAudio
import com.example.data.model.VoiceSlot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID

class AudioExportManager(
    private val context: Context,
    private val tts: TextToSpeech,
    private val voiceRepository: VoiceRepository
) {

    companion object {
        private const val TAG = "AudioExportManager"
    }

    suspend fun exportDialogueToWav(
        dialogueLines: List<DialogueLine>,
        voices: List<VoiceSlot>,
        silenceGapMs: Int,
        onProgress: (current: Int, total: Int) -> Unit
    ): Result<ExportedAudio> = withContext(Dispatchers.IO) {
        if (dialogueLines.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Le dialogue est vide."))
        }

        val outputDir = File(context.filesDir, "audio").apply { if (!exists()) mkdirs() }
        val tempDir = File(context.cacheDir, "tts_chunks_${System.currentTimeMillis()}").apply { mkdirs() }

        val voiceMap = voices.associateBy { it.id }
        val generatedWavFiles = mutableListOf<File>()

        try {
            // 1. Synthesize each segment to a temporary WAV file
            for ((index, line) in dialogueLines.withIndex()) {
                onProgress(index + 1, dialogueLines.size)
                val voice = voiceMap[line.voiceSlotId] ?: voices.firstOrNull() ?: VoiceSlot(1, "Voix")
                val tempWav = File(tempDir, "chunk_$index.wav")

                val success = synthesizeLineToFile(line.text, voice, tempWav)
                if (success && tempWav.exists() && tempWav.length() > 44) {
                    generatedWavFiles.add(tempWav)
                }
            }

            if (generatedWavFiles.isEmpty()) {
                return@withContext Result.failure(Exception("La synthèse vocale n'a généré aucun fichier audio."))
            }

            // 2. Concatenate all WAV chunks into a single clean audio file
            val timestamp = System.currentTimeMillis()
            val finalWavFile = File(outputDir, "GroupTTS_Dialogue_$timestamp.wav")

            mergeWavFiles(generatedWavFiles, finalWavFile, silenceGapMs)

            // Approximate duration: 44 bytes header, 16-bit PCM @ sample rate
            val durationMs = calculateWavDurationMs(finalWavFile)

            val exported = ExportedAudio(
                id = UUID.randomUUID().toString(),
                title = "Dialogue (${dialogueLines.size} répliques)",
                filePath = finalWavFile.absolutePath,
                durationMs = durationMs,
                fileSizeBytes = finalWavFile.length(),
                createdAt = timestamp,
                lineCount = dialogueLines.size
            )

            voiceRepository.addExportedAudio(exported)
            Result.success(exported)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur export WAV", e)
            Result.failure(e)
        } finally {
            // Clean up temporary segment files
            tempDir.deleteRecursively()
        }
    }

    private suspend fun synthesizeLineToFile(
        text: String,
        voiceSlot: VoiceSlot,
        destinationFile: File
    ): Boolean = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<Boolean>()
        val utteranceId = "export_${UUID.randomUUID()}"

        // Configure TTS voice, pitch, and speed
        tts.setPitch(voiceSlot.pitch)
        tts.setSpeechRate(voiceSlot.speechRate)

        // Select system voice if specified
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

        val originalListener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) deferred.complete(true)
            }
            override fun onError(id: String?) {
                if (id == utteranceId) deferred.complete(false)
            }
        }

        tts.setOnUtteranceProgressListener(originalListener)

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        val result = tts.synthesizeToFile(text, params, destinationFile, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            deferred.complete(false)
        }

        deferred.await()
    }

    private fun mergeWavFiles(wavFiles: List<File>, outputFile: File, silenceGapMs: Int) {
        if (wavFiles.isEmpty()) return

        // Read format specifications from first file
        val firstHeader = ByteArray(44)
        FileInputStream(wavFiles[0]).use { it.read(firstHeader) }

        val sampleRate = ByteBuffer.wrap(firstHeader, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val channels = ByteBuffer.wrap(firstHeader, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        val bitsPerSample = ByteBuffer.wrap(firstHeader, 34, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        val bytesPerSample = bitsPerSample / 8

        // Calculate silence bytes
        val silenceSampleCount = (sampleRate * (silenceGapMs / 1000.0)).toInt()
        val silenceBytes = ByteArray(silenceSampleCount * channels * bytesPerSample) // filled with 0

        FileOutputStream(outputFile).use { outStream ->
            // Write placeholder header
            outStream.write(ByteArray(44))

            var totalPcmBytes = 0L

            for ((i, file) in wavFiles.withIndex()) {
                val fileSize = file.length()
                if (fileSize > 44) {
                    val pcmLength = fileSize - 44
                    FileInputStream(file).use { input ->
                        input.skip(44) // skip header
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            outStream.write(buffer, 0, bytesRead)
                            totalPcmBytes += bytesRead
                        }
                    }

                    // Add silence between dialogue turns
                    if (i < wavFiles.size - 1 && silenceBytes.isNotEmpty()) {
                        outStream.write(silenceBytes)
                        totalPcmBytes += silenceBytes.size
                    }
                }
            }

            outStream.flush()

            // Update real header with total sizes
            val header = buildWavHeader(totalPcmBytes, sampleRate, channels, bitsPerSample)
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.seek(0)
                raf.write(header)
            }
        }
    }

    private fun buildWavHeader(
        pcmDataLength: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val totalDataLen = pcmDataLength + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen.toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16) // Subchunk1Size for PCM
        header.putShort(1.toShort()) // AudioFormat (1 = PCM)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign)
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray())
        header.putInt(pcmDataLength.toInt())

        return header.array()
    }

    private fun calculateWavDurationMs(wavFile: File): Long {
        if (!wavFile.exists() || wavFile.length() <= 44) return 0L
        return try {
            val header = ByteArray(44)
            FileInputStream(wavFile).use { it.read(header) }
            val byteRate = ByteBuffer.wrap(header, 28, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (byteRate > 0) {
                val pcmLength = wavFile.length() - 44
                (pcmLength * 1000L) / byteRate
            } else 0L
        } catch (_: Exception) {
            0L
        }
    }
}
