package com.cue.reply.llm

import com.cue.reply.data.db.entity.MessageEntity
import com.cue.reply.domain.model.MessageDirection

/**
 * Measured fingerprint of how the user actually writes to one specific person.
 * Derived from their own outgoing messages so drafts imitate them instead of a
 * generic "assistant" voice.
 */
data class StyleProfile(
    val sampleCount: Int,
    val avgWords: Int,
    val emojiRate: Double,
    val lowercaseRate: Double,
    val questionRate: Double,
    val exclamationRate: Double,
    val commonEmojis: List<String>,
    val commonOpeners: List<String>,
) {
    val isUsable: Boolean get() = sampleCount >= MIN_SAMPLES

    /** Prompt fragment describing the measured habits; empty when there isn't enough data. */
    fun toPromptSection(): String {
        if (!isUsable) return ""
        return buildString {
            appendLine("MEASURED FACTS about how I write to this person (copy these habits):")
            appendLine("- My replies average about $avgWords words. Stay near that length.")
            appendLine(
                when {
                    emojiRate >= 0.5 -> "- I use emojis often" +
                        if (commonEmojis.isNotEmpty()) " — mostly ${commonEmojis.joinToString(" ")}." else "."
                    emojiRate > 0.1 -> "- I use emojis sparingly. At most one, and not every message."
                    else -> "- I almost never use emojis. Do not add any."
                }
            )
            appendLine(
                if (lowercaseRate >= 0.6) "- I usually type in lowercase and skip full punctuation."
                else "- I write with normal capitalization and punctuation."
            )
            if (questionRate >= 0.4) appendLine("- I often end with a question to keep it going.")
            if (exclamationRate >= 0.4) appendLine("- I use exclamation marks freely.")
            else if (exclamationRate < 0.1) appendLine("- I rarely use exclamation marks.")
            if (commonOpeners.isNotEmpty()) {
                appendLine("- I often open with: ${commonOpeners.joinToString(", ")}.")
            }
        }
    }

    companion object {
        const val MIN_SAMPLES = 3

        private val emojiRegex =
            Regex("[\\p{So}\\p{Cn}🌀-🧿☀-➿]")

        /** Builds a profile from the user's outgoing messages in [history]. */
        fun from(history: List<MessageEntity>): StyleProfile {
            val mine = history.filter { it.direction == MessageDirection.OUTGOING }
                .map { it.text.trim() }
                .filter { it.isNotEmpty() }

            if (mine.isEmpty()) {
                return StyleProfile(0, 0, 0.0, 0.0, 0.0, 0.0, emptyList(), emptyList())
            }

            val avgWords = mine.map { it.split(Regex("\\s+")).size }.average().toInt().coerceAtLeast(1)
            val withEmoji = mine.count { emojiRegex.containsMatchIn(it) }
            val lowercase = mine.count { msg ->
                val letters = msg.filter { it.isLetter() }
                letters.isNotEmpty() && letters.none { it.isUpperCase() }
            }
            val questions = mine.count { it.contains('?') }
            val exclamations = mine.count { it.contains('!') }

            val emojis = mine.flatMap { emojiRegex.findAll(it).map { m -> m.value }.toList() }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }
                .take(4).map { it.key }

            val openers = mine.mapNotNull { msg ->
                msg.split(Regex("\\s+")).firstOrNull()?.trim(',', '.', '!', '?')?.lowercase()
            }
                .filter { it.length in 2..12 }
                .groupingBy { it }.eachCount()
                .entries.filter { it.value >= 2 }
                .sortedByDescending { it.value }
                .take(3).map { it.key }

            val n = mine.size.toDouble()
            return StyleProfile(
                sampleCount = mine.size,
                avgWords = avgWords,
                emojiRate = withEmoji / n,
                lowercaseRate = lowercase / n,
                questionRate = questions / n,
                exclamationRate = exclamations / n,
                commonEmojis = emojis,
                commonOpeners = openers,
            )
        }
    }
}
