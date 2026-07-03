package com.vulnrbot.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChatSessionEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class VulnrBotDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile private var instance: VulnrBotDatabase? = null

        fun getInstance(context: Context): VulnrBotDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                VulnrBotDatabase::class.java,
                "vulnrbot.db",
            ).build().also { instance = it }
        }
    }
}
