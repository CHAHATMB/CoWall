package com.example.cowall.activities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cowall.CameraActivity
import com.example.cowall.FireBaseConnector
import com.example.cowall.MessageAdapter
import com.example.cowall.data.MessageModel
import com.example.cowall.adapters.ImageFiltersAdapter
import com.example.cowall.databinding.ActivityChatRoomBinding
import com.example.cowall.utilities.displayToast
import com.example.cowall.utilities.hide
import com.example.cowall.utilities.printLog
import com.example.cowall.utilities.show
import com.example.cowall.viewmodel.ChatRoomViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.koin.android.ext.android.bind
import org.koin.androidx.viewmodel.ext.android.viewModel

class ChatRoomActivity : AppCompatActivity(), FireBaseConnector.MessageUpdateCallback, MessageAdapter.OnItemClickLongListener {

    private lateinit var binding: ActivityChatRoomBinding
    private lateinit var cameraButton: FloatingActionButton
    var REQUEST_IMAGE_CAPTURE = 340
    private lateinit var adapter : MessageAdapter
    private lateinit var fbc : FireBaseConnector
    private val viewModel: ChatRoomViewModel by viewModel()

    val messages = ArrayList<MessageModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatRoomBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

//        cameraButton = binding.cameraButton
        binding.cameraButton.setOnClickListener {
            onCameraButtonPress()
        }

        fbc = FireBaseConnector()
        fbc.initializeConnection(this.applicationContext)
        fbc.setMessageUpdateCallback(this)
        fbc.getAllMessageData()

        adapter = MessageAdapter(this, messages, this)
        binding.chatRoomRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatRoomRecyclerView.adapter = adapter
//        setUpObserver()

        intent.getParcelableExtra<Uri>("filteredImage")?.let{
                imageUri ->
            adapter.addMessage(
                MessageModel("You set a pic!", imageUri,
                    FireBaseConnector.userUniqueId
                )
            )
            fbc.uploadImageToFirebase(imageUri)
        }
        fetchAndSetUserName()

    }
    fun setUpObserver(){
        viewModel.messages.observe(this) {
            printLog("Getting Messages! : ${it.toString()}")
            val messageList = it ?: return@observe

//            binding.imageFiltersProgressBar.visibility =
//                if (imageFilterDataState.isLoading) View.VISIBLE else View.GONE
            messageList.let{ messages->
                val adapter = MessageAdapter(this, ArrayList(messages), this)
                binding.chatRoomRecyclerView.adapter = adapter
                binding.chatRoomRecyclerView.layoutManager = LinearLayoutManager(this)
            }
        }

        viewModel.getAllMessages()
    }
    override fun onResume(){
        super.onResume()
        intent.getParcelableExtra<Uri>("filteredImage")?.let{
                imageUri ->
            adapter.addMessage(MessageModel("hi stupid!", imageUri, FireBaseConnector.userUniqueId))
            fbc.uploadImageToFirebase(imageUri)
        }
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
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK && data != null) {
//            val capturedImagePath = data?.getStringExtra("capturedImage")
////            messages.add()
//            var imageUri =  Uri.fromFile(File(capturedImagePath))
            Uri.parse(data.extras?.getString(EditImageActivity.KEY_FILTERED_IMAGE_URI)).let{
                Log.d("Walld","recieve${it.toString()}")
                if(it!=null) {
                    adapter.addMessage(
                        MessageModel(
                            "Hi there sajan!",
                            it,
                            FireBaseConnector.userUniqueId
                        )
                    )
                    fbc.uploadImageToFirebase(it)
                }
            }
        }
    }

    fun fetchAndSetUserName(){
        fbc.getPatnerUserName(){ partnerUserName ->
            binding.partnerUserNameText.text = partnerUserName
        }
    }

    override fun onItemLongClicked(item: MessageModel) {
        binding.imagePreview.setImageURI(item.imageUri)
        binding.imagePreview.show()
        binding.imagePreview.setOnClickListener{
            binding.imagePreview.hide()
        }
    }

}