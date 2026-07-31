package com.cue.reply.source.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cue.reply.domain.model.IncomingMessage
import com.cue.reply.domain.model.MessageDirection
import com.cue.reply.domain.model.ServiceType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** A captured RemoteInput reply action, used by the protocol-safe WhatsApp sink. */
data class ReplyAction(
    val pendingIntent: android.app.PendingIntent,
    val remoteInputs: Array<android.app.RemoteInput>,
    val resultKey: String,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

/**
 * WhatsApp input surface. Reads incoming messages from MessagingStyle notifications
 * and stashes the notification's RemoteInput "Reply" action so replies go back through
 * WhatsApp's own protocol (no scraping, no bot signature).
 */
class CueNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d(TAG, "listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (ServiceType.fromPackage(sbn.packageName) != ServiceType.WHATSAPP) {
            if (sbn.packageName?.contains("whatsapp") == true) {
                Log.d(TAG, "ignored whatsapp-ish pkg=${sbn.packageName}")
            }
            return
        }
        val notification = sbn.notification ?: return

        val rawTitle = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        // WhatsApp appends a running count to grouped notifications ("Ana (3 messages)").
        // Left in, every new message would spawn a brand-new conversation.
        val title = rawTitle?.replace(MESSAGE_COUNT_SUFFIX, "")?.trim()
        val contactKey = title?.takeIf { it.isNotBlank() } ?: sbn.key
        val threadKey = "${ServiceType.WHATSAPP.packageName}::$contactKey"

        captureReplyAction(notification)?.let { pendingReplies[threadKey] = it }

        val style = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(notification)
        val messages = style?.messages
        if (messages.isNullOrEmpty()) {
            Log.d(TAG, "pkg=${sbn.packageName} user=${sbn.user} no MessagingStyle (summary/silent?), title=$title")
            return
        }

        // The latest message; a null person means it was sent by the user.
        val latest = messages.last()
        val text = latest.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return

        // A reply we sent through RemoteInput comes straight back in this notification's
        // history, attributed to the self Person. Treated as inbound it would trigger a
        // fresh draft for our own words — an endless self-conversation.
        val selfName = style.user?.name?.toString()
        val fromSelf = latest.person == null ||
            (selfName != null && latest.person?.name?.toString() == selfName) ||
            recentlySent.contains(text)
        val incoming = !fromSelf
        if (fromSelf) {
            Log.d(TAG, "self message ignored as inbound: ${text.take(40)}")
        }

        Log.d(TAG, "emit pkg=${sbn.packageName} user=${sbn.user} contact=$contactKey incoming=$incoming len=${text.length}")
        _events.tryEmit(
            IncomingMessage(
                service = ServiceType.WHATSAPP,
                contactKey = contactKey,
                contactName = title ?: latest.person?.name?.toString(),
                text = text,
                direction = if (incoming) MessageDirection.INCOMING else MessageDirection.OUTGOING,
                timestamp = latest.timestamp,
            )
        )
    }

    private fun captureReplyAction(notification: Notification): ReplyAction? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            if (remoteInputs.isEmpty()) continue
            val pending = action.actionIntent ?: continue
            return ReplyAction(pending, remoteInputs, remoteInputs.first().resultKey)
        }
        return null
    }

    companion object {
        private const val TAG = "CueListener"

        @Volatile
        var instance: CueNotificationListenerService? = null
            private set

        val isConnected: Boolean get() = instance != null

        private val _events = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 32)
        val events: SharedFlow<IncomingMessage> = _events

        /** threadKey -> latest RemoteInput reply action. Read by RemoteInputReplySink. */
        val pendingReplies = java.util.concurrent.ConcurrentHashMap<String, ReplyAction>()

        private val MESSAGE_COUNT_SUFFIX = Regex("""\s*\(\d+\s+messages?\)\s*$""")

        /**
         * Texts this app just sent, so the echo arriving back through the
         * notification is recognised even when the self Person is unnamed.
         */
        private val recentlySent = java.util.Collections.newSetFromMap(
            java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        )

        fun rememberSent(text: String) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return
            recentlySent.add(trimmed)
            if (recentlySent.size > 50) {
                recentlySent.iterator().let { if (it.hasNext()) { it.next(); it.remove() } }
            }
        }
    }
}
