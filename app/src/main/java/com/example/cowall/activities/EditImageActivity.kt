package com.example.cowall.activities

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import com.example.cowall.ChatRoomActivity
import com.example.cowall.adapters.ImageFiltersAdapter
import com.example.cowall.data.ImageFilter
import com.example.cowall.databinding.ActivityEditImageBinding
import com.example.cowall.listeners.ImageFilterListener
import com.example.cowall.utilities.displayToast
import com.example.cowall.utilities.show
import com.example.cowall.viewmodel.EditImageViewModel
import jp.co.cyberagent.android.gpuimage.GPUImage
import org.koin.androidx.viewmodel.ext.android.viewModel

class EditImageActivity : AppCompatActivity(), ImageFilterListener {
    private lateinit var binding: ActivityEditImageBinding
    private val viewModel: EditImageViewModel by viewModel()
    private lateinit var gpuImage: GPUImage

    // Image Bitmaps
    private lateinit var originalBitmap: Bitmap
    private val filteredBitmap = MutableLiveData<Bitmap>()

    companion object {
        val KEY_FILTERED_IMAGE_URI = "filteredImage"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditImageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setListeners()
        setupObservers()
        prepareImagePreview()
    }


    private fun setupObservers() {
        viewModel.imagePreviewUiState.observe(this) {
            val dataState = it ?: return@observe
            binding.previewProgressBar.visibility =
                if (dataState.isLoading) View.VISIBLE else View.GONE
            dataState.bitmap?.let { bitmap ->

                originalBitmap = bitmap
                filteredBitmap.value = bitmap

                with(originalBitmap){
                    gpuImage.setImage(this)
//                    binding.imagePreview.setImageBitmap(bitmap)
                    binding.imagePreview.show()
                    viewModel.loadImeFilters(this)

                }
            } ?: kotlin.run {
                dataState.error?.let { error ->
                    displayToast(error)
                }
            }
        }

        viewModel.imageFiltersUiState.observe(this) {
            val imageFilterDataState = it ?: return@observe
            binding.imageFiltersProgressBar.visibility =
                if (imageFilterDataState.isLoading) View.VISIBLE else View.GONE
            imageFilterDataState.imageFilters?.let { imageFilters ->
                ImageFiltersAdapter(imageFilters, this).also { adapter ->
                    binding.fRecyclerView.adapter = adapter
                }
            } ?: kotlin.run {
                imageFilterDataState.error?.let { error ->
                    displayToast(error)
                }
            }
        }

        filteredBitmap.observe(this){ bitmap ->
            binding.imagePreview.setImageBitmap(bitmap)
        }

        viewModel.saveFilteredImageUiState.observe(  this) {
            Log.d("Walld","value changed!"+it.toString())

            val saveFilteredImageDataState = it ?: return@observe
            if (saveFilteredImageDataState.isLoading) {
//                binding.imageSave.visibility = View. GONE
//                binding.savingProgressBar.visibility = View. VISIBLE
            } else {
//                binding.savingProgressBar.visibility = View. GONE
//                binding.imageSave.visibility = View. VISIBLE
            }
            saveFilteredImageDataState.uri?.let { savedImageUri ->
                displayToast("saving immage and !")
                Log.d("Walld","should start intent")
//                Intent(
//                    applicationContext,
//                    ChatRoomActivity::class.java
//                ).also { filteredImageIntent ->
//                    filteredImageIntent.putExtra(KEY_FILTERED_IMAGE_URI, savedImageUri)
//                    startActivity(filteredImageIntent)
//                }
                Log.d("Walld", "this is saveImage " + savedImageUri.toString())
                Intent().also { filteredImageIntent ->
                    filteredImageIntent.putExtra(KEY_FILTERED_IMAGE_URI, savedImageUri.toString())
                    setResult(RESULT_OK, filteredImageIntent)
                    finish()
                }

            } ?: kotlin.run {
                saveFilteredImageDataState.error?.let{ error->
                    displayToast(error)
                }
            }
        }
    }


    private fun prepareImagePreview(){
        gpuImage = GPUImage(applicationContext)
        intent.getParcelableExtra<Uri>("capturedImage")?.let{
            imageUri -> viewModel.prepareImagePreview(imageUri)
        }
    }

    private fun setListeners(){
        binding.imageBack.setOnClickListener{
            onBackPressed()
        }
        binding.imageSave.setOnClickListener {
            filteredBitmap. value ?. let { bitmap ->
                viewModel.saveFilteredImage(bitmap)
            }
        }

        binding.imagePreview.setOnLongClickListener{
            binding.imagePreview.setImageBitmap(originalBitmap)
            return@setOnLongClickListener false
        }

        binding.imagePreview.setOnClickListener{
            binding.imagePreview.setImageBitmap(filteredBitmap.value)
        }
    }

    override fun onFilterSelected(imageFilter: ImageFilter) {
        with(imageFilter){
            with(gpuImage){
                setFilter(filter)
                filteredBitmap.value = bitmapWithFilterApplied
            }
        }

    }
}