package com.example.cowall

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.cowall.activities.EditImageActivity
import com.example.cowall.databinding.ActivityCameraBinding
import com.example.cowall.utilities.printLog
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
    private var cameraState = false
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private val REQUEST_CODE_PERMISSION = 101
    private val REQUEST_CODE_PICK_IMAGE = 102


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
        binding.switchCameraButton.setOnClickListener{
            onSwitchCameraButtonPress()
        }
        binding.flashButton.setOnClickListener(){
            onFlashButtonPress()
        }
        binding.galleryButton.setOnClickListener(){
            onGalleryButtonPress()
        }
        startCamera()
    }



    private fun bindCameraPreview(cameraProvider: ProcessCameraProvider, cameraFlag: Boolean = false) {
        val cameraSelector = if (cameraFlag) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val rotation = when (cameraSelector) {
            CameraSelector.DEFAULT_BACK_CAMERA -> Surface.ROTATION_270
            CameraSelector.DEFAULT_FRONT_CAMERA -> Surface.ROTATION_90
            else -> Surface.ROTATION_0 // Default to back camera rotation
        }

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(480, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
//            .setTargetRotation(rotation)
            .build()

        val preview: Preview = Preview.Builder()
//            .setTargetRotation(rotation)
            .build()
        preview.targetRotation = Surface.ROTATION_180
        preview.setSurfaceProvider(binding.surfaceView.surfaceProvider)
//        printLog("display rotation ${Display.getRotation(view)}")
        // Additional adjustment for front camera
        if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            imageCapture.targetRotation = Surface.ROTATION_270
        }

        cameraProvider.unbindAll()

        val camera = cameraProvider.bindToLifecycle(
            this,
            cameraSelector,
            imageCapture,
            preview,
            imageAnalyzer
        )
        printLog("Camera info" +  camera.cameraInfo.hasFlashUnit().toString())
//        if(flashState && camera.cameraInfo.hasFlashUnit()){
//            camera.cameraControl.enableTorch(true)
//        }
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

    fun getCorrectionMatrix(imageProxy: ImageProxy, previewView: PreviewView) : Matrix {
        val cropRect = imageProxy.cropRect
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val matrix = Matrix()

        // A float array of the source vertices (crop rect) in clockwise order.
        val source = floatArrayOf(
            cropRect.left.toFloat(),
            cropRect.top.toFloat(),
            cropRect.right.toFloat(),
            cropRect.top.toFloat(),
            cropRect.right.toFloat(),
            cropRect.bottom.toFloat(),
            cropRect.left.toFloat(),
            cropRect.bottom.toFloat()
        )

        // A float array of the destination vertices in clockwise order.
        val destination = floatArrayOf(
            0f,
            0f,
            previewView.width.toFloat(),
            0f,
            previewView.width.toFloat(),
            previewView.height.toFloat(),
            0f,
            previewView.height.toFloat()
        )

        // The destination vertexes need to be shifted based on rotation degrees. The
        // rotation degree represents the clockwise rotation needed to correct the image.

        // Each vertex is represented by 2 float numbers in the vertices array.
        val vertexSize = 2
        // The destination needs to be shifted 1 vertex for every 90° rotation.
        val shiftOffset = rotationDegrees / 90 * vertexSize;
        val tempArray = destination.clone()
        for (toIndex in source.indices) {
            val fromIndex = (toIndex + shiftOffset) % source.size
            destination[toIndex] = tempArray[fromIndex]
        }
        matrix.setPolyToPoly(source, 0, destination, 0, 4)
        return matrix
    }

    private fun takePhoto() {
        outputPath = getOutputFile()
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(outputPath).build()
        Log.d("Walld","Capturing photo jgh")
        imageCapture.flashMode = flashMode

//        imageCapture.takePicture(
//            cameraExecutor,
//            object : ImageCapture.OnImageCapturedCallback() {
//                override fun onCaptureSuccess(image: ImageProxy) {
//                    imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
//                    printLog("We code image "+ image.imageInfo.rotationDegrees )
//                    // Get the captured image as a bitmap
////                    image = getCorrectionMatrix(image, binding.surfaceView)
//                    val bitmap = image.toBitmap()
//
//
//
//                    // Compress the bitmap
//                    val compressedBitmap = compressBitmap(bitmap!!, 10) // Adjust quality level as needed
//                    Log.d("Walld","Compressing the image on go")
//                    val compressedImageFile = getCompressedImageFile()
//                    compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 10, FileOutputStream(compressedImageFile))
//                    outputPath = compressedImageFile
//
//                    // Get the orientation of the captured image
//                    val ei = ExifInterface(outputPath.absolutePath)
//                    val orientation = ei.getAttributeInt(
//                        ExifInterface.TAG_ORIENTATION,
//                        ExifInterface.ORIENTATION_UNDEFINED
//                    )
//                    // Rotate the bitmap if necessary
//                    val rotatedBitmap = rotateImage(bitmap, 90f)
//                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 10, FileOutputStream(compressedImageFile))
//
//                    image.close()
//                    sendImage()
//                }
//
//                override fun onError(exception: ImageCaptureException) {
//                    exception.printStackTrace()
//                }
//            }
//        )

        imageCapture.takePicture(
            outputFileOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
//                    val bitmap = BitmapFactory.decodeFile(outputFileResults.savedUri?.path)
//                    outputPath = outputFileResults
//                    val filteredBitmap = applyFilter(bitmap)
                    Log.d("Walld","Captured photo ${outputFileResults.savedUri?.path}")
//                    val compressedBitmap = compressBitmap(bitmap, 50)
                    // Display the filtered photo
//                    binding.photoImageView.setImageBitmap(filteredBitmap)
//                    binding.photoImageView.visibility = View.VISIBLE
//                    displayCapturedImage(compressedBitmap,outputPath)
                    sendImage(outputFileResults.savedUri!!)
                    cameraProvider.unbindAll()
                }

                override fun onError(exception: ImageCaptureException) {
                    // Handle error
                    Log.e("Walld",exception.toString())
                }
            }
        )
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

//        binding.photoImageView.visibility = View.VISIBLE
//        binding.sendImageButton.rotationY = 20f
//        binding.sendImageButton.visibility = View.VISIBLE

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
//        binding.photoImageView.setImageBitmap(bitmap)
    }

    private fun sendImage(uri:Uri? = null) {
//
//        val resultIntent = Intent()
//        resultIntent.putExtra("capturedImage", outputPath.absolutePath)
//        setResult(Activity.RESULT_OK, resultIntent)
//        finish()
        printLog("on send Image function")
        val imageUri = uri ?: Uri.fromFile(File(outputPath.absolutePath))

        val resultIntent = Intent(this, EditImageActivity::class.java)
        resultIntent.putExtra("capturedImage",  imageUri)
        startActivityForResult(resultIntent,89)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 89 && resultCode == RESULT_OK && data != null) {

            data.extras?.getString(EditImageActivity.KEY_FILTERED_IMAGE_URI).let{
                Log.d("Walld","rwwecieve in cama${it}  " + data.extras)
                Intent().also { camintent ->
                    camintent.putExtra(EditImageActivity.KEY_FILTERED_IMAGE_URI, it)
                    setResult(RESULT_OK, camintent)
                    finish()
                }
            }
        }
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            val selectedImage = data.data
            sendImage(data.data)

        }
    }
    fun onFlashButtonPress(){
        flashMode = when (flashMode){
            ImageCapture.FLASH_MODE_AUTO -> {
                binding.flashButton.setImageDrawable(resources.getDrawable(R.drawable.ic_flash_off_icon))
                ImageCapture.FLASH_MODE_OFF
            }
            ImageCapture.FLASH_MODE_OFF -> {
                binding.flashButton.setImageDrawable(resources.getDrawable(R.drawable.ic_flash_on))
                ImageCapture.FLASH_MODE_ON
            }
            ImageCapture.FLASH_MODE_ON -> {
                binding.flashButton.setImageDrawable(resources.getDrawable(R.drawable.ic_flash_auto))
                ImageCapture.FLASH_MODE_AUTO
            }
            else -> {
                binding.flashButton.setImageDrawable(resources.getDrawable(R.drawable.ic_flash_off_icon))
                ImageCapture.FLASH_MODE_OFF
            }
        }
    }
    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            REQUEST_CODE_PERMISSION
        )
    }
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
//        startActivity()
    }

    fun onGalleryButtonPress(){
        if (checkPermission()) {
            openImagePicker()
            printLog("opening image picker")

        } else {
            requestPermission()
            printLog("requesting permsission")
        }
    }
    fun onSwitchCameraButtonPress(){
        printLog("Switch Camera button pressed!")
        cameraState = !cameraState
        startCamera()
    }

    fun startCamera(){
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
                bindCameraPreview(cameraProvider,cameraState)
            },
            cameraExecutor
        )

    }
}
