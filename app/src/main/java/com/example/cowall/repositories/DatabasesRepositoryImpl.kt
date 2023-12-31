package com.example.cowall.repositories

import android.Manifest
import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.example.cowall.FireBaseConnector
import com.example.cowall.data.MessageModel
import com.example.cowall.data.UserChat
import com.example.cowall.utilities.printLog
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

class DatabasesRepositoryImpl(private val context: Context,private val storage: FirebaseStorage, private val database: FirebaseDatabase, sharedPref: SharedPreferences) : DatabaseRepository{

    lateinit var userUniqueId : String
    lateinit var roomId : String
    private val Wall_Tag = "Walld"
    init {
        if (sharedPref.contains("joinedRoomId")) {
            roomId = sharedPref.getString("joinedRoomId", "default_value").toString()
            userUniqueId = sharedPref.getString("userUniqueId","default_user_id").toString()
        } else {
            println("Some Error in geting data form shared Prefernce")
        }
    }


    private fun getCompressedImageFile(fileName:String = "compressed_image_${System.currentTimeMillis()}.jpg" ): File {

        // Define a location for saving compressed images
        val directory = File(context.getExternalFilesDir(null), "compressed_images")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return File(directory, fileName)
    }

    override suspend fun getImageFromFirebase(imgPath: String, sendId: String): Uri {

//        val storageRef = Firebase.storage.reference
        val imageRef = storage.reference.child("file/${FireBaseConnector.roomId}").child(imgPath) // Replace with your actual image path

        val downloadImageFile  = getCompressedImageFile(imgPath)
        printLog("Download Started " + imgPath)
        try {
            imageRef.getFile(downloadImageFile).addOnSuccessListener {
                printLog("Images downloaded successfully")
            }.addOnFailureListener {
                // Handle the failure to download the image
                Log.e(Wall_Tag,it.toString())
            }
        } catch ( e:Exception ) {
            Log.d(Wall_Tag, "ecexpetion ${e}")
        } finally {
            Log.d(Wall_Tag, "Downloaded Image URI from firebase" + Uri.fromFile(downloadImageFile).toString())
            return Uri.fromFile(downloadImageFile)
        }

    }


    fun getImageFromFirebaseForGetAllData(imgPath: String, sendId: String): Uri {

//        val storageRef = Firebase.storage.reference
        val imageRef = storage.reference.child("file/${FireBaseConnector.roomId}").child(imgPath) // Replace with your actual image path

        val downloadImageFile  = getCompressedImageFile(imgPath)
        printLog("Download Started " + imgPath)
        try {
            imageRef.getFile(downloadImageFile).addOnSuccessListener {
                printLog("Images downloaded successfully")
            }.addOnFailureListener {
                // Handle the failure to download the image
                Log.e(Wall_Tag,it.toString())
            }
        } catch ( e:Exception ) {
            Log.d(Wall_Tag, "ecexpetion ${e}")
        } finally {
            Log.d(Wall_Tag, "Downloaded Image URI from firebase" + Uri.fromFile(downloadImageFile).toString())
            return Uri.fromFile(downloadImageFile)
        }

    }

    override suspend fun sendMessage(childPath: String, message: String) {
        database.reference.child(childPath)
            .push()
            .setValue(
                message,
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
                })
    }

    fun sendUri(uri:String) {
        // Convert object to JSON
        val userChat = UserChat(userUniqueId, uri)
        val json = Gson().toJson(userChat)
        val myRef = database.getReference("roomChat/${roomId}")
        myRef.push().setValue(json)
    }

    override suspend fun uploadImageToFirebase(selectedImage: Uri) : String {
        printLog("Recieved URI in uploadImageToFirebase funtion ${selectedImage.toString()}")
        val contentResolver = context.contentResolver
        val documentFile = DocumentFile.fromSingleUri(context, selectedImage)
        val uniqueImageName = documentFile?.name ?: "${UUID.randomUUID()}.jpg"

//        val uniqueImageName = selectedImage.lastPathSegment ?: "${UUID.randomUUID()}.jpg"
        val uploadTask = storage.reference.child("file/${FireBaseConnector.roomId}/$uniqueImageName").putFile(selectedImage)
        // On success, download the file URL and display it
        uploadTask.addOnSuccessListener {
            // using glide library to display the image
            storage.reference.child("file/${roomId}/$uniqueImageName").downloadUrl.addOnSuccessListener {
//                    Glide.with(this@MainActivity)
//                        .load(it)
//                        .into(imageview)

                Log.d(Wall_Tag, "download passed " + it.path)
                sendUri(uniqueImageName)
            }.addOnFailureListener {
                Log.e(Wall_Tag, "Failed in downloading")
            }
        }.addOnFailureListener {
            Log.e(Wall_Tag, "Image Upload fail " + it.toString())
        }

        return uniqueImageName
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

    override suspend fun getAllMessageData(): List<MessageModel> {
            return withContext(Dispatchers.IO) {
                val messages = ArrayList<MessageModel>()
                database.reference.child("roomChat/${FireBaseConnector.roomId}")
                    .orderByChild("timestamp")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.hasChildren()) {
                                for (messageSnapshot in snapshot.children) {
                                    val message = messageSnapshot.getValue(String::class.java)
                                    val gson = Gson()
                                    val userChat = gson.fromJson(message, UserChat::class.java)

                                    userChat?.let {
                                        printLog("traversing through list of message ${it.userUniqueId}, ${it.uri}")
                                        val downloadedImageUri: Uri =
                                            getImageFromFirebaseForGetAllData(it.uri, it.userUniqueId)
                                        printLog("downloaded Uri - ${downloadedImageUri.toString()}")
                                        messages.add(
                                            MessageModel(
                                                "Send from ${it.userUniqueId}",
                                                downloadedImageUri,
                                                it.userUniqueId
                                            )
                                        )
                                    }
                                }

                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("Walld", "we got error downloading ${error.toString()}")
                        }
                    })
                printLog("Messages we got ${messages.toString()}") // Log after population
                return@withContext messages // Return the list within the callback
            }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun setWallpaper(imagePath: String) {

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SET_WALLPAPER)
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

     suspend fun lookForUpdates(path: String) {
        database.reference.child("roomChat/${FireBaseConnector.roomId}").addValueEventListener(object : ValueEventListener {
            override fun onCancelled(error: DatabaseError) {
                Log.d(Wall_Tag, "not able to send message " + error)
            }
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(Wall_Tag, "message arried " + snapshot.getValue())
                if (snapshot.hasChildren()) {
                    Log.d(Wall_Tag,"has last snapshot")
                    val lastMessageSnapshot = snapshot.children.last()
                    val lastMessage = lastMessageSnapshot.getValue(String::class.java)

                    if (lastMessage != null) {
                        Log.d(Wall_Tag,"till now no null")
                        val gson = Gson()
                        val userChat = gson.fromJson(lastMessage, UserChat::class.java)
                        Log.d(Wall_Tag, "Last Message: $lastMessage")
                        if(userChat.userUniqueId != FireBaseConnector.userUniqueId) {
                            GlobalScope.launch {
                                getImageFromFirebase(userChat.uri, userChat.userUniqueId)
                            }
                        }
                    }
                }
            }
        })
    }
}