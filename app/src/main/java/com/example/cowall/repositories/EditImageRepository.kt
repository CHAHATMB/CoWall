package com.example.cowall.repositories

import android.graphics.Bitmap
import android.net.Uri
import com.example.cowall.data.ImageFilter

interface EditImageRepository {
    suspend fun prepareImagePreview(imageUri: Uri): Bitmap?
    suspend fun getImageFilters(image: Bitmap): List<ImageFilter>
}