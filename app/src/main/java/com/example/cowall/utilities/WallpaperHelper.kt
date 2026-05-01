package com.example.cowall.utilities

import android.Manifest.permission.SET_WALLPAPER
import android.app.WallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.content.ContextCompat

object WallpaperHelper {

    private const val LOG_TAG = "CoWall"

    fun setWallpaper(context: Context, imagePath: String, target: String = "lock") {
        if (isUpdatesPaused(context)) return
        val bitmap = BitmapFactory.decodeFile(imagePath)
        if (bitmap == null) {
            Log.e(LOG_TAG, "WallpaperHelper: Failed to decode bitmap at: $imagePath")
            return
        }
        setWallpaper(context, bitmap, target)
    }

    fun setWallpaper(context: Context, bitmap: Bitmap, target: String = "lock") {
        if (isUpdatesPaused(context)) return
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

            val sharedPref = context.getSharedPreferences("cowall", Context.MODE_PRIVATE)
            if (sharedPref.getBoolean("autoResetEnabled", false) && target != "home") {
                sharedPref.edit().putBoolean("wallpaperPendingReset", true).apply()
                Log.d(LOG_TAG, "WallpaperHelper: Pending reset flagged")
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "WallpaperHelper: setWallpaper failed: $e")
        }
    }

    private fun isUpdatesPaused(context: Context): Boolean {
        val paused = context.getSharedPreferences("cowall", Context.MODE_PRIVATE)
            .getBoolean("updatesPaused", false)
        if (paused) Log.d(LOG_TAG, "WallpaperHelper: Updates paused, skipping")
        return paused
    }
}
