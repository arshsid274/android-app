package com.cue.reply.source.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.cue.reply.CueApplication
import com.cue.reply.data.db.ActivityType
import com.cue.reply.domain.model.IncomingMessage
import com.cue.reply.domain.model.MessageDirection
import com.cue.reply.domain.model.ServiceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * The Tinder/Hinge input+output surface. On chat window/content changes it scrapes
 * the visible thread (Loop B), emits new inbound messages onto [events], and offers
 * set-text / tap-send primitives for the accessibility [com.cue.reply.sink.AccessibilityReplySink].
 *
 * Event-driven first; a bounded jittered poll is only a fallback so an idle chat
 * never spins. Fixed timing is a bot tell, so the poll is jittered and STALE_CAP-bounded.
 */
class CueAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob())
    private val scraper = ChatScraper()

    private var scrapeJob: Job? = null
    private var pollJob: Job? = null

    private var currentService: ServiceType? = null
    private val lastEmittedByThread = mutableMapOf<String, String>()

    private val container get() = (application as CueApplication).container

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val service = ServiceType.fromPackage(pkg) ?: return
        if (service !in ServiceType.accessibilityBacked) return
        currentService = service

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> scheduleScrape(service)
        }
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        scope.cancel()
    }

    /** Debounced snapshot so a burst of content-changed events triggers one scrape. */
    private fun scheduleScrape(service: ServiceType) {
        scrapeJob?.cancel()
        scrapeJob = scope.launch {
            delay(250)
            runScrape(service, fromPoll = false)
            restartPollFallback(service)
        }
    }

    /** Loop B fallback: jittered poll, bounded by STALE_CAP empty reads, then pause. */
    private fun restartPollFallback(service: ServiceType) {
        pollJob?.cancel()
        pollJob = scope.launch {
            val settings = container.settingsRepository.currentSettings()
            var emptyReads = 0
            while (emptyReads < settings.staleCap) {
                val jitter = (settings.pollMinMs..settings.pollMaxMs).random()
                delay(jitter)
                val changed = runScrape(service, fromPoll = true)
                emptyReads = if (changed) 0 else emptyReads + 1
            }
            // STALE_CAP hit: stop polling and wait for the next window event.
        }
    }

    /** @return true if a new inbound message was emitted this pass. */
    private suspend fun runScrape(service: ServiceType, fromPoll: Boolean): Boolean {
        val selectors = SelectorMap.forService(service) ?: return false
        val width = resources.displayMetrics.let(::screenWidth)

        when (val result = scraper.scrape(rootInActiveWindow, selectors, width)) {
            ScrapeResult.SelectorsBroken -> {
                if (!fromPoll) {
                    container.activityRepository.log(
                        ActivityType.SELECTOR_BROKEN,
                        service = service,
                        detail = "Chat container/text not found — SelectorMap for ${service.displayName} may be stale",
                    )
                    container.diagnostics.markBroken(service)
                }
                return false
            }

            is ScrapeResult.Success -> {
                container.diagnostics.markHealthy(service)
                val last = result.messages.lastOrNull { it.direction == MessageDirection.INCOMING }
                    ?: return false

                val contactKey = result.matchName?.takeIf { it.isNotBlank() } ?: "active_chat"
                val threadKey = "${service.packageName}::$contactKey"
                if (lastEmittedByThread[threadKey] == last.text) return false
                lastEmittedByThread[threadKey] = last.text

                _events.emit(
                    IncomingMessage(
                        service = service,
                        contactKey = contactKey,
                        contactName = result.matchName,
                        text = last.text,
                        direction = MessageDirection.INCOMING,
                        timestamp = System.currentTimeMillis(),
                    )
                )
                return true
            }
        }
    }

    // ---- Primitives used by AccessibilityReplySink (Loop C) ----

    /** Sets [text] into the compose field. @return true if the field now holds [text]. */
    fun setComposeText(service: ServiceType, text: String): Boolean {
        val selectors = SelectorMap.forService(service) ?: return false
        val field = rootInActiveWindow?.findAccessibilityNodeInfosByViewId(selectors.composeId)
            ?.firstOrNull() ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return currentComposeText(service) == text
    }

    fun currentComposeText(service: ServiceType): String? {
        val selectors = SelectorMap.forService(service) ?: return null
        return rootInActiveWindow?.findAccessibilityNodeInfosByViewId(selectors.composeId)
            ?.firstOrNull()?.text?.toString()
    }

    fun tapSend(service: ServiceType): Boolean {
        val selectors = SelectorMap.forService(service) ?: return false
        val button = rootInActiveWindow?.findAccessibilityNodeInfosByViewId(selectors.sendId)
            ?.firstOrNull() ?: return false
        return button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** True if [text] appears among the outbound messages currently on screen (send verify). */
    fun threadContainsOutbound(service: ServiceType, text: String): Boolean {
        val selectors = SelectorMap.forService(service) ?: return false
        val width = resources.displayMetrics.let(::screenWidth)
        val result = scraper.scrape(rootInActiveWindow, selectors, width)
        return result is ScrapeResult.Success &&
            result.messages.any { it.direction == MessageDirection.OUTGOING && it.text == text }
    }

    private fun screenWidth(dm: DisplayMetrics): Int = dm.widthPixels

    companion object {
        @Volatile
        var instance: CueAccessibilityService? = null
            private set

        private val _events = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 32)
        val events: SharedFlow<IncomingMessage> = _events

        val isConnected: Boolean get() = instance != null
    }
}
