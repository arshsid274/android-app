package com.cue.reply.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cue.reply.data.db.ActivityType
import com.cue.reply.domain.model.MessageDirection
import com.cue.reply.domain.model.ServiceType

/** One stored message. Shared schema for every source. */
@Entity(
    tableName = "messages",
    indices = [Index("threadKey"), Index("timestamp")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadKey: String,
    val service: ServiceType,
    val contactKey: String,
    val contactName: String?,
    val text: String,
    val direction: MessageDirection,
    val timestamp: Long,
)

/** Per-conversation summary + per-thread flags (allowlist, mute). */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val threadKey: String,
    val service: ServiceType,
    val contactKey: String,
    val contactName: String?,
    val lastMessageText: String,
    val lastMessageTs: Long,
    val lastDirection: MessageDirection,
    val allowlisted: Boolean = false,
    val muted: Boolean = false,
    val unreadDraft: Boolean = false,
    /** PersonaPreset name, or null to follow the global persona from Settings. */
    val personaPreset: String? = null,
)

/** Append-only audit entry. */
@Entity(
    tableName = "activity_log",
    indices = [Index("timestamp"), Index("type")],
)
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ActivityType,
    val threadKey: String?,
    val service: ServiceType?,
    val contactName: String?,
    val detail: String?,
    val timestamp: Long,
)
