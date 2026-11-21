package com.raikaai.app.reminder

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Parsed reminder command.
 *
 * @property triggerAtMillis trigger time in epoch millis.
 * @property note note content shown in the notification.
 */
data class ReminderSpec(
    val triggerAtMillis: Long,
    val note: String,
)

/**
 * Pure parser (no Android dependencies, unit-testable) for reminder commands
 * in Indonesian and English.
 *
 * Supported patterns (examples are literal match inputs, not UI copy):
 * - "ingatkan saya dalam 10 menit untuk minum obat"
 * - "remind me in 2 hours to call mom"
 * - "ingatkan saya jam 15:30 untuk meeting" (past time today rolls to tomorrow)
 * - "ingatkan saya besok jam 9 untuk lari pagi"
 * - "remind me at 3pm to drink water" (past time today rolls to tomorrow)
 * - "remind me tomorrow at 9am to join standup"
 *
 * Returns null when the text matches no pattern.
 */
object ReminderParser {

    private val RELATIVE_ID = Regex(
        """^(?:tolong\s+)?ingatkan\s+(?:saya|aku)\s+dalam\s+(\d+)\s+(detik|menit|jam|hari)\s+(?:untuk|buat|agar)\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val RELATIVE_EN = Regex(
        """^remind\s+me\s+in\s+(\d+)\s+(seconds?|minutes?|hours?|days?)\s+to\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val ABSOLUTE_TODAY_ID = Regex(
        """^(?:tolong\s+)?ingatkan\s+(?:saya|aku)\s+(?:jam|pukul)\s+(\d{1,2})[:.](\d{2})\s+(?:untuk|buat|agar)\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val ABSOLUTE_TODAY_EN = Regex(
        """^remind\s+me\s+at\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)\s+to\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val ABSOLUTE_TODAY_EN_24H = Regex(
        """^remind\s+me\s+at\s+(\d{1,2}):(\d{2})\s+to\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val TOMORROW_ID = Regex(
        """^(?:tolong\s+)?ingatkan\s+(?:saya|aku)\s+besok\s+(?:(?:jam|pukul)\s+)?(\d{1,2})(?::(\d{2}))?\s+(?:untuk|buat|agar)\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val TOMORROW_EN = Regex(
        """^remind\s+me\s+tomorrow\s+at\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s+to\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * @param input raw command text from the user.
     * @param nowMillis "now" in epoch millis; injectable for deterministic tests.
     * @return [ReminderSpec] on match, null otherwise.
     */
    fun parse(input: String, nowMillis: Long = System.currentTimeMillis()): ReminderSpec? {
        val text = input.trim()
        if (text.isEmpty()) return null
        return parseRelativeId(text, nowMillis)
            ?: parseRelativeEn(text, nowMillis)
            ?: parseAbsoluteTodayId(text, nowMillis)
            ?: parseAbsoluteTodayEn(text, nowMillis)
            ?: parseAbsoluteTodayEn24h(text, nowMillis)
            ?: parseTomorrowId(text, nowMillis)
            ?: parseTomorrowEn(text, nowMillis)
    }

    private fun parseRelativeId(text: String, nowMillis: Long): ReminderSpec? {
        val m = RELATIVE_ID.matchEntire(text) ?: return null
        val amount = m.groupValues[1].toLongOrNull() ?: return null
        if (amount <= 0L || amount > 365_000L) return null
        val unitMillis = when (m.groupValues[2].lowercase()) {
            "detik" -> 1_000L
            "menit" -> 60_000L
            "jam" -> 3_600_000L
            "hari" -> 86_400_000L
            else -> return null
        }
        val note = m.groupValues[3].trim()
        if (note.isEmpty()) return null
        return ReminderSpec(triggerAtMillis = nowMillis + amount * unitMillis, note = note)
    }

    private fun parseRelativeEn(text: String, nowMillis: Long): ReminderSpec? {
        val m = RELATIVE_EN.matchEntire(text) ?: return null
        val amount = m.groupValues[1].toLongOrNull() ?: return null
        if (amount <= 0L || amount > 365_000L) return null
        val unitMillis = when (m.groupValues[2].lowercase()) {
            "second", "seconds" -> 1_000L
            "minute", "minutes" -> 60_000L
            "hour", "hours" -> 3_600_000L
            "day", "days" -> 86_400_000L
            else -> return null
        }
        val note = m.groupValues[3].trim()
        if (note.isEmpty()) return null
        return ReminderSpec(triggerAtMillis = nowMillis + amount * unitMillis, note = note)
    }

    private fun parseAbsoluteTodayId(text: String, nowMillis: Long): ReminderSpec? {
        val m = ABSOLUTE_TODAY_ID.matchEntire(text) ?: return null
        val hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: return null
        val note = m.groupValues[3].trim()
        if (note.isEmpty()) return null
        val trigger = resolveTodayOrTomorrow(hour, minute, nowMillis) ?: return null
        return ReminderSpec(triggerAtMillis = trigger, note = note)
    }

    /** "remind me at 3pm to drink water" / "remind me at 3:30pm to …". */
    private fun parseAbsoluteTodayEn(text: String, nowMillis: Long): ReminderSpec? {
        val m = ABSOLUTE_TODAY_EN.matchEntire(text) ?: return null
        var hour = m.groupValues[1].toIntOrNull() ?: return null
        if (hour !in 1..12) return null
        val minute = m.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        when (m.groupValues[3].lowercase()) {
            "am" -> if (hour == 12) hour = 0
            "pm" -> if (hour < 12) hour += 12
        }
        val note = m.groupValues[4].trim()
        if (note.isEmpty()) return null
        val trigger = resolveTodayOrTomorrow(hour, minute, nowMillis) ?: return null
        return ReminderSpec(triggerAtMillis = trigger, note = note)
    }

    /** "remind me at 15:30 to …" (24-hour clock, minutes required). */
    private fun parseAbsoluteTodayEn24h(text: String, nowMillis: Long): ReminderSpec? {
        val m = ABSOLUTE_TODAY_EN_24H.matchEntire(text) ?: return null
        val hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: return null
        val note = m.groupValues[3].trim()
        if (note.isEmpty()) return null
        val trigger = resolveTodayOrTomorrow(hour, minute, nowMillis) ?: return null
        return ReminderSpec(triggerAtMillis = trigger, note = note)
    }

    /**
     * Resolves a wall-clock time on the current day, rolling over to tomorrow
     * when it has already passed. Returns null for out-of-range values.
     */
    private fun resolveTodayOrTomorrow(hour: Int, minute: Int, nowMillis: Long): Long? {
        if (hour !in 0..23 || minute !in 0..59) return null
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val time = LocalTime.of(hour, minute)
        val todayMillis = LocalDateTime.of(today, time).atZone(zone).toInstant().toEpochMilli()
        return if (todayMillis > nowMillis) {
            todayMillis
        } else {
            LocalDateTime.of(today.plusDays(1), time).atZone(zone).toInstant().toEpochMilli()
        }
    }

    private fun parseTomorrowId(text: String, nowMillis: Long): ReminderSpec? {
        val m = TOMORROW_ID.matchEntire(text) ?: return null
        val hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59) return null
        val note = m.groupValues[3].trim()
        if (note.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        val tomorrow = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().plusDays(1)
        val trigger = LocalDateTime.of(tomorrow, LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()
        return ReminderSpec(triggerAtMillis = trigger, note = note)
    }

    private fun parseTomorrowEn(text: String, nowMillis: Long): ReminderSpec? {
        val m = TOMORROW_EN.matchEntire(text) ?: return null
        var hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        val suffix = m.groupValues[3].lowercase()
        if (suffix.isNotEmpty()) {
            if (hour !in 1..12) return null
            when (suffix) {
                "am" -> if (hour == 12) hour = 0
                "pm" -> if (hour < 12) hour += 12
            }
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        val note = m.groupValues[4].trim()
        if (note.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        // "Tomorrow" is always the next calendar day, including midnight (12am).
        val tomorrow = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().plusDays(1)
        val trigger = LocalDateTime.of(tomorrow, LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()
        return ReminderSpec(triggerAtMillis = trigger, note = note)
    }
}
