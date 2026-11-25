package org.example.slippy.foundation

import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeTest {
    private fun millis(iso: String) = Date.parse(iso)

    @Test
    fun weekStartsOnMondayMidnightUtc() =
        // 2026-11-25 is a Wednesday
        assertEquals("2026-11-23T00:00:00.000Z", isoTime(startOfWeekMillis(millis("2026-11-25T13:35:00.000Z"))))

    @Test
    fun mondayItselfStartsItsOwnWeek() =
        assertEquals("2026-11-23T00:00:00.000Z", isoTime(startOfWeekMillis(millis("2026-11-23T00:00:00.000Z"))))

    @Test
    fun sundayBelongsToTheWeekBefore() =
        assertEquals("2026-11-16T00:00:00.000Z", isoTime(startOfWeekMillis(millis("2026-11-22T23:59:59.999Z"))))

    @Test
    fun dayIsUtc() {
        assertEquals("2026-11-25", utcDay(millis("2026-11-25T23:30:00.000Z")))
        assertEquals("2026-11-25T00:00:00.000Z", isoTime(startOfDayMillis(millis("2026-11-25T23:30:00.000Z"))))
    }
}
