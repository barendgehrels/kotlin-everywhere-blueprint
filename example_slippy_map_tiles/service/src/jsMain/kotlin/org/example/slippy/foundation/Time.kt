package org.example.slippy.foundation

import kotlin.js.Date

private const val MILLIS_PER_DAY = 86_400_000.0

fun nowMillis(): Double = Date.now()

/** ISO 8601 in UTC, as the database stores times: they sort and compare as strings. */
fun isoTime(millis: Double): String = Date(millis).toISOString()

fun nowIso(): String = isoTime(nowMillis())

/** The UTC day, such as "2026-11-25". */
fun utcDay(millis: Double): String = isoTime(millis).substring(0, 10)

/** Midnight UTC at the start of the day. */
fun startOfDayMillis(millis: Double): Double = kotlin.math.floor(millis / MILLIS_PER_DAY) * MILLIS_PER_DAY

/** Monday 00:00 UTC of the week: the start of the weekly budget. */
fun startOfWeekMillis(millis: Double): Double {
    val day = kotlin.math.floor(millis / MILLIS_PER_DAY)
    // 1970-01-01 was a Thursday: day 0 is 3 days after a Monday
    val daysSinceMonday = ((day + 3) % 7 + 7) % 7
    return (day - daysSinceMonday) * MILLIS_PER_DAY
}
