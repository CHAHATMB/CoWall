package com.example.cowall.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_messages")
data class CachedMessage(
    @PrimaryKey val messageKey: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val type: String = "text",           // "text" or "image"
    val text: String = "",
    val localImagePath: String? = null,  // absolute path to downloaded image file
    val remoteImageUri: String? = null,  // Firebase path or HTTPS URL for re-download fallback
    val timestamp: Long = 0L,
    val replyToKey: String? = null,
    val replyPreview: String? = null
)
