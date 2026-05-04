package com.example.cowall.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.cowall.R
import com.example.cowall.activities.ChatRoomActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class CoWallWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val LOG_TAG = "CoWall"
        private const val PREVIEW_FILENAME = "widget_preview.jpg"

        // Max pixel dimension for the bitmap sent over Binder to the launcher.
        // 400px * 400px * 4 bytes = 640 KB, well within the 1 MB Binder limit.
        private const val MAX_BITMAP_PX = 400

        const val ACTION_WIDGET_UPDATE = "com.example.cowall.ACTION_WIDGET_UPDATE"
        const val ACTION_WIDGET_CLEAR  = "com.example.cowall.ACTION_WIDGET_CLEAR"
        const val ACTION_WIDGET_STOP   = "com.example.cowall.ACTION_WIDGET_STOP"

        const val PREF_WIDGET_STATE = "widget_state"
        const val STATE_ACTIVE  = "active"
        const val STATE_CLEARED = "cleared"
        const val STATE_STOPPED = "stopped"

        fun widgetPreviewFile(context: Context): File = File(context.filesDir, PREVIEW_FILENAME)

        /**
         * Called by WallpaperHelper after a new wallpaper is set.
         * Refreshes the widget only when state is ACTIVE; otherwise the preview file is
         * already updated so the next "Update" tap will show the latest image.
         */
        fun notifyNewWallpaper(context: Context) {
            val state = context.getSharedPreferences("cowall", Context.MODE_PRIVATE)
                .getString(PREF_WIDGET_STATE, STATE_ACTIVE) ?: STATE_ACTIVE
            if (state != STATE_ACTIVE) return

            CoroutineScope(Dispatchers.IO).launch {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, CoWallWidgetProvider::class.java))
                ids.forEach { id -> refreshWidget(context, manager, id) }
            }
        }

        private fun refreshWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences("cowall", Context.MODE_PRIVATE)
            val state = prefs.getString(PREF_WIDGET_STATE, STATE_ACTIVE) ?: STATE_ACTIVE

            val views = RemoteViews(context.packageName, R.layout.widget_cowall)
            views.setOnClickPendingIntent(R.id.widgetRootFrame, buildLaunchIntent(context))

            if (state == STATE_CLEARED) {
                views.setTextViewText(R.id.widgetEmptyText, context.getString(R.string.widget_empty_cleared))
                showEmptyState(views)
            } else {
                val preview = widgetPreviewFile(context)
                val bitmap = if (preview.exists()) loadDownsampled(preview.absolutePath) else null
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.widgetImage, bitmap)
                    views.setViewVisibility(R.id.widgetImage, View.VISIBLE)
                    views.setViewVisibility(R.id.widgetEmptyState, View.GONE)
                } else {
                    views.setTextViewText(R.id.widgetEmptyText, context.getString(R.string.widget_empty_no_image))
                    showEmptyState(views)
                }
            }

            manager.updateAppWidget(widgetId, views)
        }

        private fun showEmptyState(views: RemoteViews) {
            views.setViewVisibility(R.id.widgetImage, View.GONE)
            views.setViewVisibility(R.id.widgetEmptyState, View.VISIBLE)
        }

        private fun buildLaunchIntent(context: Context): PendingIntent {
            val intent = Intent(context, ChatRoomActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun loadDownsampled(path: String): Bitmap? {
            return try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sampleSize = 1
                while (bounds.outWidth / sampleSize > MAX_BITMAP_PX ||
                    bounds.outHeight / sampleSize > MAX_BITMAP_PX) {
                    sampleSize *= 2
                }
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Widget: loadDownsampled failed: $e")
                null
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> refreshWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val newState = when (intent.action) {
            ACTION_WIDGET_UPDATE -> STATE_ACTIVE
            ACTION_WIDGET_CLEAR  -> STATE_CLEARED
            ACTION_WIDGET_STOP   -> STATE_STOPPED
            else -> return
        }

        context.getSharedPreferences("cowall", Context.MODE_PRIVATE)
            .edit().putString(PREF_WIDGET_STATE, newState).apply()

        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, CoWallWidgetProvider::class.java))
        ids.forEach { id -> refreshWidget(context, manager, id) }
    }
}
