package com.example.cowall

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.cowall.data.CachedMessage
import com.example.cowall.data.MessageModel
import com.example.cowall.data.MessageStatus
import com.example.cowall.data.User
import com.example.cowall.data.UserChat
import com.example.cowall.utilities.printLog
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.database.*
import com.google.firebase.database.database
import com.google.firebase.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import com.google.gson.Gson

class FireBaseConnector : ChatConnector {

    private var messageUpdateCallback: ChatConnector.MessageUpdateCallback? = null
    private var uploadCallback: ChatConnector.UploadCallback? = null
    private var roomChatListener: ValueEventListener? = null
    private var roomChatRef: DatabaseReference? = null

    private lateinit var database: FirebaseDatabase
    private lateinit var storageRef: FirebaseStorage
    private lateinit var localCache: ChatLocalCache
    lateinit var context: Context

    companion object {
        private const val LOG_TAG = "CoWall"
        private const val SYNC_MESSAGE_LIMIT = 30
        private var persistenceEnabled = false

        lateinit var userUniqueId: String
        lateinit var roomId: String
        var partnerUserName: String = ""
        var partnerEmail: String = ""
        var partnerAvatarUrl: String = ""

        fun setUniqueIds(userId: String, room: String) {
            userUniqueId = userId
            roomId = room
        }

        fun getUserUniqueID(): String = userUniqueId
    }

    override fun setMessageUpdateCallback(callback: ChatConnector.MessageUpdateCallback?) {
        messageUpdateCallback = callback
    }

    override fun setUploadCallback(callback: ChatConnector.UploadCallback?) {
        uploadCallback = callback
    }

    override fun initializeConnection(context: Context) {
        this.context = context
        localCache = ChatLocalCache(context)
        enablePersistence()
        storageRef = Firebase.storage
        database = Firebase.database
    }

    private fun enablePersistence() {
        if (persistenceEnabled) return
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            persistenceEnabled = true
        } catch (e: Exception) {
            // Already enabled from a prior call — safe to ignore
        }
    }

    override fun lookForUpdates(path: String) {
        val ref = database.reference.child("roomChat/$roomId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(LOG_TAG, "roomChat update received")
                if (!snapshot.hasChildren()) return
                val lastSnapshot = snapshot.children.last()
                val messageKey = lastSnapshot.key ?: ""
                val lastMessage = lastSnapshot.getValue(String::class.java) ?: return
                val userChat = Gson().fromJson(lastMessage, UserChat::class.java) ?: return
                if (userChat.userUniqueId != userUniqueId) {
                    when (userChat.type) {
                        "text" -> {
                            val msg = MessageModel(
                                message = userChat.text ?: "",
                                senderId = userChat.userUniqueId,
                                timestamp = userChat.timestamp,
                                messageKey = messageKey,
                                status = MessageStatus.SENT,
                                replyToKey = userChat.replyToKey,
                                replyPreview = userChat.replyPreview
                            )
                            CoroutineScope(Dispatchers.IO).launch {
                                localCache.append(roomId, CachedMessage(
                                    messageKey = messageKey,
                                    senderId = userChat.userUniqueId,
                                    type = "text",
                                    text = userChat.text ?: "",
                                    timestamp = userChat.timestamp,
                                    replyToKey = userChat.replyToKey,
                                    replyPreview = userChat.replyPreview
                                ))
                            }
                            messageUpdateCallback?.onMessageUpdated(msg)
                        }
                        else -> getImageFromFirebase(
                            userChat.uri, userChat.userUniqueId,
                            messageKey = messageKey,
                            replyToKey = userChat.replyToKey,
                            replyPreview = userChat.replyPreview,
                            timestamp = userChat.timestamp
                        )
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(LOG_TAG, "lookForUpdates cancelled: $error")
            }
        }
        roomChatRef = ref
        roomChatListener = listener
        ref.addValueEventListener(listener)
    }

    override fun stopListening() {
        roomChatListener?.let { roomChatRef?.removeEventListener(it) }
        roomChatListener = null
        roomChatRef = null
    }

    override fun setWallpaper(imagePath: String) {
        val sharedPref = context.getSharedPreferences("cowall", Context.MODE_PRIVATE)
        val target = sharedPref.getString("wallpaperTarget", "lock") ?: "lock"
        com.example.cowall.utilities.WallpaperHelper.setWallpaper(context, imagePath, target, partnerUserName)
    }

    fun getImageFromFirebase(
        imgPath: String,
        senderId: String = "",
        setAsWallpaper: Boolean = true,
        messageKey: String = "",
        replyToKey: String? = null,
        replyPreview: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (imgPath.startsWith("https://")) {
            downloadImageFromUrl(imgPath, senderId, setAsWallpaper, messageKey, replyToKey, replyPreview, timestamp)
            return
        }

        val imageRef = storageRef.reference.child("file/$roomId").child(imgPath)
        val storageDir = File(context.getExternalFilesDir(null), "images").also { it.mkdirs() }
        val localFile = File(storageDir, imgPath)

        if (localFile.exists()) {
            dispatchMessage(localFile, senderId, messageKey, replyToKey, replyPreview, remoteUri = imgPath, timestamp = timestamp)
            if (setAsWallpaper) setWallpaper(localFile.absolutePath)
            return
        }

        imageRef.getFile(localFile)
            .addOnSuccessListener {
                dispatchMessage(localFile, senderId, messageKey, replyToKey, replyPreview, remoteUri = imgPath, timestamp = timestamp)
                if (setAsWallpaper) setWallpaper(localFile.absolutePath)
            }
            .addOnFailureListener { e ->
                Log.e(LOG_TAG, "Image download failed for $imgPath: $e")
            }
    }

    private fun downloadImageFromUrl(
        url: String,
        senderId: String,
        setAsWallpaper: Boolean,
        messageKey: String = "",
        replyToKey: String? = null,
        replyPreview: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val storageDir = File(context.getExternalFilesDir(null), "images").also { it.mkdirs() }
                // Deterministic filename so the same URL always maps to the same local file,
                // preventing duplicate downloads and duplicate wallpaper history entries.
                val fileName = urlToFilename(url)
                val localFile = File(storageDir, fileName)

                if (localFile.exists()) {
                    dispatchMessage(localFile, senderId, messageKey, replyToKey, replyPreview, remoteUri = url, timestamp = timestamp)
                    if (setAsWallpaper) setWallpaper(localFile.absolutePath)
                    return@launch
                }

                val authToken = if (isDriveUrl(url)) getDriveAccessToken() else null
                val bytes = fetchUrlBytes(url, authToken)
                if (bytes == null) {
                    Log.e(LOG_TAG, "downloadImageFromUrl: empty response for $url")
                    return@launch
                }

                localFile.writeBytes(bytes)
                dispatchMessage(localFile, senderId, messageKey, replyToKey, replyPreview, remoteUri = url, timestamp = timestamp)
                if (setAsWallpaper) setWallpaper(localFile.absolutePath)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "downloadImageFromUrl failed: $e")
            }
        }
    }

    private fun urlToFilename(url: String): String {
        val bytes = java.security.MessageDigest.getInstance("MD5").digest(url.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) } + ".jpg"
    }

    private fun isDriveUrl(url: String): Boolean =
        url.contains("drive.usercontent.google.com") || url.contains("googleapis.com/drive")

    private fun getDriveAccessToken(): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return try {
            GoogleAuthUtil.getToken(
                context,
                account.account!!,
                "oauth2:https://www.googleapis.com/auth/drive.readonly"
            )
        } catch (e: Exception) {
            Log.e(LOG_TAG, "getDriveAccessToken error: $e")
            null
        }
    }

    /**
     * Follows redirects manually across domains (HttpURLConnection won't auto-follow
     * cross-domain redirects on all Android versions).
     */
    private fun fetchUrlBytes(urlStr: String, authToken: String? = null, redirectsRemaining: Int = 5): ByteArray? {
        if (redirectsRemaining == 0) {
            Log.e(LOG_TAG, "fetchUrlBytes: too many redirects for $urlStr")
            return null
        }
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = false  // handle manually to support cross-domain
            authToken?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        return when (val code = conn.responseCode) {
            in 200..299 -> conn.inputStream.use { it.readBytes() }
            301, 302, 303, 307, 308 -> {
                val location = conn.getHeaderField("Location")
                if (location.isNullOrEmpty()) {
                    Log.e(LOG_TAG, "fetchUrlBytes: redirect with no Location header")
                    null
                } else {
                    Log.d(LOG_TAG, "fetchUrlBytes: redirect $code → $location")
                    fetchUrlBytes(location, authToken, redirectsRemaining - 1)
                }
            }
            else -> {
                Log.e(LOG_TAG, "fetchUrlBytes: HTTP $code for $urlStr")
                null
            }
        }
    }

    private fun dispatchMessage(
        localFile: File,
        senderId: String,
        messageKey: String = "",
        replyToKey: String? = null,
        replyPreview: String? = null,
        remoteUri: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val senderName = partnerUserName.ifEmpty { senderId }
        val msg = if (senderId != userUniqueId) "$senderName set a pic!" else "You set a pic!"
        messageUpdateCallback?.onMessageUpdated(
            MessageModel(
                message = msg,
                imageUri = Uri.fromFile(localFile),
                senderId = senderId,
                timestamp = timestamp,
                messageKey = messageKey,
                status = MessageStatus.SENT,
                replyToKey = replyToKey,
                replyPreview = replyPreview
            )
        )
        if (messageKey.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                localCache.append(roomId, CachedMessage(
                    messageKey = messageKey,
                    senderId = senderId,
                    type = "image",
                    localImagePath = localFile.absolutePath,
                    remoteImageUri = remoteUri,
                    timestamp = timestamp,
                    replyToKey = replyToKey,
                    replyPreview = replyPreview
                ))
            }
        }
    }

    override fun sendMessage(childPath: String, msg: String) {
        database.reference.child(childPath).push().setValue(msg) { error, _ ->
            if (error != null) Log.e(LOG_TAG, "sendMessage failed: $error")
        }
    }

    override fun setProperty(childPath: String, value: String) {
        database.reference.child(childPath).setValue(value) { error, _ ->
            if (error != null) Log.e(LOG_TAG, "setProperty failed: $error")
        }
    }

    fun sendUri(uri: String, localFilePath: String? = null, replyToKey: String? = null, replyPreview: String? = null) {
        val ref = database.getReference("roomChat/$roomId").push()
        val key = ref.key ?: ""
        val timestamp = System.currentTimeMillis()
        val json = Gson().toJson(UserChat(userUniqueId, uri, timestamp, type = "image", replyToKey = replyToKey, replyPreview = replyPreview))
        ref.setValue(json)
        if (key.isNotEmpty() && localFilePath != null) {
            CoroutineScope(Dispatchers.IO).launch {
                localCache.append(roomId, CachedMessage(
                    messageKey = key,
                    senderId = userUniqueId,
                    type = "image",
                    localImagePath = localFilePath,
                    remoteImageUri = uri,
                    timestamp = timestamp,
                    replyToKey = replyToKey,
                    replyPreview = replyPreview
                ))
            }
        }
    }

    override fun sendTextMessage(text: String, replyToKey: String?, replyPreview: String?) {
        val ref = database.getReference("roomChat/$roomId").push()
        val key = ref.key ?: ""
        val timestamp = System.currentTimeMillis()
        val json = Gson().toJson(UserChat(userUniqueId, "", timestamp, type = "text", text = text, replyToKey = replyToKey, replyPreview = replyPreview))
        ref.setValue(json)
        val msg = MessageModel(
            message = text,
            senderId = userUniqueId,
            timestamp = timestamp,
            messageKey = key,
            replyToKey = replyToKey,
            replyPreview = replyPreview
        )
        CoroutineScope(Dispatchers.IO).launch {
            localCache.append(roomId, CachedMessage(
                messageKey = key,
                senderId = userUniqueId,
                type = "text",
                text = text,
                timestamp = timestamp,
                replyToKey = replyToKey,
                replyPreview = replyPreview
            ))
        }
        messageUpdateCallback?.onMessageUpdated(msg)
    }

    override fun uploadImageToDrive(selectedImage: Uri) {
        val fileName = "${UUID.randomUUID()}.jpg"
        val compressedUri = compressImage(selectedImage, fileName)
        if (compressedUri == null) {
            uploadCallback?.onUploadFailure("Failed to compress image")
            return
        }
        val localFilePath = File(context.getExternalFilesDir(null), "images/$fileName").absolutePath
        uploadCallback?.onUploadStarted()
        val driveManager = GoogleDriveManager()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                    ?: run {
                        Log.e(LOG_TAG, "uploadImageToDrive: no signed-in account")
                        withContext(Dispatchers.Main) {
                            uploadCallback?.onUploadFailure("Not signed in. Please re-login.")
                        }
                        return@launch
                    }
                val token = GoogleAuthUtil.getToken(
                    context,
                    account.account!!,
                    "oauth2:https://www.googleapis.com/auth/drive.file"
                )
                val isSecureShare = context.getSharedPreferences("cowall", Context.MODE_PRIVATE)
                    .getBoolean("secureImageShare", true)
                val resolvedPartnerEmail = if (isSecureShare) {
                    partnerEmail.ifEmpty {
                        fetchPartnerEmailSuspend().also { if (!it.isNullOrEmpty()) partnerEmail = it }
                    }
                } else null
                val fileId = driveManager.uploadImageToDrive(context, token, compressedUri, fileName, resolvedPartnerEmail)
                    ?: run {
                        Log.e(LOG_TAG, "uploadImageToDrive: Drive upload returned null")
                        withContext(Dispatchers.Main) {
                            uploadCallback?.onUploadFailure("Upload failed. Please try again.")
                        }
                        return@launch
                    }
                val downloadUrl = driveManager.getDirectDownloadUrl(fileId)
                Log.d(LOG_TAG, "Drive upload success, sharing URL: $downloadUrl")
                sendUri(downloadUrl, localFilePath = localFilePath)
                withContext(Dispatchers.Main) { uploadCallback?.onUploadSuccess() }
            } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                Log.w(LOG_TAG, "Drive token needs re-consent: ${e.message}")
                withContext(Dispatchers.Main) {
                    uploadCallback?.onUploadFailure("Google Drive access expired. Please re-login.")
                }
            } catch (e: java.io.IOException) {
                Log.e(LOG_TAG, "uploadImageToDrive: network error: $e")
                withContext(Dispatchers.Main) {
                    uploadCallback?.onUploadFailure("No internet connection. Please check your network and try again.")
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "uploadImageToDrive error: $e")
                withContext(Dispatchers.Main) {
                    uploadCallback?.onUploadFailure("Upload failed: ${e.localizedMessage}")
                }
            }
        }
    }

    private suspend fun fetchPartnerEmailSuspend(): String? =
        suspendCancellableCoroutine { continuation ->
            getPartnerEmail { email ->
                if (continuation.isActive) continuation.resume(email)
            }
        }

    fun getPartnerEmail(callback: (String?) -> Unit) {
        database.getReference("chatRooms/$roomId/participants")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val partnerId = child.key ?: continue
                        if (partnerId == userUniqueId) continue
                        database.reference.child("userEmail").child(partnerId)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snap: DataSnapshot) {
                                    callback(snap.getValue(String::class.java))
                                }
                                override fun onCancelled(error: DatabaseError) {
                                    callback(null)
                                }
                            })
                        return
                    }
                    callback(null)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(LOG_TAG, "getPartnerEmail cancelled: $error")
                    callback(null)
                }
            })
    }

    private fun compressImage(selectedImage: Uri, imagePath: String): Uri? {
        return try {
            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, selectedImage)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream)
            val outDir = File(context.getExternalFilesDir(null), "images").also { it.mkdirs() }
            val outFile = File(outDir, imagePath)
            outFile.outputStream().use { it.write(stream.toByteArray()) }
            Uri.fromFile(outFile)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Image compression failed: $e")
            null
        }
    }

    override fun getMessageCount(child: String): Int {
        var count = 0
        database.reference.child(child).get()
            .addOnSuccessListener { count = it.childrenCount.toInt() }
        return count
    }

    // Clears the Room cache first (on IO), then fetches fresh data from Firebase on the main thread.
    override fun getAllMessageData() {
        CoroutineScope(Dispatchers.IO).launch {
            localCache.clear(roomId)
            withContext(Dispatchers.Main) { fetchAllFromFirebase() }
        }
    }

    private fun fetchAllFromFirebase() {
        database.reference.child("roomChat/$roomId")
            .orderByKey()
            .limitToLast(SYNC_MESSAGE_LIMIT)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.hasChildren()) return
                    for (messageSnapshot in snapshot.children) {
                        val messageKey = messageSnapshot.key ?: ""
                        val message = messageSnapshot.getValue(String::class.java) ?: continue
                        val userChat = Gson().fromJson(message, UserChat::class.java) ?: continue
                        when (userChat.type) {
                            "text" -> {
                                val msg = MessageModel(
                                    message = userChat.text ?: "",
                                    senderId = userChat.userUniqueId,
                                    timestamp = userChat.timestamp,
                                    messageKey = messageKey,
                                    status = MessageStatus.SENT,
                                    replyToKey = userChat.replyToKey,
                                    replyPreview = userChat.replyPreview
                                )
                                CoroutineScope(Dispatchers.IO).launch {
                                    localCache.append(roomId, CachedMessage(
                                        messageKey = messageKey,
                                        senderId = userChat.userUniqueId,
                                        type = "text",
                                        text = userChat.text ?: "",
                                        timestamp = userChat.timestamp,
                                        replyToKey = userChat.replyToKey,
                                        replyPreview = userChat.replyPreview
                                    ))
                                }
                                messageUpdateCallback?.onMessageUpdated(msg)
                            }
                            else -> {
                                // Pre-cache with remote URI; local path filled in by dispatchMessage after download.
                                CoroutineScope(Dispatchers.IO).launch {
                                    localCache.append(roomId, CachedMessage(
                                        messageKey = messageKey,
                                        senderId = userChat.userUniqueId,
                                        type = "image",
                                        remoteImageUri = userChat.uri,
                                        timestamp = userChat.timestamp,
                                        replyToKey = userChat.replyToKey,
                                        replyPreview = userChat.replyPreview
                                    ))
                                }
                                getImageFromFirebase(
                                    userChat.uri, userChat.userUniqueId, false, messageKey,
                                    userChat.replyToKey, userChat.replyPreview, userChat.timestamp
                                )
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(LOG_TAG, "getAllMessageData cancelled: $error")
                }
            })
    }

    override fun loadCachedMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            val cached = localCache.load(roomId)
            val readyMessages = mutableListOf<MessageModel>()
            val pendingDownloads = mutableListOf<CachedMessage>()

            for (msg in cached) {
                when (msg.type) {
                    "text" -> readyMessages.add(MessageModel(
                        message = msg.text,
                        senderId = msg.senderId,
                        timestamp = msg.timestamp,
                        messageKey = msg.messageKey,
                        status = MessageStatus.SENT,
                        replyToKey = msg.replyToKey,
                        replyPreview = msg.replyPreview
                    ))
                    "image" -> {
                        val localFile = msg.localImagePath?.let { File(it) }
                        if (localFile != null && localFile.exists()) {
                            val label = if (msg.senderId == userUniqueId) "You set a pic!"
                                        else "${partnerUserName.ifEmpty { "Partner" }} set a pic!"
                            readyMessages.add(MessageModel(
                                message = label,
                                imageUri = Uri.fromFile(localFile),
                                senderId = msg.senderId,
                                timestamp = msg.timestamp,
                                messageKey = msg.messageKey,
                                status = MessageStatus.SENT,
                                replyToKey = msg.replyToKey,
                                replyPreview = msg.replyPreview
                            ))
                        } else if (!msg.remoteImageUri.isNullOrEmpty()) {
                            pendingDownloads.add(msg)
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                messageUpdateCallback?.onMessageGet(readyMessages)
                // Re-download images whose local files are missing (storage cleared, first install, etc.)
                // Must run on main thread — Firebase Storage listeners require a Looper.
                pendingDownloads.forEach { msg ->
                    getImageFromFirebase(
                        msg.remoteImageUri!!, msg.senderId, false,
                        msg.messageKey, msg.replyToKey, msg.replyPreview, msg.timestamp
                    )
                }
            }
        }
    }

    override fun clearMessageCache() {
        try {
            val currentRoomId = roomId
            CoroutineScope(Dispatchers.IO).launch { localCache.clear(currentRoomId) }
        } catch (_: UninitializedPropertyAccessException) {}
    }

    override fun getPatnerUserName(callback: (String?) -> Unit) {
        val participantsRef = database.getReference("chatRooms/$roomId/participants")
        var listener: ValueEventListener? = null
        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val partnerId = child.key ?: continue
                    if (partnerId == userUniqueId) continue
                    // Partner found — stop watching participants.
                    participantsRef.removeEventListener(listener!!)
                    database.reference.child("userName").child(partnerId)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snap: DataSnapshot) {
                                val name = snap.getValue(String::class.java) ?: "Partner"
                                if (partnerUserName.isEmpty()) {
                                    partnerUserName = name
                                }
                                callback(name)
                            }
                            override fun onCancelled(error: DatabaseError) {
                                callback("Partner")
                            }
                        })
                    return
                }
                // Partner not in participants yet — keep listening until they appear.
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(LOG_TAG, "getPartnerUserName cancelled: $error")
                callback(null)
            }
        }
        participantsRef.addValueEventListener(listener)
    }

    override fun getPartnerAvatarUrl(callback: (String?) -> Unit) {
        val participantsRef = database.getReference("chatRooms/$roomId/participants")
        var listener: ValueEventListener? = null
        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val partnerId = child.key ?: continue
                    if (partnerId == userUniqueId) continue
                    participantsRef.removeEventListener(listener!!)
                    database.reference.child("userProfiles").child(partnerId).child("avatarUrl")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snap: DataSnapshot) {
                                val url = snap.getValue(String::class.java)
                                if (!url.isNullOrEmpty()) partnerAvatarUrl = url
                                callback(url)
                            }
                            override fun onCancelled(error: DatabaseError) {
                                callback(null)
                            }
                        })
                    return
                }
            }
            override fun onCancelled(error: DatabaseError) {
                callback(null)
            }
        }
        participantsRef.addValueEventListener(listener)
    }
}
