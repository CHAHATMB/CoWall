package com.example.cowall.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.cowall.LoginActivity
import com.example.cowall.R
import com.example.cowall.adapters.OnboardingAdapter
import com.example.cowall.adapters.OnboardingPage
import com.example.cowall.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var dots: Array<TextView>

    private val pages = listOf(
        OnboardingPage(
            icon = "\uD83D\uDCF1",
            title = "Welcome to CoWall",
            description = "Share photos with your partner that automatically become their wallpaper"
        ),
        OnboardingPage(
            icon = "\uD83D\uDCF8",
            title = "Capture & Filter",
            description = "Take photos or pick from gallery, then apply beautiful filters before sending"
        ),
        OnboardingPage(
            icon = "\u2728",
            title = "Auto Wallpaper Magic",
            description = "Photos you send automatically set as your partner's lock screen wallpaper"
        ),
        OnboardingPage(
            icon = "\u2764\uFE0F",
            title = "Get Started",
            description = "Create a room and share the code with your partner to connect"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupDots()
        setupButtons()
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = OnboardingAdapter(pages)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                binding.nextButton.text = if (position == pages.size - 1) "Get Started" else "Next"
                binding.skipButton.visibility = if (position == pages.size - 1) View.INVISIBLE else View.VISIBLE
                vibratePageChange()
            }
        })
    }

    @Suppress("DEPRECATION")
    private fun vibratePageChange() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(30)
        }
    }

    private fun setupDots() {
        dots = Array(pages.size) { index ->
            TextView(this).apply {
                text = "\u25CF"
                textSize = 12f
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 12 }
                layoutParams = params
            }
        }
        dots.forEach { binding.dotsLayout.addView(it) }
        updateDots(0)
    }

    private fun updateDots(activePosition: Int) {
        dots.forEachIndexed { index, dot ->
            dot.setTextColor(
                if (index == activePosition) getColor(R.color.accent)
                else Color.parseColor("#616161")
            )
        }
    }

    private fun setupButtons() {
        binding.skipButton.setOnClickListener { completeOnboarding() }
        binding.nextButton.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < pages.size - 1) {
                binding.viewPager.currentItem = current + 1
            } else {
                completeOnboarding()
            }
        }
    }

    private fun completeOnboarding() {
        getSharedPreferences("cowall", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("onboardingComplete", true)
            .apply()

        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }
}
