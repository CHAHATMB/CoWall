package com.example.cowall.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedMessageDao {

    @Query("SELECT * FROM cached_messages WHERE roomId = :roomId ORDER BY timestamp ASC")
    suspend fun loadMessages(roomId: String): List<CachedMessage>

    // REPLACE handles both insert-new and update-existing (e.g. image path filled in after download)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: CachedMessage)

    @Query("DELETE FROM cached_messages WHERE roomId = :roomId")
    suspend fun clearRoom(roomId: String)

    @Query("SELECT COUNT(*) > 0 FROM cached_messages WHERE roomId = :roomId")
    suspend fun hasMessages(roomId: String): Boolean

    @Query("SELECT * FROM cached_messages WHERE roomId = :roomId AND type = 'image' AND localImagePath IS NOT NULL ORDER BY timestamp ASC")
    suspend fun loadImageMessages(roomId: String): List<CachedMessage>
}
