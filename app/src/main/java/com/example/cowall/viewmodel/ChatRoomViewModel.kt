package com.example.cowall.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cowall.data.MessageModel
import com.example.cowall.repositories.DatabaseRepository
import com.example.cowall.utilities.printLog
import kotlinx.coroutines.launch

class ChatRoomViewModel(private val databaseRepository: DatabaseRepository) : ViewModel() {

    private val _messages = MutableLiveData<List<MessageModel>>()
    val messages: LiveData<List<MessageModel>> = _messages

    fun getAllMessages() {
        viewModelScope.launch {
            val messageList = databaseRepository.getAllMessageData()
            printLog("Messages loaded: ${messageList.size}")
            _messages.postValue(messageList)
        }
    }

    fun sendMessageAndImage(uri: Uri) {
        viewModelScope.launch {
            runCatching { databaseRepository.uploadImageToFirebase(uri) }
                .onSuccess { printLog("Uploaded: $it") }
                .onFailure { printLog("Upload error: ${it.message}") }
        }
    }
}
