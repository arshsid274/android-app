package com.cue.reply.policy

import com.cue.reply.data.db.ActivityType
import com.cue.reply.data.repository.ActivityRepository
import com.cue.reply.data.repository.ConversationRepository
import com.cue.reply.data.settings.CueSettings
import com.cue.reply.data.settings.ReplyMode
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Outcome of asking the guardrails whether an auto-send may proceed. */
sealed interface PolicyDecision {
    /** Allowed; [holdMs] is the human-cadence delay to apply before acting. */
    data class Allow(val holdMs: Long) : PolicyDecision
    data class Deny(val type: ActivityType, val reason: String) : PolicyDecision
}

/**
 * Central gate every auto-send passes through. Enforces kill switch, mode,
 * allowlist, rate limit, loop-breaker, and the randomized human-cadence floor.
 * SUGGEST mode never reaches here — only AUTO-SEND does.
 */
class GuardrailEngine(
    private val activity: ActivityRepository,
    private val conversations: ConversationRepository,
    private val random: Random = Random.Default,
) {
    suspend fun evaluateAutoSend(threadKey: String, settings: CueSettings): PolicyDecision {
        if (settings.killed || !settings.masterEnabled) {
            return PolicyDecision.Deny(ActivityType.KILLED, "Cue is paused")
        }
        if (settings.mode != ReplyMode.AUTO_SEND) {
            return PolicyDecision.Deny(ActivityType.BLOCKED_NOT_ALLOWLISTED, "Not in auto-send mode")
        }
        if (!conversations.isAllowlisted(threadKey)) {
            return PolicyDecision.Deny(ActivityType.BLOCKED_NOT_ALLOWLISTED, "Thread not allowlisted")
        }

        val hourWindow = 60 * 60 * 1000L
        val sentThisHour = activity.countWithin(ActivityType.AUTO_SENT, hourWindow)
        if (sentThisHour >= settings.repliesPerHour) {
            return PolicyDecision.Deny(ActivityType.RATE_LIMITED, "Hourly reply cap reached")
        }

        // Loop-breaker: too many automated exchanges without human input in a row.
        val recentAuto = activity.countWithin(ActivityType.AUTO_SENT, 10 * 60 * 1000L)
        if (recentAuto >= settings.loopBreakerStreak) {
            return PolicyDecision.Deny(ActivityType.RATE_LIMITED, "Loop-breaker: too many auto-sends recently")
        }

        val hold = randomHold(settings)
        return PolicyDecision.Allow(hold)
    }

    /** Randomized delay floor — no fixed timing signature. */
    fun randomHold(settings: CueSettings): Long {
        val lo = settings.cadenceFloorMs
        val hi = settings.cadenceCeilMs.coerceAtLeast(lo + 1)
        return random.nextLong(lo, hi)
    }

    /** Applies the human-cadence delay. Bounded and interruptible via coroutine cancellation. */
    suspend fun applyHold(holdMs: Long) {
        if (holdMs > 0) delay(holdMs)
    }
}
