package com.cue.reply.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Default backend: Anthropic Messages API. Provider details are isolated here so
 * a different LLM only means a new [LlmClient]. The API key is supplied per call
 * from settings and never hard-coded.
 */
class AnthropicLlmClient(
    private val apiKeyProvider: () -> String,
    private val baseUrlProvider: () -> String,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build(),
) : LlmClient {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mediaType = "application/json".toMediaType()

    override suspend fun complete(
        system: String,
        turns: List<LlmTurn>,
        model: String,
        maxTokens: Int,
    ): LlmResult = withContext(Dispatchers.IO) {
        val key = apiKeyProvider().trim()
        if (key.isEmpty()) return@withContext LlmResult.Error("No API key set")

        val payload = MessagesRequest(
            model = model,
            maxTokens = maxTokens,
            system = system,
            messages = turns.map { MessageParam(it.role, it.content) },
        )
        val request = Request.Builder()
            .url(baseUrlProvider().trimEnd('/') + "/v1/messages")
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(json.encodeToString(payload).toRequestBody(mediaType))
            .build()

        try {
            http.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext LlmResult.Error("HTTP ${resp.code}: ${body.take(300)}")
                }
                val parsed = json.decodeFromString<MessagesResponse>(body)
                val text = parsed.content.firstOrNull { it.type == "text" }?.text?.trim()
                if (text.isNullOrEmpty()) LlmResult.Error("Empty completion")
                else LlmResult.Success(text)
            }
        } catch (e: Exception) {
            LlmResult.Error(e.message ?: "Network error")
        }
    }

    @Serializable
    private data class MessagesRequest(
        val model: String,
        @SerialName("max_tokens") val maxTokens: Int,
        val system: String,
        val messages: List<MessageParam>,
    )

    @Serializable
    private data class MessageParam(val role: String, val content: String)

    @Serializable
    private data class MessagesResponse(val content: List<ContentBlock> = emptyList())

    @Serializable
    private data class ContentBlock(val type: String = "", val text: String? = null)
}
