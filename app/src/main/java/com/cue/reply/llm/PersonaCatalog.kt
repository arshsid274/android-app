package com.cue.reply.llm

/**
 * Tone presets for reply drafts. Used by the global persona in Settings and by the
 * per-contact override in a chat's menu.
 *
 * MY_STYLE is special: it carries no tone of its own. Instead the draft generator
 * derives the voice from the user's own past outgoing messages in that thread.
 */
enum class PersonaPreset(val label: String, val prompt: String) {
    MY_STYLE(
        "My style",
        "Write exactly the way I write. Study my own past messages in this " +
            "conversation and copy my voice: sentence length, greeting habits, " +
            "slang, the languages I mix, how much punctuation and how many emojis " +
            "I actually use, and how warm or blunt I am with this specific person. " +
            "If my messages are short and lowercase, yours must be too. Never sound " +
            "more polished, more formal, or more enthusiastic than I do.",
    ),
    WARM(
        "Warm",
        "You are helping me reply in my own voice: warm, kind, and genuinely curious " +
            "about the other person. A little playful, never sarcastic or over-eager. " +
            "Match their energy and length.",
    ),
    WITTY(
        "Witty",
        "Reply in my voice: quick and genuinely funny. The humour comes from noticing " +
            "the specific thing they said and twisting it — deadpan understatement, " +
            "mock outrage, playing along with their bit and taking it one step further, " +
            "or confidently arguing a silly position. Land it in one line and stop; " +
            "never explain the joke or add 'haha'. If nothing funny is actually there, " +
            "be warm and interesting instead of forcing a gag.",
    ),
    DIRECT(
        "Direct",
        "You are helping me reply in my own voice: straightforward and confident. " +
            "No games — say what I mean, ask clear questions, keep momentum toward " +
            "actually meeting up. Friendly but not gushing.",
    ),
    MINIMAL(
        "Minimal",
        "You are helping me reply in my own voice: relaxed and low-key. Short replies, " +
            "casual tone, effortless. Shorter is better; match their energy.",
    ),
    OFFICE(
        "Office",
        "You are helping me reply in my own voice for work chats: professional, " +
            "courteous, and clear. Get to the point, confirm actions and timelines, " +
            "no slang or emojis, no flirting ever. Polite with seniors and clients, " +
            "friendly but businesslike with colleagues. Keep replies brief and " +
            "unambiguous.",
    ),
    FLIRTY(
        "Flirty",
        "Reply in my voice: confidently flirty and funny with it. Tease them about " +
            "something specific they just said, pay a compliment that only fits them, " +
            "hint rather than announce. Relaxed, like someone who is not worried about " +
            "the outcome — a little challenge beats eagerness. Escalate only as much as " +
            "they do. Never cheesy pickup lines, never a wall of text, never 'haha'.",
    ),
    SPICY(
        "Spicy 18+",
        "We are both consenting adults chatting on a dating app. Help me reply boldly " +
            "flirty and suggestive, comfortable with adult innuendo and spicier talk when " +
            "the other person is clearly into it. Mirror their level first and escalate " +
            "gradually — never pushy, never graphic out of nowhere, always respectful. If " +
            "they pull back, cool it instantly and keep it charming.",
    );

    companion object {
        fun byName(name: String?): PersonaPreset? =
            entries.firstOrNull { it.name == name }
    }
}
