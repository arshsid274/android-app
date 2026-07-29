package com.cue.reply.data.db

import androidx.room.TypeConverter
import com.cue.reply.domain.model.MessageDirection
import com.cue.reply.domain.model.ServiceType

class Converters {
    @TypeConverter fun serviceToString(v: ServiceType): String = v.name
    @TypeConverter fun stringToService(v: String): ServiceType = ServiceType.valueOf(v)

    @TypeConverter fun directionToString(v: MessageDirection): String = v.name
    @TypeConverter fun stringToDirection(v: String): MessageDirection = MessageDirection.valueOf(v)

    @TypeConverter fun activityToString(v: ActivityType): String = v.name
    @TypeConverter fun stringToActivity(v: String): ActivityType = ActivityType.valueOf(v)
}
