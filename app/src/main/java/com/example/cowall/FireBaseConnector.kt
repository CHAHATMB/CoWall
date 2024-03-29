package com.example.cowall

import android.Manifest.permission.SET_WALLPAPER
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.cowall.data.MessageModel
import com.example.cowall.data.User
import com.example.cowall.data.UserChat
import com.example.cowall.utilities.printLog
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import java.io.File
import java.util.*
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import kotlin.collections.ArrayList

class FireBaseConnector {
    interface MessageUpdateCallback {
        fun onMessageUpdated(updatedMessages: MessageModel)
        fun onMessageGet(updatedMessages: List<MessageModel>)
    }
    private var messageUpdateCallback: MessageUpdateCallback? = null


    private lateinit var database: FirebaseDatabase
    private lateinit var storageRef: FirebaseStorage
    private val Wall_Tag = "Walld"
    lateinit var context: Context

    companion object {
        lateinit var userUniqueId : String
        lateinit var roomId : String
        lateinit var partnerUserName : String

        fun setUniqueIds(userUniqueId: String, roomId:String){
            this.roomId = roomId
            this.userUniqueId = userUniqueId
        }

        fun getUserUniqueID (): String {
            return userUniqueId
        }

    }
    fun setMessageUpdateCallback(callback: MessageUpdateCallback) {
        messageUpdateCallback = callback
    }

    fun initializeConnection(context: Context){
        this.context = context
        storageRef = Firebase.storage

//        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        database = Firebase.database
//        database.setPersistenceEnabled(true)
    }

    fun lookForUpdates(path: String) {

        database.reference.child("roomChat/$roomId").addValueEventListener(object : ValueEventListener {
            override fun onCancelled(error: DatabaseError) {
                Log.d(Wall_Tag, "not able to send message " + error)
            }

            override fun onDataChange(snapshot: DataSnapshot) {

//                val user = snapshot.getValue(String::class.java)
//                val gson = Gson()
//                val userChat = gson.fromJson(user, UserChat::class.java)
//                Log.d(Wall_Tag, "Last Message: $user")
//                if(userChat.userUniqueId != userUniqueId) {
//                    getImageFromFirebase(userChat.uri)
//                }
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
                        if(userChat.userUniqueId != userUniqueId) {
                            getImageFromFirebase(userChat.uri, userChat.userUniqueId)
                        }
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

    fun getImageFromFirebase(imgPath: String = "images/image.jpg", senderId: String = "3245", flag: Boolean = true) {
        val storageRef = Firebase.storage.reference
        val imageRef = storageRef.child("file/$roomId").child(imgPath)

        // Use permanent storage directory
        val permanentStorageDirectory = File(context.getExternalFilesDir(null), "images")
        if (!permanentStorageDirectory.exists()) {
            permanentStorageDirectory.mkdirs()
        }

        val permanentFile = File(permanentStorageDirectory, imgPath)
        Log.d(Wall_Tag, "Checking if file exists: ${permanentFile.absolutePath}")

        // Check if the file exists in permanent storage
        if (permanentFile.exists()) {
            // Image already exists in permanent storage, use it
            val msg = if(senderId!= userUniqueId) "$partnerUserName set a pic!" else "You set a Pic!"
            messageUpdateCallback?.onMessageUpdated(

                MessageModel(
                    msg,
                    Uri.fromFile(permanentFile),
                    senderId
                )
            )

            if (flag) {
                // Now set it as wallpaper
                setWallpaper(permanentFile.absolutePath)
            }

            Log.d(Wall_Tag, "Image already exists in permanent storage")
        } else {
            // File doesn't exist in permanent storage, download it
            Log.d(Wall_Tag, "File doesn't exist, downloading...")

            try {
                imageRef.getFile(permanentFile).addOnSuccessListener {
                    // Image downloaded successfully
                    messageUpdateCallback?.onMessageUpdated(
                        MessageModel(
                            "Send from $senderId",
                            Uri.fromFile(permanentFile),
                            senderId
                        )
                    )

                    if (flag) {
                        // Now set it as wallpaper
                        setWallpaper(permanentFile.absolutePath)
                    }

                    Log.d(Wall_Tag, "Downloaded successfully to permanent storage")
                }.addOnFailureListener {
                    // Handle the failure to download the image
                    Log.e(Wall_Tag, it.toString())
                }
            } catch (e: Exception) {
                Log.d(Wall_Tag, "Exception: $e")
            }
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
                })
    }


    fun setProperty(childPath:String, msg:String){
        database.reference.child(childPath)
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
                })
    }

    fun sendUri(uri:String){
        // Convert object to JSON
        val userChat = UserChat(userUniqueId, uri)
        val gson = Gson()
        val json = gson.toJson(userChat)

        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("roomChat/$roomId")
        myRef.push().setValue(json)
    }
    fun uploadImageToFirebase(selectedImage: Uri) : String {
        val sd = "${UUID.randomUUID()}.jpg"
        val compressedImageUri = compressImage(selectedImage, sd) ?: return "" // Handle compression failure
        printLog("selected $selectedImage and comp $compressedImageUri")
        val uploadTask = storageRef.reference.child("file/$roomId/$sd").putFile(compressedImageUri)
        // On success, download the file URL and display it
        uploadTask.addOnSuccessListener {
            // using glide library to display the image
            storageRef.reference.child("file/$roomId/$sd").downloadUrl.addOnSuccessListener {
//                    Glide.with(this@MainActivity)
//                        .load(it)
//                        .into(imageview)

                Log.d(Wall_Tag, "download passed " + it.path)
                sendUri(sd)
            }.addOnFailureListener {
                Log.e(Wall_Tag, "Failed in downloading")
            }
        }.addOnFailureListener {
            Log.e(Wall_Tag, "Image Upload fail " + it.toString())
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

    private fun compressImage(selectedImage: Uri, imagePath: String): Uri? {
        val file = File(selectedImage.path!!) // Get the file from the URI
        printLog("iamge uurri $selectedImage, $file, \n ${file.absolutePath}")
        return try {
            val options = BitmapFactory.Options()
            options.inPreferredConfig = Bitmap.Config.RGB_565
//            val bitmap = BitmapFactory.decodeFile(file.path, options)
            val bitmap: Bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), selectedImage)

            printLog("bitmap, ${bitmap.toString()}")
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream) // Compress to 50% quality
            val compressedImage = File(context.getExternalFilesDir(null), "images/$imagePath")
            compressedImage.outputStream().use {
                it.write(stream.toByteArray())
            }
            // Return the URI of the compressed image
            Uri.fromFile(compressedImage)
        } catch (e: Exception) {
            // Handle any errors during compression
            Log.e(Wall_Tag, "Image compression failed: $e")
            null
        }
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

    fun getAllMessageData(){

        var messageList = ArrayList<MessageModel>()
        database.reference.child("roomChat/$roomId")
            .orderByChild("timestamp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.hasChildren()) {
                        for (messageSnapshot in snapshot.children) {
                            val message = messageSnapshot.getValue(String::class.java)
                            val gson = Gson()
                            val userChat = gson.fromJson(message, UserChat::class.java)
                            userChat?.let {
                                Log.d("Walld","traversing throug ${it.userUniqueId}")
                                // Process and display the message in your UI
                                // For example, you can add it to your adapter
                                // messageAdapter.addMessage(message)
//                                messageList.add(MessageModel("Chahats mesa",Uri.fromFile(File(it.uri)), it.userUniqueId))
                                getImageFromFirebase(it.uri,it.userUniqueId,false)
                            }
                        }
                    }
//                    messageUpdateCallback?.onMessageGet(messageList)
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                }
            })
    }
    fun getPatnerUserName( callback: (String?) -> Unit){
        val roomRef = database.getReference("chatRooms/$roomId/participants")
        roomRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Handle the event when participants change
                // Notify the room creator or update UI
                val participants = snapshot.children.map { it.key }.toList()
                printLog("Participants: $participants")
                for (participantSnapshot in snapshot.children) {
                    val partnerUserId: String? = participantSnapshot.key
                    val isJoined = participantSnapshot.value as Boolean
                    if(partnerUserId != userUniqueId ){
                        val userReference = database.reference.child("userName").child(partnerUserId!!)

                        userReference.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                // Check if the user exists
                                val userName:String = snapshot.getValue() as String
                                partnerUserName = userName
                                callback.invoke(userName)
                            }

                            override fun onCancelled(error: DatabaseError) {
                                callback.invoke("UserName")
                            }
                        })
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle the error
                Log.e("ChatRoomActivity", "Error: $error")
            }
        })


    }
}