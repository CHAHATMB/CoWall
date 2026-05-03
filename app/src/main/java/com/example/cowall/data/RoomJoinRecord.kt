package com.example.cowall.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "room_join_history")
data class RoomJoinRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val roomId: String,
    val timestamp: Long,
    val partnerName: String = ""
)
