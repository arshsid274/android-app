package com.cue.reply.domain

import com.cue.reply.domain.model.IncomingMessage
import kotlinx.coroutines.flow.Flow

/**
 * A producer of messages. Both [com.cue.reply.source.notification.NotificationMessageSource]
 * and [com.cue.reply.source.accessibility.AccessibilityMessageSource] implement this so the
 * rest of the app never knows which surface a message came from.
 */
interface MessageSource {
    /** Emits every new message (inbound and outbound) as it is observed. */
    fun observe(): Flow<IncomingMessage>
}
