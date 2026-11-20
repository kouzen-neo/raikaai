package com.raikaai.app.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderParserTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun testRelativeIndonesianMinutes() {
        val now = millis(2026, 10, 1, 10, 0)
        val spec = ReminderParser.parse("ingatkan saya dalam 10 menit untuk minum obat", now)
        assertNotNull(spec)
        assertEquals(now + 10 * 60_000L, spec?.triggerAtMillis)
        assertEquals("minum obat", spec?.note)
    }

    @Test
    fun testRelativeEnglishHours() {
        val now = millis(2026, 10, 1, 10, 0)
        val spec = ReminderParser.parse("remind me in 2 hours to call mom", now)
        assertNotNull(spec)
        assertEquals(now + 2 * 3_600_000L, spec?.triggerAtMillis)
        assertEquals("call mom", spec?.note)
    }

    @Test
    fun testRelativeSecondsAndDays() {
        val now = millis(2026, 10, 1, 10, 0)
        val seconds = ReminderParser.parse("ingatkan saya dalam 30 detik untuk cek oven", now)
        assertEquals(now + 30_000L, seconds?.triggerAtMillis)
        assertEquals("cek oven", seconds?.note)

        val days = ReminderParser.parse("remind me in 1 day to pay rent", now)
        assertEquals(now + 86_400_000L, days?.triggerAtMillis)
        assertEquals("pay rent", days?.note)
    }

    @Test
    fun testAbsoluteTodayIndonesian() {
        val now = millis(2026, 10, 1, 10, 0)
        val spec = ReminderParser.parse("ingatkan saya jam 15:30 untuk meeting", now)
        assertNotNull(spec)
        assertEquals(millis(2026, 10, 1, 15, 30), spec?.triggerAtMillis)
        assertEquals("meeting", spec?.note)
    }

    @Test
    fun testAbsolutePastTimeRollsToTomorrow() {
        val now = millis(2026, 10, 1, 16, 0)
        val spec = ReminderParser.parse("ingatkan saya jam 15:30 untuk meeting", now)
        assertNotNull(spec)
        assertEquals(millis(2026, 10, 2, 15, 30), spec?.triggerAtMillis)
    }

    @Test
    fun testTomorrowIndonesian() {
        val now = millis(2026, 10, 1, 10, 0)
        val spec = ReminderParser.parse("ingatkan saya besok jam 9 untuk lari pagi", now)
        assertNotNull(spec)
        assertEquals(millis(2026, 10, 2, 9, 0), spec?.triggerAtMillis)
        assertEquals("lari pagi", spec?.note)

        val withMinutes = ReminderParser.parse("ingatkan saya besok pukul 07:30 untuk sahur", now)
        assertEquals(millis(2026, 10, 2, 7, 30), withMinutes?.triggerAtMillis)
    }

    @Test
    fun testTomorrowEnglishAmPm() {
        val now = millis(2026, 10, 1, 10, 0)
        val morning = ReminderParser.parse("remind me tomorrow at 9am to join standup", now)
        assertNotNull(morning)
        assertEquals(millis(2026, 10, 2, 9, 0), morning?.triggerAtMillis)
        assertEquals("join standup", morning?.note)

        val evening = ReminderParser.parse("remind me tomorrow at 5pm to call dad", now)
        assertEquals(millis(2026, 10, 2, 17, 0), evening?.triggerAtMillis)

        val noon = ReminderParser.parse("remind me tomorrow at 12pm to lunch", now)
        assertEquals(millis(2026, 10, 2, 12, 0), noon?.triggerAtMillis)

        val midnight = ReminderParser.parse("remind me tomorrow at 12am to sleep", now)
        assertEquals(millis(2026, 10, 2, 0, 0), midnight?.triggerAtMillis)
    }

    @Test
    fun testAbsoluteTodayEnglishAmPm() {
        val now = millis(2026, 10, 1, 10, 0)

        val afternoon = ReminderParser.parse("remind me at 3pm to drink water", now)
        assertEquals(millis(2026, 10, 1, 15, 0), afternoon?.triggerAtMillis)
        assertEquals("drink water", afternoon?.note)

        val withMinutes = ReminderParser.parse("remind me at 3:30pm to stretch", now)
        assertEquals(millis(2026, 10, 1, 15, 30), withMinutes?.triggerAtMillis)

        // Noon and midnight are not swapped around.
        assertEquals(
            millis(2026, 10, 1, 12, 0),
            ReminderParser.parse("remind me at 12pm to lunch", now)?.triggerAtMillis,
        )
        assertEquals(
            millis(2026, 10, 2, 0, 30),
            ReminderParser.parse("remind me at 12:30am to sleep", now)?.triggerAtMillis,
        )
    }

    @Test
    fun testAbsoluteTodayEnglish24Hour() {
        val now = millis(2026, 10, 1, 10, 0)
        assertEquals(
            millis(2026, 10, 1, 15, 30),
            ReminderParser.parse("remind me at 15:30 to meeting", now)?.triggerAtMillis,
        )
        // Same rollover rule as the Indonesian form: a time already past means tomorrow.
        assertEquals(
            millis(2026, 10, 2, 9, 0),
            ReminderParser.parse("remind me at 09:00 to standup", now)?.triggerAtMillis,
        )
    }

    @Test
    fun testPastTimeRollsToTomorrowInEnglish() {
        val now = millis(2026, 10, 1, 16, 0)
        assertEquals(
            millis(2026, 10, 2, 15, 30),
            ReminderParser.parse("remind me at 3:30pm to call mom", now)?.triggerAtMillis,
        )
    }

    @Test
    fun testInvalidEnglishTimeReturnsNull() {
        val now = millis(2026, 10, 1, 10, 0)
        assertNull(ReminderParser.parse("remind me at 13pm to call mom", now))
        assertNull(ReminderParser.parse("remind me at 3:75pm to call mom", now))
        assertNull(ReminderParser.parse("remind me at 25:00 to call mom", now))
    }

    @Test
    fun testCaseInsensitive() {
        val now = millis(2026, 10, 1, 10, 0)
        val spec = ReminderParser.parse("INGATKAN SAYA DALAM 5 MENIT UNTUK Istirahat", now)
        assertNotNull(spec)
        assertEquals(now + 5 * 60_000L, spec?.triggerAtMillis)
        assertEquals("Istirahat", spec?.note)
    }

    @Test
    fun testNoMatchReturnsNull() {
        val now = millis(2026, 10, 1, 10, 0)
        assertNull(ReminderParser.parse("halo apa kabar", now))
        assertNull(ReminderParser.parse("ingatkan saya untuk makan", now))
        assertNull(ReminderParser.parse("remind me to call mom", now))
        assertNull(ReminderParser.parse("", now))
        assertNull(ReminderParser.parse("   ", now))
    }

    @Test
    fun testBlankNoteReturnsNull() {
        val now = millis(2026, 10, 1, 10, 0)
        assertNull(ReminderParser.parse("ingatkan saya dalam 5 menit untuk   ", now))
    }

    @Test
    fun testInvalidTimeReturnsNull() {
        val now = millis(2026, 10, 1, 10, 0)
        assertNull(ReminderParser.parse("ingatkan saya jam 25:00 untuk meeting", now))
        assertNull(ReminderParser.parse("ingatkan saya jam 10:75 untuk meeting", now))
    }
}
