package com.example.cowall

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class EmojiAnimationOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        // Trigger set for the love animation system
        val LOVE_EMOJI_SET = setOf(
            "❤️", "❤", "💕", "💖", "💘", "😘", "😍",
            "🩷", "💗", "💓", "💝", "💞", "🥰"
        )
        // Variants shown as particles (mix of colors/styles)
        private val HEART_VARIANTS = listOf("❤️", "💕", "💖", "💗", "💓", "💝")

        private const val COOLDOWN_MS = 500L
    }

    // Separate flags so love and burst/float animations don't block each other
    private var loveAnimating = false
    private var lastLoveTime = 0L

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?) = false
    override fun onTouchEvent(ev: MotionEvent?) = false

    // ─── Public API ────────────────────────────────────────────────────

    // Non-love single emoji: particles float upward from the message side.
    fun startFloating(emoji: String, isSentByUser: Boolean = false) {
        val anchorX = if (isSentByUser) 0.72f else 0.28f
        repeat(5) { i -> postDelayed({ spawnFloatingParticle(emoji, anchorX) }, i * 130L) }
    }

    // Multiple unique emoji types → confetti burst from screen center.
    fun startBurst(emojis: List<String>) {
        val perEmoji = (18 / emojis.size).coerceAtLeast(3)
        val particles = emojis.flatMap { e -> List(perEmoji) { e } }.shuffled()
        particles.forEachIndexed { i, e -> postDelayed({ spawnBurstParticle(e) }, i * 25L) }
    }

    // Love emoji system — weighted random between 3 behaviour modes.
    fun startLoveAnimation(isSentByUser: Boolean) {
        if (loveAnimating) return
        if (System.currentTimeMillis() - lastLoveTime < COOLDOWN_MS) return
        if (isBatterySaverActive()) return

        loveAnimating = true
        lastLoveTime = System.currentTimeMillis()
        triggerHaptic()

        val countMultiplier = if (isLowRamDevice()) 0.6f else 1.0f

        // 50–120 ms delay between message render and animation start
        postDelayed({
            val r = Random.nextFloat()
            when {
                r < 0.50f -> playFloatingBurst(isSentByUser, countMultiplier)
                r < 0.80f -> playHeartTrail(isSentByUser, countMultiplier)
                else      -> playLoveRain(countMultiplier)
            }
        }, 50L + Random.nextLong(70L))
    }

    // ─── FLOATING_BURST ────────────────────────────────────────────────

    private fun playFloatingBurst(isSentByUser: Boolean, countMult: Float) {
        if (width == 0 || height == 0) { loveAnimating = false; return }
        val anchorX = if (isSentByUser) width * 0.72f else width * 0.28f
        val anchorY = height * 0.68f
        val count = ((6 + Random.nextInt(5)) * countMult).toInt().coerceAtLeast(4)

        repeat(count) { i ->
            postDelayed({ spawnBurstHeart(anchorX, anchorY) }, i * 40L)
        }
        postDelayed({ loveAnimating = false }, 2600L)
    }

    private fun spawnBurstHeart(anchorX: Float, anchorY: Float) {
        if (width == 0 || height == 0) return
        val tv = makeHeartParticle(22f)
        tv.x = anchorX - tv.measuredWidth / 2f
        tv.y = anchorY - tv.measuredHeight / 2f
        tv.scaleX = 0.6f
        tv.scaleY = 0.6f
        addView(tv)

        val offsetX = (Random.nextFloat() - 0.5f) * 80f
        val rise = 180f + Random.nextFloat() * 240f
        val duration = 1200L + Random.nextLong(600L)
        val frequency = 1.5f + Random.nextFloat() * 1.5f
        val phase = Random.nextFloat() * 2f * PI.toFloat()
        val maxRotation = (Random.nextFloat() - 0.5f) * 30f

        // Spring pop: 0.6 → 1.2 → 1.0
        ValueAnimator.ofFloat(0.6f, 1.2f, 1.0f).apply {
            this.duration = 280L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val s = anim.animatedValue as Float
                tv.scaleX = s; tv.scaleY = s
            }
            start()
        }

        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener { anim ->
                val raw = anim.animatedFraction
                val t = easeOutCubic(raw)
                val tDrift = easeInOutSine(raw)
                tv.translationX = offsetX + sin(tDrift * PI.toFloat() * frequency + phase) * 30f
                tv.translationY = -rise * t
                tv.alpha = if (raw < 0.65f) 1f else 1f - (raw - 0.65f) / 0.35f
                if (raw > 0.25f) {
                    val shrink = 1f - (raw - 0.25f) * 0.25f
                    tv.scaleX = shrink
                    tv.scaleY = shrink
                }
                tv.rotation = maxRotation * sin(raw * PI.toFloat())
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { removeView(tv) }
            })
            start()
        }
    }

    // ─── HEART_TRAIL ───────────────────────────────────────────────────

    private fun playHeartTrail(isSentByUser: Boolean, countMult: Float) {
        if (width == 0 || height == 0) { loveAnimating = false; return }
        val startX = if (isSentByUser) width * 0.72f else width * 0.28f
        val endX   = if (isSentByUser) width * 0.28f else width * 0.72f
        val startY = height * 0.65f
        val endY   = height * 0.65f

        val controlX = (startX + endX) / 2f + (Random.nextFloat() - 0.5f) * 60f
        val controlY = startY - (40f + Random.nextFloat() * 80f)

        val count = ((5 + Random.nextInt(3)) * countMult).toInt().coerceAtLeast(3)
        repeat(count) { i ->
            postDelayed({
                spawnTrailHeart(
                    startX, startY, endX, endY,
                    controlX, controlY,
                    isFinal = (i == count - 1)
                )
            }, i * 60L)
        }
        postDelayed({ loveAnimating = false }, 2200L)
    }

    private fun spawnTrailHeart(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        controlX: Float, controlY: Float,
        isFinal: Boolean
    ) {
        val tv = makeHeartParticle(20f)
        tv.x = startX - tv.measuredWidth / 2f
        tv.y = startY - tv.measuredHeight / 2f
        addView(tv)

        val duration = 800L + Random.nextLong(400L)

        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener { anim ->
                val rawT = anim.animatedFraction
                val t = easeInOutSine(rawT)
                val invT = 1f - t
                // Quadratic Bezier: P = (1-t)²·P0 + 2(1-t)t·P1 + t²·P2
                tv.x = (invT * invT * startX + 2f * invT * t * controlX + t * t * endX) - tv.measuredWidth / 2f
                tv.y = (invT * invT * startY + 2f * invT * t * controlY + t * t * endY) - tv.measuredHeight / 2f
                val s = 1f - rawT * 0.3f
                tv.scaleX = s; tv.scaleY = s
                tv.alpha = if (!isFinal && rawT > 0.75f) 1f - (rawT - 0.75f) / 0.25f else 1f
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (isFinal) pulseOnArrival(tv) else removeView(tv)
                }
            })
            start()
        }
    }

    private fun pulseOnArrival(tv: TextView) {
        ValueAnimator.ofFloat(0.8f, 1.2f, 1.0f).apply {
            duration = 350L
            interpolator = OvershootInterpolator(2.5f)
            addUpdateListener { anim ->
                val s = anim.animatedValue as Float
                tv.scaleX = s; tv.scaleY = s
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    tv.animate().alpha(0f).setDuration(300L)
                        .withEndAction { removeView(tv) }.start()
                }
            })
            start()
        }
    }

    // ─── LOVE_RAIN ─────────────────────────────────────────────────────

    private fun playLoveRain(countMult: Float) {
        if (width == 0 || height == 0) { loveAnimating = false; return }
        val count = ((12 + Random.nextInt(9)) * countMult).toInt().coerceAtLeast(8)
        val spawnWindowMs = 800L

        repeat(count) {
            postDelayed({ spawnRainingHeart() }, (Random.nextFloat() * spawnWindowMs).toLong())
        }
        postDelayed({ loveAnimating = false }, 4500L)
    }

    private fun spawnRainingHeart() {
        if (width == 0 || height == 0) return
        val isForeground = Random.nextFloat() < 0.3f
        val tv = makeHeartParticle(if (isForeground) 22f else 14f)
        val baseAlpha = if (isForeground) 1f else 0.65f

        tv.x = Random.nextFloat() * width - tv.measuredWidth / 2f
        tv.y = -tv.measuredHeight.toFloat()
        tv.alpha = baseAlpha
        addView(tv)

        val duration = 2500L + Random.nextLong(1000L)
        val driftAmplitude = (Random.nextFloat() - 0.5f) * 40f
        val driftFreq = 0.7f + Random.nextFloat() * 0.4f
        val totalFall = height.toFloat() + tv.measuredHeight * 2f

        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener { anim ->
                val raw = anim.animatedFraction
                val t = easeInOutSine(raw)
                tv.translationY = totalFall * t
                tv.translationX = driftAmplitude * sin(raw * PI.toFloat() * driftFreq * 2f)
                tv.alpha = if (raw < 0.78f) baseAlpha else baseAlpha * (1f - (raw - 0.78f) / 0.22f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { removeView(tv) }
            })
            start()
        }
    }

    // ─── Non-love floating / burst particles (unchanged behaviour) ─────

    private fun spawnFloatingParticle(emoji: String, anchorXFraction: Float) {
        if (width == 0 || height == 0) return
        val tv = makeParticle(emoji, 26f)
        tv.x = width * anchorXFraction + (Random.nextFloat() - 0.5f) * 60f - tv.measuredWidth / 2f
        tv.y = height * 0.72f - tv.measuredHeight / 2f
        addView(tv)

        val driftX = (Random.nextFloat() - 0.5f) * 90f
        val rise = height * 0.45f + Random.nextFloat() * height * 0.1f
        val duration = 1600L + Random.nextLong(400L)

        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                tv.translationX = driftX * sin(t * PI.toFloat() * 2.5f)
                tv.translationY = -rise * t
                tv.alpha = if (t < 0.7f) 1f else 1f - (t - 0.7f) / 0.3f
                val s = 1f + 0.25f * sin(t * PI.toFloat())
                tv.scaleX = s; tv.scaleY = s
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { removeView(tv) }
            })
            start()
        }
    }

    private fun spawnBurstParticle(emoji: String) {
        if (width == 0 || height == 0) return
        val tv = makeParticle(emoji, 20f)
        tv.x = width / 2f - tv.measuredWidth / 2f
        tv.y = height / 2f - tv.measuredHeight / 2f
        addView(tv)

        val angle = Random.nextFloat() * 2f * PI.toFloat()
        val speed = 350f + Random.nextFloat() * 500f
        val velX = cos(angle) * speed
        val velY = sin(angle) * speed
        val duration = 1200L + Random.nextLong(400L)
        val durationSec = duration.toFloat() / 1000f

        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                val s = t * durationSec
                tv.translationX = velX * s
                tv.translationY = velY * s + 0.5f * 300f * s * s
                tv.alpha = if (t < 0.6f) 1f else 1f - (t - 0.6f) / 0.4f
                val sc = (1f - t * 0.4f).coerceAtLeast(0.2f)
                tv.scaleX = sc; tv.scaleY = sc
                tv.rotation = t * 360f * if (velX > 0) 1f else -1f
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { removeView(tv) }
            })
            start()
        }
    }

    // ─── Factories ─────────────────────────────────────────────────────

    private fun makeHeartParticle(sizeSp: Float = 20f) =
        makeParticle(HEART_VARIANTS.random(), sizeSp)

    private fun makeParticle(emoji: String, sizeSp: Float) = TextView(context).apply {
        text = emoji
        textSize = sizeSp
        gravity = Gravity.CENTER
        measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
    }

    // ─── Easing ────────────────────────────────────────────────────────

    private fun easeOutCubic(t: Float) = 1f - (1f - t).pow(3f)

    // Smooth sine-based ease: 0 → 0, 0.5 → 0.5, 1 → 1
    private fun easeInOutSine(t: Float) = (1f - cos(PI.toFloat() * t)) / 2f

    // ─── Device / system helpers ───────────────────────────────────────

    private fun isLowRamDevice() =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice

    private fun isBatterySaverActive() =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode

    @Suppress("DEPRECATION")
    private fun triggerHaptic() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                vibrator.vibrate(25)
            }
        } catch (_: Exception) {
            // Haptic is non-critical; ignore silently
        }
    }
}
