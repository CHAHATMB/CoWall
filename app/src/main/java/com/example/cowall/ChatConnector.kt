package com.example.cowall

import android.content.Context
import android.net.Uri
import com.example.cowall.data.MessageModel

interface ChatConnector {

    interface MessageUpdateCallback {
        fun onMessageUpdated(message: MessageModel)
        fun onMessageGet(messages: List<MessageModel>)
    }

    interface UploadCallback {
        fun onUploadStarted()
        fun onUploadSuccess()
        fun onUploadFailure(error: String)
    }

    fun initializeConnection(context: Context)
    fun setMessageUpdateCallback(callback: MessageUpdateCallback?)
    fun setUploadCallback(callback: UploadCallback?)

    fun lookForUpdates(path: String)
    fun stopListening()

    fun sendTextMessage(text: String, replyToKey: String? = null, replyPreview: String? = null)
    fun uploadImageToDrive(selectedImage: Uri)
    fun getAllMessageData()
    fun getPatnerUserName(callback: (String?) -> Unit)

    fun sendMessage(childPath: String, msg: String)
    fun setProperty(childPath: String, value: String)
    fun getMessageCount(child: String): Int
    fun setWallpaper(imagePath: String)
}
