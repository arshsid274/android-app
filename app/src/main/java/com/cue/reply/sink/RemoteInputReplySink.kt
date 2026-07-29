package com.cue.reply.sink

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.cue.reply.domain.ReplySink
import com.cue.reply.domain.model.DeliveryResult
import com.cue.reply.source.notification.CueNotificationListenerService

/**
 * WhatsApp sink. Fires the notification's own RemoteInput reply action — the
 * protocol-safe path (no scraping, no timing signature). There is no "insert only"
 * mode here: WhatsApp has no compose field to prefill, so SUGGEST mode surfaces the
 * draft in the app UI and the user taps to send. This sink is used for AUTO-SEND.
 */
class RemoteInputReplySink(private val context: Context) : ReplySink {

    override suspend fun deliver(threadKey: String, text: String, send: Boolean): DeliveryResult {
        if (!send) {
            // SUGGEST: nothing to insert into WhatsApp; the draft is shown in-app.
            return DeliveryResult.Inserted
        }
        val action = CueNotificationListenerService.pendingReplies[threadKey]
            ?: return DeliveryResult.Failed("No active WhatsApp reply action for this thread")

        return try {
            val intent = Intent()
            val results = Bundle().apply { putCharSequence(action.resultKey, text) }
            RemoteInput.addResultsToIntent(action.remoteInputs, intent, results)
            action.pendingIntent.send(context, 0, intent)
            DeliveryResult.Sent
        } catch (e: Exception) {
            DeliveryResult.Failed(e.message ?: "RemoteInput send failed")
        }
    }
}
