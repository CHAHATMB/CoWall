package com.example.cowall.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.cowall.FireBaseConnector
import com.example.cowall.R
import com.example.cowall.adapters.GalleryAdapter
import com.example.cowall.data.AppDatabase
import com.example.cowall.databinding.ActivityGalleryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val roomId = try { FireBaseConnector.roomId } catch (_: UninitializedPropertyAccessException) {
            binding.loadingIndicator.visibility = View.GONE
            updateUi()
            return
        }

        lifecycleScope.launch {
            val imageMessages = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(this@GalleryActivity)
                    .cachedMessageDao()
                    .loadImageMessages(roomId)
            }

            for (msg in imageMessages) {
                val localPath = msg.localImagePath ?: continue
                val file = File(localPath)
                if (file.exists()) photos.add(Uri.fromFile(file))
            }

            binding.loadingIndicator.visibility = View.GONE
            galleryAdapter.notifyDataSetChanged()
            updateUi()
        }
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
