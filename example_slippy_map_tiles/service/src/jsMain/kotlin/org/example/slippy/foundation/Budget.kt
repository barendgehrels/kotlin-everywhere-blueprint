package org.example.slippy.foundation

import kotlinx.coroutines.await
import org.example.slippy.cloudflare.D1Database
import kotlin.math.ceil

// Budgets are in ticks (1 tick = $0.0001), not tokens. See migrations/0001_init.sql.

/** A user's limits; a guest has no weekly budget, only its total. */
data class BudgetLimits(val weeklyTicks: Int?, val totalTicks: Int)

fun budgetLimits(user: User, env: Env): BudgetLimits =
    if (user.isGuest) {
        BudgetLimits(null, user.totalBudgetTicks
            ?: requireIntEnv(env.GUEST_TOTAL_BUDGET_TICKS, ENV_GUEST_TOTAL_BUDGET_TICKS))
    } else {
        BudgetLimits(
            user.weeklyBudgetTicks ?: requireIntEnv(env.WEEKLY_BUDGET_TICKS, ENV_WEEKLY_BUDGET_TICKS),
            user.totalBudgetTicks ?: requireIntEnv(env.TOTAL_BUDGET_TICKS, ENV_TOTAL_BUDGET_TICKS))
    }

/** The 429 for a used-up budget, the total before the week; null within the budget. */
fun budgetExceeded(usedThisWeek: Int, usedTotal: Int, limits: BudgetLimits): ServiceException? = when {
    usedTotal >= limits.totalTicks ->
        ServiceException(ERROR_CODE_TOTAL_BUDGET_EXCEEDED, "Total budget used up", 429)
    limits.weeklyTicks != null && usedThisWeek >= limits.weeklyTicks ->
        ServiceException(ERROR_CODE_BUDGET_EXCEEDED, "Weekly budget used up", 429)
    else -> null
}

/** The ticks of a call, rounded up: a call with tokens never costs nothing. */
fun ticksFor(inputTokens: Int, outputTokens: Int, ticksPerMillionInput: Int, ticksPerMillionOutput: Int): Int =
    ceil((inputTokens.toDouble() * ticksPerMillionInput + outputTokens.toDouble() * ticksPerMillionOutput) / 1_000_000.0)
        .toInt()

private external interface UsageRow {
    val total: Int
    val week: Int
}

suspend fun requireWithinBudget(call: Call, user: User) {
    val limits = budgetLimits(user, call.env)
    val weekStart = isoTime(startOfWeekMillis(nowMillis()))
    val usage = requireDb(call.env).prepare(
        """
        SELECT COALESCE(SUM(ticks), 0) AS total,
               COALESCE(SUM(CASE WHEN created_at >= ? THEN ticks ELSE 0 END), 0) AS week
        FROM ai_calls WHERE user_id = ?
        """.trimIndent()
    ).bind(weekStart, user.id).first<UsageRow>().await()
    budgetExceeded(usage?.week ?: 0, usage?.total ?: 0, limits)?.let { throw it }
}

private external interface IdRow {
    val id: Int
}

private external interface RateRow {
    val input: Int?
    val output: Int?
}

/** Stores a paid call, after the response, with its ticks worked out now. */
fun storeAiCall(
    call: Call, userId: Int, operation: String, provider: String, model: String,
    inputTokens: Int, outputTokens: Int,
) = afterResponse(call, "storeAiCall") {
    val db = requireDb(call.env)
    val now = nowIso()
    val modelId = modelId(db, model, provider)
    val rate = db.prepare(
        """
        SELECT ticks_per_million_input AS input, ticks_per_million_output AS output FROM model_rates
        WHERE model_id = ? AND valid_from <= ? ORDER BY valid_from DESC LIMIT 1
        """.trimIndent()
    ).bind(modelId, now).first<RateRow>().await()
        ?: run {
            console.error("Model $model has no rate: counted at the highest known rate. Add its rate.")
            db.prepare(
                "SELECT MAX(ticks_per_million_input) AS input, MAX(ticks_per_million_output) AS output FROM model_rates"
            ).first<RateRow>().await()
        }
    val ticks = ticksFor(inputTokens, outputTokens, rate?.input ?: 0, rate?.output ?: 0)
    db.prepare(
        """
        INSERT INTO ai_calls (user_id, created_at, operation, model_id, input_tokens, output_tokens, ticks)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
    ).bind(userId, now, operation, modelId, inputTokens, outputTokens, ticks).run().await()
}

/** The model's id; a model used for the first time is added. */
private suspend fun modelId(db: D1Database, model: String, provider: String): Int {
    db.prepare("INSERT INTO models (name, provider) VALUES (?, ?) ON CONFLICT (name) DO NOTHING")
        .bind(model, provider).run().await()
    return db.prepare("SELECT id FROM models WHERE name = ?").bind(model).first<IdRow>().await()?.id
        ?: throw IllegalStateException("Model row missing after its insert")
}
