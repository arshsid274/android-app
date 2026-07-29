package com.cue.reply.llm

import com.cue.reply.data.settings.LlmProvider

/** Model id → display label, per provider. Single source for every picker UI. */
object LlmCatalog {
    val ANTHROPIC_MODELS = listOf(
        "claude-sonnet-4-5" to "Claude Sonnet 4.5 (Recommended)",
        "claude-haiku-4-5-20251001" to "Claude Haiku 4.5 (Fast)",
        "claude-opus-5" to "Claude Opus 5",
    )

    val OPENAI_MODELS = listOf(
        "gpt-5.2" to "GPT-5.2 (Recommended)",
        "gpt-5.1" to "GPT-5.1",
        "gpt-5" to "GPT-5",
        "gpt-5-mini" to "GPT-5 Mini (Fast)",
        "gpt-5-nano" to "GPT-5 Nano (Fastest)",
        "gpt-4.1" to "GPT-4.1",
        "gpt-4o" to "GPT-4o",
    )

    /** xAI's API is OpenAI-compatible, so these run through [OpenAiLlmClient]. */
    val GROK_MODELS = listOf(
        "grok-4-fast" to "Grok 4 Fast (Recommended)",
        "grok-4" to "Grok 4",
        "grok-3" to "Grok 3",
        "grok-3-mini" to "Grok 3 Mini (Cheapest)",
    )

    /** Groq serves open models on its own hardware; the API is OpenAI-compatible. */
    val GROQ_MODELS = listOf(
        "llama-3.3-70b-versatile" to "Llama 3.3 70B (Recommended)",
        "llama-3.1-8b-instant" to "Llama 3.1 8B (Fastest)",
        "openai/gpt-oss-120b" to "GPT-OSS 120B",
        "openai/gpt-oss-20b" to "GPT-OSS 20B",
        "moonshotai/kimi-k2-instruct" to "Kimi K2",
        "qwen/qwen3-32b" to "Qwen 3 32B",
        "deepseek-r1-distill-llama-70b" to "DeepSeek R1 Distill 70B",
    )

    fun modelsFor(provider: LlmProvider) = when (provider) {
        LlmProvider.ANTHROPIC -> ANTHROPIC_MODELS
        LlmProvider.OPENAI -> OPENAI_MODELS
        LlmProvider.GROK -> GROK_MODELS
        LlmProvider.GROQ -> GROQ_MODELS
    }
}
