package com.cue.reply.llm

import com.cue.reply.data.db.entity.MessageEntity
import com.cue.reply.data.settings.CueSettings
import com.cue.reply.domain.model.MessageDirection
import com.cue.reply.domain.model.ReplyDraft

sealed interface DraftResult {
    data class Ready(val drafts: List<ReplyDraft>) : DraftResult
    data class Error(val reason: String) : DraftResult
}

/**
 * Turns a conversation history into 2–3 reply drafts via the LLM. Prompt shape and
 * parsing live here so the sources/sinks stay provider-agnostic.
 */
class DraftGenerator(private val llm: LlmClient) {

    suspend fun generate(
        history: List<MessageEntity>,
        settings: CueSettings,
        steer: String? = null,
        /** Per-contact persona override; null follows the global persona. */
        personaOverride: PersonaPreset? = null,
    ): DraftResult {
        if (history.isEmpty()) return DraftResult.Error("No messages to reply to")

        val n = settings.draftCount.coerceIn(1, 5)
        val system = buildSystemPrompt(settings, n, steer, personaOverride, StyleProfile.from(history))
        val turns = history.map { it.toTurn() }

        return when (val res = llm.complete(system, turns, settings.activeModel, maxTokens = 400)) {
            is LlmResult.Error -> DraftResult.Error(res.reason)
            is LlmResult.Success -> {
                val drafts = parseDrafts(res.text, settings.maxDraftChars).take(n)
                if (drafts.isEmpty()) DraftResult.Error("Model returned no usable drafts")
                else DraftResult.Ready(drafts)
            }
        }
    }

    private fun buildSystemPrompt(
        settings: CueSettings,
        n: Int,
        steer: String?,
        personaOverride: PersonaPreset?,
        style: StyleProfile,
    ): String =
        buildString {
            appendLine((personaOverride?.prompt ?: settings.persona).trim())
            appendLine()
            style.toPromptSection().takeIf { it.isNotBlank() }?.let {
                appendLine(it)
            }
            appendLine("Draft $n distinct replies to the LAST message from the other person.")
            appendLine()
            appendLine("SOUND LIKE A REAL PERSON TEXTING:")
            appendLine("- React to what they actually said. Pick up their specific words, joke,")
            appendLine("  or detail — a reply that could be pasted into any chat is a failed reply.")
            appendLine("- Texting rhythm, not email rhythm. Contractions, casual punctuation,")
            appendLine("  sometimes a fragment instead of a full sentence.")
            appendLine("- Humour comes from being specific and a little unexpected: tease them")
            appendLine("  about the thing they just said, play along with their bit, exaggerate")
            appendLine("  slightly, act mock-offended. Never explain the joke.")
            appendLine("- Match their language, including Hinglish or mixed script, if they use it.")
            appendLine()
            appendLine("NEVER DO THESE (they read as a bot instantly):")
            appendLine("- Assistant-speak: 'How can I help', 'Let me know if', 'I'd be happy to',")
            appendLine("  'Sounds good!', 'That's great!', 'Absolutely!'")
            appendLine("- Answering with a bare acknowledgement like 'Okay', 'Yes', 'Noted', 'Later'.")
            appendLine("- Corporate or customer-service politeness. You are not a service desk.")
            appendLine("- Starting every option the same way, or repeating their sentence back.")
            appendLine("- Em dashes, bullet points, or emoji spam. At most one emoji, only if it earns its place.")
            appendLine()
            appendLine("FORMAT:")
            appendLine("- Each reply stands alone and is ready to send as-is.")
            appendLine("- Under ${settings.maxDraftChars} characters; most should be much shorter.")
            appendLine("- Make the $n options genuinely different in angle, not reworded twins.")
            if (!steer.isNullOrBlank()) appendLine("- Extra steer for this batch: ${steer.trim()}.")
            appendLine("- Output ONLY the replies, each on its own line prefixed with '- '.")
            appendLine("- No numbering, no commentary, no quotation marks, no labels.")
        }

    private fun MessageEntity.toTurn(): LlmTurn {
        // From the model's perspective: the other person is the user, I am the assistant.
        val role = if (direction == MessageDirection.INCOMING) "user" else "assistant"
        return LlmTurn(role, text)
    }

    // Strips a leading bullet ("- ", "* ") or list number ("1.", "2)") and surrounding quotes.
    private val leadMarker = Regex("""^\s*(?:[-*]\s+|\d+[.)]\s*)""")

    private fun parseDrafts(raw: String, maxChars: Int): List<ReplyDraft> =
        raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replaceFirst(leadMarker, "").trim().trim('"').trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { i, text -> ReplyDraft(id = "d$i", text = text.take(maxChars)) }
}
