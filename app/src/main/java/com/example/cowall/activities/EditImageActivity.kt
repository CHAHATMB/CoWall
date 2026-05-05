package com.example.cowall.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.example.cowall.R
import com.example.cowall.adapters.ImageFiltersAdapter
import com.example.cowall.data.ImageFilter
import com.example.cowall.databinding.ActivityEditImageBinding
import com.example.cowall.listeners.ImageFilterListener
import com.example.cowall.utilities.displayToast
import com.example.cowall.utilities.show
import com.example.cowall.viewmodel.EditImageViewModel
import jp.co.cyberagent.android.gpuimage.GPUImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel

class EditImageActivity : AppCompatActivity(), ImageFilterListener {

    private lateinit var binding: ActivityEditImageBinding
    private val viewModel: EditImageViewModel by viewModel()
    private lateinit var gpuImage: GPUImage

    private lateinit var originalBitmap: Bitmap
    private var gpuFilteredBitmap: Bitmap? = null
    private val filteredBitmap = MutableLiveData<Bitmap>()

    private var filterIntensity: Float = 1.0f
    private var tintColor: Int = Color.TRANSPARENT
    private var tintAlpha: Float = 0.4f
    private var overlayText: String = ""
    private var overlayTextColor: Int = Color.WHITE

    private var recomputeJob: Job? = null

    private enum class EditTool { FILTERS, INTENSITY, TINT, TEXT }

    companion object {
        const val KEY_FILTERED_IMAGE_URI = "filteredImage"

        private val TINT_COLORS = listOf(
            Color.TRANSPARENT,
            Color.parseColor("#FFD700"),  // Joy
            Color.parseColor("#4A90E2"),  // Calm
            Color.parseColor("#E94560"),  // Love
            Color.parseColor("#4CAF50"),  // Nature
            Color.parseColor("#9C27B0"),  // Mystery
            Color.parseColor("#1A237E")   // Night
        )
        private val TINT_LABELS = listOf("None", "Joy", "Calm", "Love", "Nature", "Mystery", "Night")

        private val TEXT_COLORS = listOf(
            Color.WHITE,
            Color.BLACK,
            Color.YELLOW,
            Color.parseColor("#FF6B6B"),
            Color.parseColor("#74B9FF"),
            Color.parseColor("#A29BFE")
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditImageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolTabs()
        setupIntensityPanel()
        setupTintPanel()
        setupTextPanel()
        setupObservers()
        prepareImagePreview()
        setListeners()
    }

    private fun setupObservers() {
        viewModel.imagePreviewUiState.observe(this) {
            val dataState = it ?: return@observe
            binding.previewProgressBar.visibility = if (dataState.isLoading) View.VISIBLE else View.GONE
            dataState.bitmap?.let { bitmap ->
                originalBitmap = bitmap
                gpuFilteredBitmap = bitmap
                gpuImage.setImage(bitmap)
                binding.imagePreview.show()
                viewModel.loadImeFilters(bitmap)
                filteredBitmap.value = bitmap
            } ?: dataState.error?.let { error -> displayToast(error) }
        }

        viewModel.imageFiltersUiState.observe(this) {
            val state = it ?: return@observe
            binding.imageFiltersProgressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            state.imageFilters?.let { filters ->
                binding.fRecyclerView.adapter = ImageFiltersAdapter(filters, this)
            } ?: state.error?.let { error -> displayToast(error) }
        }

        filteredBitmap.observe(this) { bitmap ->
            binding.imagePreview.setImageBitmap(bitmap)
        }

        viewModel.saveFilteredImageUiState.observe(this) {
            val state = it ?: return@observe
            state.uri?.let { uri ->
                Intent().also { resultIntent ->
                    resultIntent.putExtra(KEY_FILTERED_IMAGE_URI, uri.toString())
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            } ?: state.error?.let { error -> displayToast(error) }
        }
    }

    private fun setupToolTabs() {
        listOf(
            binding.tabFilters to EditTool.FILTERS,
            binding.tabIntensity to EditTool.INTENSITY,
            binding.tabTint to EditTool.TINT,
            binding.tabText to EditTool.TEXT
        ).forEach { (tab, tool) ->
            tab.setOnClickListener { switchToTool(tool) }
        }
        switchToTool(EditTool.FILTERS)
    }

    private fun switchToTool(tool: EditTool) {
        binding.panelFilters.visibility = View.GONE
        binding.panelIntensity.visibility = View.GONE
        binding.panelTint.visibility = View.GONE
        binding.panelText.visibility = View.GONE
        when (tool) {
            EditTool.FILTERS -> binding.panelFilters.visibility = View.VISIBLE
            EditTool.INTENSITY -> binding.panelIntensity.visibility = View.VISIBLE
            EditTool.TINT -> binding.panelTint.visibility = View.VISIBLE
            EditTool.TEXT -> {
                binding.panelText.visibility = View.VISIBLE
                binding.editOverlayText.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.editOverlayText, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        updateTabHighlights(tool)
    }

    private fun updateTabHighlights(activeTool: EditTool) {
        val activeColor = getColor(R.color.accent)
        val inactiveColor = getColor(R.color.text_secondary)
        mapOf(
            binding.textTabFilters to EditTool.FILTERS,
            binding.textTabIntensity to EditTool.INTENSITY,
            binding.textTabTint to EditTool.TINT,
            binding.textTabText to EditTool.TEXT
        ).forEach { (label, tool) ->
            label.setTextColor(if (tool == activeTool) activeColor else inactiveColor)
        }
    }

    private fun setupIntensityPanel() {
        binding.seekBarIntensity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                filterIntensity = progress / 100f
                binding.textIntensityValue.text = "Filter Strength: $progress%"
                recomputeDisplayBitmap()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setupTintPanel() {
        TINT_COLORS.forEachIndexed { index, color ->
            val swatch = makeTintSwatch(color, TINT_LABELS[index]) { selectedColor ->
                tintColor = selectedColor
                refreshTintSwatches(selectedColor)
                recomputeDisplayBitmap()
            }
            binding.tintColorRow.addView(swatch)
        }
        binding.seekBarTintOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                tintAlpha = progress / 100f
                recomputeDisplayBitmap()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        refreshTintSwatches(Color.TRANSPARENT)
    }

    private fun refreshTintSwatches(selectedColor: Int) {
        for (i in 0 until binding.tintColorRow.childCount) {
            val container = binding.tintColorRow.getChildAt(i) as? LinearLayout ?: continue
            val circle = container.getChildAt(0) ?: continue
            circle.background = makeSwatchBackground(TINT_COLORS[i], TINT_COLORS[i] == selectedColor)
        }
    }

    private fun setupTextPanel() {
        TEXT_COLORS.forEach { color ->
            val circle = View(this).apply {
                val size = dpToPx(36)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dpToPx(8)
                }
                background = makeSwatchBackground(color, color == overlayTextColor)
                setOnClickListener {
                    overlayTextColor = color
                    refreshTextColorSwatches(color)
                    recomputeDisplayBitmap()
                }
            }
            binding.textColorRow.addView(circle)
        }

        binding.editOverlayText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.editOverlayText.windowToken, 0)
                true
            } else false
        }

        binding.editOverlayText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                overlayText = s?.toString() ?: ""
                recomputeDisplayBitmap()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        refreshTextColorSwatches(overlayTextColor)
    }

    private fun refreshTextColorSwatches(selectedColor: Int) {
        for (i in 0 until binding.textColorRow.childCount) {
            binding.textColorRow.getChildAt(i)?.background =
                makeSwatchBackground(TEXT_COLORS[i], TEXT_COLORS[i] == selectedColor)
        }
    }

    private fun makeTintSwatch(color: Int, label: String, onClick: (Int) -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            val margin = dpToPx(6)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { setMargins(margin, 0, margin, 0) }

            val circle = View(context).apply {
                val size = dpToPx(36)
                layoutParams = LinearLayout.LayoutParams(size, size)
                background = makeSwatchBackground(color, false)
            }

            val labelView = TextView(context).apply {
                text = label
                textSize = 9f
                setTextColor(getColor(R.color.text_secondary))
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dpToPx(2) }
            }

            addView(circle)
            addView(labelView)
            setOnClickListener { onClick(color) }
        }
    }

    private fun makeSwatchBackground(color: Int, isSelected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (color == Color.TRANSPARENT) Color.parseColor("#333333") else color)
            setStroke(
                if (isSelected) dpToPx(3) else dpToPx(1),
                if (isSelected) Color.WHITE else Color.parseColor("#555555")
            )
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun recomputeDisplayBitmap() {
        if (!::originalBitmap.isInitialized) return
        recomputeJob?.cancel()
        recomputeJob = lifecycleScope.launch(Dispatchers.Default) {
            val gpuFiltered = gpuFilteredBitmap ?: originalBitmap
            val blended = blendBitmaps(originalBitmap, gpuFiltered, filterIntensity)
            val tinted = if (tintColor != Color.TRANSPARENT) applyColorTint(blended, tintColor, tintAlpha) else blended
            val result = if (overlayText.isNotBlank()) drawTextOnBitmap(tinted, overlayText, overlayTextColor) else tinted
            withContext(Dispatchers.Main) {
                filteredBitmap.value = result
            }
        }
    }

    // Blends base (always originalBitmap) with overlay at the given alpha.
    // overlay alpha=1.0 → fully filtered; alpha=0.0 → original unchanged.
    private fun blendBitmaps(base: Bitmap, overlay: Bitmap, overlayAlpha: Float): Bitmap {
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply { alpha = (overlayAlpha * 255).toInt() }
        canvas.drawBitmap(overlay, 0f, 0f, paint)
        return result
    }

    private fun applyColorTint(source: Bitmap, color: Int, alpha: Float): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            this.color = color
            this.alpha = (alpha * 255).toInt()
        }
        canvas.drawRect(0f, 0f, result.width.toFloat(), result.height.toFloat(), paint)
        return result
    }

    private fun drawTextOnBitmap(source: Bitmap, text: String, textColor: Int): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = textColor
            this.textSize = result.height * 0.055f
            this.textAlign = Paint.Align.CENTER
            this.isFakeBoldText = true
            setShadowLayer(6f, 2f, 2f, Color.BLACK)
        }
        canvas.drawText(text, result.width / 2f, result.height * 0.87f, paint)
        return result
    }

    private fun prepareImagePreview() {
        gpuImage = GPUImage(applicationContext)
        intent.getParcelableExtra<Uri>("capturedImage")?.let { imageUri ->
            viewModel.prepareImagePreview(imageUri)
        }
    }

    private fun setListeners() {
        binding.imageBack.setOnClickListener { onBackPressed() }
        binding.imageSave.setOnClickListener {
            filteredBitmap.value?.let { bitmap -> viewModel.saveFilteredImage(bitmap) }
        }
        binding.imagePreview.setOnLongClickListener {
            binding.imagePreview.setImageBitmap(originalBitmap)
            false
        }
        binding.imagePreview.setOnClickListener {
            binding.imagePreview.setImageBitmap(filteredBitmap.value)
        }
    }

    override fun onFilterSelected(imageFilter: ImageFilter) {
        gpuImage.setFilter(imageFilter.filter)
        gpuFilteredBitmap = gpuImage.bitmapWithFilterApplied
        recomputeDisplayBitmap()
    }
}
