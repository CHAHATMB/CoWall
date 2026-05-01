package com.example.cowall.adapters

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cowall.R
import com.example.cowall.data.WallpaperRecord
import java.text.SimpleDateFormat
import java.util.*

class WallpaperHistoryAdapter(
    private val context: Context,
    private val records: List<WallpaperRecord>,
    private val onClick: (WallpaperRecord) -> Unit
) : RecyclerView.Adapter<WallpaperHistoryAdapter.HistoryViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.historyImage)
        val dateText: TextView = itemView.findViewById(R.id.historyDateText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_wallpaper_history, parent, false)
        val size = parent.measuredWidth / 3
        view.layoutParams = ViewGroup.LayoutParams(size, size)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val record = records[position]
        holder.dateText.text = dateFormat.format(Date(record.timestamp))

        val uri = Uri.parse(record.imageUri)
        Glide.with(context)
            .load(uri)
            .placeholder(R.drawable.new_wallpaper)
            .centerCrop()
            .into(holder.image)

        holder.itemView.setOnClickListener { onClick(record) }
    }

    override fun getItemCount(): Int = records.size
}
