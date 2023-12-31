package com.example.cowall.repositories

import android.net.Uri
import com.example.cowall.data.MessageModel

interface DatabaseRepository {
    suspend fun getImageFromFirebase(imgPath:String, sendId: String) : Uri
    suspend fun sendMessage(childPath: String, message: String)
    suspend fun uploadImageToFirebase(selectedImage: Uri) : String
    suspend fun getAllMessageData(): List<MessageModel>
}