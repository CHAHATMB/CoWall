package com.example.cowall

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.example.cowall.utilities.Coroutines
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.cowall.activities.ChatRoomActivity
import com.example.cowall.data.AppDatabase
import com.example.cowall.data.RoomJoinRecord
import com.example.cowall.data.User
import com.google.android.gms.auth.api.signin.GoogleSignIn
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
    private var pulseAnimator: ObjectAnimator? = null
    private var waitingTimerJob: Job? = null

    companion object {
        private const val LOG_TAG = "CoWall"
        private const val PREF_WAITING_STATE = "waitingStatus"
        private const val PREF_USERNAME = "userName"
        private const val PREF_JOINED_ROOM = "joinedRoomId"
        private const val PREF_PARTNER_NAME = "partnerName"
        private const val PREF_PENDING_INVITE = "pendingInviteCode"
        private const val PREF_WAITING_START_TIME = "waitingStartTime"
        private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.example.cowall"
        private const val TIMER_TICK_MS = 30_000L
        const val EXTRA_GOOGLE_NAME = "extra_google_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateOrJoinRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseconn.initializeConnection(applicationContext)
        sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        userUniqueId = getOrGenerateId("userUniqueId")

        binding.submitButton.setOnClickListener { onSubmitPressed() }
        binding.exitButtonCS.setOnClickListener { onExitPressed() }
        binding.shareButtonCS.setOnClickListener { shareSpaceCode() }

        watchEditText()
        prefillGoogleNameIfNeeded()
        // Try to restore session from Firebase before generating a new room.
        // If the user signed out but their room is still active, this navigates them back.
        attemptEmailRecovery()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::roomRef.isInitialized) {
            partnerListener?.let { roomRef.removeEventListener(it) }
        }
        stopPulseAnimation()
        stopWaitingTimer()
    }

    private fun watchEditText() {
        binding.editRoomId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.submitButton.text = if (s?.trim().isNullOrEmpty()) "Create Space" else "Join Space"
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun onSubmitPressed() {
        // Guard against pressing submit while email recovery is still in progress.
        if (!::roomId.isInitialized) return

        val userName = binding.yourName.text.toString().trim()
        if (userName.isEmpty()) {
            binding.yourName.error = "Please enter your name"
            binding.yourName.requestFocus()
            return
        }
        binding.yourName.error = null
        firebaseconn.setProperty("userName/$userUniqueId", userName)
        GoogleSignIn.getLastSignedInAccount(this)?.email?.let { email ->
            firebaseconn.setProperty("userEmail/$userUniqueId", email)
        }
        sharedPref.edit().putString(PREF_USERNAME, userName).apply()

        val partnerCode = binding.editRoomId.text.toString().trim().replace("-", "")
        if (partnerCode.isEmpty()) {
            sharedPref.edit()
                .putString(PREF_WAITING_STATE, "true")
                .putLong(PREF_WAITING_START_TIME, System.currentTimeMillis())
                .apply()
            showWaiting(true)
            showSuccessSnackbar("Space created! Share your code with your partner")
            lookForPartner()
        } else {
            if (partnerCode.length != 8 || !partnerCode.all { it.isDigit() }) {
                binding.editRoomId.error = "Enter a valid space code (e.g. 1234-5678)"
                binding.editRoomId.requestFocus()
                return
            }
            binding.editRoomId.error = null
            val loadingDialog = showLoadingDialog("Joining space...")
            firebaseconn.sendMessage("roomId/$partnerCode", userUniqueId)
            joinChatRoom(User(userUniqueId, userName), partnerCode)
            sharedPref.edit()
                .putString(PREF_JOINED_ROOM, partnerCode)
                .putString(PREF_WAITING_STATE, "false")
                .apply()
            FireBaseConnector.setUniqueIds(userUniqueId, partnerCode)
            saveEmailMapping(userUniqueId, partnerCode)
            recordRoomJoin(partnerCode, partnerUserId = null)
            loadingDialog.dismiss()
            navigateToChatRoom()
        }
    }

    private fun onExitPressed() {
        if (::roomRef.isInitialized) {
            partnerListener?.let { roomRef.removeEventListener(it) }
        }
        partnerListener = null
        sharedPref.edit()
            .putString(PREF_WAITING_STATE, "false")
            .remove(PREF_WAITING_START_TIME)
            .apply()
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
        if (show) {
            binding.createRoomCL.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_out))
            binding.createRoomCL.hide()
            binding.waitingRoomCS.show()
            binding.waitingRoomCS.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
            startPulseAnimation()
            startWaitingTimer()
            displayQrCode()
        } else {
            binding.waitingRoomCS.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_out))
            binding.waitingRoomCS.hide()
            binding.createRoomCL.show()
            binding.createRoomCL.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
            stopPulseAnimation()
            stopWaitingTimer()
        }
    }

    private fun startPulseAnimation() {
        pulseAnimator = ObjectAnimator.ofFloat(binding.yourRoomIdCS, "alpha", 1f, 0.35f).apply {
            duration = 900
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        binding.yourRoomIdCS.alpha = 1f
    }

    private fun startWaitingTimer() {
        val startTime = sharedPref.getLong(PREF_WAITING_START_TIME, System.currentTimeMillis())
        waitingTimerJob = lifecycleScope.launch {
            while (true) {
                val minutes = (System.currentTimeMillis() - startTime) / 60_000
                binding.waitingTimerText.text = if (minutes < 1) {
                    getString(R.string.waiting_just_started)
                } else {
                    resources.getQuantityString(R.plurals.waiting_elapsed, minutes.toInt(), minutes.toInt())
                }
                delay(TIMER_TICK_MS)
            }
        }
    }

    private fun stopWaitingTimer() {
        waitingTimerJob?.cancel()
        waitingTimerJob = null
    }

    private fun displayQrCode() {
        if (!::roomId.isInitialized) return
        val content = "cowall://join?code=$roomId"
        Coroutines.io {
            val bitmap = generateQrBitmap(content, sizePx = 500)
            withContext(Dispatchers.Main) {
                binding.qrCodeImage.setImageBitmap(bitmap)
            }
        }
    }

    private fun generateQrBitmap(content: String, sizePx: Int): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val darkColor = Color.parseColor("#2D004D")
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) darkColor else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun shareSpaceCode() {
        if (!::roomId.isInitialized) return
        val myName = sharedPref.getString(PREF_USERNAME, null)
        val deepLink = "cowall://join?code=$roomId"
        val greeting = if (myName != null) {
            getString(R.string.share_greeting_named, myName)
        } else {
            getString(R.string.share_greeting_anonymous)
        }
        val body = getString(R.string.share_body, deepLink, PLAY_STORE_URL, formatSpaceCode(roomId))
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$greeting\n\n$body")
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
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
                        saveEmailMapping(userUniqueId, roomId)
                        recordRoomJoin(roomId, partnerUserId = userId)
                        partnerListener?.let { roomRef.removeEventListener(it) }
                        partnerListener = null
                        showSuccessSnackbar("Your partner joined!")
                        binding.root.postDelayed({ navigateToChatRoom() }, 1000)
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

    private fun formatSpaceCode(rawCode: String): String =
        if (rawCode.length == 8) "${rawCode.substring(0, 4)}-${rawCode.substring(4)}" else rawCode

    private fun prefillGoogleNameIfNeeded() {
        if (!sharedPref.contains(PREF_USERNAME)) {
            intent.getStringExtra(EXTRA_GOOGLE_NAME)?.let { binding.yourName.setText(it) }
        }
    }

    private fun recordRoomJoin(joinedRoomId: String, partnerUserId: String?) {
        val db = FirebaseDatabase.getInstance().reference

        fun saveRecord(partnerName: String) {
            Thread {
                AppDatabase.getInstance(applicationContext)
                    .roomJoinRecordDao()
                    .insert(RoomJoinRecord(
                        roomId = joinedRoomId,
                        timestamp = System.currentTimeMillis(),
                        partnerName = partnerName
                    ))
            }.start()
        }

        if (partnerUserId != null) {
            db.child("userName/$partnerUserId")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        saveRecord(snapshot.getValue(String::class.java) ?: "")
                    }
                    override fun onCancelled(error: DatabaseError) {
                        saveRecord("")
                    }
                })
        } else {
            // Joiner: find the creator among participants to resolve their display name
            db.child("chatRooms/$joinedRoomId/participants")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val creatorId = snapshot.children
                            .mapNotNull { it.key }
                            .firstOrNull { it != userUniqueId }
                        if (creatorId == null) { saveRecord(""); return }
                        db.child("userName/$creatorId")
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snap: DataSnapshot) {
                                    saveRecord(snap.getValue(String::class.java) ?: "")
                                }
                                override fun onCancelled(error: DatabaseError) {
                                    saveRecord("")
                                }
                            })
                    }
                    override fun onCancelled(error: DatabaseError) {
                        saveRecord("")
                    }
                })
        }
    }

    private fun navigateToChatRoom() {
        startActivity(Intent(this, ChatRoomActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    // ─── Email-based session recovery ─────────────────────────────

    /**
     * Attempts to restore a previous session using the signed-in Google email.
     * Only runs when there is no local room state (e.g. after sign-out).
     * Falls through to initRoom() if no valid session is found.
     */
    private fun attemptEmailRecovery() {
        val email = GoogleSignIn.getLastSignedInAccount(this)?.email
        if (email == null || sharedPref.contains("roomId")) {
            initRoom()
            return
        }
        val sanitized = email.replace(".", ",")
        val db = FirebaseDatabase.getInstance().reference
        db.child("emailToUserId/$sanitized")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val userId = snapshot.getValue(String::class.java)
                    if (userId == null) { initRoom(); return }
                    db.child("emailToRoomId/$sanitized")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snap: DataSnapshot) {
                                val recoveredRoomId = snap.getValue(String::class.java)
                                if (recoveredRoomId == null) { initRoom(); return }
                                validateRecovery(userId, recoveredRoomId)
                            }
                            override fun onCancelled(error: DatabaseError) { initRoom() }
                        })
                }
                override fun onCancelled(error: DatabaseError) { initRoom() }
            })
    }

    /**
     * Confirms the recovered userId is still an active participant before navigating.
     * Prevents restoring into a room that has since been dissolved.
     */
    private fun validateRecovery(userId: String, recoveredRoomId: String) {
        FirebaseDatabase.getInstance()
            .getReference("chatRooms/$recoveredRoomId/participants/$userId")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.getValue(Boolean::class.java) == true) {
                        sharedPref.edit()
                            .putString("userUniqueId", userId)
                            .putString("roomId", recoveredRoomId)
                            .putString("joinedRoomId", recoveredRoomId)
                            .apply()
                        userUniqueId = userId
                        FireBaseConnector.setUniqueIds(userId, recoveredRoomId)
                        navigateToChatRoom()
                    } else {
                        initRoom()
                    }
                }
                override fun onCancelled(error: DatabaseError) { initRoom() }
            })
    }

    /** Initialises the room ID and all UI elements that depend on it. */
    private fun initRoom() {
        roomId = getOrGenerateId("roomId")
        roomRef = FirebaseDatabase.getInstance().getReference("chatRooms/$roomId/participants")
        val formattedCode = formatSpaceCode(roomId)
        binding.yourRoomId.text = "your space: $formattedCode"
        binding.yourRoomIdCS.text = formattedCode
        binding.yourRoomId.setOnClickListener { copyToClipboard("Space Code", roomId) }
        binding.yourRoomIdCS.setOnClickListener { copyToClipboard("Space Code", roomId) }
        restoreUiState()
        applyPendingInviteCode()
    }

    /**
     * If the user arrived via a cowall://join?code=XXXX deep link, SplashScreenActivity
     * saves the code to SharedPrefs. Pick it up here and pre-fill the join field.
     * Only applies when not already in the waiting state.
     */
    private fun applyPendingInviteCode() {
        val isWaiting = sharedPref.getString(PREF_WAITING_STATE, "false") == "true"
        if (isWaiting) return
        val code = sharedPref.getString(PREF_PENDING_INVITE, null) ?: return
        sharedPref.edit().remove(PREF_PENDING_INVITE).apply()
        val cleaned = code.replace("-", "")
        binding.editRoomId.setText(
            if (cleaned.length == 8) formatSpaceCode(cleaned) else code
        )
    }

    /** Persists email→userId and email→roomId in Firebase for cross-device/sign-out recovery. */
    private fun saveEmailMapping(userId: String, targetRoomId: String) {
        val email = GoogleSignIn.getLastSignedInAccount(this)?.email ?: return
        val sanitized = email.replace(".", ",")
        val db = FirebaseDatabase.getInstance().reference
        db.child("emailToUserId/$sanitized").setValue(userId)
        db.child("emailToRoomId/$sanitized").setValue(targetRoomId)
    }
}
