package org.example.slippy.tile

import kotlinx.coroutines.await
import org.example.slippy.cloudflare.D1Database

// A guest's tiles of today, counted in memory per isolate, starting from what the database had:
// one read per guest a day per isolate. A little more gets through, never much.

/** Whether a guest that already had [countToday] tiles today may have one more. */
fun isWithinGuestQuota(countToday: Int, tilesPerDay: Int): Boolean = countToday < tilesPerDay

/** The counts of one day in memory; a new day starts empty. */
class DailyCounts {
    private var day = ""
    private val counts = mutableMapOf<Int, Int>()

    /** The count of [userId] on [today], loading it once with [load] when memory has none. */
    suspend fun count(userId: Int, today: String, load: suspend () -> Int): Int {
        if (today != day) {
            day = today
            counts.clear()
        }
        return counts[userId] ?: load().also { counts[userId] = it }
    }

    fun increment(userId: Int, today: String) {
        if (today == day) counts[userId] = (counts[userId] ?: 0) + 1
    }
}

internal val guestTileCounts = DailyCounts()

private external interface UsageDayRow {
    val count: Int
}

suspend fun loadUsageDay(db: D1Database, userId: Int, day: String): Int =
    db.prepare("SELECT count FROM usage_days WHERE user_id = ? AND day = ?")
        .bind(userId, day).first<UsageDayRow>().await()?.count ?: 0

/** Adds one to the database's count: what other isolates counted is kept. */
suspend fun storeUsageDayTile(db: D1Database, userId: Int, day: String) {
    db.prepare(
        """
        INSERT INTO usage_days (user_id, day, count) VALUES (?, ?, 1)
        ON CONFLICT (user_id, day) DO UPDATE SET count = count + 1
        """.trimIndent()
    ).bind(userId, day).run().await()
}
