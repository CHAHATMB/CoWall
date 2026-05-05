package com.example.cowall.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.example.cowall.DrawingCanvasView
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

    // Filter / intensity / tint state — composited into filteredBitmap
    private var filterIntensity: Float = 1.0f
    private var tintColor: Int = Color.TRANSPARENT
    private var tintAlpha: Float = 0.4f

    // Text overlay state — shown via draggable textOverlayPreview, baked on save
    private var overlayText: String = ""
    private var overlayTextColor: Int = Color.WHITE
    private var textHasBeenPositioned = false
    private var textDragStartX = 0f
    private var textDragStartY = 0f
    private var textDragStartViewX = 0f
    private var textDragStartViewY = 0f

    private var recomputeJob: Job? = null

    private enum class EditTool { FILTERS, INTENSITY, TINT, TEXT, DRAW }
    private var activeTool = EditTool.FILTERS

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

        private val BRUSH_COLORS = listOf(
            Color.WHITE,
            Color.RED,
            Color.parseColor("#FF6B00"),  // Orange
            Color.YELLOW,
            Color.parseColor("#4CAF50"),  // Green
            Color.CYAN,
            Color.parseColor("#2196F3"),  // Blue
            Color.parseColor("#9C27B0"),  // Purple
            Color.BLACK
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
        setupDrawPanel()
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
            binding.tabText to EditTool.TEXT,
            binding.tabDraw to EditTool.DRAW
        ).forEach { (tab, tool) ->
            tab.setOnClickListener { switchToTool(tool) }
        }
        switchToTool(EditTool.FILTERS)
    }

    private fun switchToTool(tool: EditTool) {
        // Dismiss keyboard when leaving text tool
        if (activeTool == EditTool.TEXT && tool != EditTool.TEXT) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.editOverlayText.windowToken, 0)
        }
        activeTool = tool
        binding.panelFilters.visibility = View.GONE
        binding.panelIntensity.visibility = View.GONE
        binding.panelTint.visibility = View.GONE
        binding.panelText.visibility = View.GONE
        binding.panelDraw.visibility = View.GONE
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
            EditTool.DRAW -> binding.panelDraw.visibility = View.VISIBLE
        }
        binding.drawingCanvas.isDrawingEnabled = (tool == EditTool.DRAW)
        updateTabHighlights(tool)
    }

    private fun updateTabHighlights(activeTool: EditTool) {
        val activeColor = getColor(R.color.accent)
        val inactiveColor = getColor(R.color.text_secondary)
        mapOf(
            binding.textTabFilters to EditTool.FILTERS,
            binding.textTabIntensity to EditTool.INTENSITY,
            binding.textTabTint to EditTool.TINT,
            binding.textTabText to EditTool.TEXT,
            binding.textTabDraw to EditTool.DRAW
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
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dpToPx(8) }
                background = makeSwatchBackground(color, color == overlayTextColor)
                setOnClickListener {
                    overlayTextColor = color
                    binding.textOverlayPreview.setTextColor(color)
                    refreshTextColorSwatches(color)
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
                if (overlayText.isNotBlank()) {
                    binding.textOverlayPreview.text = overlayText
                    if (!textHasBeenPositioned) positionTextOverlayAtCenter()
                } else {
                    binding.textOverlayPreview.visibility = View.GONE
                    textHasBeenPositioned = false
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Drag handler for the text overlay — active only in TEXT mode
        binding.textOverlayPreview.setOnTouchListener { view, event ->
            if (activeTool != EditTool.TEXT) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    textDragStartX = event.rawX
                    textDragStartY = event.rawY
                    textDragStartViewX = view.x
                    textDragStartViewY = view.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - textDragStartX
                    val dy = event.rawY - textDragStartY
                    val maxX = (binding.imageContainer.width - view.width).toFloat()
                    val maxY = (binding.imageContainer.height - view.height).toFloat()
                    view.x = (textDragStartViewX + dx).coerceIn(0f, maxX.coerceAtLeast(0f))
                    view.y = (textDragStartViewY + dy).coerceIn(0f, maxY.coerceAtLeast(0f))
                    true
                }
                MotionEvent.ACTION_UP -> true
                else -> false
            }
        }

        refreshTextColorSwatches(overlayTextColor)
        binding.textOverlayPreview.setTextColor(overlayTextColor)
    }

    private fun positionTextOverlayAtCenter() {
        binding.textOverlayPreview.visibility = View.INVISIBLE
        binding.imageContainer.post {
            val cx = (binding.imageContainer.width - binding.textOverlayPreview.width) / 2f
            val cy = (binding.imageContainer.height - binding.textOverlayPreview.height) / 2f
            binding.textOverlayPreview.x = cx.coerceAtLeast(0f)
            binding.textOverlayPreview.y = cy.coerceAtLeast(0f)
            binding.textOverlayPreview.visibility = View.VISIBLE
            textHasBeenPositioned = true
        }
    }

    private fun refreshTextColorSwatches(selectedColor: Int) {
        for (i in 0 until binding.textColorRow.childCount) {
            binding.textColorRow.getChildAt(i)?.background =
                makeSwatchBackground(TEXT_COLORS[i], TEXT_COLORS[i] == selectedColor)
        }
    }

    private fun setupDrawPanel() {
        BRUSH_COLORS.forEach { color ->
            val circle = View(this).apply {
                val size = dpToPx(32)
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dpToPx(8) }
                background = makeSwatchBackground(color, color == Color.WHITE)
                setOnClickListener {
                    binding.drawingCanvas.setStrokeColor(color)
                    refreshBrushColorSwatches(color)
                }
            }
            binding.brushColorRow.addView(circle)
        }

        val density = resources.displayMetrics.density
        binding.seekBarBrushSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                binding.drawingCanvas.setStrokeWidthPx((progress + 4) * density)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        // Sync initial width with seekbar's starting progress
        binding.drawingCanvas.setStrokeWidthPx((binding.seekBarBrushSize.progress + 4) * density)

        binding.btnUndo.setOnClickListener { binding.drawingCanvas.undo() }
        binding.btnClearDoodle.setOnClickListener { binding.drawingCanvas.clearAll() }
    }

    private fun refreshBrushColorSwatches(selectedColor: Int) {
        for (i in 0 until binding.brushColorRow.childCount) {
            binding.brushColorRow.getChildAt(i)?.background =
                makeSwatchBackground(BRUSH_COLORS[i], BRUSH_COLORS[i] == selectedColor)
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

    // Composites filter → intensity → tint. Text and doodle are separate overlays.
    private fun recomputeDisplayBitmap() {
        if (!::originalBitmap.isInitialized) return
        recomputeJob?.cancel()
        recomputeJob = lifecycleScope.launch(Dispatchers.Default) {
            val gpuFiltered = gpuFilteredBitmap ?: originalBitmap
            val blended = blendBitmaps(originalBitmap, gpuFiltered, filterIntensity)
            val result = if (tintColor != Color.TRANSPARENT) applyColorTint(blended, tintColor, tintAlpha) else blended
            withContext(Dispatchers.Main) { filteredBitmap.value = result }
        }
    }

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

    private fun drawTextOnBitmap(
        source: Bitmap, text: String, textColor: Int,
        bitmapX: Float, bitmapY: Float, textSizePx: Float
    ): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = textColor
            this.textSize = textSizePx
            this.textAlign = Paint.Align.CENTER
            this.isFakeBoldText = true
            setShadowLayer(6f, 2f, 2f, Color.BLACK)
        }
        // bitmapY is the center of the text overlay view; adjust to baseline
        val fontMetrics = paint.fontMetrics
        val adjustedY = bitmapY - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(text, bitmapX, adjustedY, paint)
        return result
    }

    private fun compositeStrokes(
        bitmap: Bitmap,
        strokes: List<DrawingCanvasView.StrokePath>,
        imageViewMatrix: Matrix
    ): Bitmap {
        if (strokes.isEmpty()) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val inverseMatrix = Matrix()
        imageViewMatrix.invert(inverseMatrix)

        // Scale factor for stroke width: view → bitmap space
        val matrixValues = FloatArray(9)
        inverseMatrix.getValues(matrixValues)
        val scale = kotlin.math.abs(matrixValues[Matrix.MSCALE_X])

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (stroke in strokes) {
            paint.color = stroke.color
            paint.strokeWidth = stroke.widthPx * scale
            val bitmapPath = Path()
            stroke.path.transform(inverseMatrix, bitmapPath)
            canvas.drawPath(bitmapPath, paint)
        }
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
            val currentBitmap = filteredBitmap.value ?: return@setOnClickListener

            // Capture all view-dependent state on main thread before background dispatch
            val imageMatrix = binding.imagePreview.imageMatrix
            val inverseMatrix = Matrix()
            val matrixValid = imageMatrix.invert(inverseMatrix)

            val hasText = overlayText.isNotBlank() && binding.textOverlayPreview.visibility != View.GONE
            val textBitmapX: Float
            val textBitmapY: Float
            val textSizePx: Float
            if (hasText && matrixValid) {
                val cx = binding.textOverlayPreview.x + binding.textOverlayPreview.width / 2f
                val cy = binding.textOverlayPreview.y + binding.textOverlayPreview.height / 2f
                val pts = floatArrayOf(cx, cy)
                inverseMatrix.mapPoints(pts)
                textBitmapX = pts[0]
                textBitmapY = pts[1]
                textSizePx = binding.textOverlayPreview.textSize
            } else {
                textBitmapX = 0f; textBitmapY = 0f; textSizePx = 0f
            }

            val strokes = binding.drawingCanvas.getStrokesSnapshot()

            lifecycleScope.launch(Dispatchers.Default) {
                var result = currentBitmap
                if (hasText && textSizePx > 0f) {
                    result = drawTextOnBitmap(result, overlayText, overlayTextColor, textBitmapX, textBitmapY, textSizePx)
                }
                if (strokes.isNotEmpty() && matrixValid) {
                    result = compositeStrokes(result, strokes, imageMatrix)
                }
                withContext(Dispatchers.Main) {
                    viewModel.saveFilteredImage(result)
                }
            }
        }

        binding.imagePreview.setOnLongClickListener {
            if (::originalBitmap.isInitialized) binding.imagePreview.setImageBitmap(originalBitmap)
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
