package com.astroluna.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.astroluna.app.data.local.dao.ChatDao
import com.astroluna.app.data.local.entity.ChatMessageEntity

@Database(entities = [ChatMessageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "astro5star_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
