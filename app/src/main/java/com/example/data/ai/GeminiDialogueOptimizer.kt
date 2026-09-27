package com.example.data.ai

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.VoiceSlot
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @param:Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @param:Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @param:Json(name = "content") val content: GeminiContent?
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @param:Json(name = "contents") val contents: List<GeminiContent>
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @param:Json(name = "candidates") val candidates: List<GeminiCandidate>?
)

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiDialogueOptimizer {

    private const val TAG = "GeminiOptimizer"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val api: GeminiApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApi::class.java)
    }

    /**
     * Optimizes a script or raw text using Gemini 3.5 Flash or heuristic fallback.
     */
    suspend fun optimizeScript(
        rawText: String,
        voices: List<VoiceSlot>
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY

        val voiceDescriptions = voices.joinToString(", ") { v ->
            "${v.name} (Rôle/Voix ${v.id})"
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            // Heuristic offline optimization fallback
            val heuristicScript = optimizeLocally(rawText, voices)
            return@withContext Result.success(heuristicScript)
        }

        try {
            val systemInstruction = """
                Tu es un metteur en scène audio et dramaturge professionnel spécialisé dans la synthèse vocale (TTS).
                Ton rôle est d'adapter et d'optimiser le texte fourni en un dialogue captivant et naturel pour 2 à 5 personnages.
                Personnages disponibles : $voiceDescriptions.
                
                Règles strictes :
                1. Chaque réplique doit commencer par le nom exact d'un personnage suivi de deux points (ex: "${voices.firstOrNull()?.name ?: "Alice"}: Bonjour !").
                2. Adapte la ponctuation pour la synthèse vocale : insère des virgules pour des pauses de respiration naturelles, utilise des points de suspension (...) pour les hésitations réalistes.
                3. Alterne les prises de parole de manière équilibrée et fluide.
                4. Renvoie UNIQUEMENT le texte formaté du dialogue, sans balises de code markdown (pas de ```), sans préambule ni explications.
            """.trimIndent()

            val prompt = "$systemInstruction\n\nTexte à optimiser :\n$rawText"

            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = prompt))
                    )
                )
            )

            val response = api.generateContent(apiKey, request)
            val generated = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()

            if (!generated.isNullOrBlank()) {
                val cleaned = generated
                    .removePrefix("```markdown")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
                Result.success(cleaned)
            } else {
                Result.success(optimizeLocally(rawText, voices))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini API call failed, falling back to local optimization: ${e.message}")
            Result.success(optimizeLocally(rawText, voices))
        }
    }

    /**
     * Local heuristic optimizer when offline or API key is not configured.
     */
    fun optimizeLocally(rawText: String, voices: List<VoiceSlot>): String {
        val paragraphs = rawText.split(Regex("""\n{2,}"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val activeVoices = if (voices.isNotEmpty()) voices else VoiceSlot.defaultVoices()
        val result = StringBuilder()
        var voiceIdx = 0

        for (paragraph in paragraphs) {
            val sentences = paragraph.split(Regex("""(?<=[.!?])\s+"""))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            for (sentence in sentences) {
                // If sentence already starts with a speaker name
                val alreadyTagged = activeVoices.any { sentence.startsWith("${it.name}:", ignoreCase = true) }
                if (alreadyTagged) {
                    result.append(sentence).append("\n")
                } else {
                    val assignedVoice = activeVoices[voiceIdx % activeVoices.size]
                    voiceIdx++

                    // Enhance punctuation for smoother TTS rhythm
                    val enhancedSentence = sentence
                        .replace(" - ", ", ")
                        .replace("...", "… ")

                    result.append("${assignedVoice.name}: $enhancedSentence\n")
                }
            }
        }

        return result.toString().trim()
    }
}
