package com.usj.news_application.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object GeminiTranslator {
    private val GEMINI_API_KEY: String = System.getenv("GEMINI_API_KEY") ?: ""

    data class TranslationResult(
        val translatedTitle: String,
        val translatedContent: String,
        val translatedDetailed: String,
        val targetLanguageLabel: String
    )

    fun containsArabic(text: String): Boolean {
        return text.any { it in '\u0600'..'\u06FF' }
    }

    suspend fun translateArticle(
        title: String,
        content: String,
        detailedDescription: String
    ): TranslationResult = withContext(Dispatchers.IO) {
        val isSourceArabic = containsArabic("$title $content $detailedDescription")
        val targetLangCode = if (isSourceArabic) "en" else "ar"
        val targetLangName = if (isSourceArabic) "English" else "Arabic"

        try {
            val geminiResult = callGeminiApi(title, content, detailedDescription, targetLangName)
            if (geminiResult != null) {
                return@withContext geminiResult
            }
        } catch (_: Exception) {
        }

        val tTitle = translateFullText(title, targetLangCode)
        val tContent = translateFullText(content, targetLangCode)
        val tDetailed = if (detailedDescription.isBlank() || detailedDescription == content) {
            tContent
        } else {
            translateFullText(detailedDescription, targetLangCode)
        }

        TranslationResult(
            translatedTitle = tTitle,
            translatedContent = tContent,
            translatedDetailed = tDetailed,
            targetLanguageLabel = targetLangName
        )
    }

    private fun callGeminiApi(
        title: String,
        content: String,
        detailed: String,
        targetLangName: String
    ): TranslationResult? {
        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$GEMINI_API_KEY"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connectTimeout = 6000
            readTimeout = 6000
            doOutput = true
        }

        val prompt = """
            You are a professional news translator. Translate the following news article completely into $targetLangName.
            Translate every single sentence of TITLE, CONTENT, and DETAILED.
            Return ONLY valid JSON with keys "title", "content", and "detailed".
            TITLE: $title
            CONTENT: $content
            DETAILED: $detailed
        """.trimIndent()

        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload.toString())
            writer.flush()
        }

        if (conn.responseCode in 200..299) {
            val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            val root = JSONObject(responseText)
            val textOut = root.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val parsed = JSONObject(textOut)
            val outTitle = parsed.optString("title", "")
            val outContent = parsed.optString("content", "")
            val outDetailed = parsed.optString("detailed", "")
            if (outTitle.isNotBlank() && outDetailed.isNotBlank()) {
                return TranslationResult(
                    translatedTitle = outTitle,
                    translatedContent = outContent.ifBlank { outTitle },
                    translatedDetailed = outDetailed,
                    targetLanguageLabel = targetLangName
                )
            }
        }
        return null
    }

    private fun translateFullText(text: String, targetLang: String): String {
        if (text.isBlank()) return text

        val paragraphs = text.split("\n")
        val translatedParagraphs = mutableListOf<String>()

        for (para in paragraphs) {
            if (para.isBlank()) {
                translatedParagraphs.add("")
                continue
            }
            val chunks = splitIntoChunks(para, 350)
            val translatedChunkBuilder = StringBuilder()
            for (chunk in chunks) {
                val translatedChunk = translateSingleChunk(chunk, targetLang)
                if (translatedChunkBuilder.isNotEmpty()) {
                    translatedChunkBuilder.append(" ")
                }
                translatedChunkBuilder.append(translatedChunk.trim())
            }
            translatedParagraphs.add(translatedChunkBuilder.toString())
        }

        return translatedParagraphs.joinToString("\n")
    }

    private fun splitIntoChunks(text: String, maxChunkSize: Int): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)
        val result = mutableListOf<String>()
        val sentences = text.split(Regex("(?<=[.،!؟?;])\\s+"))
        val current = StringBuilder()

        for (sentence in sentences) {
            if (current.length + sentence.length + 1 > maxChunkSize) {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    current.clear()
                }
                if (sentence.length > maxChunkSize) {
                    val words = sentence.split(" ")
                    for (word in words) {
                        if (current.length + word.length + 1 > maxChunkSize) {
                            result.add(current.toString())
                            current.clear()
                        }
                        if (current.isNotEmpty()) current.append(" ")
                        current.append(word)
                    }
                } else {
                    current.append(sentence)
                }
            } else {
                if (current.isNotEmpty()) current.append(" ")
                current.append(sentence)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    private fun translateSingleChunk(chunk: String, targetLang: String): String {
        if (chunk.isBlank()) return chunk
        return try {
            val encoded = URLEncoder.encode(chunk, "UTF-8")
            val urlStr =
                "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t&q=$encoded"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 6000
                readTimeout = 6000
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                val outer = JSONArray(body).getJSONArray(0)
                val sb = StringBuilder()
                for (i in 0 until outer.length()) {
                    val seg = outer.getJSONArray(i)
                    if (!seg.isNull(0)) {
                        sb.append(seg.getString(0))
                    }
                }
                sb.toString()
            } else {
                chunk
            }
        } catch (_: Exception) {
            chunk
        }
    }
}
