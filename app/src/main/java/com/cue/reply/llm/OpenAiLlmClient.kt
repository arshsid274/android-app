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
 * OpenAI backend via the Chat Completions API. Mirrors [AnthropicLlmClient]:
 * key and endpoint come from settings per call, nothing hard-coded.
 */
class OpenAiLlmClient(
    private val apiKeyProvider: () -> String,
    private val baseUrlProvider: () -> String,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build(),
) : LlmClient {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val mediaType = "application/json".toMediaType()

    override suspend fun complete(
        system: String,
        turns: List<LlmTurn>,
        model: String,
        maxTokens: Int,
    ): LlmResult = withContext(Dispatchers.IO) {
        val key = apiKeyProvider().trim()
        if (key.isEmpty()) return@withContext LlmResult.Error("No API key set")

        // GPT-5-family models spend completion tokens on internal reasoning, so a
        // small cap can yield an empty message; keep effort low and floor the cap.
        val isGpt5 = model.startsWith("gpt-5")
        val payload = ChatRequest(
            model = model,
            maxCompletionTokens = if (isGpt5) maxOf(maxTokens, 256) else maxTokens,
            reasoningEffort = if (isGpt5) "low" else null,
            messages = buildList {
                add(ChatMessage("system", system))
                turns.forEach { add(ChatMessage(it.role, it.content)) }
            },
        )
        val request = Request.Builder()
            .url(baseUrlProvider().trimEnd('/') + "/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("content-type", "application/json")
            .post(json.encodeToString(payload).toRequestBody(mediaType))
            .build()

        try {
            http.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext LlmResult.Error("HTTP ${resp.code}: ${body.take(300)}")
                }
                val parsed = json.decodeFromString<ChatResponse>(body)
                val text = parsed.choices.firstOrNull()?.message?.content?.trim()
                if (text.isNullOrEmpty()) LlmResult.Error("Empty completion")
                else LlmResult.Success(text)
            }
        } catch (e: Exception) {
            LlmResult.Error(e.message ?: "Network error")
        }
    }

    @Serializable
    private data class ChatRequest(
        val model: String,
        @SerialName("max_completion_tokens") val maxCompletionTokens: Int,
        @SerialName("reasoning_effort") val reasoningEffort: String? = null,
        val messages: List<ChatMessage>,
    )

    @Serializable
    private data class ChatMessage(val role: String, val content: String)

    @Serializable
    private data class ChatResponse(val choices: List<Choice> = emptyList())

    @Serializable
    private data class Choice(val message: ChoiceMessage? = null)

    @Serializable
    private data class ChoiceMessage(val content: String? = null)
}
