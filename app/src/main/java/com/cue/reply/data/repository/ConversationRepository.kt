package com.cue.reply.data.repository

import com.cue.reply.data.db.dao.ConversationDao
import com.cue.reply.data.db.dao.MessageDao
import com.cue.reply.data.db.entity.ConversationEntity
import com.cue.reply.data.db.entity.MessageEntity
import com.cue.reply.domain.model.IncomingMessage
import com.cue.reply.domain.model.MessageDirection
import com.cue.reply.domain.model.ServiceType
import kotlinx.coroutines.flow.Flow

/** Persists messages and keeps the per-conversation summary in sync. */
class ConversationRepository(
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
) {
    /** Store an observed message and update its conversation head. */
    suspend fun record(message: IncomingMessage) {
        // The same outgoing text can arrive twice: once recorded at send time and
        // again echoed back through the notification's message history. Keep one.
        val latest = messageDao.recentForThread(message.threadKey, 1).firstOrNull()
        if (latest != null && latest.text == message.text && latest.direction == message.direction) {
            return
        }
        messageDao.insert(
            MessageEntity(
                threadKey = message.threadKey,
                service = message.service,
                contactKey = message.contactKey,
                contactName = message.contactName,
                text = message.text,
                direction = message.direction,
                timestamp = message.timestamp,
            )
        )
        val existing = conversationDao.get(message.threadKey)
        conversationDao.upsert(
            ConversationEntity(
                threadKey = message.threadKey,
                service = message.service,
                contactKey = message.contactKey,
                contactName = message.contactName ?: existing?.contactName,
                lastMessageText = message.text,
                lastMessageTs = message.timestamp,
                lastDirection = message.direction,
                allowlisted = existing?.allowlisted ?: false,
                muted = existing?.muted ?: false,
                unreadDraft = message.direction == MessageDirection.INCOMING,
                personaPreset = existing?.personaPreset,
            )
        )
    }

    fun conversations(): Flow<List<ConversationEntity>> = conversationDao.all()

    fun conversationsFor(service: ServiceType): Flow<List<ConversationEntity>> =
        conversationDao.byService(service.name)

    fun conversation(threadKey: String): Flow<ConversationEntity?> =
        conversationDao.observe(threadKey)

    fun messages(threadKey: String): Flow<List<MessageEntity>> =
        messageDao.messagesForThread(threadKey)

    suspend fun recentMessages(threadKey: String, limit: Int = 20): List<MessageEntity> =
        messageDao.recentForThread(threadKey, limit).reversed()

    fun allowlisted(): Flow<List<ConversationEntity>> = conversationDao.allowlisted()

    suspend fun isAllowlisted(threadKey: String): Boolean =
        conversationDao.get(threadKey)?.allowlisted == true

    suspend fun setAllowlisted(threadKey: String, value: Boolean) =
        conversationDao.setAllowlisted(threadKey, value)

    suspend fun setMuted(threadKey: String, value: Boolean) =
        conversationDao.setMuted(threadKey, value)

    /** Null clears the override so the thread follows the global persona. */
    suspend fun setPersonaPreset(threadKey: String, preset: String?) =
        conversationDao.setPersonaPreset(threadKey, preset)

    suspend fun personaPreset(threadKey: String): String? =
        conversationDao.get(threadKey)?.personaPreset

    suspend fun isMuted(threadKey: String): Boolean =
        conversationDao.get(threadKey)?.muted == true

    suspend fun markDraftSeen(threadKey: String) =
        conversationDao.setUnreadDraft(threadKey, false)

    suspend fun clearAllowlist() = conversationDao.clearAllowlist()

    suspend fun deleteThread(threadKey: String) {
        messageDao.deleteThread(threadKey)
        conversationDao.delete(threadKey)
    }

    suspend fun pruneOlderThan(cutoff: Long) = messageDao.deleteOlderThan(cutoff)

    suspend fun deleteEverything() {
        messageDao.clear()
        conversationDao.clear()
    }
}
