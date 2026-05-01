package com.example.cowall.data

data class PresenceData(
    val online: Boolean = false,
    val lastSeen: Long = 0L,
    val typing: Boolean = false
)
