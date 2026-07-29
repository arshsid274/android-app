package com.cue.reply.llm

import com.cue.reply.data.settings.LlmProvider

/** Delegates each call to the backend currently selected in settings. */
class RoutingLlmClient(
    private val providerSelector: () -> LlmProvider,
    private val anthropic: LlmClient,
    private val openAi: LlmClient,
    private val grok: LlmClient,
    private val groq: LlmClient,
) : LlmClient {
    override suspend fun complete(
        system: String,
        turns: List<LlmTurn>,
        model: String,
        maxTokens: Int,
    ): LlmResult = when (providerSelector()) {
        LlmProvider.ANTHROPIC -> anthropic.complete(system, turns, model, maxTokens)
        LlmProvider.OPENAI -> openAi.complete(system, turns, model, maxTokens)
        LlmProvider.GROK -> grok.complete(system, turns, model, maxTokens)
        LlmProvider.GROQ -> groq.complete(system, turns, model, maxTokens)
    }
}
