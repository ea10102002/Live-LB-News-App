package com.usj.news_application.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object NewsDateParser {

    private val beirutZone: TimeZone = TimeZone.getTimeZone("Asia/Beirut")
    private val utcZone: TimeZone = TimeZone.getTimeZone("UTC")

    fun parseTimestampMillis(rawDateTime: String?, articleUrl: String = ""): Long {
        if (rawDateTime.isNullOrBlank()) return extractFallbackIdScore(articleUrl)
        val trimmed = rawDateTime.trim()

        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'" to utcZone,
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" to utcZone,
            "yyyy-MM-dd'T'HH:mm:ss" to beirutZone,
            "yyyy-MM-dd HH:mm:ss" to beirutZone,
            "yyyy-MM-dd HH:mm" to beirutZone,
            "dd/MM/yyyy HH:mm" to beirutZone,
            "dd-MM-yyyy HH:mm" to beirutZone
        )

        for ((pattern, tz) in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = tz
                    isLenient = false
                }
                val parsed = sdf.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }

        // Handle time-only strings like "12:48" from LBCI cards (today in Asia/Beirut)
        if (Regex("^\\d{1,2}:\\d{2}$").matches(trimmed)) {
            try {
                val parts = trimmed.split(":")
                val hour = parts[0].toInt()
                val minute = parts[1].toInt()
                val now = Calendar.getInstance(beirutZone)
                val candidate = Calendar.getInstance(beirutZone).apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                // If the time appears ahead of current time by > 15 min, it belongs to yesterday
                if (candidate.timeInMillis > now.timeInMillis + 15 * 60 * 1000L) {
                    candidate.add(Calendar.DAY_OF_MONTH, -1)
                }
                return candidate.timeInMillis
            } catch (_: Exception) {
            }
        }

        return extractFallbackIdScore(articleUrl)
    }

    fun normalizeForDisplay(rawDateTime: String?): String {
        if (rawDateTime.isNullOrBlank()) return ""
        val millis = parseTimestampMillis(rawDateTime)
        if (millis <= 1_000_000_000_000L) {
            return rawDateTime.replace('T', ' ').substringBefore("Z").trim()
        }
        val outFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).apply {
            timeZone = beirutZone
        }
        return outFmt.format(millis)
    }

    private fun extractFallbackIdScore(articleUrl: String): Long {
        val match = Regex("/(\\d{5,8})(?:/|$)").find(articleUrl)
        return match?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
    }
}
