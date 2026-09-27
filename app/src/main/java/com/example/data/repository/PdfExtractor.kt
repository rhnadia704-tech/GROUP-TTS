package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.InflaterInputStream

object PdfExtractor {

    private const val TAG = "PdfExtractor"

    suspend fun extractTextFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: ""
            val inputStream = contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Impossible d'ouvrir le fichier sélectionné."))

            val bytes = inputStream.use { it.readBytes() }

            // If it's a plain text file or starts with non-PDF header
            if (mimeType.contains("text") || !bytes.take(5).toByteArray().toString(Charsets.ISO_8859_1).startsWith("%PDF")) {
                val text = String(bytes, Charsets.UTF_8)
                return@withContext Result.success(cleanExtractedText(text))
            }

            // Extract text from PDF content streams
            val extracted = extractTextFromPdfBytes(bytes)
            if (extracted.isNotBlank()) {
                Result.success(cleanExtractedText(extracted))
            } else {
                Result.failure(Exception("Le document PDF ne contient pas de texte vectoriel extractible (il s'agit peut-être d'un document numérisé/image)."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'extraction PDF", e)
            Result.failure(e)
        }
    }

    private fun extractTextFromPdfBytes(bytes: ByteArray): String {
        val fullString = String(bytes, Charsets.ISO_8859_1)
        val extractedText = StringBuilder()

        // Locate stream ... endstream blocks
        val streamRegex = Regex("""stream\r?\n([\s\S]*?)\r?\nendstream""")
        val matches = streamRegex.findAll(fullString)

        for (match in matches) {
            val streamContent = match.groupValues[1]
            val streamBytes = streamContent.toByteArray(Charsets.ISO_8859_1)

            // Try decompressing with Inflater (FlateDecode)
            var decompressed: String? = null
            try {
                val inflaterStream = InflaterInputStream(ByteArrayInputStream(streamBytes))
                val outputStream = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var len: Int
                while (inflaterStream.read(buffer).also { len = it } != -1) {
                    outputStream.write(buffer, 0, len)
                }
                decompressed = outputStream.toString("ISO-8859-1")
            } catch (_: Exception) {
                // Not Flate compressed or raw stream
                decompressed = streamContent
            }

            if (decompressed != null) {
                val parsed = extractPdfTextTokens(decompressed)
                if (parsed.isNotBlank()) {
                    extractedText.append(parsed).append("\n\n")
                }
            }
        }

        // If streams didn't yield text, scan for raw parentheses strings in BT ... ET blocks
        if (extractedText.isBlank()) {
            val btEtRegex = Regex("""BT([\s\S]*?)ET""")
            for (btMatch in btEtRegex.findAll(fullString)) {
                val block = btMatch.groupValues[1]
                val tokens = extractPdfTextTokens(block)
                if (tokens.isNotBlank()) {
                    extractedText.append(tokens).append("\n")
                }
            }
        }

        return extractedText.toString().trim()
    }

    private fun extractPdfTextTokens(streamText: String): String {
        val out = StringBuilder()

        // 1. Array strings: [(Hello) 10 (World)] TJ
        val tjArrayRegex = Regex("""\[(.*?)\]\s*TJ""", RegexOption.DOT_MATCHES_ALL)
        for (match in tjArrayRegex.findAll(streamText)) {
            val arrayBody = match.groupValues[1]
            val stringItemRegex = Regex("""\((.*?)\)""")
            val lineItems = stringItemRegex.findAll(arrayBody).map { decodePdfString(it.groupValues[1]) }
            val joined = lineItems.joinToString("").trim()
            if (joined.isNotBlank()) {
                out.append(joined).append(" ")
            }
        }

        // 2. Direct string: (Hello World) Tj or '
        val tjSingleRegex = Regex("""\((.*?)\)\s*(?:Tj|'|")""")
        for (match in tjSingleRegex.findAll(streamText)) {
            val raw = match.groupValues[1]
            val decoded = decodePdfString(raw).trim()
            if (decoded.isNotBlank()) {
                out.append(decoded).append(" ")
            }
        }

        return out.toString().trim()
    }

    private fun decodePdfString(pdfStr: String): String {
        return pdfStr
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }

    private fun cleanExtractedText(raw: String): String {
        return raw
            .replace(Regex("""[ \t]+"""), " ")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }
}
