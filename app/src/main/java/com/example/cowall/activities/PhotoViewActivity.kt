package com.example.cowall.activities

import android.animation.ValueAnimator
import android.net.Uri
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.ChangeImageTransform
import android.transition.TransitionSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.cowall.databinding.ActivityPhotoViewBinding
import kotlin.math.abs

class PhotoViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_URI = "imageUri"
        const val EXTRA_TRANSITION_NAME = "transitionName"
        private const val SWIPE_DISMISS_THRESHOLD = 200f
        private const val SWIPE_VELOCITY_THRESHOLD = 500f
    }

    private lateinit var binding: ActivityPhotoViewBinding
    private var scaleFactor = 1.0f
    private var translationY = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.5f, 5.0f)
            binding.fullscreenImage.scaleX = scaleFactor
            binding.fullscreenImage.scaleY = scaleFactor
            return true
        }
    }

    private val doubleTapListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val targetScale = if (scaleFactor > 1.5f) 1.0f else 3.0f
            animateScale(scaleFactor, targetScale)
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (scaleFactor < 1.1f && abs(translationY) < 10f) {
                finishAfterTransition()
            }
            return true
        }
    }

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedTransition = TransitionSet().apply {
            addTransition(ChangeBounds())
            addTransition(ChangeImageTransform())
            duration = 300
        }
        window.sharedElementEnterTransition = sharedTransition
        window.sharedElementReturnTransition = sharedTransition

        @Suppress("DEPRECATION")
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        binding = ActivityPhotoViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val transitionName = intent.getStringExtra(EXTRA_TRANSITION_NAME)
        if (transitionName != null) {
            binding.fullscreenImage.transitionName = transitionName
        }

        scaleGestureDetector = ScaleGestureDetector(this, scaleListener)
        gestureDetector = GestureDetector(this, doubleTapListener)

        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<Uri>(EXTRA_IMAGE_URI)
        if (uri != null) {
            Glide.with(this).load(uri).into(binding.fullscreenImage)
        }

        binding.fullscreenImage.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            // Swipe-down to dismiss (only when not zoomed)
            if (scaleFactor < 1.2f && !scaleGestureDetector.isInProgress) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchY = event.rawY
                        isDragging = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = event.rawY - lastTouchY
                        if (abs(deltaY) > 20f) {
                            isDragging = true
                            translationY = deltaY
                            view.translationY = translationY
                            val alpha = 1f - (abs(translationY) / 800f).coerceIn(0f, 0.6f)
                            binding.root.alpha = alpha
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            if (abs(translationY) > SWIPE_DISMISS_THRESHOLD) {
                                finishAfterTransition()
                            } else {
                                view.animate().translationY(0f).setDuration(200).start()
                                binding.root.animate().alpha(1f).setDuration(200).start()
                                translationY = 0f
                            }
                            isDragging = false
                        }
                    }
                }
            }
            true
        }

        binding.closeButton.setOnClickListener { finishAfterTransition() }
    }

    private fun animateScale(from: Float, to: Float) {
        ValueAnimator.ofFloat(from, to).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                scaleFactor = animator.animatedValue as Float
                binding.fullscreenImage.scaleX = scaleFactor
                binding.fullscreenImage.scaleY = scaleFactor
            }
            start()
        }
    }
}
