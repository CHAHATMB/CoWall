package com.example.cowall

import android.content.Context
import android.content.Intent
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.example.cowall.activities.ChatRoomActivity
import com.example.cowall.activities.OnboardingActivity
import com.example.cowall.utilities.isNetworkAvailable
import com.example.cowall.utilities.showErrorSnackbar
import org.koin.android.ext.android.inject

class SplashScreenActivity : AppCompatActivity() {
    private val SPLASH_DELAY_MS: Long = 1800
    private val authProvider: AuthProvider by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        playEntryAnimations()

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isNetworkAvailable()) {
                showErrorSnackbar("No internet connection. Please check your network.") 
                Handler(Looper.getMainLooper()).postDelayed({ navigateToNextScreen() }, 2000)
            } else {
                navigateToNextScreen()
            }
        }, SPLASH_DELAY_MS)
    }

    private fun playEntryAnimations() {
        val splashImage = findViewById<ImageView>(R.id.splashImage)
        val titleText = findViewById<TextView>(R.id.titleText)
        val descriptionText = findViewById<TextView>(R.id.descriptionText)

        splashImage.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(800)
            .setStartDelay(200)
            .setInterpolator(DecelerateInterpolator())
            .start()

        titleText.animate()
            .alpha(1f).translationY(0f)
            .setDuration(600)
            .setStartDelay(300)
            .setInterpolator(DecelerateInterpolator())
            .start()

        descriptionText.animate()
            .alpha(1f).translationY(0f)
            .setDuration(600)
            .setStartDelay(500)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun navigateToNextScreen() {
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        if (!sharedPref.getBoolean("onboardingComplete", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
            return
        }

        if (!isGoogleSignedIn()) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        val destination = if (hasJoinedRoom()) {
            Intent(this, ChatRoomActivity::class.java)
        } else {
            Intent(this, CreateOrJoinRoom::class.java)
        }
        destination.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(destination)
        finish()
    }

    private fun isGoogleSignedIn(): Boolean {
        return authProvider.isSignedIn(this)
    }

    private fun hasJoinedRoom(): Boolean {
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        if (sharedPref.contains("joinedRoomId")) {
            return true
        }
        if (sharedPref.contains("roomId")) {
            val roomId = sharedPref.getString("roomId", null) ?: return false
            val fbc: ChatConnector by inject()
            fbc.initializeConnection(applicationContext)
            val count = fbc.getMessageCount("roomId/$roomId")
            if (count > 1) {
                sharedPref.edit().putString("joinedRoomId", roomId).apply()
                return true
            }
        }
        return false
    }
}
