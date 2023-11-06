package com.example.cowall.repositories

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.cowall.data.ImageFilter
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageColorMatrixFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSepiaToneFilter
import java.io.InputStream

class EditImageRepositoryImpl(private val context: Context) : EditImageRepository {
    override suspend fun prepareImagePreview(imageUri: Uri): Bitmap? {
        getInputStreamFromUri (imageUri)?. let { inputStream ->
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            val width = context.resources.displayMetrics.widthPixels
            val height = ((originalBitmap.height * width) / originalBitmap.width)
            return Bitmap.createScaledBitmap(originalBitmap, width, height, false)
        }?: return null
    }

    override suspend fun getImageFilters(image: Bitmap): List<ImageFilter> {
        val gpuImage = GPUImage(context).apply {
            setImage(image)
        }
        var imageFilters: ArrayList<ImageFilter> = ArrayList()


        //region:: Image Filters

        // Normal
        GPUImageFilter().also{ filter ->
            gpuImage.setFilter(filter)
            imageFilters.add(
                ImageFilter (
                    name = "Normal",
                    filter = filter,
                    filterPreview = gpuImage.bitmapWithFilterApplied
                )
            )
        }

        // Retro
        GPUImageColorMatrixFilter(
            1.0f,
            floatArrayOf(
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0F, 0.2f, 0.0f,
                0.1f, 0.1f, 1.0f, 0.0f,
                1.0f, 0.0f, 0.0f, 1.0f
            )
        ).also{ filter ->
            gpuImage.setFilter(filter)
            imageFilters.add(
                ImageFilter (
                    name = "Retro",
                    filter = filter,
                    filterPreview = gpuImage.bitmapWithFilterApplied
                )
            )
        }

        // Desert
        GPUImageColorMatrixFilter(
            1.0f,
        floatArrayOf(
            0.6f, 0.4f, 0.2f, 0.05f,
            0.0f, 0.8f, 0.3f, 0.05f,
            0.3f, 0.3f, 0.5f, 0.08f,
            0.0f, 0.0f, 0.0f, 1.0f
        )).also { filter ->
            gpuImage.setFilter(filter)
            imageFilters.add(
                ImageFilter(
                    name = "Desert",
                    filter=filter,
                    filterPreview = gpuImage.bitmapWithFilterApplied
                )
            )
        }


        // Limo
        GPUImageColorMatrixFilter(
            1.0f,
            floatArrayOf(
                1.0f, 0.0f, 0.08f, 0.0f,
                0.4f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.1f,
                0.0f, 0.0f, 0.5f, 1.0f
            )).also { filter ->
            gpuImage.setFilter(filter)
            imageFilters.add(
                ImageFilter(
                    name = "Limo",
                    filter=filter,
                    filterPreview = gpuImage.bitmapWithFilterApplied
                )
            )
        }



        // Sepia
        GPUImageSepiaToneFilter().also { filter ->
            gpuImage.setFilter(filter)
            imageFilters.add(
                ImageFilter(
                    name = "Sepia",
                    filter =filter,
                    filterPreview = gpuImage.bitmapWithFilterApplied
                )
            )
        }

        //endregion

        return imageFilters
    }

    private fun getInputStreamFromUri (uri: Uri): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }

    //region:: Edit Image


    //endregion
}