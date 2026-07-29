package com.cue.reply.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.cue.reply.data.db.ActivityType
import com.cue.reply.data.db.entity.ActivityLogEntity
import com.cue.reply.data.db.entity.ConversationEntity
import com.cue.reply.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE threadKey = :threadKey ORDER BY timestamp ASC")
    fun messagesForThread(threadKey: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE threadKey = :threadKey ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentForThread(threadKey: String, limit: Int): List<MessageEntity>

    @Query("DELETE FROM messages WHERE threadKey = :threadKey")
    suspend fun deleteThread(threadKey: String)

    @Query("DELETE FROM messages WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Dao
interface ConversationDao {
    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations ORDER BY lastMessageTs DESC")
    fun all(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE service = :service ORDER BY lastMessageTs DESC")
    fun byService(service: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE threadKey = :threadKey")
    fun observe(threadKey: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE threadKey = :threadKey")
    suspend fun get(threadKey: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE allowlisted = 1 ORDER BY lastMessageTs DESC")
    fun allowlisted(): Flow<List<ConversationEntity>>

    @Query("UPDATE conversations SET allowlisted = :value WHERE threadKey = :threadKey")
    suspend fun setAllowlisted(threadKey: String, value: Boolean)

    @Query("UPDATE conversations SET muted = :value WHERE threadKey = :threadKey")
    suspend fun setMuted(threadKey: String, value: Boolean)

    @Query("UPDATE conversations SET unreadDraft = :value WHERE threadKey = :threadKey")
    suspend fun setUnreadDraft(threadKey: String, value: Boolean)

    @Query("UPDATE conversations SET personaPreset = :preset WHERE threadKey = :threadKey")
    suspend fun setPersonaPreset(threadKey: String, preset: String?)

    @Query("UPDATE conversations SET allowlisted = 0")
    suspend fun clearAllowlist()

    @Query("DELETE FROM conversations WHERE threadKey = :threadKey")
    suspend fun delete(threadKey: String)

    @Query("DELETE FROM conversations")
    suspend fun clear()
}

@Dao
interface ActivityLogDao {
    @Insert
    suspend fun insert(entry: ActivityLogEntity): Long

    @Query("SELECT * FROM activity_log ORDER BY timestamp DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_log WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    fun recentByType(type: ActivityType, limit: Int): Flow<List<ActivityLogEntity>>

    @Query("SELECT COUNT(*) FROM activity_log WHERE type = :type AND timestamp >= :since")
    suspend fun countSince(type: ActivityType, since: Long): Int

    @Query("DELETE FROM activity_log WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM activity_log")
    suspend fun clear()
}
