package com.example.cowall

import android.Manifest.permission.SET_WALLPAPER
import android.app.WallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import java.io.File
import java.io.IOException
import java.util.*

class FireBaseConnector {
    private lateinit var database: FirebaseDatabase
    private lateinit var storageRef: FirebaseStorage
    private val Wall_Tag = "Walld"
    lateinit var context: Context

    fun initializeConnection(context: Context){
        this.context = context
        storageRef = Firebase.storage

        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        database = Firebase.database
    }

    fun lookForUpdates(path: String) {

        database.reference.child(path).addValueEventListener(object : ValueEventListener {
            override fun onCancelled(error: DatabaseError) {
                Log.d(Wall_Tag, "not able to send message " + error)
            }

            override fun onDataChange(snapshot: DataSnapshot) {

                val user = snapshot.getValue()
                Log.d(Wall_Tag, "message arried " + user)
                if (snapshot.hasChildren()) {
                    val lastMessageSnapshot = snapshot.children.last()
                    val lastMessage = lastMessageSnapshot.getValue()

                    if (lastMessage != null) {
                        // Do something with the last message
                        Log.d(Wall_Tag, "Last Message: $lastMessage")
                        getImageFromFirebase(lastMessage.toString())
                    }
                }
            }
        })
    }

    fun setWallpaper(imagePath: String) {
//        val wallpaperManager = WallpaperManager.getInstance(context)
//
//        val bitmap = BitmapFactory.decodeFile(imagePath)
//
//        try {
//            wallpaperManager.setBitmap(bitmap)
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }

        if (ContextCompat.checkSelfPermission(context, SET_WALLPAPER)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val bitmapImg = BitmapFactory.decodeFile(imagePath)
            Log.d(Wall_Tag,"setting wallpaper")
            val wallpaperManager = WallpaperManager.getInstance(context)
            wallpaperManager.setBitmap(bitmapImg, null, true, WallpaperManager.FLAG_LOCK)
        } else {
            Log.d(Wall_Tag, "not able to set")
        }
    }

    fun getImageFromFirebase(imgPath : String = "images/image.jpg"){
        val storageRef = Firebase.storage.reference
        val imageRef = storageRef.child("file").child(imgPath) // Replace with your actual image path

        val localFile = File.createTempFile("tempImage", "jpg")
        Log.d(Wall_Tag,"downloaded on the way " + imgPath)
        try {
            imageRef.getFile(localFile).addOnSuccessListener {
                // Image downloaded successfully
                // Now set it as wallpaper
                setWallpaper(localFile.absolutePath)
                Log.d(Wall_Tag,"downloaded sucesffuly ")
            }.addOnFailureListener {
                // Handle the failure to download the image
                Log.e(Wall_Tag,it.toString())
            }
        } catch ( e:Exception ) {
            Log.d(Wall_Tag, "ecexpetion ${e}")
        } finally {
            Log.d(Wall_Tag, "nothing happens")
        }


    }

    fun sendMessage(childPath:String, msg:String){
        database.reference.child(childPath)
            .push()
            .setValue(
                msg,
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

    fun uploadImageToFirebase(selectedImage: Uri) : String {
        val sd = "${UUID.randomUUID()}.jpg"
        val uploadTask = storageRef.reference.child("file/$sd").putFile(selectedImage)
        // On success, download the file URL and display it
        uploadTask.addOnSuccessListener {
            // using glide library to display the image
            storageRef.reference.child("file/$sd").downloadUrl.addOnSuccessListener {
//                    Glide.with(this@MainActivity)
//                        .load(it)
//                        .into(imageview)

                Log.d(Wall_Tag, "download passed " + it.path)
//                sendMessage("uriString", sd.toString())
            }.addOnFailureListener {
                Log.e(Wall_Tag, "Failed in downloading")
            }
        }.addOnFailureListener {
            Log.e(Wall_Tag, "Image Upload fail")
        }

        return sd
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

    fun getMessageCount(child: String) : Int {
        var returningValue : Int = 0
        database.reference.child(child).get().addOnSuccessListener {
            Log.i("firebase", "Got value ${it.value}")
            returningValue = it.childrenCount.toInt()
        }.addOnFailureListener{
            returningValue = 0
        }
        return returningValue
    }
}