package com.example.cowall.data

// Model class for a user
data class User(val userId: String, val userName: String)

// Model class for a chat room
data class ChatRoom(val participants: Map<String, Boolean>)
