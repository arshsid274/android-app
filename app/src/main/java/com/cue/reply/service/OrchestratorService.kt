package com.cue.reply.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cue.reply.CueApplication
import com.cue.reply.MainActivity
import com.cue.reply.data.db.ActivityType
import com.cue.reply.data.settings.CueSettings
import com.cue.reply.data.settings.ReplyMode
import com.cue.reply.di.AppContainer
import com.cue.reply.domain.model.DeliveryResult
import com.cue.reply.domain.model.IncomingMessage
import com.cue.reply.domain.model.MessageDirection
import com.cue.reply.domain.model.ReplyDraft
import com.cue.reply.llm.DraftResult
import com.cue.reply.llm.PersonaPreset
import com.cue.reply.policy.PolicyDecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service that keeps the sources alive and runs the per-message pipeline:
 * record → draft → surface (SUGGEST) → optionally auto-send through the guardrails.
 * Owns the draft overlay. This is the runtime home of the plan's loops.
 */
class OrchestratorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var container: AppContainer
    private lateinit var overlay: OverlayController

    private var lastDrafts: Map<String, List<ReplyDraft>> = emptyMap()

    override fun onCreate() {
        super.onCreate()
        container = (application as CueApplication).container
        overlay = OverlayController(this)
        startForegroundNotification()
        pruneRetention()
        collectMessages()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_KILL) {
            scope.launch {
                container.settingsRepository.update { it.copy(killed = true) }
                container.activityRepository.log(ActivityType.KILLED, detail = "Kill switch (notification)")
                overlay.hide()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        overlay.hide()
        scope.cancel()
    }

    private fun collectMessages() {
        val merged = merge(
            container.notificationSource.observe(),
            container.accessibilitySource.observe(),
        )
        scope.launch {
            merged.collect { message ->
                runCatching { handle(message) }
                    .onFailure {
                        container.activityRepository.log(
                            ActivityType.ERROR,
                            message.threadKey,
                            message.service,
                            detail = it.message,
                        )
                    }
            }
        }
    }

    private suspend fun handle(message: IncomingMessage) {
        container.conversationRepository.record(message)
        if (message.direction == MessageDirection.OUTGOING) return

        val settings = container.settingsRepository.settings.first()
        container.activityRepository.log(
            ActivityType.RECEIVED, message.threadKey, message.service, message.contactName,
        )

        if (settings.killed || !settings.masterEnabled) return
        if (container.conversationRepository.isMuted(message.threadKey)) return
        if (!sourceEnabled(message, settings)) return

        val history = container.conversationRepository.recentMessages(message.threadKey)
        val persona = PersonaPreset.byName(
            container.conversationRepository.personaPreset(message.threadKey)
        )
        when (val result = container.draftGenerator.generate(history, settings, personaOverride = persona)) {
            is DraftResult.Error -> container.activityRepository.log(
                ActivityType.ERROR, message.threadKey, message.service, detail = result.reason,
            )

            is DraftResult.Ready -> {
                lastDrafts = lastDrafts + (message.threadKey to result.drafts)
                container.activityRepository.log(
                    ActivityType.SUGGESTED, message.threadKey, message.service, message.contactName,
                    detail = result.drafts.joinToString(" | ") { it.text },
                )
                surfaceDrafts(message, result.drafts)

                if (settings.mode == ReplyMode.AUTO_SEND) {
                    maybeAutoSend(message, result.drafts.first(), settings)
                }
            }
        }
    }

    private fun surfaceDrafts(message: IncomingMessage, drafts: List<ReplyDraft>) {
        if (!overlay.canDraw()) {
            scope.launch {
                container.activityRepository.log(
                    ActivityType.ERROR, message.threadKey, message.service,
                    detail = "Overlay permission missing — enable 'Display over other apps' for Cue",
                )
            }
            return
        }
        overlay.show(
            title = "Cue · ${message.contactName ?: message.service.displayName}",
            drafts = drafts,
            onChoose = { draft -> scope.launch { insert(message, draft) } },
            onRegenerate = { scope.launch { regenerate(message) } },
            onDismiss = { scope.launch { container.conversationRepository.markDraftSeen(message.threadKey) } },
        )
    }

    private suspend fun insert(message: IncomingMessage, draft: ReplyDraft) {
        val sink = container.sinkFor(message.service)
        when (val res = sink.deliver(message.threadKey, draft.text, send = false)) {
            is DeliveryResult.Inserted, is DeliveryResult.Sent ->
                container.activityRepository.log(
                    ActivityType.INSERTED, message.threadKey, message.service, detail = draft.text,
                )
            is DeliveryResult.Failed ->
                container.activityRepository.log(
                    ActivityType.ERROR, message.threadKey, message.service, detail = res.reason,
                )
            is DeliveryResult.Blocked -> Unit
        }
        container.conversationRepository.markDraftSeen(message.threadKey)
        overlay.hide()
    }

    private suspend fun regenerate(message: IncomingMessage) {
        val settings = container.settingsRepository.settings.first()
        val history = container.conversationRepository.recentMessages(message.threadKey)
        val persona = PersonaPreset.byName(
            container.conversationRepository.personaPreset(message.threadKey)
        )
        when (
            val result = container.draftGenerator.generate(
                history, settings, steer = "give different angles", personaOverride = persona,
            )
        ) {
            is DraftResult.Ready -> surfaceDrafts(message, result.drafts)
            is DraftResult.Error -> container.activityRepository.log(
                ActivityType.ERROR, message.threadKey, message.service, detail = result.reason,
            )
        }
    }

    private suspend fun maybeAutoSend(message: IncomingMessage, draft: ReplyDraft, settings: CueSettings) {
        when (val decision = container.guardrails.evaluateAutoSend(message.threadKey, settings)) {
            is PolicyDecision.Deny -> container.activityRepository.log(
                decision.type, message.threadKey, message.service, detail = decision.reason,
            )

            is PolicyDecision.Allow -> {
                container.activityRepository.log(
                    ActivityType.CADENCE_HELD, message.threadKey, message.service,
                    detail = "holding ${decision.holdMs}ms",
                )
                if (!runCountdown(message, draft, decision.holdMs)) {
                    container.activityRepository.log(
                        ActivityType.ERROR, message.threadKey, message.service,
                        detail = "Auto-send cancelled from the countdown card",
                    )
                    return
                }
                val sink = container.sinkFor(message.service)
                when (val res = sink.deliver(message.threadKey, draft.text, send = true)) {
                    is DeliveryResult.Sent -> {
                        container.activityRepository.log(
                            ActivityType.AUTO_SENT, message.threadKey, message.service, detail = draft.text,
                        )
                        container.conversationRepository.record(
                            IncomingMessage(
                                service = message.service,
                                contactKey = message.contactKey,
                                contactName = message.contactName,
                                text = draft.text,
                                direction = MessageDirection.OUTGOING,
                                timestamp = System.currentTimeMillis(),
                            )
                        )
                    }
                    is DeliveryResult.Failed -> container.activityRepository.log(
                        ActivityType.SEND_FAILED, message.threadKey, message.service, detail = res.reason,
                    )
                    is DeliveryResult.Blocked -> container.activityRepository.log(
                        ActivityType.BLOCKED_NOT_ALLOWLISTED, message.threadKey, message.service, detail = res.reason,
                    )
                    is DeliveryResult.Inserted -> Unit
                }
            }
        }
    }

    /**
     * Counts the hold down on screen so the user can watch, skip, or stop it.
     * Returns false when the user cancelled.
     */
    private suspend fun runCountdown(
        message: IncomingMessage,
        draft: ReplyDraft,
        holdMs: Long,
    ): Boolean {
        if (holdMs <= 0L || !overlay.canDraw()) {
            container.guardrails.applyHold(holdMs)
            return true
        }
        val cancelled = AtomicBoolean(false)
        val sendNow = AtomicBoolean(false)
        overlay.showCountdown(
            title = message.contactName ?: message.service.displayName,
            draftText = draft.text,
            onSendNow = { sendNow.set(true) },
            onCancel = { cancelled.set(true) },
        )
        var remaining = holdMs
        while (remaining > 0 && !cancelled.get() && !sendNow.get()) {
            overlay.updateCountdown(((remaining + 999) / 1000).toInt())
            val slice = minOf(250L, remaining)
            delay(slice)
            remaining -= slice
        }
        if (cancelled.get()) {
            overlay.hide()
            return false
        }
        overlay.updateCountdown(0)
        return true
    }

    private fun sourceEnabled(message: IncomingMessage, settings: CueSettings): Boolean = when (message.service) {
        com.cue.reply.domain.model.ServiceType.WHATSAPP -> settings.whatsappEnabled
        com.cue.reply.domain.model.ServiceType.TINDER -> settings.tinderEnabled
        com.cue.reply.domain.model.ServiceType.HINGE -> settings.hingeEnabled
    }

    private fun pruneRetention() = scope.launch {
        val settings = container.settingsRepository.settings.first()
        val cutoff = System.currentTimeMillis() - settings.retentionDays * 24L * 60 * 60 * 1000
        container.conversationRepository.pruneOlderThan(cutoff)
        container.activityRepository.pruneOlderThan(cutoff)
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Cue", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val kill = PendingIntent.getService(
            this, 1, Intent(this, OrchestratorService::class.java).setAction(ACTION_KILL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cue is running")
            .setContentText("Reading messages and drafting replies")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_lock_power_off, "Kill switch", kill)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "cue_orchestrator"
        private const val NOTIF_ID = 1001
        const val ACTION_KILL = "com.cue.reply.action.KILL"

        fun start(context: Context) {
            val intent = Intent(context, OrchestratorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OrchestratorService::class.java))
        }
    }
}
