package com.cue.reply.source.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.cue.reply.domain.model.MessageDirection

data class ScrapedMessage(val text: String, val direction: MessageDirection)

sealed interface ScrapeResult {
    data class Success(val matchName: String?, val messages: List<ScrapedMessage>) : ScrapeResult
    /** Container/text selectors matched nothing — the SelectorMap is likely stale. */
    data object SelectorsBroken : ScrapeResult
}

/**
 * Walks the active window's view tree for a dating-app chat and reconstructs the
 * ordered messages. Direction is taken from an inbound/outbound hint id when the
 * app exposes one, otherwise inferred from horizontal alignment of the bubble.
 *
 * Isolated from the service so selector changes and parsing bugs stay in one place
 * (feeds D2's scrape gate and the SelectorMap firewall).
 */
class ChatScraper {

    fun scrape(
        root: AccessibilityNodeInfo?,
        selectors: Selectors,
        screenWidth: Int,
    ): ScrapeResult {
        if (root == null) return ScrapeResult.SelectorsBroken

        val container = root.findByViewId(selectors.chatContainerId).firstOrNull()
        val textNodes = (container ?: root).findByViewId(selectors.messageTextId)
        if (textNodes.isEmpty()) return ScrapeResult.SelectorsBroken

        val matchName = selectors.matchNameId
            ?.let { root.findByViewId(it).firstOrNull()?.text?.toString() }
            ?.takeIf { it.isNotBlank() }

        val messages = textNodes.mapNotNull { node ->
            val text = node.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return@mapNotNull null
            ScrapedMessage(text, directionOf(node, selectors, screenWidth))
        }
        return ScrapeResult.Success(matchName, messages)
    }

    private fun directionOf(
        node: AccessibilityNodeInfo,
        selectors: Selectors,
        screenWidth: Int,
    ): MessageDirection {
        // Prefer explicit hint ids on the node or an ancestor.
        selectors.inboundHintId?.let { if (node.hasAncestorWithId(it)) return MessageDirection.INCOMING }
        selectors.outboundHintId?.let { if (node.hasAncestorWithId(it)) return MessageDirection.OUTGOING }

        // Fallback: bubbles aligned to the left half are inbound, right half outbound.
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val center = (bounds.left + bounds.right) / 2
        return if (center < screenWidth / 2) MessageDirection.INCOMING else MessageDirection.OUTGOING
    }

    private fun AccessibilityNodeInfo.findByViewId(viewId: String): List<AccessibilityNodeInfo> =
        try {
            findAccessibilityNodeInfosByViewId(viewId) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

    private fun AccessibilityNodeInfo.hasAncestorWithId(viewId: String): Boolean {
        var current: AccessibilityNodeInfo? = this
        var depth = 0
        while (current != null && depth < 6) {
            if (current.viewIdResourceName == viewId) return true
            current = current.parent
            depth++
        }
        return false
    }
}
