package com.example.cowall.repositories

import android.net.Uri

interface DatabaseRepository {
    suspend fun getImageFromFirebase(imgPath:String, sendId: String) : Uri
    suspend fun sendMessage(childPath: String, message: String)
    suspend fun sendUri()
}