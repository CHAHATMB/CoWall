package com.example.cowall

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.cowall.databinding.ActivityMainBinding
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import java.io.File
import java.io.IOException


class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_PERMISSION = 101
    private val REQUEST_CODE_PICK_IMAGE = 102
    private var selectedImageBitmap: Bitmap? = null
    private val Wall_Tag = "Walld"
    private lateinit var database: FirebaseDatabase
    private lateinit var storageRef: FirebaseStorage
    private lateinit var binding: ActivityMainBinding
    var context: Context? = null
    private  lateinit var firebaseconn : FireBaseConnector
    private lateinit var imgAdd : String
    private lateinit var sharedPref : SharedPreferences
    private val sharedPrefString : String = "cowall"
    private lateinit var uniqueId : String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
//        setContentView(R.layout.activity_main)
        setContentView(view)

        binding.btnSetWallpaper.setOnClickListener { btnSetWallpaperFunction() }
        binding.btnSetWallpaper.isEnabled = false
        binding.btnUpload.setOnClickListener { btnUploadFunction() }
        displaySelectedImage(null)
//        context = applicationContext;
        firebaseconn = FireBaseConnector()
        firebaseconn.initializeConnection(this.applicationContext)

        if (!checkPermission()){
            requestPermission()
        }

        // Check if we have permission to set wallpaper
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SET_WALLPAPER)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // If not, request the permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SET_WALLPAPER),
                REQUEST_CODE_PERMISSION
            )
            Log.d(Wall_Tag,"no permissions ")

        }
        storageRef = Firebase.storage

//        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
//        database = Firebase.database

        Intent(applicationContext, RunningService::class.java).also{
            it.action =  RunningService.Actions.START.toString()
            startService(it)
        }
        Intent(this, ChatRoomActivity::class.java).also {
            startActivity(it)
            finish()
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



    fun btnUploadFunction() {
        if (checkPermission()) {
            openImagePicker()
            Log.d(Wall_Tag,"opening image picker    ")

        } else {
            requestPermission()
            Log.d(Wall_Tag,"requesting permsission")
        }
    }

    fun btnSetWallpaperFunction() {
        firebaseconn.sendMessage("uriString", imgAdd)
        binding.btnSetWallpaper.isEnabled = false
    }
    private fun displaySelectedImage(imageUri: Uri?) {

        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        if (imageUri != null) {
            binding.imageView.setImageURI(imageUri)
            val editor = sharedPref.edit()
            editor.putString("imageUri", imageUri.toString())
            editor.apply()
        } else {
            val imageUriString = sharedPref.getString("imageUri", null)

            if (imageUriString != null) {
                val imageUri = Uri.parse(imageUriString)
                binding.imageView.setImageURI(imageUri)
            } else {
                binding.imageView.setImageDrawable(null)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImagePicker()
            } else {
                // Permission denied, handle it
                Log.d(Wall_Tag,"function permission denied")
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
//        startActivity()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            val selectedImage = data.data
            try {
                selectedImageBitmap =
                    BitmapFactory.decodeStream(contentResolver.openInputStream(selectedImage!!))
                binding.btnSetWallpaper.isEnabled = true

            } catch (e: IOException) {
                e.printStackTrace()
            }
            selectedImage?.let { imgAdd = firebaseconn.uploadImageToFirebase(it) }
            displaySelectedImage(selectedImage)

        }
    }



}