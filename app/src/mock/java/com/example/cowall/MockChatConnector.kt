package com.example.cowall

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.cowall.data.MessageModel
import com.example.cowall.data.MessageStatus

class MockChatConnector : ChatConnector {

    private var messageUpdateCallback: ChatConnector.MessageUpdateCallback? = null
    private var uploadCallback: ChatConnector.UploadCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isListening = false

    companion object {
        private const val LOG_TAG = "MockChatConnector"
        private const val MOCK_PARTNER_ID = "mock_partner_99999999"
        private const val MOCK_PARTNER_NAME = "Mock Partner"
        private const val SIMULATED_DELAY_MS = 800L
        private const val UPLOAD_DELAY_MS = 1500L
        private const val PARTNER_REPLY_DELAY_MS = 3000L

        private val MOCK_MESSAGES = listOf(
            MessageModel(
                message = "Hey! Welcome to CoWall",
                senderId = MOCK_PARTNER_ID,
                timestamp = System.currentTimeMillis() - 3600_000,
                messageKey = "mock_msg_1",
                status = MessageStatus.DELIVERED
            ),
            MessageModel(
                message = "Set a wallpaper and I'll see it too!",
                senderId = MOCK_PARTNER_ID,
                timestamp = System.currentTimeMillis() - 3500_000,
                messageKey = "mock_msg_2",
                status = MessageStatus.DELIVERED
            ),
            MessageModel(
                message = "That sounds awesome, let me try",
                senderId = "SELF",
                timestamp = System.currentTimeMillis() - 3400_000,
                messageKey = "mock_msg_3",
                status = MessageStatus.SENT
            ),
            MessageModel(
                message = "This is a mock conversation for UI development",
                senderId = MOCK_PARTNER_ID,
                timestamp = System.currentTimeMillis() - 1800_000,
                messageKey = "mock_msg_4",
                status = MessageStatus.DELIVERED
            )
        )

        private val PARTNER_REPLIES = listOf(
            "Nice pic!",
            "Love that wallpaper!",
            "Looks great!",
            "Cool, let me set one too",
            "That's a vibe"
        )
    }

    override fun initializeConnection(context: Context) {
        Log.d(LOG_TAG, "Mock connection initialized")
    }

    override fun setMessageUpdateCallback(callback: ChatConnector.MessageUpdateCallback?) {
        messageUpdateCallback = callback
    }

    override fun setUploadCallback(callback: ChatConnector.UploadCallback?) {
        uploadCallback = callback
    }

    override fun lookForUpdates(path: String) {
        isListening = true
        Log.d(LOG_TAG, "Mock: listening for updates on $path")
    }

    override fun stopListening() {
        isListening = false
        handler.removeCallbacksAndMessages(null)
        Log.d(LOG_TAG, "Mock: stopped listening")
    }

    override fun sendTextMessage(text: String, replyToKey: String?, replyPreview: String?) {
        Log.d(LOG_TAG, "Mock sendTextMessage: $text")
        val key = "mock_msg_${System.currentTimeMillis()}"
        val msg = MessageModel(
            message = text,
            senderId = FireBaseConnector.userUniqueId,
            timestamp = System.currentTimeMillis(),
            messageKey = key,
            status = MessageStatus.SENT,
            replyToKey = replyToKey,
            replyPreview = replyPreview
        )
        messageUpdateCallback?.onMessageUpdated(msg)
        schedulePartnerReply()
    }

    override fun uploadImageToDrive(selectedImage: Uri) {
        Log.d(LOG_TAG, "Mock uploadImageToDrive: $selectedImage")
        uploadCallback?.onUploadStarted()

        handler.postDelayed({
            uploadCallback?.onUploadSuccess()
            schedulePartnerReply()
        }, UPLOAD_DELAY_MS)
    }

    override fun getAllMessageData() {
        Log.d(LOG_TAG, "Mock getAllMessageData")
        val selfId = FireBaseConnector.userUniqueId
        val messages = MOCK_MESSAGES.map { msg ->
            if (msg.senderId == "SELF") msg.copy(senderId = selfId) else msg
        }
        handler.postDelayed({
            messages.forEach { messageUpdateCallback?.onMessageUpdated(it) }
        }, SIMULATED_DELAY_MS)
    }

    override fun loadCachedMessages() {
        Log.d(LOG_TAG, "Mock loadCachedMessages — delegating to getAllMessageData")
        getAllMessageData()
    }

    override fun clearMessageCache() {
        Log.d(LOG_TAG, "Mock clearMessageCache (no-op)")
    }

    override fun getPatnerUserName(callback: (String?) -> Unit) {
        handler.postDelayed({
            FireBaseConnector.partnerUserName = MOCK_PARTNER_NAME
            callback(MOCK_PARTNER_NAME)
        }, SIMULATED_DELAY_MS / 2)
    }

    override fun sendMessage(childPath: String, msg: String) {
        Log.d(LOG_TAG, "Mock sendMessage: $childPath -> $msg")
    }

    override fun setProperty(childPath: String, value: String) {
        Log.d(LOG_TAG, "Mock setProperty: $childPath -> $value")
    }

    override fun getMessageCount(child: String): Int {
        Log.d(LOG_TAG, "Mock getMessageCount: $child")
        return 2
    }

    override fun setWallpaper(imagePath: String) {
        Log.d(LOG_TAG, "Mock setWallpaper: $imagePath (no-op)")
    }

    private fun schedulePartnerReply() {
        if (!isListening) return
        handler.postDelayed({
            val reply = PARTNER_REPLIES.random()
            val key = "mock_reply_${System.currentTimeMillis()}"
            val msg = MessageModel(
                message = reply,
                senderId = MOCK_PARTNER_ID,
                timestamp = System.currentTimeMillis(),
                messageKey = key,
                status = MessageStatus.DELIVERED
            )
            messageUpdateCallback?.onMessageUpdated(msg)
        }, PARTNER_REPLY_DELAY_MS)
    }
}
