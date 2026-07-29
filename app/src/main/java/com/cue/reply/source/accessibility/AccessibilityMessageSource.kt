package com.cue.reply.source.accessibility

import com.cue.reply.domain.MessageSource
import com.cue.reply.domain.model.IncomingMessage
import kotlinx.coroutines.flow.Flow

/**
 * Exposes the accessibility service's scraped messages as a [MessageSource] so the
 * pipeline treats Tinder/Hinge exactly like WhatsApp.
 */
class AccessibilityMessageSource : MessageSource {
    override fun observe(): Flow<IncomingMessage> = CueAccessibilityService.events
}
