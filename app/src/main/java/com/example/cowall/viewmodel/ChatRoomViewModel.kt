package com.example.cowall.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cowall.data.MessageModel
import com.example.cowall.repositories.DatabaseRepository
import com.example.cowall.utilities.Coroutines
import com.example.cowall.utilities.printLog
import kotlinx.coroutines.runBlocking

class ChatRoomViewModel(private val databaseRepository: DatabaseRepository): ViewModel() {

    private val _messages = MutableLiveData<List<MessageModel>>()
    val messages: LiveData<List<MessageModel>> = _messages
    fun getAllMessages(){
        Coroutines.io{
            runBlocking {
                val messageList = databaseRepository.getAllMessageData()
                printLog("List of messages $messageList")
                _messages.postValue(messageList)
            }
//                .onSuccess { messageList ->
//                if(messageList.isEmpty()){
//                    printLog("List of messages is empty")
//                } else {
//                    printLog("List of messages $messageList")
//                    _messages.postValue(messageList)
//                }
//            }.onFailure {
//                printLog("Some error in geting all messages")
//            }

        }
    }


    fun sendMessageAndImage(uri: Uri){
        Coroutines.io{
            runCatching {
                databaseRepository.uploadImageToFirebase(uri)
            }.onSuccess {
                printLog("Uploaded Image Name $it")
            }.onFailure {
                printLog("Some Error in Uploading Image")
            }
        }
    }



}