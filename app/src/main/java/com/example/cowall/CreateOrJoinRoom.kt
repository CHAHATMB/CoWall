package com.example.cowall

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import com.example.cowall.data.User
import com.example.cowall.data.UserChat
import com.example.cowall.databinding.ActivityCreateOrJoinRoomBinding
import com.example.cowall.databinding.ActivityMainBinding
import com.example.cowall.utilities.displayToast
import com.example.cowall.utilities.hide
import com.example.cowall.utilities.printLog
import com.example.cowall.utilities.show
import com.google.firebase.database.*
import com.google.gson.Gson
import org.koin.android.ext.android.bind
import kotlin.random.Random.Default.nextInt

class CreateOrJoinRoom : AppCompatActivity() {

    private lateinit var sharedPref : SharedPreferences
    private val sharedPrefString : String = "cowall"
    private lateinit var userUniqueId : String
    private lateinit var binding: ActivityCreateOrJoinRoomBinding
    private val Wall_Tag = "Walld"
    private  lateinit var firebaseconn : FireBaseConnector
    private lateinit var roomId : String
    val VARIABLE_WAITING_STATE = "waitingStatus"
    val VARIABLE_USERNAME = "userName"
    lateinit  var roomRef : DatabaseReference
    private var firstUserJoined = false
    private lateinit var valueEventListener: ValueEventListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCreateOrJoinRoomBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

            firebaseconn = FireBaseConnector()
            firebaseconn.initializeConnection(this.applicationContext)
//            editTextWatch()


        sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)

        userUniqueId = getOrGenerateId("userUniqueId")
        roomId = getOrGenerateId("roomId")
        binding.yourRoomId.text = "share your code: "+roomId
        binding.yourRoomIdCS.text = "share your code: "+roomId
        binding.submitButton.setOnClickListener{ roomIdSubmit() }
        binding.exitButtonCS.setOnClickListener{ onExitButtonPressed() }
        // Reference to the chat room in the database
        roomRef = FirebaseDatabase.getInstance().getReference("chatRooms/$roomId/participants")

        loadWaitingPage()
    }
    fun editTextWatch(){
        binding.editRoomId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if(binding.editRoomId.text.toString().trim().isEmpty()){
                    binding.submitButton.text = "Create"
                } else {
                    binding.submitButton.text = "Join"
                }
            }

            override fun afterTextChanged(s: Editable?) {
                if(binding.editRoomId.text.toString().trim().isEmpty()){
                    binding.submitButton.text = "Create"
                } else {
                    binding.submitButton.text = "Join"
                }
            }
        })

    }
    fun roomIdSubmit() {

        val userName = binding.yourName.text.toString()
        if(userName.trim().isEmpty()){
            displayToast("Please Enter Your Name!")
            return
        } else {
            firebaseconn.setProperty("userName/$userUniqueId",userName)
            with (sharedPref.edit()) {
                putString(VARIABLE_USERNAME, userName)
                apply()
            }
        }

        val partnerRoomId = binding.editRoomId.text.toString()
        if(partnerRoomId.trim().isEmpty()) {
            onExitButtonPressed(true)
            lookForPartnerToJoin("roomId/$roomId")
        } else {
            Log.d(Wall_Tag, "sending mesage to firebase ${partnerRoomId}")
            firebaseconn.sendMessage("roomId/$partnerRoomId", userUniqueId)
            val user = User(userUniqueId, userName)
            joinChatRoom(user)
            Log.d(Wall_Tag, "after sending")
            with(sharedPref.edit()) {
                putString("joinedRoomId", partnerRoomId)
                apply()
            }
            Log.d(Wall_Tag, "after sharedprefd")
            val intent = Intent(this.applicationContext, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun getOrGenerateId(identifierString : String) : String {

        var uniqueId : String
        if (sharedPref.contains(identifierString)) {
            uniqueId = sharedPref.getString(identifierString, "default_value").toString()
        } else {
            uniqueId = nextInt(11111111,99999999).toString()
            with (sharedPref.edit()) {
                putString(identifierString, uniqueId)
                apply()
            }
            if(identifierString == "roomId"){
                firebaseconn.sendMessage("roomId/${uniqueId}",userUniqueId)
            }
        }
        Log.d(Wall_Tag,identifierString + uniqueId)
        return uniqueId
    }
    private fun loadWaitingPage(){
        if (sharedPref.contains(VARIABLE_USERNAME)) {
            if( sharedPref.contains(VARIABLE_WAITING_STATE) && sharedPref.getString(VARIABLE_WAITING_STATE, "false").equals("true")){
                showWaitingPage()
            } else {
               binding.yourName.setText(sharedPref.getString(VARIABLE_USERNAME, "Your Named").toString())
                showWaitingPage(true)
            }
        } else {
            printLog("We are in else")
            showWaitingPage(true)
        }
    }
    private fun showWaitingPage(boolean: Boolean=false){
        if(boolean){
            binding.createRoomCL.show()
            binding.waitingRoomCS.hide()
        } else {
            binding.createRoomCL.hide()
            binding.waitingRoomCS.show()
        }
    }

    private fun onExitButtonPressed(boolean: Boolean = false){
        val value = if(boolean) "true" else "false"
        printLog("value s"+value)
        with (sharedPref.edit()) {
            putString(VARIABLE_WAITING_STATE, value)
            apply()
        }
        loadWaitingPage()
    }

    fun lookForPartnerToJoin(path: String) {

        // Add a ValueEventListener to listen for changes in participants
        valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Handle the event when participants change
                // Notify the room creator or update UI
                val participants = snapshot.children.map { it.key }.toList()
                printLog("Participants: $participants")
                if (!firstUserJoined) {
                    // Find out details of each participant
                    for (participantSnapshot in snapshot.children) {
                        val userId = participantSnapshot.key
                        val isJoined = participantSnapshot.value as Boolean

                        if (isJoined && userId != userUniqueId) {
                            joinChatRoom(User(userUniqueId,"jon dee"))
                            with(sharedPref.edit()) {
                                putString("joinedRoomId", roomId)
                                apply()
                            }
                            with(sharedPref.edit()) {
                                putString("partnerName", userId)
                                apply()
                            }
                            val intent = Intent(this@CreateOrJoinRoom.applicationContext, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                            break
                        }
                    }
                    // Stop listening after processing the first user
                    roomRef.removeEventListener(valueEventListener)
                    firstUserJoined = true



                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle the error
                Log.e("ChatRoomActivity", "Error: $error")
            }
        }
        roomRef.addValueEventListener(valueEventListener)
    }
    private fun joinChatRoom(user: User) {
        // Add the user to the participants list in the chat room
        roomRef.child(user.userId).setValue(true)
    }
}