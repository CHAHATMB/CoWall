package com.example.cowall

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.example.cowall.activities.ChatRoomActivity
import com.example.cowall.activities.SettingsActivity.Companion.DEFAULT_RESET_DELAY_MINUTES
import com.example.cowall.activities.SettingsActivity.Companion.PREF_AUTO_RESET_DELAY_MINUTES
import com.example.cowall.activities.SettingsActivity.Companion.PREF_AUTO_RESET_ENABLED
import com.example.cowall.activities.SettingsActivity.Companion.PREF_DEFAULT_WALLPAPER_PATH
import com.example.cowall.activities.SettingsActivity.Companion.PREF_WALLPAPER_PENDING_RESET
import com.example.cowall.activities.SettingsActivity.Companion.PREF_WALLPAPER_TARGET
import com.example.cowall.activities.SettingsActivity.Companion.PREF_UPDATES_PAUSED
import com.example.cowall.activities.SettingsActivity.Companion.TARGET_HOME
import com.example.cowall.utilities.WallpaperHelper
import org.koin.android.ext.android.inject

class RunningService : Service() {

    enum class Actions { START, STOP, REACT, REPLY, TOGGLE_PAUSE, REFRESH_NOTIFICATION }

    private val fbc: ChatConnector by inject()
    private var unlockReceiver: BroadcastReceiver? = null
    private val resetHandler = Handler(Looper.getMainLooper())
    private var pendingResetRunnable: Runnable? = null
    private var notificationRestoreRunnable: Runnable? = null
    private var countdownTargetMillis: Long = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Actions.START.toString() -> start()
            Actions.STOP.toString() -> stop()
            Actions.REACT.toString() -> handleReact()
            Actions.REPLY.toString() -> handleReply(intent)
            Actions.TOGGLE_PAUSE.toString() -> handleTogglePause()
            Actions.REFRESH_NOTIFICATION.toString() -> restoreNotificationState()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        fbc.stopListening()
        unregisterUnlockReceiver()
    }

    // ─── Service lifecycle ─────────────────────────────

    private fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.w(LOG_TAG, "RunningService requires API 26+; skipping foreground start")
            return
        }

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Wallpaper Updates",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Listens for wallpaper updates from partner" }
        manager.createNotificationChannel(channel)

        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        val initialStatus = if (sharedPref.getBoolean(PREF_UPDATES_PAUSED, false))
            STATUS_PAUSED else STATUS_IDLE
        startForeground(NOTIFICATION_ID, buildNotification(initialStatus))
        val joinedRoomId = sharedPref.getString("joinedRoomId", null)
        if (joinedRoomId == null) {
            Log.w(LOG_TAG, "RunningService: no joinedRoomId found, stopping")
            stopSelf()
            return
        }

        val userId = sharedPref.getString("userUniqueId", "") ?: ""
        FireBaseConnector.setUniqueIds(userId, joinedRoomId)

        fbc.initializeConnection(applicationContext)
        fbc.lookForUpdates("roomChat/$joinedRoomId")

        registerUnlockReceiver()
    }

    private fun stop() {
        fbc.stopListening()
        unregisterUnlockReceiver()
        stopSelf()
    }

    // ─── Notification ──────────────────────────────────

    private fun buildNotification(
        contentText: String,
        showCountdown: Boolean = false,
        countdownTarget: Long = 0
    ): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this, REQUEST_CONTENT,
            Intent(this, ChatRoomActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("CoWall")
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(buildReplyAction())
            .addAction(buildReactAction())
            .addAction(buildPauseAction())

        if (showCountdown && countdownTarget > System.currentTimeMillis()) {
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(countdownTarget)
                .setShowWhen(true)
        } else {
            builder.setShowWhen(false)
        }

        return builder.build()
    }

    private fun buildReplyAction(): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel("Message...")
            .build()
        val pendingIntent = PendingIntent.getService(
            this, REQUEST_REPLY,
            Intent(this, RunningService::class.java).apply { action = Actions.REPLY.toString() },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_baseline_send_24, "Reply", pendingIntent
        ).addRemoteInput(remoteInput).build()
    }

    private fun buildReactAction(): NotificationCompat.Action {
        val pendingIntent = PendingIntent.getService(
            this, REQUEST_REACT,
            Intent(this, RunningService::class.java).apply { action = Actions.REACT.toString() },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, "❤️", pendingIntent).build()
    }

    private fun buildPauseAction(): NotificationCompat.Action {
        val paused = getSharedPreferences("cowall", Context.MODE_PRIVATE)
            .getBoolean(PREF_UPDATES_PAUSED, false)
        val label = if (paused) "Resume" else "Pause"
        val pendingIntent = PendingIntent.getService(
            this, REQUEST_PAUSE,
            Intent(this, RunningService::class.java).apply {
                action = Actions.TOGGLE_PAUSE.toString()
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, label, pendingIntent).build()
    }

    private fun updateNotification(contentText: String) {
        val notification = buildNotification(contentText)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationWithCountdown(contentText: String, resetTimeMillis: Long) {
        countdownTargetMillis = resetTimeMillis
        val notification = buildNotification(
            contentText, showCountdown = true, countdownTarget = resetTimeMillis
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showBriefStatus(text: String) {
        notificationRestoreRunnable?.let { resetHandler.removeCallbacks(it) }
        updateNotification(text)
        notificationRestoreRunnable = Runnable { restoreNotificationState() }
        resetHandler.postDelayed(notificationRestoreRunnable!!, STATUS_DISPLAY_DURATION)
    }

    private fun restoreNotificationState() {
        val paused = getSharedPreferences("cowall", Context.MODE_PRIVATE)
            .getBoolean(PREF_UPDATES_PAUSED, false)
        if (paused) {
            countdownTargetMillis = 0
            updateNotification(STATUS_PAUSED)
        } else if (countdownTargetMillis > System.currentTimeMillis()) {
            updateNotificationWithCountdown(STATUS_COUNTDOWN, countdownTargetMillis)
        } else {
            countdownTargetMillis = 0
            updateNotification(STATUS_IDLE)
        }
    }

    // ─── Notification action handlers ──────────────────

    private fun handleReact() {
        fbc.sendTextMessage("❤️")
        showBriefStatus("Sent ❤️ to partner")
        Log.d(LOG_TAG, "React sent from notification")
    }

    private fun handleReply(intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_REPLY_TEXT)?.toString()
        if (text.isNullOrBlank()) return
        fbc.sendTextMessage(text)
        showBriefStatus("Sent: $text")
        Log.d(LOG_TAG, "Reply sent from notification: $text")
    }

    private fun handleTogglePause() {
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        val wasPaused = sharedPref.getBoolean(PREF_UPDATES_PAUSED, false)
        val nowPaused = !wasPaused
        sharedPref.edit().putBoolean(PREF_UPDATES_PAUSED, nowPaused).apply()

        if (nowPaused) {
            pendingResetRunnable?.let { resetHandler.removeCallbacks(it) }
            pendingResetRunnable = null
            countdownTargetMillis = 0
            updateNotification(STATUS_PAUSED)
            Log.d(LOG_TAG, "Updates paused from notification")
        } else {
            restoreNotificationState()
            Log.d(LOG_TAG, "Updates resumed from notification")
        }
    }

    // ─── Unlock detection ──────────────────────────────

    private fun registerUnlockReceiver() {
        unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_USER_PRESENT) {
                    onDeviceUnlocked()
                }
            }
        }
        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_EXPORTED
        )
        Log.d(LOG_TAG, "Unlock receiver registered")
    }

    private fun unregisterUnlockReceiver() {
        pendingResetRunnable?.let { resetHandler.removeCallbacks(it) }
        pendingResetRunnable = null
        notificationRestoreRunnable?.let { resetHandler.removeCallbacks(it) }
        notificationRestoreRunnable = null
        unlockReceiver?.let {
            try { unregisterReceiver(it) } catch (_: IllegalArgumentException) {}
        }
        unlockReceiver = null
    }

    private fun onDeviceUnlocked() {
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        if (sharedPref.getBoolean(PREF_UPDATES_PAUSED, false)) return
        if (!sharedPref.getBoolean(PREF_AUTO_RESET_ENABLED, false)) return
        if (!sharedPref.getBoolean(PREF_WALLPAPER_PENDING_RESET, false)) return

        val target = sharedPref.getString(PREF_WALLPAPER_TARGET, "lock") ?: "lock"
        if (target == TARGET_HOME) return

        pendingResetRunnable?.let { resetHandler.removeCallbacks(it) }

        val delayMinutes = sharedPref.getInt(PREF_AUTO_RESET_DELAY_MINUTES, DEFAULT_RESET_DELAY_MINUTES)
        val delayMillis = delayMinutes * 60 * 1000L
        val resetTimeMillis = System.currentTimeMillis() + delayMillis

        pendingResetRunnable = Runnable {
            resetWallpaperToDefault()
            countdownTargetMillis = 0
            showBriefStatus("Wallpaper reset to default")
        }
        resetHandler.postDelayed(pendingResetRunnable!!, delayMillis)

        updateNotificationWithCountdown(STATUS_COUNTDOWN, resetTimeMillis)
        Log.d(LOG_TAG, "Wallpaper reset scheduled in $delayMinutes min")
    }

    // ─── Wallpaper reset (auto, lock-screen only) ──────

    private fun resetWallpaperToDefault() {
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        val defaultPath = sharedPref.getString(PREF_DEFAULT_WALLPAPER_PATH, null)
        if (defaultPath == null) {
            Log.w(LOG_TAG, "No default wallpaper set, skipping reset")
            return
        }

        val bitmap = BitmapFactory.decodeFile(defaultPath)
        if (bitmap == null) {
            Log.e(LOG_TAG, "Failed to decode default wallpaper: $defaultPath")
            return
        }

        try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
            sharedPref.edit().putBoolean(PREF_WALLPAPER_PENDING_RESET, false).apply()
            WallpaperHelper.updateWidgetPreview(this, bitmap)
            Log.d(LOG_TAG, "Wallpaper auto-reset to default (lock)")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to reset wallpaper: $e")
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        private const val LOG_TAG = "CoWall"
        private const val CHANNEL_ID = "cowall_wallpaper_updates"
        private const val NOTIFICATION_ID = 1

        private const val KEY_REPLY_TEXT = "key_reply_text"
        private const val REQUEST_CONTENT = 100
        private const val REQUEST_REACT = 101
        private const val REQUEST_REPLY = 102
        private const val REQUEST_PAUSE = 103

        private const val STATUS_IDLE = "Watching for wallpaper updates"
        private const val STATUS_PAUSED = "Updates paused"
        private const val STATUS_COUNTDOWN = "Wallpaper resets to default"
        private const val STATUS_DISPLAY_DURATION = 3000L
    }
}
