package org.example.slippy.foundation

import kotlinx.coroutines.await
import org.example.slippy.cloudflare.D1Database

/** A row of `users`, as D1 returns it. */
external interface UserRow {
    val id: Int
    val firebase_id: String
    val email: String?
    val email_verified: Int
    val is_guest: Int
    val name: String?
    val weekly_budget_ticks: Int?
    val total_budget_ticks: Int?
}

/** Our user: the row, read once per call. */
class User(
    val id: Int,
    val isGuest: Boolean,
    val name: String?,
    val weeklyBudgetTicks: Int?,
    val totalBudgetTicks: Int?,
)

private fun UserRow.toUser() = User(id, is_guest == 1, name, weekly_budget_ticks, total_budget_ticks)

private fun Boolean.toDb(): Int = if (this) 1 else 0

suspend fun findUser(db: D1Database, firebaseId: String): User? =
    db.prepare("SELECT * FROM users WHERE firebase_id = ?").bind(firebaseId).first<UserRow>().await()?.toUser()

/** Makes or updates the row of a registered user (login, refresh, register, the first call). */
suspend fun syncRegisteredUser(db: D1Database, firebaseId: String, email: String?, emailVerified: Boolean): User {
    val now = nowIso()
    db.prepare(
        """
        INSERT INTO users (firebase_id, email, email_verified, is_guest, created_at, synced_at)
        VALUES (?, ?, ?, 0, ?, ?)
        ON CONFLICT (firebase_id) DO UPDATE SET
          email = excluded.email, email_verified = excluded.email_verified, is_guest = 0,
          synced_at = excluded.synced_at
        """.trimIndent()
    ).bind(firebaseId, email, emailVerified.toDb(), now, now).run().await()
    return findUser(db, firebaseId) ?: throw IllegalStateException("User row missing after its insert")
}

/** Only POST /guest makes a guest. */
suspend fun insertGuest(db: D1Database, firebaseId: String, name: String) {
    val now = nowIso()
    db.prepare(
        "INSERT INTO users (firebase_id, is_guest, name, created_at, synced_at) VALUES (?, 1, ?, ?, ?)"
    ).bind(firebaseId, name, now, now).run().await()
}

suspend fun touchUser(db: D1Database, userId: Int) {
    db.prepare("UPDATE users SET synced_at = ? WHERE id = ?").bind(nowIso(), userId).run().await()
}

private external interface CountRow {
    val n: Int
}

suspend fun countGuestsSince(db: D1Database, sinceIso: String): Int =
    db.prepare("SELECT COUNT(*) AS n FROM users WHERE is_guest = 1 AND created_at >= ?")
        .bind(sinceIso).first<CountRow>().await()?.n ?: 0
