package com.example.cowall

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.NonCancellable.start

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

    private fun start(){
            val notification = NotificationCompat.Builder(this, "running_channel")
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