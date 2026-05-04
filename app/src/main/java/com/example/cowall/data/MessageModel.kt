package com.example.cowall.data

import android.net.Uri

enum class MessageStatus { SENDING, SENT, DELIVERED, FAILED }

data class MessageModel(
    val message: String = "",
    val imageUri: Uri? = null,
    val senderId: String = "",
    val caption: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENDING,
    val messageKey: String = "",
    val reactions: Map<String, String> = emptyMap(),
    val replyToKey: String? = null,
    val replyPreview: String? = null,
    val isWallpaper: Boolean = false
)