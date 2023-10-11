package com.example.cowall

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
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
        for( i in 1..100){
            val notification = NotificationCompat.Builder(this, "running_channel")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Wallpaper Updates")
                .setContentText("Looking for wallpapers ${i}")
                .build()
            startForeground(1,notification)
        }


        val fbc = FireBaseConnector()
        fbc.initializeConnection()
        fbc.lookForUpdates()
    }
}