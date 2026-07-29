package com.cue.reply.sink

import com.cue.reply.data.db.ActivityType
import com.cue.reply.data.repository.ActivityRepository
import com.cue.reply.data.settings.SettingsRepository
import com.cue.reply.domain.ReplySink
import com.cue.reply.domain.model.DeliveryResult
import com.cue.reply.domain.model.ServiceType
import com.cue.reply.source.accessibility.CueAccessibilityService
import kotlinx.coroutines.delay

/**
 * Tinder/Hinge sink. In SUGGEST it only sets the compose text. In AUTO-SEND it runs
 * Loop C: set-text (verify it stuck) → tap Send → verify the message appears in the
 * thread, bounded by sendVerifyMax. Never loops a send more than the cap — repeated
 * failed sends against a moved UI is exactly the burst pattern anti-bot watches for.
 */
class AccessibilityReplySink(
    private val settingsRepository: SettingsRepository,
    private val activity: ActivityRepository,
) : ReplySink {

    override suspend fun deliver(threadKey: String, text: String, send: Boolean): DeliveryResult {
        val service = serviceFor(threadKey)
            ?: return DeliveryResult.Failed("Unknown service for thread")
        val svc = CueAccessibilityService.instance
            ?: return DeliveryResult.Failed("Accessibility service not connected")

        if (!send) {
            return if (svc.setComposeText(service, text)) DeliveryResult.Inserted
            else DeliveryResult.Failed("Could not set compose text")
        }

        val max = settingsRepository.currentSettings().sendVerifyMax.coerceAtLeast(1)
        repeat(max) { attempt ->
            if (!svc.setComposeText(service, text)) {
                activity.log(ActivityType.SEND_RETRY, threadKey, service, detail = "set-text failed (attempt ${attempt + 1})")
                return@repeat
            }
            if (!svc.tapSend(service)) {
                activity.log(ActivityType.SEND_RETRY, threadKey, service, detail = "send tap failed (attempt ${attempt + 1})")
                return@repeat
            }
            if (verifyInThread(svc, service, text)) {
                return DeliveryResult.Sent
            }
            activity.log(ActivityType.SEND_RETRY, threadKey, service, detail = "unverified after send (attempt ${attempt + 1})")
        }
        return DeliveryResult.Failed("Send unverified after $max attempts")
    }

    /** Polls the thread briefly for the outbound message (verify step of Loop C). */
    private suspend fun verifyInThread(
        svc: CueAccessibilityService,
        service: ServiceType,
        text: String,
    ): Boolean {
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline) {
            if (svc.threadContainsOutbound(service, text)) return true
            delay(300)
        }
        return false
    }

    private fun serviceFor(threadKey: String): ServiceType? {
        val pkg = threadKey.substringBefore("::")
        return ServiceType.fromPackage(pkg)
    }
}
