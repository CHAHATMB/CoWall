package com.example.cowall.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cowall.R
import com.example.cowall.data.RoomJoinRecord
import java.text.SimpleDateFormat
import java.util.*

class RoomJoinHistoryAdapter(
    private val records: List<RoomJoinRecord>
) : RecyclerView.Adapter<RoomJoinHistoryAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val roomCodeText: TextView = itemView.findViewById(R.id.roomCodeText)
        val partnerNameText: TextView = itemView.findViewById(R.id.partnerNameText)
        val timestampText: TextView = itemView.findViewById(R.id.timestampText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room_join_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        holder.roomCodeText.text = formatRoomCode(record.roomId)
        holder.partnerNameText.text = if (record.partnerName.isNotEmpty()) record.partnerName else "Unknown"
        holder.timestampText.text = dateFormat.format(Date(record.timestamp))
    }

    override fun getItemCount(): Int = records.size

    private fun formatRoomCode(rawCode: String): String =
        if (rawCode.length == 8) "${rawCode.substring(0, 4)}-${rawCode.substring(4)}" else rawCode
}
