package com.cue.reply.data.repository

import com.cue.reply.data.db.ActivityType
import com.cue.reply.data.db.dao.ActivityLogDao
import com.cue.reply.data.db.entity.ActivityLogEntity
import com.cue.reply.domain.model.ServiceType
import kotlinx.coroutines.flow.Flow

/** Append-only audit trail. Every automated decision passes through here. */
class ActivityRepository(
    private val dao: ActivityLogDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun log(
        type: ActivityType,
        threadKey: String? = null,
        service: ServiceType? = null,
        contactName: String? = null,
        detail: String? = null,
    ) {
        dao.insert(
            ActivityLogEntity(
                type = type,
                threadKey = threadKey,
                service = service,
                contactName = contactName,
                detail = detail,
                timestamp = clock(),
            )
        )
    }

    fun recent(limit: Int = 200): Flow<List<ActivityLogEntity>> = dao.recent(limit)

    fun recentByType(type: ActivityType, limit: Int = 200): Flow<List<ActivityLogEntity>> =
        dao.recentByType(type, limit)

    /** Count of a given event within the trailing [windowMs] (used by the rate limiter). */
    suspend fun countWithin(type: ActivityType, windowMs: Long): Int =
        dao.countSince(type, clock() - windowMs)

    /**
     * Automated sends in one thread since the last time that person actually wrote.
     * This is the loop-breaker's real question — "am I talking to myself?" — which a
     * global count answers wrongly: a busy chat would mute every other chat.
     */
    suspend fun autoSentSinceLastInbound(threadKey: String): Int {
        val lastInbound = dao.lastTimestampOf(ActivityType.RECEIVED, threadKey) ?: return 0
        return dao.countSinceForThread(ActivityType.AUTO_SENT, threadKey, lastInbound)
    }

    suspend fun pruneOlderThan(cutoff: Long) = dao.deleteOlderThan(cutoff)

    suspend fun clear() = dao.clear()
}
