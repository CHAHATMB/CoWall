package com.example.cowall.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.cowall.FireBaseConnector
import com.example.cowall.R
import com.example.cowall.adapters.GalleryAdapter
import com.example.cowall.data.UserChat
import com.example.cowall.databinding.ActivityGalleryBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import java.io.File

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private val photos = mutableListOf<Uri>()
    private lateinit var galleryAdapter: GalleryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        loadPhotos()
    }

    private fun setupRecyclerView() {
        galleryAdapter = GalleryAdapter(
            context = this,
            photos = photos,
            onPhotoClick = { uri, imageView -> openFullscreen(uri, imageView) },
            onPhotoLongClick = { uri -> openWallpaperPreview(uri) }
        )
        binding.galleryRecyclerView.layoutManager = GridLayoutManager(this, 3)
        binding.galleryRecyclerView.adapter = galleryAdapter
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun loadPhotos() {
        binding.loadingIndicator.visibility = View.VISIBLE
        val roomId = FireBaseConnector.roomId

        FirebaseDatabase.getInstance().getReference("roomChat/$roomId")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    binding.loadingIndicator.visibility = View.GONE
                    val imagesDir = File(getExternalFilesDir(null), "images")

                    for (messageSnapshot in snapshot.children) {
                        val message = messageSnapshot.getValue(String::class.java) ?: continue
                        val userChat = Gson().fromJson(message, UserChat::class.java) ?: continue
                        if (userChat.type != "image") continue

                        val uri = userChat.uri
                        when {
                            uri.startsWith("https://") -> photos.add(Uri.parse(uri))
                            uri.isNotEmpty() -> {
                                val localFile = File(imagesDir, uri)
                                if (localFile.exists()) {
                                    photos.add(Uri.fromFile(localFile))
                                }
                            }
                        }
                    }

                    galleryAdapter.notifyDataSetChanged()
                    updateUi()
                }

                override fun onCancelled(error: DatabaseError) {
                    binding.loadingIndicator.visibility = View.GONE
                    updateUi()
                }
            })
    }

    private fun updateUi() {
        if (photos.isEmpty()) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.galleryRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.galleryRecyclerView.visibility = View.VISIBLE
            binding.photoCountText.text = "${photos.size} photos"
            binding.photoCountText.visibility = View.VISIBLE
        }
    }

    private fun openFullscreen(uri: Uri, imageView: ImageView) {
        val intent = Intent(this, PhotoViewActivity::class.java).apply {
            putExtra(PhotoViewActivity.EXTRA_IMAGE_URI, uri)
            putExtra(PhotoViewActivity.EXTRA_TRANSITION_NAME, imageView.transitionName)
        }
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            this, imageView, imageView.transitionName
        )
        startActivity(intent, options.toBundle())
    }

    private fun openWallpaperPreview(uri: Uri) {
        startActivity(Intent(this, WallpaperPreviewActivity::class.java).apply {
            putExtra(WallpaperPreviewActivity.EXTRA_IMAGE_URI, uri)
        })
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
}
