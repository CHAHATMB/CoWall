package com.example.cowall.data

data class UserChat(
    val userUniqueId: String = "",
    val uri: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "image",   // "image", "text", "reaction"
    val text: String? = null,
    val replyToKey: String? = null,
    val replyPreview: String? = null
)
