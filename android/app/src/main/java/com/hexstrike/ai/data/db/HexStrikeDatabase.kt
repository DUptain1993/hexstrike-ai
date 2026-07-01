package com.hexstrike.ai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChatSessionEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class HexStrikeDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile private var instance: HexStrikeDatabase? = null

        fun getInstance(context: Context): HexStrikeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HexStrikeDatabase::class.java,
                "hexstrike.db",
            ).build().also { instance = it }
        }
    }
}
