package com.example.cowall

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

class RunningService : Service() {

    enum class Actions {
        START, STOP
    }
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action){
            Actions.START.toString() -> start()
            Actions.STOP.toString() -> stopSelf()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun start(){

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val chan = NotificationChannel(
            "NOTIFICATION_CHANNEL_ID",
            "channelName", NotificationManager.IMPORTANCE_NONE
        )
        chan.description = "for muting"

        assert(manager != null)
        manager!!.createNotificationChannel(chan)


        val notification = NotificationCompat.Builder(this, "NOTIFICATION_CHANNEL_ID")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Wallpaper Updates")
                .setContentText("Looking for wallpapers 100")
                .build()
            startForeground(1,notification)


        val fbc = FireBaseConnector()
        fbc.initializeConnection(this.applicationContext)
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        var roomId : String = "unknown"
        if (sharedPref.contains("joinedRoomId")) {
            roomId = sharedPref.getString("joinedRoomId", "default_value").toString()
            FireBaseConnector.setUniqueIds(sharedPref.getString("userUniqueId","default_user_id").toString(),roomId)
            fbc.lookForUpdates("roomChat/${roomId}")
        } else {
            Toast.makeText(this,"Some Error",Toast.LENGTH_LONG)
        }
    }
}