package com.example.cowall.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cowall.data.ImageFilter
import com.example.cowall.repositories.EditImageRepository
import com.example.cowall.utilities.Coroutines

class EditImageViewModel(private val editImageRepository: EditImageRepository) : ViewModel() {


    //region :: prepare Image
    data class ImagePreviewDataState(
        val isLoading: Boolean,
        val bitmap: Bitmap?,
        val error: String?
    )

    private val imagePreviewDataState = MutableLiveData <ImagePreviewDataState>()
    val imagePreviewUiState: LiveData<ImagePreviewDataState> get() = imagePreviewDataState
    fun prepareImagePreview(imageUri: Uri) {
        Coroutines.io {
            runCatching {
                emitImagePreviewUiState(isLoading = true)
                editImageRepository.prepareImagePreview(imageUri)
            }.onSuccess { bitmap ->
                if (bitmap != null) {
                    emitImagePreviewUiState(isLoading = false, bitmap = bitmap)
                } else {
                    emitUiState(error = "Unable to prepare image preview")
                }
            }.onFailure {
                emitUiState(error = it.message.toString())
            }
        }
    }

    private fun emitImagePreviewUiState( isLoading: Boolean, bitmap: Bitmap? = null, error: String? = null){
        val dataState = ImagePreviewDataState(isLoading, bitmap, error)
        imagePreviewDataState.postValue(dataState)

    }

    private fun emitUiState(
        isLoading: Boolean = false,
        bitmap: Bitmap? = null,
        error: String? = null
    ) {
        val dataState = ImagePreviewDataState(isLoading, bitmap, error)
        imagePreviewDataState.value = dataState
        data class ImagePreviewDataState(
            val isLoading: Boolean,
            val bitmap: Bitmap?,
            val error: String?
        )
    }

    //endregion

    //region :: Load Image Filter


    data class ImageFiltersDataState(
        val isLoading: Boolean,
        val imageFilters: List<ImageFilter>?,
        val error: String?
    )

    private val imageFiltersDataState = MutableLiveData<ImageFiltersDataState>()
    val imageFiltersUiState: LiveData<ImageFiltersDataState> get() = imageFiltersDataState

    private fun emitImageFiltersUiState(
        isLoading: Boolean = false,
        imageFilters: List<ImageFilter>? = null,
        error: String? = null
    ) {
        val dataState = ImageFiltersDataState(isLoading, imageFilters, error)
        imageFiltersDataState.postValue(dataState)
    }


    private fun getPreviewImage (originalImage: Bitmap): Bitmap {
        return runCatching {
            val previewWidth = 150
            val previewHeight = originalImage.height * previewWidth / originalImage.width
            Bitmap.createScaledBitmap(originalImage, previewWidth, previewHeight, false)
        }.getOrDefault (originalImage)
    }

    fun loadImeFilters(originalImage: Bitmap) {
        Coroutines.io {
            runCatching {
                emitImageFiltersUiState(isLoading = true)
                editImageRepository.getImageFilters(getPreviewImage(originalImage))
            }.onSuccess { imageFilters ->
                emitImageFiltersUiState(imageFilters = imageFilters)
            }.onFailure {
                emitImageFiltersUiState(error = it.message.toString())
            }
        }
    }
    //endregion
}