package com.cue.reply.source.accessibility

import com.cue.reply.domain.model.ServiceType

/**
 * The brittleness firewall. ALL app-version-specific node identifiers live here so
 * a UI change in Tinder/Hinge is a one-file fix. When a version can't be mapped,
 * that is a WONT-FIX-THIS-VERSION blocker — not an infinite retry.
 *
 * These view-ids are placeholders and WILL need updating against real APK layouts
 * (inspect with `uiautomatorviewer` / Layout Inspector). Treat every id as unverified
 * until D2's scrape gate passes on a live chat.
 */
data class Selectors(
    val chatContainerId: String,
    val messageRowClass: String,
    val messageTextId: String,
    val inboundHintId: String?,   // view-id present only on inbound bubbles, if any
    val outboundHintId: String?,  // view-id present only on outbound bubbles, if any
    val composeId: String,
    val sendId: String,
    val matchNameId: String?,     // header/title showing the match's name
)

object SelectorMap {

    private val tinder = Selectors(
        chatContainerId = "com.tinder:id/messageList",
        messageRowClass = "androidx.recyclerview.widget.RecyclerView\$ViewHolder",
        messageTextId = "com.tinder:id/messageContent",
        inboundHintId = "com.tinder:id/inbound",
        outboundHintId = "com.tinder:id/outbound",
        composeId = "com.tinder:id/textField",
        sendId = "com.tinder:id/sendButton",
        matchNameId = "com.tinder:id/matchName",
    )

    private val hinge = Selectors(
        chatContainerId = "co.hinge.app:id/chatRecyclerView",
        messageRowClass = "androidx.recyclerview.widget.RecyclerView\$ViewHolder",
        messageTextId = "co.hinge.app:id/messageText",
        inboundHintId = "co.hinge.app:id/received",
        outboundHintId = "co.hinge.app:id/sent",
        composeId = "co.hinge.app:id/composeText",
        sendId = "co.hinge.app:id/sendButton",
        matchNameId = "co.hinge.app:id/profileName",
    )

    fun forService(service: ServiceType): Selectors? = when (service) {
        ServiceType.TINDER -> tinder
        ServiceType.HINGE -> hinge
        ServiceType.WHATSAPP -> null // WhatsApp uses the notification path, not scraping.
    }
}
