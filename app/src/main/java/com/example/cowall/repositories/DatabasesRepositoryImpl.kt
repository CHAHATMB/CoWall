package com.example.cowall.repositories

import android.net.Uri
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

class DatabasesRepositoryImpl : DatabaseRepository{

    private lateinit var database: FirebaseDatabase
    private lateinit var storageRef: FirebaseStorage

    override suspend fun getImageFromFirebase(imgPath: String, sendId: String): Uri {
        TODO("Not yet implemented")
    }

    override suspend fun sendMessage(childPath: String, message: String) {
        TODO("Not yet implemented")
    }

    override suspend fun sendUri() {
        TODO("Not yet implemented")
    }
}