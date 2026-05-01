package com.example.cowall.adapters

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cowall.R

class GalleryAdapter(
    private val context: Context,
    private val photos: List<Uri>,
    private val onPhotoClick: (Uri, ImageView) -> Unit,
    private val onPhotoLongClick: (Uri) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.PhotoViewHolder>() {

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.galleryImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_gallery_photo, parent, false)
        // Make items square
        val size = parent.measuredWidth / 3
        view.layoutParams = ViewGroup.LayoutParams(size, size)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val uri = photos[position]
        holder.image.transitionName = "gallery_photo_$position"
        Glide.with(context)
            .load(uri)
            .placeholder(R.drawable.new_wallpaper)
            .centerCrop()
            .into(holder.image)

        holder.image.setOnClickListener { onPhotoClick(uri, holder.image) }
        holder.image.setOnLongClickListener {
            onPhotoLongClick(uri)
            true
        }
    }

    override fun getItemCount(): Int = photos.size
}
