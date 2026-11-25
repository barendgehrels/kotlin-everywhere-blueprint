package org.example.slippy.geocode

import org.example.slippy.claude.callClaude
import org.example.slippy.claude.decodeAiJson
import org.example.slippy.foundation.Call
import org.example.slippy.foundation.ERROR_CODE_GENERAL
import org.example.slippy.foundation.ERROR_CODE_PROVIDER
import org.example.slippy.foundation.ServiceException
import org.example.slippy.foundation.decodeBody
import org.example.slippy.foundation.guardRequest
import org.example.slippy.foundation.isAiDebug
import org.example.slippy.foundation.jsonResponse
import org.example.slippy.foundation.requireVerifiedUserId
import org.example.slippy.foundation.storeAiCall
import web.http.Response

/** POST /geocode: a place name to its most probable location, by Claude. Verified users only. */
suspend fun handleGeocode(call: Call): Response {
    requireVerifiedUserId(call)
    val userId = guardRequest(call)
    val body = call.request.decodeBody<GeocodeRequest>()
    if (!isPlaceNameValid(body.placeName)) throw ServiceException(ERROR_CODE_GENERAL, "Invalid place name", 400)
    val result = callClaude(call.env, GEOCODE_SYSTEM_PROMPT, geocodeUserPrompt(body.placeName))
    // Paid for, also when the answer turns out unusable
    storeAiCall(call, userId, "geocode", "claude", result.model, result.inputTokens, result.outputTokens)
    val response = checkGeocodeAnswer(decodeAiJson<GeocodeAnswer>(result.content, isAiDebug(call.env))) ?: run {
        if (isAiDebug(call.env)) console.log("Unusable geocode answer: ${result.content}")
        console.error("Claude's geocode answer is out of range or without a name")
        throw ServiceException(ERROR_CODE_PROVIDER, "The AI answered something unusable", 502)
    }
    return jsonResponse(response)
}
