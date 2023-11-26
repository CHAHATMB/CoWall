package com.example.cowall

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.SurfaceView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.cowall.activities.EditImageActivity
import com.example.cowall.databinding.ActivityCameraBinding
import com.google.common.util.concurrent.ListenableFuture
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor


class CameraActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: Executor
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    val CAMERA_PERMISSION_REQUEST_CODE = 98
    private val surfaceView: SurfaceView? = null
    lateinit var binding : ActivityCameraBinding
    private lateinit var imageCapture: ImageCapture
    private lateinit var imageAnalyzer: ImageAnalysis
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var selectImage: Bitmap
    private lateinit var outputPath: File


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_camera)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        val view = binding.root
//        setContentView(R.layout.activity_main)
        setContentView(view)

        binding.captureButton.setOnClickListener {
            takePhoto()
        }
        binding.sendImageButton.setOnClickListener{sendImage()}
        // Request camera permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }

        cameraExecutor = ContextCompat.getMainExecutor(this)
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()

                // Initialize camera preview
                bindCameraPreview(cameraProvider)
            },
            cameraExecutor
        )
    }



    private fun bindCameraPreview(cameraProvider: ProcessCameraProvider) {
        val preview: Preview = Preview.Builder().build()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

//        imageAnalyzer = ImageAnalysis.Builder()
//            .build()
//            .also {
//                it.setAnalyzer(cameraExecutor, { imageProxy ->
//                    processImage(imageProxy)
//                })
//            }
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(480, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
        preview.setSurfaceProvider(binding.surfaceView.surfaceProvider)
//        cameraProvider.unbindAll()
        val camera = cameraProvider.bindToLifecycle(
            this,
            cameraSelector,
            imageCapture,
            preview,
            imageAnalyzer
        )
    }
    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
//    fun ImageProxy.toBitmap(): Bitmap? {
//        return this.image?.let { image ->
//            val buffer = image.planes[0].buffer
//            val bytes = ByteArray(buffer.remaining())
//            buffer.get(bytes)
//            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
//        }
//    }

//    private fun processImage(imageProxy: ImageProxy) {
//        val bitmap = imageProxy.toBitmap()
//
//        // Apply filter to the bitmap
//        val filteredBitmap = applyFilter(bitmap)
//
//        // Display the filtered bitmap in the ImageView
//        runOnUiThread {
//            binding.liveFilterImageView.setImageBitmap(bitmap)
//        }
//
//        imageProxy.close()
//    }


    private fun getOutputFile(): File {
        val storageDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "image_${System.currentTimeMillis()}",  // Prefix for the file name
            ".jpg",                                   // Suffix for the file name
            storageDirectory                           // Directory where the file will be saved
        )
    }

    private fun takePhoto() {
        outputPath = getOutputFile()
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(outputPath).build()
        Log.d("Walld","Capturing photo jgh")

        imageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // Get the captured image as a bitmap
                    val bitmap = image.toBitmap()



                    // Compress the bitmap
                    val compressedBitmap = compressBitmap(bitmap!!, 10) // Adjust quality level as needed
                    Log.d("Walld","Compressing the image on go")
                    val compressedImageFile = getCompressedImageFile()
                    compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 10, FileOutputStream(compressedImageFile))
                    outputPath = compressedImageFile

                    // Get the orientation of the captured image
                    val ei = ExifInterface(outputPath.absolutePath)
                    val orientation = ei.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_UNDEFINED
                    )
                    // Rotate the bitmap if necessary
                    val rotatedBitmap = rotateImage(bitmap, 90f)
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 10, FileOutputStream(compressedImageFile))

//                    displayCapturedImage(rotatedBitmap,outputPath)

                    image.close()
                    sendImage()
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                }
            }
        )

//        imageCapture.takePicture(
//            outputFileOptions,
//            cameraExecutor,
//            object : ImageCapture.OnImageSavedCallback {
//                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
//                    val bitmap = BitmapFactory.decodeFile(outputFileResults.savedUri?.path)
////                    val filteredBitmap = applyFilter(bitmap)
//                    Log.d("Walld","Capturing photo")
//                    val compressedBitmap = compressBitmap(bitmap, 50)
//                    // Display the filtered photo
////                    binding.photoImageView.setImageBitmap(filteredBitmap)
////                    binding.photoImageView.visibility = View.VISIBLE
//                    displayCapturedImage(compressedBitmap,outputPath)
//                    cameraProvider.unbindAll()
//                }
//
//                override fun onError(exception: ImageCaptureException) {
//                    // Handle error
//                    Log.e("Walld",exception.toString())
//                }
//            }
//        )
    }
    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
    private fun displayCapturedImage(bitmap: Bitmap,outputPath: File) {
        binding.surfaceView.visibility = View.GONE
//        binding.previewContainer.visibility = View.GONE
        binding.captureButton.visibility = View.GONE

        binding.photoImageView.visibility = View.VISIBLE
        binding.sendImageButton.rotationY = 20f
        binding.sendImageButton.visibility = View.VISIBLE

//        // Get the orientation of the captured image
//        val ei = ExifInterface(outputPath.absolutePath)
//        val orientation = ei.getAttributeInt(
//            ExifInterface.TAG_ORIENTATION,
//            ExifInterface.ORIENTATION_UNDEFINED
//        )
//
//        // Rotate the bitmap if necessary
//        val rotatedBitmap = when (orientation) {
//            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
//            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
//            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
//            else -> bitmap
//        }
        Log.d("Walld","setted the imgae")
//        selectImage = rotatedBitmap
        binding.photoImageView.setImageBitmap(bitmap)
    }

    private fun sendImage() {
//
//        val resultIntent = Intent()
//        resultIntent.putExtra("capturedImage", outputPath.absolutePath)
//        setResult(Activity.RESULT_OK, resultIntent)
//        finish()

        val resultIntent = Intent(this, EditImageActivity::class.java)
        resultIntent.putExtra("capturedImage",  Uri.fromFile(File(outputPath.absolutePath)))
        startActivity(resultIntent)
    }

    private fun compressBitmap(bitmap: Bitmap, quality: Int): Bitmap {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
        val compressedBitmap = BitmapFactory.decodeByteArray(byteArrayOutputStream.toByteArray(), 0, byteArrayOutputStream.size())
        byteArrayOutputStream.close()
        return compressedBitmap
    }

    private fun getCompressedImageFile(): File {
        // Define a location for saving compressed images
        val directory = File(getExternalFilesDir(null), "compressed_images")
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val fileName = "compressed_image_${System.currentTimeMillis()}.jpg"
        return File(directory, fileName)
    }
}
