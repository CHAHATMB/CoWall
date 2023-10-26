package com.example.cowall

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.example.cowall.databinding.ActivityCreateOrJoinRoomBinding
import com.example.cowall.databinding.ActivityMainBinding
import kotlin.random.Random.Default.nextInt

class CreateOrJoinRoom : AppCompatActivity() {

    private lateinit var sharedPref : SharedPreferences
    private val sharedPrefString : String = "cowall"
    private lateinit var userUniqueId : String
    private lateinit var binding: ActivityCreateOrJoinRoomBinding
    private val Wall_Tag = "Walld"
    private  lateinit var firebaseconn : FireBaseConnector
    private lateinit var roomId : String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCreateOrJoinRoomBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        firebaseconn = FireBaseConnector()
        firebaseconn.initializeConnection(this.applicationContext)

        sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)

        userUniqueId = getOrGenerateId("userUniqueId")

        roomId = getOrGenerateId("roomId")
        binding.yourRoomId.text = roomId
//        binding.button.setOnClickListener { viewModel.userClicked() }
        binding.submitButton.setOnClickListener{ roomIdSubmit() }

    }

    fun roomIdSubmit() {
        val roomId = binding.editRoomId.text.toString()
//        FirebaseDatabase.getInstance().getReference("messages")
        Log.d(Wall_Tag,"sending mesage to firebase ${roomId}")
//        Toast.makeText(this,"button pressed",Toast.LENGTH_LONG)
//        database = FirebaseDatabase.getInstance().getReference("messages")
//        database.child("Chat").push().setValue("Firebass wellscom sstring honi chahiye")
//        sendMessage("sendMessage","hey there")
        firebaseconn.sendMessage("roomId/$roomId",userUniqueId)
//        firebaseconn.
        Log.d(Wall_Tag,"after sending")
        with (sharedPref.edit()) {
            putString("joinedRoomId", roomId)
            apply()
        }
        Log.d(Wall_Tag, "after sharedprefd")
        val intent = Intent(this.applicationContext,MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun getOrGenerateId(identifierString : String) : String {
        // Writing to SharedPreferences

        var uniqueId : String
        if (sharedPref.contains(identifierString)) {
            uniqueId = sharedPref.getString(identifierString, "default_value").toString()
        } else {
//            uniqueId = ((11111111..99999999).random()).toString()
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

}