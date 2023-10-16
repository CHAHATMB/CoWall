package com.example.cowall

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import kotlin.random.Random

class SplashScreenActivity : AppCompatActivity() {
    private val SPLASH_TIME_OUT: Long = 1000
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen);

        val shouldShowMainScreen = checkConditions() // Replace with your own condition check
        Handler().postDelayed({
            val intent = if (shouldShowMainScreen) {
                Intent(this, MainActivity::class.java)
            } else {
                Intent(this, CreateOrJoinRoom::class.java)
            }

            startActivity(intent)
            finish()
        }, SPLASH_TIME_OUT)
    }

    private fun checkConditions(): Boolean {
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        val identifierString = "joinedRoomId"
        var joinedRoomId : String
        if (sharedPref.contains(identifierString)) {
            joinedRoomId = sharedPref.getString(identifierString, "default_value").toString()
            return true
        } else {
            if(sharedPref.contains("roomId")){
                val roomId = sharedPref.getString("roomId", "default_value").toString()
                val fbc = FireBaseConnector()
                fbc.initializeConnection(this.applicationContext)
                val roomDataCounts = fbc.getMessageCount("roomId/${roomId}")
                if(roomDataCounts > 1){
                    with (sharedPref.edit()) {
                        putString(identifierString, roomId)
                        apply()
                    }
                    return true
                } else{
                    return false
                }

            } else {
                return false
            }
        }
    }
}
