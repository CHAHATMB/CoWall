package com.example.cowall.utilities

import android.Manifest.permission.SET_WALLPAPER
import android.app.WallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.cowall.data.AppDatabase
import com.example.cowall.data.WallpaperHistoryEntry
import com.example.cowall.widget.CoWallWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

object WallpaperHelper {

    private const val LOG_TAG = "CoWall"
    private const val THUMBNAIL_DIR = "wallpaper_thumbnails"
    private const val THUMBNAIL_MAX_SIZE = 400

    fun setWallpaper(context: Context, imagePath: String, target: String = "lock", userName: String = "") {
        if (isUpdatesPaused(context)) return
        val bitmap = BitmapFactory.decodeFile(imagePath)
        if (bitmap == null) {
            Log.e(LOG_TAG, "WallpaperHelper: Failed to decode bitmap at: $imagePath")
            return
        }
        setWallpaper(context, bitmap, target, userName)
    }

    fun setWallpaper(context: Context, bitmap: Bitmap, target: String = "lock", userName: String = "") {
        if (isUpdatesPaused(context)) return
        if (target == "stop") {
            Log.d(LOG_TAG, "WallpaperHelper: Target is stop, skipping")
            return
        }
        if (ContextCompat.checkSelfPermission(context, SET_WALLPAPER) != PackageManager.PERMISSION_GRANTED) {
            Log.w(LOG_TAG, "WallpaperHelper: SET_WALLPAPER permission not granted")
            return
        }
        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val flags = when (target) {
                "home" -> WallpaperManager.FLAG_SYSTEM
                "both" -> WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM
                else -> WallpaperManager.FLAG_LOCK
            }
            wallpaperManager.setBitmap(bitmap, null, true, flags)
            Log.d(LOG_TAG, "WallpaperHelper: Wallpaper set successfully (target=$target)")

            saveWidgetPreview(context, bitmap)
            CoWallWidgetProvider.notifyNewWallpaper(context)

            val sharedPref = context.getSharedPreferences("cowall", Context.MODE_PRIVATE)
            if (sharedPref.getBoolean("autoResetEnabled", false) && target != "home") {
                sharedPref.edit().putBoolean("wallpaperPendingReset", true).apply()
                Log.d(LOG_TAG, "WallpaperHelper: Pending reset flagged")
            }

            CoroutineScope(Dispatchers.IO).launch {
                recordHistory(context, bitmap, target, userName)
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "WallpaperHelper: setWallpaper failed: $e")
        }
    }

    private suspend fun recordHistory(context: Context, bitmap: Bitmap, target: String, userName: String) {
        try {
            val dir = File(context.filesDir, THUMBNAIL_DIR).also { it.mkdirs() }
            val timestamp = System.currentTimeMillis()
            val thumbnailFile = File(dir, "thumb_$timestamp.jpg")

            val thumbnail = scaleThumbnail(bitmap)
            thumbnailFile.outputStream().use { thumbnail.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            if (thumbnail !== bitmap) thumbnail.recycle()

            val dao = AppDatabase.getInstance(context).wallpaperHistoryDao()
            dao.insert(WallpaperHistoryEntry(thumbnailPath = thumbnailFile.absolutePath, timestamp = timestamp, target = target, userName = userName))

            // Keep the table bounded
            val count = dao.count()
            val max = AppDatabase.MAX_WALLPAPER_HISTORY
            if (count > max) {
                dao.deleteOldest(count - max)
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "WallpaperHelper: Failed to record history: $e")
        }
    }

    private fun saveWidgetPreview(context: Context, bitmap: Bitmap) {
        try {
            val file = CoWallWidgetProvider.widgetPreviewFile(context)
            val scaled = scaleThumbnail(bitmap)
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            if (scaled !== bitmap) scaled.recycle()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "WallpaperHelper: Failed to save widget preview: $e")
        }
    }

    private fun scaleThumbnail(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= THUMBNAIL_MAX_SIZE && height <= THUMBNAIL_MAX_SIZE) return bitmap
        val scale = THUMBNAIL_MAX_SIZE.toFloat() / maxOf(width, height)
        return Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
    }

    private fun isUpdatesPaused(context: Context): Boolean {
        val paused = context.getSharedPreferences("cowall", Context.MODE_PRIVATE)
            .getBoolean("updatesPaused", false)
        if (paused) Log.d(LOG_TAG, "WallpaperHelper: Updates paused, skipping")
        return paused
    }
}
