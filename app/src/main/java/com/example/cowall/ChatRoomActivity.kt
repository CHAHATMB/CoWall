package com.example.cowall

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cowall.databinding.ActivityChatRoomBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File

class ChatRoomActivity : AppCompatActivity(), FireBaseConnector.MessageUpdateCallback {

    private lateinit var binding: ActivityChatRoomBinding
    private lateinit var cameraButton: FloatingActionButton
    var REQUEST_IMAGE_CAPTURE = 340
    private lateinit var adapter : MessageAdapter
    private lateinit var fbc : FireBaseConnector

    val messages = ArrayList<MessageModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatRoomBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        cameraButton = binding.cameraButton
        binding.cameraButton.setOnClickListener {
            onCameraButtonPress()
        }

        fbc = FireBaseConnector()
        fbc.initializeConnection(this.applicationContext)
        fbc.setMessageUpdateCallback(this)
        fbc.getAllMessageData()

        adapter = MessageAdapter(this, messages)
        binding.chatRoomRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatRoomRecyclerView.adapter = adapter
    }
    override fun onMessageUpdated(newMessage: MessageModel) {
        adapter.addMessage(newMessage)
    }

    override fun onMessageGet(updatedMessages: List<MessageModel>) {
        adapter.addAllMessages(updatedMessages)
    }


    fun onCameraButtonPress(){
        Log.d("Walld","button pressed")
        var captureImageIntent = Intent(this, CameraActivity::class.java);
        startActivityForResult(captureImageIntent, REQUEST_IMAGE_CAPTURE)

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            val capturedImagePath = data?.getStringExtra("capturedImage")
//            messages.add()
            var imageUri =  Uri.fromFile(File(capturedImagePath))
            adapter.addMessage(MessageModel("Hi there sajan!", imageUri,FireBaseConnector.userUniqueId))
            fbc.uploadImageToFirebase(imageUri)
            Log.d("Walld","recieve$capturedImagePath")
        }
    }

}