package com.example.cowall.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RoomJoinRecordDao {
    @Query("SELECT * FROM room_join_history ORDER BY timestamp DESC")
    fun getAll(): List<RoomJoinRecord>

    @Insert
    fun insert(record: RoomJoinRecord)
}
