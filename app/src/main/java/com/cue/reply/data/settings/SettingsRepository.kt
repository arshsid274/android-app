package com.cue.reply.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cue_settings")

/** Reply behavior mode. SUGGEST is the safe default per the plan. */
enum class ReplyMode { SUGGEST, AUTO_SEND }

/** LLM backend. Each provider keeps its own key/model/endpoint so switching is lossless. */
enum class LlmProvider(val displayName: String, val keyHint: String) {
    ANTHROPIC("Claude", "sk-ant-…"),
    OPENAI("OpenAI", "sk-…"),
    GROK("Grok (xAI)", "xai-…"),
    GROQ("Groq", "gsk_…"),
}

/**
 * All tunable settings, backed by DataStore. Values mirror the guardrails and
 * loop parameters described in plan-dating-adapter.md.
 */
data class CueSettings(
    val mode: ReplyMode = ReplyMode.SUGGEST,
    val masterEnabled: Boolean = true,
    val killed: Boolean = false,

    // Sources
    val whatsappEnabled: Boolean = true,
    val tinderEnabled: Boolean = false,
    val hingeEnabled: Boolean = false,

    // LLM
    val provider: LlmProvider = LlmProvider.ANTHROPIC,
    val apiKey: String = "",
    val model: String = "claude-sonnet-4-5",
    val baseUrl: String = "https://api.anthropic.com",
    val openaiApiKey: String = "",
    val openaiModel: String = "gpt-5.2",
    val openaiBaseUrl: String = "https://api.openai.com",
    val grokApiKey: String = "",
    val grokModel: String = "grok-4-fast",
    val grokBaseUrl: String = "https://api.x.ai",
    val groqApiKey: String = "",
    val groqModel: String = "llama-3.3-70b-versatile",
    val groqBaseUrl: String = "https://api.groq.com/openai",
    val persona: String = DEFAULT_PERSONA,
    val draftCount: Int = 3,
    val maxDraftChars: Int = 240,

    // Guardrails (Loop B/C parameters)
    val pollMinMs: Long = 800,
    val pollMaxMs: Long = 1500,
    val staleCap: Int = 8,
    val sendVerifyMax: Int = 2,
    val repliesPerHour: Int = 20,
    val cadenceFloorMs: Long = 8_000,
    val cadenceCeilMs: Long = 45_000,
    val autoDropOnDetection: Boolean = true,
    val loopBreakerStreak: Int = 6,

    // Privacy
    val retentionDays: Int = 30,
    val storeMessageText: Boolean = true,

    // First-run
    val onboardingDone: Boolean = false,
) {
    /** Key/model/endpoint of whichever provider is currently selected. */
    val activeApiKey: String
        get() = when (provider) {
            LlmProvider.ANTHROPIC -> apiKey
            LlmProvider.OPENAI -> openaiApiKey
            LlmProvider.GROK -> grokApiKey
            LlmProvider.GROQ -> groqApiKey
        }
    val activeModel: String
        get() = when (provider) {
            LlmProvider.ANTHROPIC -> model
            LlmProvider.OPENAI -> openaiModel
            LlmProvider.GROK -> grokModel
            LlmProvider.GROQ -> groqModel
        }
    val activeBaseUrl: String
        get() = when (provider) {
            LlmProvider.ANTHROPIC -> baseUrl
            LlmProvider.OPENAI -> openaiBaseUrl
            LlmProvider.GROK -> grokBaseUrl
            LlmProvider.GROQ -> groqBaseUrl
        }

    /** Writes into the key/model field belonging to the active provider. */
    fun withActiveApiKey(key: String): CueSettings = when (provider) {
        LlmProvider.ANTHROPIC -> copy(apiKey = key)
        LlmProvider.OPENAI -> copy(openaiApiKey = key)
        LlmProvider.GROK -> copy(grokApiKey = key)
        LlmProvider.GROQ -> copy(groqApiKey = key)
    }

    fun withActiveModel(id: String): CueSettings = when (provider) {
        LlmProvider.ANTHROPIC -> copy(model = id)
        LlmProvider.OPENAI -> copy(openaiModel = id)
        LlmProvider.GROK -> copy(grokModel = id)
        LlmProvider.GROQ -> copy(groqModel = id)
    }

    companion object {
        const val DEFAULT_PERSONA =
            "You are helping me reply in my own voice: warm, a little playful, concise, " +
                "never cheesy or over-eager. Match the other person's energy and length."
    }
}

class SettingsRepository(private val context: Context) {

    val settings: Flow<CueSettings> = context.dataStore.data.map { p -> p.toSettings() }

    /** One-shot read of the current settings snapshot. */
    suspend fun currentSettings(): CueSettings = settings.first()

    suspend fun update(transform: (CueSettings) -> CueSettings) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.MODE] = next.mode.name
            prefs[Keys.MASTER_ENABLED] = next.masterEnabled
            prefs[Keys.KILLED] = next.killed
            prefs[Keys.WHATSAPP] = next.whatsappEnabled
            prefs[Keys.TINDER] = next.tinderEnabled
            prefs[Keys.HINGE] = next.hingeEnabled
            prefs[Keys.PROVIDER] = next.provider.name
            prefs[Keys.API_KEY] = next.apiKey
            prefs[Keys.MODEL] = next.model
            prefs[Keys.BASE_URL] = next.baseUrl
            prefs[Keys.OPENAI_API_KEY] = next.openaiApiKey
            prefs[Keys.OPENAI_MODEL] = next.openaiModel
            prefs[Keys.OPENAI_BASE_URL] = next.openaiBaseUrl
            prefs[Keys.GROK_API_KEY] = next.grokApiKey
            prefs[Keys.GROK_MODEL] = next.grokModel
            prefs[Keys.GROK_BASE_URL] = next.grokBaseUrl
            prefs[Keys.GROQ_API_KEY] = next.groqApiKey
            prefs[Keys.GROQ_MODEL] = next.groqModel
            prefs[Keys.GROQ_BASE_URL] = next.groqBaseUrl
            prefs[Keys.PERSONA] = next.persona
            prefs[Keys.DRAFT_COUNT] = next.draftCount
            prefs[Keys.MAX_DRAFT_CHARS] = next.maxDraftChars
            prefs[Keys.POLL_MIN] = next.pollMinMs
            prefs[Keys.POLL_MAX] = next.pollMaxMs
            prefs[Keys.STALE_CAP] = next.staleCap
            prefs[Keys.SEND_VERIFY_MAX] = next.sendVerifyMax
            prefs[Keys.REPLIES_PER_HOUR] = next.repliesPerHour
            prefs[Keys.CADENCE_FLOOR] = next.cadenceFloorMs
            prefs[Keys.CADENCE_CEIL] = next.cadenceCeilMs
            prefs[Keys.AUTO_DROP] = next.autoDropOnDetection
            prefs[Keys.LOOP_BREAKER] = next.loopBreakerStreak
            prefs[Keys.RETENTION_DAYS] = next.retentionDays
            prefs[Keys.STORE_TEXT] = next.storeMessageText
            prefs[Keys.ONBOARDING_DONE] = next.onboardingDone
        }
    }

    private fun Preferences.toSettings(): CueSettings {
        val d = CueSettings()
        return CueSettings(
            mode = this[Keys.MODE]?.let { runCatching { ReplyMode.valueOf(it) }.getOrNull() } ?: d.mode,
            masterEnabled = this[Keys.MASTER_ENABLED] ?: d.masterEnabled,
            killed = this[Keys.KILLED] ?: d.killed,
            whatsappEnabled = this[Keys.WHATSAPP] ?: d.whatsappEnabled,
            tinderEnabled = this[Keys.TINDER] ?: d.tinderEnabled,
            hingeEnabled = this[Keys.HINGE] ?: d.hingeEnabled,
            provider = this[Keys.PROVIDER]?.let { runCatching { LlmProvider.valueOf(it) }.getOrNull() } ?: d.provider,
            apiKey = this[Keys.API_KEY] ?: d.apiKey,
            model = this[Keys.MODEL] ?: d.model,
            baseUrl = this[Keys.BASE_URL] ?: d.baseUrl,
            openaiApiKey = this[Keys.OPENAI_API_KEY] ?: d.openaiApiKey,
            openaiModel = this[Keys.OPENAI_MODEL] ?: d.openaiModel,
            openaiBaseUrl = this[Keys.OPENAI_BASE_URL] ?: d.openaiBaseUrl,
            grokApiKey = this[Keys.GROK_API_KEY] ?: d.grokApiKey,
            grokModel = this[Keys.GROK_MODEL] ?: d.grokModel,
            grokBaseUrl = this[Keys.GROK_BASE_URL] ?: d.grokBaseUrl,
            groqApiKey = this[Keys.GROQ_API_KEY] ?: d.groqApiKey,
            groqModel = this[Keys.GROQ_MODEL] ?: d.groqModel,
            groqBaseUrl = this[Keys.GROQ_BASE_URL] ?: d.groqBaseUrl,
            persona = this[Keys.PERSONA] ?: d.persona,
            draftCount = this[Keys.DRAFT_COUNT] ?: d.draftCount,
            maxDraftChars = this[Keys.MAX_DRAFT_CHARS] ?: d.maxDraftChars,
            pollMinMs = this[Keys.POLL_MIN] ?: d.pollMinMs,
            pollMaxMs = this[Keys.POLL_MAX] ?: d.pollMaxMs,
            staleCap = this[Keys.STALE_CAP] ?: d.staleCap,
            sendVerifyMax = this[Keys.SEND_VERIFY_MAX] ?: d.sendVerifyMax,
            repliesPerHour = this[Keys.REPLIES_PER_HOUR] ?: d.repliesPerHour,
            cadenceFloorMs = this[Keys.CADENCE_FLOOR] ?: d.cadenceFloorMs,
            cadenceCeilMs = this[Keys.CADENCE_CEIL] ?: d.cadenceCeilMs,
            autoDropOnDetection = this[Keys.AUTO_DROP] ?: d.autoDropOnDetection,
            loopBreakerStreak = this[Keys.LOOP_BREAKER] ?: d.loopBreakerStreak,
            retentionDays = this[Keys.RETENTION_DAYS] ?: d.retentionDays,
            storeMessageText = this[Keys.STORE_TEXT] ?: d.storeMessageText,
            onboardingDone = this[Keys.ONBOARDING_DONE] ?: d.onboardingDone,
        )
    }

    private object Keys {
        val MODE = stringPreferencesKey("mode")
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val KILLED = booleanPreferencesKey("killed")
        val WHATSAPP = booleanPreferencesKey("src_whatsapp")
        val TINDER = booleanPreferencesKey("src_tinder")
        val HINGE = booleanPreferencesKey("src_hinge")
        val PROVIDER = stringPreferencesKey("llm_provider")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val BASE_URL = stringPreferencesKey("base_url")
        val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        val OPENAI_MODEL = stringPreferencesKey("openai_model")
        val OPENAI_BASE_URL = stringPreferencesKey("openai_base_url")
        val GROK_API_KEY = stringPreferencesKey("grok_api_key")
        val GROK_MODEL = stringPreferencesKey("grok_model")
        val GROK_BASE_URL = stringPreferencesKey("grok_base_url")
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val GROQ_MODEL = stringPreferencesKey("groq_model")
        val GROQ_BASE_URL = stringPreferencesKey("groq_base_url")
        val PERSONA = stringPreferencesKey("persona")
        val DRAFT_COUNT = intPreferencesKey("draft_count")
        val MAX_DRAFT_CHARS = intPreferencesKey("max_draft_chars")
        val POLL_MIN = longPreferencesKey("poll_min")
        val POLL_MAX = longPreferencesKey("poll_max")
        val STALE_CAP = intPreferencesKey("stale_cap")
        val SEND_VERIFY_MAX = intPreferencesKey("send_verify_max")
        val REPLIES_PER_HOUR = intPreferencesKey("replies_per_hour")
        val CADENCE_FLOOR = longPreferencesKey("cadence_floor")
        val CADENCE_CEIL = longPreferencesKey("cadence_ceil")
        val AUTO_DROP = booleanPreferencesKey("auto_drop")
        val LOOP_BREAKER = intPreferencesKey("loop_breaker")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val STORE_TEXT = booleanPreferencesKey("store_text")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}
