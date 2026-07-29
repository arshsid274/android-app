package com.cue.reply.source.notification

import com.cue.reply.domain.MessageSource
import com.cue.reply.domain.model.IncomingMessage
import kotlinx.coroutines.flow.Flow

/** Exposes WhatsApp notification messages as a [MessageSource]. */
class NotificationMessageSource : MessageSource {
    override fun observe(): Flow<IncomingMessage> = CueNotificationListenerService.events
}
