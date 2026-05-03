package com.example.cowall.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cowall.R
import com.example.cowall.data.WallpaperHistoryEntry
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class WallpaperHistoryAdapter(
    private val context: Context,
    private val entries: List<WallpaperHistoryEntry>,
    private val onClick: (WallpaperHistoryEntry) -> Unit
) : RecyclerView.Adapter<WallpaperHistoryAdapter.HistoryViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.historyImage)
        val screenText: TextView = itemView.findViewById(R.id.historyScreenText)
        val userText: TextView = itemView.findViewById(R.id.historyUserText)
        val dateText: TextView = itemView.findViewById(R.id.historyDateText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_wallpaper_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val entry = entries[position]

        holder.screenText.text = when (entry.target) {
            "home" -> "Home screen"
            "both" -> "Home & Lock screen"
            else -> "Lock screen"
        }

        holder.userText.text = if (entry.userName.isNotEmpty()) "by ${entry.userName}" else ""
        holder.userText.visibility = if (entry.userName.isNotEmpty()) View.VISIBLE else View.GONE

        holder.dateText.text = dateFormat.format(Date(entry.timestamp))

        Glide.with(context)
            .load(File(entry.thumbnailPath))
            .placeholder(R.drawable.new_wallpaper)
            .centerCrop()
            .into(holder.image)

        holder.itemView.setOnClickListener { onClick(entry) }
    }

    override fun getItemCount(): Int = entries.size
}
