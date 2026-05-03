package com.example.cowall.activities

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.cowall.R
import com.example.cowall.databinding.ActivityWallpaperPreviewBinding
import com.example.cowall.utilities.WallpaperHelper
import com.example.cowall.utilities.showSuccessSnackbar
import com.example.cowall.utilities.showErrorSnackbar
import java.text.SimpleDateFormat
import java.util.*

class WallpaperPreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_URI = "imageUri"
        const val EXTRA_IMAGE_PATH = "imagePath"
    }

    private lateinit var binding: ActivityWallpaperPreviewBinding
    private var imageUri: Uri? = null
    private var imagePath: String? = null

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            binding.previewTimeText.text = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
            clockHandler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWallpaperPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageUri = intent.getParcelableExtra(EXTRA_IMAGE_URI)
        imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)

        setupPreview()
        setupTargetToggle()
        setupListeners()
        setupTimeDisplay()
    }

    private fun setupPreview() {
        when {
            imageUri != null -> Glide.with(this).load(imageUri).centerCrop().into(binding.wallpaperPreviewImage)
            imagePath != null -> {
                val bitmap = BitmapFactory.decodeFile(imagePath)
                if (bitmap != null) binding.wallpaperPreviewImage.setImageBitmap(bitmap)
            }
        }
    }

    private fun setupTargetToggle() {
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        when (sharedPref.getString("wallpaperTarget", "lock")) {
            "lock" -> binding.targetGroup.check(R.id.btnLockScreen)
            "home" -> binding.targetGroup.check(R.id.btnHomeScreen)
            "both" -> binding.targetGroup.check(R.id.btnBothScreens)
        }
    }

    override fun onResume() {
        super.onResume()
        clockHandler.post(clockRunnable)
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockRunnable)
    }

    private fun setupTimeDisplay() {
        binding.previewDateText.text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.setWallpaperButton.setOnClickListener {
            binding.setWallpaperButton.isEnabled = false
            binding.setWallpaperButton.text = "Setting..."
            setAsWallpaper()
        }
    }

    private fun getSelectedTarget(): String {
        return when (binding.targetGroup.checkedButtonId) {
            R.id.btnHomeScreen -> "home"
            R.id.btnBothScreens -> "both"
            else -> "lock"
        }
    }

    private fun setAsWallpaper() {
        val target = getSelectedTarget()
        val uri = imageUri
        val path = imagePath
        val userName = getSharedPreferences("cowall", Context.MODE_PRIVATE)
            .getString("userName", "") ?: ""

        try {
            when {
                path != null -> WallpaperHelper.setWallpaper(this, path, target, userName)
                uri != null -> {
                    val inputStream = contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (bitmap != null) {
                        WallpaperHelper.setWallpaper(this, bitmap, target, userName)
                    } else {
                        showErrorSnackbar("Failed to load image")
                        return
                    }
                }
                else -> {
                    showErrorSnackbar("No image to set")
                    return
                }
            }

            showSuccessSnackbar("Wallpaper set successfully!")

            binding.setWallpaperButton.postDelayed({ finish() }, 1000)
        } catch (e: Exception) {
            binding.setWallpaperButton.isEnabled = true
            binding.setWallpaperButton.text = "Set Wallpaper"
            showErrorSnackbar("Failed to set wallpaper: ${e.localizedMessage}")
        }
    }

}
