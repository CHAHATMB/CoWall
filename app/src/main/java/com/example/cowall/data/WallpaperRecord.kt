package com.example.cowall.data

data class WallpaperRecord(
    val imageUri: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val target: String = "lock"
)
