package com.cue.reply.llm

/** Minimal transcript turn passed to the model. */
data class LlmTurn(val role: String, val content: String)

sealed interface LlmResult {
    data class Success(val text: String) : LlmResult
    data class Error(val reason: String) : LlmResult
}

/** Provider-agnostic chat completion. Swap the implementation to change backends. */
interface LlmClient {
    suspend fun complete(
        system: String,
        turns: List<LlmTurn>,
        model: String,
        maxTokens: Int,
    ): LlmResult
}
