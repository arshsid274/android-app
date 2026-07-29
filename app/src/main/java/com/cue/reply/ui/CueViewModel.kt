package com.cue.reply.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cue.reply.CueApplication
import com.cue.reply.data.db.ActivityType
import com.cue.reply.data.db.entity.ActivityLogEntity
import com.cue.reply.data.db.entity.ConversationEntity
import com.cue.reply.data.db.entity.MessageEntity
import com.cue.reply.data.settings.CueSettings
import com.cue.reply.diagnostics.ServiceDiagnostics
import com.cue.reply.domain.model.DeliveryResult
import com.cue.reply.domain.model.ReplyDraft
import com.cue.reply.domain.model.ServiceType
import com.cue.reply.llm.DraftResult
import com.cue.reply.llm.LlmResult
import com.cue.reply.llm.LlmTurn
import com.cue.reply.llm.PersonaPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state of the draft panel for one thread. */
sealed interface DraftsUi {
    data object Idle : DraftsUi
    data object Loading : DraftsUi
    data class Ready(val drafts: List<ReplyDraft>) : DraftsUi
    data class Error(val reason: String) : DraftsUi
}

/** Result of the LLM setup "Test connection" action. */
sealed interface LlmTestUi {
    data object Idle : LlmTestUi
    data object Testing : LlmTestUi
    data object Verified : LlmTestUi
    data class Failed(val reason: String) : LlmTestUi
}

/** Single view model backing every screen. */
class CueViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as CueApplication).container

    val settings: StateFlow<CueSettings> =
        container.settingsRepository.settings.stateIn(
            viewModelScope, SharingStarted.Eagerly, CueSettings(),
        )

    /** Where the app should land on launch; null while settings load. */
    val startRoute = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            val s = container.settingsRepository.currentSettings()
            startRoute.value = if (s.onboardingDone) "home" else "onboarding"
        }
    }

    val conversations: Flow<List<ConversationEntity>> = container.conversationRepository.conversations()
    val activity: Flow<List<ActivityLogEntity>> = container.activityRepository.recent()
    val allowlisted: Flow<List<ConversationEntity>> = container.conversationRepository.allowlisted()
    val diagnostics: StateFlow<Map<ServiceType, ServiceDiagnostics>> = container.diagnostics.state

    fun messages(threadKey: String): Flow<List<MessageEntity>> =
        container.conversationRepository.messages(threadKey)

    fun conversation(threadKey: String): Flow<ConversationEntity?> =
        container.conversationRepository.conversation(threadKey)

    fun update(transform: (CueSettings) -> CueSettings) = viewModelScope.launch {
        container.settingsRepository.update(transform)
    }

    /** Per-contact persona; null clears the override so the thread follows Settings. */
    fun setThreadPersona(threadKey: String, preset: PersonaPreset?) = viewModelScope.launch {
        container.conversationRepository.setPersonaPreset(threadKey, preset?.name)
    }

    fun setAllowlisted(threadKey: String, value: Boolean) = viewModelScope.launch {
        container.conversationRepository.setAllowlisted(threadKey, value)
    }

    fun setMuted(threadKey: String, value: Boolean) = viewModelScope.launch {
        container.conversationRepository.setMuted(threadKey, value)
    }

    fun toggleKill() = viewModelScope.launch {
        val wasKilled = container.settingsRepository.currentSettings().killed
        container.settingsRepository.update { it.copy(killed = !wasKilled) }
        container.activityRepository.log(
            if (wasKilled) ActivityType.RESUMED else ActivityType.KILLED,
            detail = if (wasKilled) "Re-enabled from app bar" else "Kill switch (app bar)",
        )
    }

    fun clearAllowlist() = viewModelScope.launch {
        container.conversationRepository.clearAllowlist()
    }

    fun deleteThread(threadKey: String) = viewModelScope.launch {
        container.conversationRepository.deleteThread(threadKey)
    }

    fun deleteAllData() = viewModelScope.launch {
        container.conversationRepository.deleteEverything()
        container.activityRepository.clear()
    }

    fun completeOnboarding() = viewModelScope.launch {
        container.settingsRepository.update { it.copy(onboardingDone = true) }
    }

    // ---- Draft panel (Conversation detail) ----

    private val draftsByThread = MutableStateFlow<Map<String, DraftsUi>>(emptyMap())
    val drafts: StateFlow<Map<String, DraftsUi>> = draftsByThread

    fun generateDrafts(threadKey: String, steer: String? = null) = viewModelScope.launch {
        draftsByThread.update { it + (threadKey to DraftsUi.Loading) }
        val settings = container.settingsRepository.currentSettings()
        val history = container.conversationRepository.recentMessages(threadKey)
        val conversation = container.conversationRepository.conversation(threadKey).first()
        val persona = PersonaPreset.byName(conversation?.personaPreset)
        when (
            val result = container.draftGenerator.generate(
                history, settings, steer, personaOverride = persona,
            )
        ) {
            is DraftResult.Ready -> {
                draftsByThread.update { it + (threadKey to DraftsUi.Ready(result.drafts)) }
                container.activityRepository.log(
                    ActivityType.SUGGESTED,
                    threadKey,
                    conversation?.service,
                    conversation?.contactName,
                    detail = result.drafts.joinToString(" | ") { it.text },
                )
                container.conversationRepository.markDraftSeen(threadKey)
            }
            is DraftResult.Error ->
                draftsByThread.update { it + (threadKey to DraftsUi.Error(result.reason)) }
        }
    }

    /** Insert a draft through the service's ReplySink (SUGGEST — never sends). */
    fun insertDraft(
        threadKey: String,
        service: ServiceType,
        text: String,
        onResult: (DeliveryResult) -> Unit,
    ) = viewModelScope.launch {
        val result = container.sinkFor(service).deliver(threadKey, text, send = false)
        if (result is DeliveryResult.Inserted || result is DeliveryResult.Sent) {
            container.activityRepository.log(ActivityType.INSERTED, threadKey, service, detail = text)
        }
        onResult(result)
    }

    // ---- LLM setup ----

    val llmTest = MutableStateFlow<LlmTestUi>(LlmTestUi.Idle)

    fun testLlmConnection() = viewModelScope.launch {
        llmTest.value = LlmTestUi.Testing
        val s = container.settingsRepository.currentSettings()
        val result = container.llmClient.complete(
            system = "Reply with the single word: ok",
            turns = listOf(LlmTurn("user", "ping")),
            model = s.activeModel,
            maxTokens = 8,
        )
        llmTest.value = when (result) {
            is LlmResult.Success -> LlmTestUi.Verified
            is LlmResult.Error -> LlmTestUi.Failed(result.reason)
        }
    }

    fun resetLlmTest() {
        llmTest.value = LlmTestUi.Idle
    }
}
