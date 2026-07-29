package com.cue.reply.di

import android.content.Context
import com.cue.reply.data.db.CueDatabase
import com.cue.reply.data.repository.ActivityRepository
import com.cue.reply.data.repository.ConversationRepository
import com.cue.reply.data.settings.SettingsRepository
import com.cue.reply.diagnostics.DiagnosticsStore
import com.cue.reply.domain.ReplySink
import com.cue.reply.domain.model.ServiceType
import com.cue.reply.llm.AnthropicLlmClient
import com.cue.reply.llm.OpenAiLlmClient
import com.cue.reply.llm.RoutingLlmClient
import com.cue.reply.llm.DraftGenerator
import com.cue.reply.llm.LlmClient
import com.cue.reply.policy.GuardrailEngine
import com.cue.reply.sink.AccessibilityReplySink
import com.cue.reply.sink.RemoteInputReplySink
import com.cue.reply.source.accessibility.AccessibilityMessageSource
import com.cue.reply.source.notification.NotificationMessageSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Hand-rolled dependency container (no Hilt, to keep the build free of annotation
 * processors beyond Room). Owned by [com.cue.reply.CueApplication] and reachable
 * from services via the Application instance.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val db = CueDatabase.get(appContext)

    val settingsRepository = SettingsRepository(appContext)
    val conversationRepository = ConversationRepository(db.messageDao(), db.conversationDao())
    val activityRepository = ActivityRepository(db.activityLogDao())
    val diagnostics = DiagnosticsStore()

    val llmClient: LlmClient = RoutingLlmClient(
        providerSelector = { runBlocking { settingsRepository.settings.first().provider } },
        anthropic = AnthropicLlmClient(
            apiKeyProvider = { runBlocking { settingsRepository.settings.first().apiKey } },
            baseUrlProvider = { runBlocking { settingsRepository.settings.first().baseUrl } },
        ),
        openAi = OpenAiLlmClient(
            apiKeyProvider = { runBlocking { settingsRepository.settings.first().openaiApiKey } },
            baseUrlProvider = { runBlocking { settingsRepository.settings.first().openaiBaseUrl } },
        ),
        // xAI speaks the OpenAI Chat Completions protocol; only the endpoint differs.
        grok = OpenAiLlmClient(
            apiKeyProvider = { runBlocking { settingsRepository.settings.first().grokApiKey } },
            baseUrlProvider = { runBlocking { settingsRepository.settings.first().grokBaseUrl } },
        ),
        // Groq (LPU inference for open models) is also OpenAI-compatible.
        groq = OpenAiLlmClient(
            apiKeyProvider = { runBlocking { settingsRepository.settings.first().groqApiKey } },
            baseUrlProvider = { runBlocking { settingsRepository.settings.first().groqBaseUrl } },
        ),
    )
    val draftGenerator = DraftGenerator(llmClient)

    val guardrails = GuardrailEngine(activityRepository, conversationRepository)

    val notificationSource = NotificationMessageSource()
    val accessibilitySource = AccessibilityMessageSource()

    private val notificationSink = RemoteInputReplySink(appContext)
    private val accessibilitySink = AccessibilityReplySink(settingsRepository, activityRepository)

    fun sinkFor(service: ServiceType): ReplySink =
        if (service == ServiceType.WHATSAPP) notificationSink else accessibilitySink
}
