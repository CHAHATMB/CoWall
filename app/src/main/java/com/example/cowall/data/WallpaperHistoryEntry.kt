package com.example.cowall.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallpaper_history")
data class WallpaperHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val thumbnailPath: String,
    val timestamp: Long,
    val target: String,     // "lock", "home", or "both"
    val userName: String = ""
)
