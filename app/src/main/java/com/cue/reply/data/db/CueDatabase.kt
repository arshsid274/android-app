package com.cue.reply.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cue.reply.data.db.dao.ActivityLogDao
import com.cue.reply.data.db.dao.ConversationDao
import com.cue.reply.data.db.dao.MessageDao
import com.cue.reply.data.db.entity.ActivityLogEntity
import com.cue.reply.data.db.entity.ConversationEntity
import com.cue.reply.data.db.entity.MessageEntity

@Database(
    entities = [MessageEntity::class, ConversationEntity::class, ActivityLogEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class CueDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        @Volatile private var instance: CueDatabase? = null

        fun get(context: Context): CueDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CueDatabase::class.java,
                    "cue.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
