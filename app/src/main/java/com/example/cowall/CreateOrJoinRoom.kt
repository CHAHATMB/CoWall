package com.example.cowall

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import com.example.cowall.activities.ChatRoomActivity
import com.example.cowall.data.User
import com.example.cowall.databinding.ActivityCreateOrJoinRoomBinding
import com.example.cowall.utilities.copyToClipboard
import com.example.cowall.utilities.displayToast
import com.example.cowall.utilities.hide
import com.example.cowall.utilities.show
import com.example.cowall.utilities.showErrorSnackbar
import com.example.cowall.utilities.showLoadingDialog
import com.example.cowall.utilities.showSuccessSnackbar
import com.google.firebase.database.*
import kotlin.random.Random.Default.nextInt
import org.koin.android.ext.android.inject

class CreateOrJoinRoom : AppCompatActivity() {

    private lateinit var sharedPref: SharedPreferences
    private lateinit var userUniqueId: String
    private lateinit var binding: ActivityCreateOrJoinRoomBinding
    private val firebaseconn: ChatConnector by inject()
    private lateinit var roomId: String
    private lateinit var roomRef: DatabaseReference
    private var partnerListener: ValueEventListener? = null

    companion object {
        private const val LOG_TAG = "CoWall"
        private const val PREF_WAITING_STATE = "waitingStatus"
        private const val PREF_USERNAME = "userName"
        private const val PREF_JOINED_ROOM = "joinedRoomId"
        private const val PREF_PARTNER_NAME = "partnerName"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateOrJoinRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseconn.initializeConnection(applicationContext)

        sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        userUniqueId = getOrGenerateId("userUniqueId")
        roomId = getOrGenerateId("roomId")
        roomRef = FirebaseDatabase.getInstance().getReference("chatRooms/$roomId/participants")

        binding.yourRoomId.text = "share your code: $roomId"
        binding.yourRoomIdCS.text = "share your code: $roomId"
        binding.submitButton.setOnClickListener { onSubmitPressed() }
        binding.exitButtonCS.setOnClickListener { onExitPressed() }

        binding.yourRoomId.setOnClickListener { copyToClipboard("Room Code", roomId) }
        binding.yourRoomIdCS.setOnClickListener { copyToClipboard("Room Code", roomId) }

        watchEditText()
        restoreUiState()
    }

    override fun onDestroy() {
        super.onDestroy()
        partnerListener?.let { roomRef.removeEventListener(it) }
    }

    private fun watchEditText() {
        binding.editRoomId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.submitButton.text = if (s?.trim().isNullOrEmpty()) "Create" else "Join"
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun onSubmitPressed() {
        val userName = binding.yourName.text.toString().trim()
        if (userName.isEmpty()) {
            binding.yourName.error = "Please enter your name"
            binding.yourName.requestFocus()
            return
        }
        binding.yourName.error = null
        firebaseconn.setProperty("userName/$userUniqueId", userName)
        sharedPref.edit().putString(PREF_USERNAME, userName).apply()

        val partnerCode = binding.editRoomId.text.toString().trim()
        if (partnerCode.isEmpty()) {
            sharedPref.edit().putString(PREF_WAITING_STATE, "true").apply()
            showWaiting(true)
            showSuccessSnackbar("Room created! Share your code with your partner")
            lookForPartner()
        } else {
            if (partnerCode.length != 8 || !partnerCode.all { it.isDigit() }) {
                binding.editRoomId.error = "Enter a valid 8-digit room code"
                binding.editRoomId.requestFocus()
                return
            }
            binding.editRoomId.error = null
            val loadingDialog = showLoadingDialog("Joining room...")
            firebaseconn.sendMessage("roomId/$partnerCode", userUniqueId)
            joinChatRoom(User(userUniqueId, userName), partnerCode)
            sharedPref.edit()
                .putString(PREF_JOINED_ROOM, partnerCode)
                .putString(PREF_WAITING_STATE, "false")
                .apply()
            FireBaseConnector.setUniqueIds(userUniqueId, partnerCode)
            loadingDialog.dismiss()
            navigateToChatRoom()
        }
    }

    private fun onExitPressed() {
        partnerListener?.let { roomRef.removeEventListener(it) }
        partnerListener = null
        sharedPref.edit().putString(PREF_WAITING_STATE, "false").apply()
        showWaiting(false)
    }

    private fun getOrGenerateId(key: String): String {
        if (sharedPref.contains(key)) {
            return sharedPref.getString(key, "")!!
        }
        val newId = nextInt(11111111, 99999999).toString()
        sharedPref.edit().putString(key, newId).apply()
        if (key == "roomId") {
            firebaseconn.sendMessage("roomId/$newId", userUniqueId)
        }
        return newId
    }

    private fun restoreUiState() {
        sharedPref.getString(PREF_USERNAME, null)?.let { binding.yourName.setText(it) }
        val isWaiting = sharedPref.getString(PREF_WAITING_STATE, "false") == "true"
        if (isWaiting) {
            showWaiting(true)
            lookForPartner()
        } else {
            showWaiting(false)
        }
    }

    private fun showWaiting(show: Boolean) {
        if (show) { binding.createRoomCL.hide(); binding.waitingRoomCS.show() }
        else { binding.createRoomCL.show(); binding.waitingRoomCS.hide() }
    }

    private fun lookForPartner() {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val userId = child.key ?: continue
                    val isJoined = child.value as? Boolean ?: continue
                    if (isJoined && userId != userUniqueId) {
                        val userName = sharedPref.getString(PREF_USERNAME, "") ?: ""
                        joinChatRoom(User(userUniqueId, userName))
                        sharedPref.edit()
                            .putString(PREF_JOINED_ROOM, roomId)
                            .putString(PREF_PARTNER_NAME, userId)
                            .putString(PREF_WAITING_STATE, "false")
                            .apply()
                        FireBaseConnector.setUniqueIds(userUniqueId, roomId)
                        partnerListener?.let { roomRef.removeEventListener(it) }
                        partnerListener = null
                        navigateToChatRoom()
                        return
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(LOG_TAG, "lookForPartner cancelled: $error")
            }
        }
        partnerListener = listener
        roomRef.addValueEventListener(listener)
    }

    private fun joinChatRoom(user: User, targetRoomId: String? = null) {
        val ref = if (targetRoomId != null) {
            FirebaseDatabase.getInstance().getReference("chatRooms/$targetRoomId/participants")
        } else {
            roomRef
        }
        ref.child(user.userId).setValue(true)
    }

    private fun navigateToChatRoom() {
        startActivity(Intent(this, ChatRoomActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
