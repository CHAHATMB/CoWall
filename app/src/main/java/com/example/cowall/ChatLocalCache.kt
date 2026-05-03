package com.example.cowall

import android.content.Context
import com.example.cowall.data.AppDatabase
import com.example.cowall.data.CachedMessage

class ChatLocalCache(context: Context) {

    private val dao = AppDatabase.getInstance(context).cachedMessageDao()

    suspend fun load(roomId: String): List<CachedMessage> = dao.loadMessages(roomId)

    suspend fun append(roomId: String, message: CachedMessage) {
        dao.upsert(message.copy(roomId = roomId))
    }

    suspend fun hasMessages(roomId: String): Boolean = dao.hasMessages(roomId)

    suspend fun clear(roomId: String) = dao.clearRoom(roomId)
}
