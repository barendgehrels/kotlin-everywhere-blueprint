package org.example.slippy.foundation

import org.example.slippy.cloudflare.D1Database
import org.example.slippy.cloudflare.RateLimiter

/** Everything the worker gets from Cloudflare: every property nullable, every var a String. */
external interface Env {
    val ALLOWED_ORIGIN: String?            // var: the web app's origins, comma-separated
    val FIREBASE_API_KEY: String?          // var: public, it is in every app as well
    val FIREBASE_PROJECT_ID: String?       // var
    val CONFIRM_URL: String?               // var: the page the email links end on (/confirm)
    val ANTHROPIC_MODEL: String?           // var
    val ANTHROPIC_MAX_TOKENS: String?      // var: a number, as text
    val ANTHROPIC_API_KEY: String?         // secret
    val WEEKLY_BUDGET_TICKS: String?       // var
    val TOTAL_BUDGET_TICKS: String?        // var
    val GUEST_TOTAL_BUDGET_TICKS: String?  // var
    val GUEST_DAILY_MAX: String?           // var: new guests a day, in all
    val GUEST_TILES_PER_DAY: String?       // var
    val TURNSTILE_SITE_KEY: String?        // var: public
    val TURNSTILE_SECRET_KEY: String?      // secret
    val MAPTILER_API_KEY: String?          // secret
    val DEBUG_AI: String?                  // var: "true" logs the user's text and the AI's answer
    val DB: D1Database?                    // binding: [[d1_databases]]
    val GUEST_LIMITER: RateLimiter?        // binding: [[ratelimits]]
    val LOGIN_LIMITER: RateLimiter?
    val ACCOUNT_LIMITER: RateLimiter?
    val USER_LIMITER: RateLimiter?
    val TILE_LIMITER: RateLimiter?
}

const val ENV_ALLOWED_ORIGIN = "ALLOWED_ORIGIN"
const val ENV_FIREBASE_API_KEY = "FIREBASE_API_KEY"
const val ENV_FIREBASE_PROJECT_ID = "FIREBASE_PROJECT_ID"
const val ENV_CONFIRM_URL = "CONFIRM_URL"
const val ENV_ANTHROPIC_MODEL = "ANTHROPIC_MODEL"
const val ENV_ANTHROPIC_API_KEY = "ANTHROPIC_API_KEY"
const val ENV_WEEKLY_BUDGET_TICKS = "WEEKLY_BUDGET_TICKS"
const val ENV_TOTAL_BUDGET_TICKS = "TOTAL_BUDGET_TICKS"
const val ENV_GUEST_TOTAL_BUDGET_TICKS = "GUEST_TOTAL_BUDGET_TICKS"
const val ENV_GUEST_DAILY_MAX = "GUEST_DAILY_MAX"
const val ENV_GUEST_TILES_PER_DAY = "GUEST_TILES_PER_DAY"
const val ENV_MAPTILER_API_KEY = "MAPTILER_API_KEY"

fun isAiDebug(env: Env): Boolean = env.DEBUG_AI == "true"
