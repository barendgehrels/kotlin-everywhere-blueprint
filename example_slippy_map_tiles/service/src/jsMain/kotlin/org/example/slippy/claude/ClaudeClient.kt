package org.example.slippy.claude

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.example.slippy.foundation.ENV_ANTHROPIC_API_KEY
import org.example.slippy.foundation.ENV_ANTHROPIC_MODEL
import org.example.slippy.foundation.Env
import org.example.slippy.foundation.callProvider
import org.example.slippy.foundation.isAiDebug
import org.example.slippy.foundation.requireEnv

/** What a handler gets back from an AI call: the text, and what to bill. */
class AiResult(val content: String, val model: String, val inputTokens: Int, val outputTokens: Int)

@Serializable
private data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<ClaudeMessage>,
)

@Serializable
private data class ClaudeMessage(val role: String, val content: String)

@Serializable
private data class ClaudeResponse(
    val content: List<ClaudeContent> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: ClaudeUsage,
)

@Serializable
private data class ClaudeContent(val type: String, val text: String? = null)

@Serializable
private data class ClaudeUsage(
    @SerialName("input_tokens") val inputTokens: Int,
    @SerialName("output_tokens") val outputTokens: Int,
)

suspend fun callClaude(env: Env, systemPrompt: String, userPrompt: String): AiResult {
    val model = requireEnv(env.ANTHROPIC_MODEL, ENV_ANTHROPIC_MODEL)
    val response = callProvider<ClaudeRequest, ClaudeResponse>(name = "Claude",
        url = "https://api.anthropic.com/v1/messages",
        request = ClaudeRequest(
            model = model,
            maxTokens = env.ANTHROPIC_MAX_TOKENS?.toIntOrNull() ?: 1024,
            system = systemPrompt,
            messages = listOf(ClaudeMessage(role = "user", content = userPrompt))),
        headers = mapOf(
            "x-api-key" to requireEnv(env.ANTHROPIC_API_KEY, ENV_ANTHROPIC_API_KEY),
            "anthropic-version" to "2023-06-01"),
        debug = isAiDebug(env))
    val text = response.content.firstOrNull { it.type == "text" }?.text.orEmpty()
    if (response.stopReason == "max_tokens") console.warn("Claude reached max_tokens: the answer is cut off")
    return AiResult(text, model, response.usage.inputTokens, response.usage.outputTokens)
}
