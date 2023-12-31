package com.example.cowall.data

import android.net.Uri

class MessageModel {
    var message: String? = null
    var imageUri: Uri? = null
    var senderId: String? = null

    constructor(){}

    constructor(message: String, imageUri: Uri, senderId:String){
        this.message = message
        this.imageUri = imageUri
        this.senderId = senderId
    }

    constructor(imageUri: Uri, senderId: String){
        this.imageUri = imageUri
        this.senderId = senderId
    }
}