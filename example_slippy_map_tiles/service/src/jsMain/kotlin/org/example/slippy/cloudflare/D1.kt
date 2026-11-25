package org.example.slippy.cloudflare

import kotlin.js.Promise

// Cloudflare D1, only what the service uses: https://developers.cloudflare.com/d1/worker-api/

external interface D1Database {
    fun prepare(query: String): D1PreparedStatement
}

external interface D1PreparedStatement {
    /** Numbers, strings and null only: a Boolean goes in as 0 or 1, never a Long (a BigInt). */
    fun bind(vararg values: Any?): D1PreparedStatement

    /** The first row, or null. */
    fun <T> first(): Promise<T?>

    fun run(): Promise<D1Result>
}

external interface D1Result {
    val success: Boolean
}
