package org.example.slippy.claude

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.example.slippy.foundation.ERROR_CODE_PROVIDER
import org.example.slippy.foundation.ServiceException
import org.example.slippy.foundation.serviceJson

/** The outermost {…} blocks of [text], in order; braces inside JSON strings don't count. */
fun jsonObjectBlocks(text: String): List<String> {
    val blocks = mutableListOf<String>()
    var depth = 0
    var start = -1
    var inString = false
    var escaped = false
    for ((i, c) in text.withIndex()) {
        when {
            inString && escaped -> escaped = false
            inString && c == '\\' -> escaped = true
            inString && c == '"' -> inString = false
            inString -> {}
            c == '"' -> if (depth > 0) inString = true
            c == '{' -> { if (depth == 0) start = i; depth++ }
            c == '}' && depth > 0 -> { depth--; if (depth == 0) blocks += text.substring(start, i + 1) }
        }
    }
    return blocks
}

/** The last complete {…} block of a model's answer that decodes, or null: a model sometimes adds a fence or a remark. */
fun <T> decodeAiJsonOrNull(text: String, serializer: KSerializer<T>): T? =
    jsonObjectBlocks(text).asReversed().firstNotNullOfOrNull { block ->
        try {
            serviceJson.decodeFromString(serializer, block)
        } catch (e: Exception) {
            null
        }
    }

/**
 * As [decodeAiJsonOrNull]; no valid block is a 502 (PROVIDER). The log gets the answer's length,
 * and the answer itself only with [debug]: a model often repeats the user's text.
 */
inline fun <reified T> decodeAiJson(text: String, debug: Boolean): T =
    decodeAiJsonOrNull(text, serializer<T>()) ?: run {
        console.error("The AI answered no valid JSON (${text.length} characters)")
        if (debug) console.log("The AI's answer: $text")
        throw ServiceException(ERROR_CODE_PROVIDER, "The AI answered something unusable", 502)
    }
