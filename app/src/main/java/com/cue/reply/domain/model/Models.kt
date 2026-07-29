package com.cue.reply.domain.model

/** The three message surfaces Cue supports. Package names double as stable ids. */
enum class ServiceType(val packageName: String, val displayName: String) {
    WHATSAPP("com.whatsapp", "WhatsApp"),
    TINDER("com.tinder", "Tinder"),
    HINGE("co.hinge.app", "Hinge");

    companion object {
        fun fromPackage(pkg: String?): ServiceType? = when (pkg) {
            // WhatsApp Business ships the same notification surface as WhatsApp.
            "com.whatsapp.w4b" -> WHATSAPP
            else -> entries.firstOrNull { it.packageName == pkg }
        }

        /** Apps read via the accessibility scrape path rather than notifications. */
        val accessibilityBacked: Set<ServiceType> = setOf(TINDER, HINGE)
    }
}

enum class MessageDirection { INCOMING, OUTGOING }

/**
 * A single message normalized onto the shared pipeline, regardless of whether it
 * came from a notification (WhatsApp) or an accessibility scrape (Tinder/Hinge).
 */
data class IncomingMessage(
    val service: ServiceType,
    val contactKey: String,
    val contactName: String?,
    val text: String,
    val direction: MessageDirection,
    val timestamp: Long,
) {
    /** Stable per-conversation identity used everywhere downstream. */
    val threadKey: String get() = "${service.packageName}::$contactKey"
}

/** One AI-generated reply candidate surfaced to the user. */
data class ReplyDraft(
    val id: String,
    val text: String,
    val tone: String? = null,
)

/** Result of attempting to deliver a reply through a ReplySink. */
sealed interface DeliveryResult {
    /** Inserted into the compose field but not sent (SUGGEST mode). */
    data object Inserted : DeliveryResult

    /** Sent and verified as present in the thread (AUTO-SEND, Loop C success). */
    data object Sent : DeliveryResult

    /** Send attempted but could not be verified after the bounded retries. */
    data class Failed(val reason: String) : DeliveryResult

    /** Sink refused because policy blocked it (not allowlisted, rate limited, killed). */
    data class Blocked(val reason: String) : DeliveryResult
}
