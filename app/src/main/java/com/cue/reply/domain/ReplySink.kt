package com.cue.reply.domain

import com.cue.reply.domain.model.DeliveryResult

/**
 * A consumer of replies. WhatsApp uses [com.cue.reply.sink.RemoteInputReplySink]
 * (protocol-safe); dating apps use [com.cue.reply.sink.AccessibilityReplySink]
 * (set-text + tap Send, with the bounded send-verify loop).
 */
interface ReplySink {
    /**
     * @param send when false, only insert the text into the compose field (SUGGEST);
     *             when true, attempt an actual send with verification (AUTO-SEND).
     */
    suspend fun deliver(threadKey: String, text: String, send: Boolean): DeliveryResult
}
