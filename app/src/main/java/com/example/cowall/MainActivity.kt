package com.example.cowall

import android.Manifest
import android.Manifest.permission.SET_WALLPAPER
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import java.io.IOException


class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_PERMISSION = 101
    private val REQUEST_CODE_PICK_IMAGE = 102
    private var selectedImageBitmap: Bitmap? = null
    private val Wall_Tag = "Walld"
    private lateinit var database: FirebaseDatabase
    private lateinit var storageRef: FirebaseStorage

//    val myRef = database.getReference("action")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        database = Firebase.database

        database.reference.addValueEventListener(object : ValueEventListener {
            override fun onCancelled(error: DatabaseError) {
                Log.d(Wall_Tag,"not able to send message "+ error)
            }

            override fun onDataChange(snapshot: DataSnapshot) {

                val user = snapshot.getValue()
                Log.d(Wall_Tag,"message arried "+user)
            }
        })
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

    fun changeWallpaper(view: View) {
//        // Check if we have permission
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SET_WALLPAPER)
//            == PackageManager.PERMISSION_GRANTED
//        ) {
//            Log.d(Wall_Tag,"setting wallpaper")
//
//            // Set lock screen wallpaper
//            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
//            val drawable = resources.getDrawable(R.drawable.new_wallpaper, null)
//            wallpaperManager.setBitmap((drawable as BitmapDrawable).bitmap, null, true, WallpaperManager.FLAG_LOCK)
//        } else {
//            // Permission not granted, show a message or handle it accordingly
//            // You can notify the user to grant the permission
//            Log.d(Wall_Tag,"not able to set")
//        }
//        FirebaseDatabase.getInstance().getReference("messages")
        Log.d(Wall_Tag,"sending mesage to firebase")
//        database = FirebaseDatabase.getInstance().getReference("messages")
//        database.child("Chat").push().setValue("Firebass wellscom sstring honi chahiye")
        database.reference.child("sendMessage")
            .push()
            .setValue(
                "my sending messages rahege ",
                DatabaseReference.CompletionListener { databaseError, databaseReference ->
                    if (databaseError != null) {
                        Log.d(
                            Wall_Tag, "Unable to write message to database.",
                            databaseError.toException()
                        )
                        return@CompletionListener
                    } else {
                        Log.d(Wall_Tag, "sab changa si bhaiyanu")
                    }


                    // Build a StorageReference and then upload the file
//                    val key = databaseReference.key
//                    val storageReference = Firebase.storage
//                        .getReference(user!!.uid)
//                        .child(key!!)
//                        .child(uri.lastPathSegment!!)
//                    putImageInStorage(storageReference, uri, key)
                })

    }



    fun btnUploadFunction(view: View) {
        if (checkPermission()) {
            openImagePicker()
            Log.d(Wall_Tag,"opening image picker    ")

        } else {
            requestPermission()
            Log.d(Wall_Tag,"requesting permsission")
        }
    }

    fun btnSetWallpaperFunction(view: View) {
        setWallpaper()
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
//                btn_set_wallpaper.isEnabled = true
            } catch (e: IOException) {
                e.printStackTrace()
            }
            selectedImage?.let { uploadImageToFirebase(it) }

        }
    }

    private fun uploadImageToFirebase(selectedImage: Uri ){
        val sd = getFileName(this.applicationContext, selectedImage!!)
        val uploadTask = storageRef.reference.child("file/$sd").putFile(selectedImage)
        // On success, download the file URL and display it
        uploadTask.addOnSuccessListener {
            // using glide library to display the image
            storageRef.reference.child("file/$sd").downloadUrl.addOnSuccessListener {
//                    Glide.with(this@MainActivity)
//                        .load(it)
//                        .into(imageview)

                Log.e(Wall_Tag, "download passed" + it.path)
            }.addOnFailureListener {
                Log.e(Wall_Tag, "Failed in downloading")
            }
        }.addOnFailureListener {
            Log.e(Wall_Tag, "Image Upload fail")
        }

//        val urlTask = uploadTask.continueWithTask { task ->
//            if (!task.isSuccessful) {
//                task.exception?.let {
//                    throw it
//                }
//            }
//            ref.downloadUrl
//        }.addOnCompleteListener { task ->
//            if (task.isSuccessful) {
//                val downloadUri = task.result
//            } else {
//                // Handle failures
//                // ...
//            }
//        }
    }


    @SuppressLint("Range")
    private fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor.use {
                if (cursor != null) {
                    if(cursor.moveToFirst()) {
                        return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                    }
                }
            }
        }
        return uri.path?.lastIndexOf('/')?.let { uri.path?.substring(it) }
    }
    private fun setWallpaper() {
        val wallpaperManager = WallpaperManager.getInstance(this)
        try {
            selectedImageBitmap?.let {
                wallpaperManager.setBitmap(it)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


}