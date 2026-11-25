package org.example.slippy.tile

import org.example.slippy.foundation.Call
import org.example.slippy.foundation.ENV_GUEST_TILES_PER_DAY
import org.example.slippy.foundation.ENV_MAPTILER_API_KEY
import org.example.slippy.foundation.ERROR_CODE_GENERAL
import org.example.slippy.foundation.ERROR_CODE_GUEST_QUOTA
import org.example.slippy.foundation.ERROR_CODE_PROVIDER
import org.example.slippy.foundation.ServiceException
import org.example.slippy.foundation.afterResponse
import org.example.slippy.foundation.headersOf
import org.example.slippy.foundation.nowMillis
import org.example.slippy.foundation.requireDb
import org.example.slippy.foundation.requireEnv
import org.example.slippy.foundation.requireIntEnv
import org.example.slippy.foundation.requireUser
import org.example.slippy.foundation.requireWithinLimit
import org.example.slippy.foundation.utcDay
import web.http.BodyInit
import web.http.Response
import web.http.ResponseInit
import web.http.fetch

/** GET /tile/{z}/{x}/{y}: MapTiler's PNG, for a guest too, without caching. */
suspend fun handleTile(call: Call, path: String): Response {
    val user = requireUser(call, allowGuest = true)
    requireWithinLimit(call.env.TILE_LIMITER, "TILE_LIMITER", "user:${user.id}")
    val tile = tileOfPath(path) ?: throw ServiceException(ERROR_CODE_GENERAL, "Invalid tile", 400)
    val apiKey = requireEnv(call.env.MAPTILER_API_KEY, ENV_MAPTILER_API_KEY)
    if (user.isGuest) requireGuestQuota(call, user.id)
    val upstream = try {
        fetch(mapTilerUrl(tile, apiKey)).value
    } catch (e: Throwable) {
        console.error("MapTiler could not be reached: ${redactKey(e.message.orEmpty(), apiKey)}")
        throw tileFailed()
    }
    if (!upstream.ok || upstream.body == null) {
        // Only the status: MapTiler's text may repeat the URL, with the key
        console.error("MapTiler failed with ${upstream.status} for tile ${tile.z}/${tile.x}/${tile.y}")
        throw tileFailed()
    }
    if (user.isGuest) countGuestTile(call, user.id)
    return Response(upstream.body.unsafeCast<BodyInit>(), ResponseInit(status = 200.toShort(), headers = headersOf(
        "Content-Type" to "image/png",
        "Cache-Control" to "no-store",
    )))
}

/** A guest's quota of today: a 429 (GUEST_QUOTA) when it is used up. */
private suspend fun requireGuestQuota(call: Call, userId: Int) {
    val tilesPerDay = requireIntEnv(call.env.GUEST_TILES_PER_DAY, ENV_GUEST_TILES_PER_DAY)
    val db = requireDb(call.env)
    val today = utcDay(nowMillis())
    val count = guestTileCounts.count(userId, today) { loadUsageDay(db, userId, today) }
    if (!isWithinGuestQuota(count, tilesPerDay)) {
        throw ServiceException(ERROR_CODE_GUEST_QUOTA, "Guest tiles of today used up", 429)
    }
}

/** Only a delivered tile counts: in memory now, in the database after the response. */
private fun countGuestTile(call: Call, userId: Int) {
    val db = requireDb(call.env)
    val today = utcDay(nowMillis())
    guestTileCounts.increment(userId, today)
    afterResponse(call, "storeUsageDayTile") { storeUsageDayTile(db, userId, today) }
}

private fun tileFailed() = ServiceException(ERROR_CODE_PROVIDER, "Tile provider failed", 502)
