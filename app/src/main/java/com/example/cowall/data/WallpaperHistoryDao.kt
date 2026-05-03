package com.example.cowall.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface WallpaperHistoryDao {

    @Query("SELECT * FROM wallpaper_history ORDER BY timestamp DESC")
    suspend fun getAll(): List<WallpaperHistoryEntry>

    @Insert
    suspend fun insert(entry: WallpaperHistoryEntry)

    @Query("SELECT COUNT(*) FROM wallpaper_history")
    suspend fun count(): Int

    @Query("DELETE FROM wallpaper_history WHERE id IN (SELECT id FROM wallpaper_history ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)
}
