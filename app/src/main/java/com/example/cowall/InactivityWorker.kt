package com.example.cowall

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.cowall.activities.ChatRoomActivity
import com.example.cowall.activities.SettingsActivity
import kotlin.random.Random

class InactivityWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sharedPref = applicationContext.getSharedPreferences("cowall", Context.MODE_PRIVATE)
        
        // Only remind if they are actually in a room
        val roomId = sharedPref.getString("joinedRoomId", null) ?: return Result.success()
        
        val lastPhotoTime = sharedPref.getLong(SettingsActivity.PREF_LAST_PHOTO_SENT_TIMESTAMP, 0L)
        val lastNotificationTime = sharedPref.getLong(SettingsActivity.PREF_LAST_NOTIFICATION_SENT_TIMESTAMP, 0L)
        val currentTime = System.currentTimeMillis()
        
        val inactivityPeriod = 24 * 60 * 60 * 1000L // 24 hours
        val minNotificationGap = 24 * 60 * 60 * 1000L // 24 hours between notifications

        // Condition 1: Inactive for > 24h
        // Condition 2: Haven't sent a notification in > 24h (avoid spamming)
        val isInactiveEnough = lastPhotoTime > 0 && (currentTime - lastPhotoTime > inactivityPeriod)
        val isGapEnough = currentTime - lastNotificationTime >= minNotificationGap

        if (isInactiveEnough && isGapEnough) {
            val partnerName = sharedPref.getString(SettingsActivity.PREF_PARTNER_NAME, "your partner")
            showInactivityNotification(partnerName)
            
            // Record that we sent a notification now
            sharedPref.edit()
                .putLong(SettingsActivity.PREF_LAST_NOTIFICATION_SENT_TIMESTAMP, currentTime)
                .apply()
        }
        
        return Result.success()
    }

    private fun showInactivityNotification(partnerName: String?) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "cowall_reminders"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to send photos to your partner"
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, ChatRoomActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, message) = getRandomReminder(partnerName ?: "your partner")

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun getRandomReminder(partnerName: String): Pair<String, String> {
        val variations = listOf(
            "Thinking of $partnerName?" to "It's been over 24 hours since your last pic. Send one now!",
            "Missing $partnerName?" to "Surprise them with a new wallpaper! It's been a while.",
            "Time for a swap!" to "Your partner $partnerName is waiting for a new photo. Snap one!",
            "Keeping the spark?" to "Don't let the screen go stale. Send a fresh pic to $partnerName!",
            "Photo check-in!" to "It's been a day. What are you up to? Share a moment with $partnerName.",
            "Quick reminder!" to "Your partner would love to see a new photo on their screen right now.",
            "Make $partnerName smile!" to "Send a photo to refresh their wallpaper and their day.",
            "A picture is worth 1000 words" to "Tell $partnerName how you're doing with a quick snap!",
            "Screen looking lonely?" to "It's been 24h. Brighten up $partnerName's phone with your face!",
            "Hey! Remember CoWall?" to "Don't leave $partnerName hanging. Time for a new wallpaper!",
            "Capture the moment" to "Whatever you're doing, $partnerName would love to see it.",
            "Update your space" to "A lot can happen in a day. Share a piece of yours with $partnerName.",
            "Thinking of you..." to "Let $partnerName know they're on your mind. Send a pic!",
            "New day, new wallpaper" to "Refresh the connection! Send a new photo to $partnerName.",
            "Distance is just a number" to "Feel closer to $partnerName by sharing a moment right now.",
            "Surprise! \uD83C\uDF81" to "Send a random pic to $partnerName just because you can.",
            "The 24h challenge" to "You haven't sent a pic in a day! Can you break the streak now?",
            "Your partner misses you" to "Show $partnerName some love with a fresh wallpaper update."
        )
        return variations[Random.nextInt(variations.size)]
    }

    companion object {
        private const val NOTIFICATION_ID = 2
    }
}
