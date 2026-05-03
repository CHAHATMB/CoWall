package com.example.cowall.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WallpaperHistoryEntry::class, CachedMessage::class, RoomJoinRecord::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun wallpaperHistoryDao(): WallpaperHistoryDao
    abstract fun cachedMessageDao(): CachedMessageDao
    abstract fun roomJoinRecordDao(): RoomJoinRecordDao

    companion object {
        private const val DATABASE_NAME = "cowall_db"
        const val MAX_WALLPAPER_HISTORY = 100

        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
