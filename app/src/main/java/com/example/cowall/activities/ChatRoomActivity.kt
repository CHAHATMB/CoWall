package com.example.cowall.activities

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cowall.CameraActivity
import com.example.cowall.ChatConnector
import com.example.cowall.FireBaseConnector
import com.example.cowall.MessageAdapter
import com.example.cowall.PresenceManager
import com.example.cowall.R
import com.example.cowall.ReactionManager
import com.example.cowall.RunningService
import com.example.cowall.SwipeToReplyCallback
import com.example.cowall.data.MessageModel
import com.example.cowall.databinding.ActivityChatRoomBinding
import com.example.cowall.utilities.showErrorSnackbar
import com.example.cowall.utilities.showInfoSnackbar
import com.example.cowall.utilities.showSuccessSnackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.FirebaseDatabase
import org.koin.android.ext.android.inject

class ChatRoomActivity : AppCompatActivity(),
    ChatConnector.MessageUpdateCallback,
    ChatConnector.UploadCallback,
    MessageAdapter.MessageActionListener {

    private lateinit var binding: ActivityChatRoomBinding
    private lateinit var adapter: MessageAdapter
    private val fbc: ChatConnector by inject()
    private lateinit var layoutManager: LinearLayoutManager

    private var presenceManager: PresenceManager? = null
    private var reactionManager: ReactionManager? = null

    private val messages = ArrayList<MessageModel>()
    private val REQUEST_PERMISSION = 101

    private var replyingTo: MessageModel? = null
    private var isUploading = false

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uriString = result.data?.extras?.getString(EditImageActivity.KEY_FILTERED_IMAGE_URI)
            if (uriString.isNullOrEmpty()) return@registerForActivityResult
            val imageUri = Uri.parse(uriString)
            val caption = binding.captionInput.text.toString().trim().takeIf { it.isNotEmpty() }
            adapter.addMessage(MessageModel(
                message = "You set a pic!",
                imageUri = imageUri,
                senderId = FireBaseConnector.userUniqueId,
                caption = caption,
                replyToKey = replyingTo?.messageKey,
                replyPreview = replyingTo?.let { buildReplyPreview(it) }
            ))
            scrollToBottom()
            updateEmptyState()
            binding.captionInput.text?.clear()
            fbc.uploadImageToDrive(imageUri)
            clearReply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initUserIds()
        startBackgroundService()
        requestRequiredPermissions()
        setupRecyclerView()
        setupToolbar()
        setupInputBar()
        setupReplyBar()
        setupSwipeToReply()
        loadHistory()
        fetchPartnerName()
        setupPresence()
        setupReactions()
        setupConnectivityMonitor()
    }

    override fun onDestroy() {
        super.onDestroy()
        fbc.setMessageUpdateCallback(null)
        fbc.setUploadCallback(null)
        presenceManager?.goOffline()
        presenceManager?.stopObserving()
        reactionManager?.stopObserving()
        networkCallback?.let {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            cm.unregisterNetworkCallback(it)
        }
        networkCallback = null
    }

    private fun initUserIds() {
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        val userId = sharedPref.getString("userUniqueId", null)
        val roomId = sharedPref.getString("joinedRoomId", null)
        if (userId != null && roomId != null) {
            FireBaseConnector.setUniqueIds(userId, roomId)
        }
    }

    private fun startBackgroundService() {
        Intent(applicationContext, RunningService::class.java).also {
            it.action = RunningService.Actions.START.toString()
            startService(it)
        }
    }

    private fun requestRequiredPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SET_WALLPAPER)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.SET_WALLPAPER)
        }
        val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, readPermission) != PackageManager.PERMISSION_GRANTED) {
            needed.add(readPermission)
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQUEST_PERMISSION)
        }
    }

    private var unreadCount = 0

    private fun setupRecyclerView() {
        layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        adapter = MessageAdapter(this, messages, this)
        binding.chatRoomRecyclerView.layoutManager = layoutManager
        binding.chatRoomRecyclerView.adapter = adapter

        fbc.initializeConnection(applicationContext)
        fbc.setMessageUpdateCallback(this)
        fbc.setUploadCallback(this)

        binding.chatRoomRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val isAtBottom = !recyclerView.canScrollVertically(1)
                if (isAtBottom) {
                    binding.scrollToBottomContainer.visibility = View.GONE
                    unreadCount = 0
                    binding.unreadBadge.visibility = View.GONE
                } else if (dy < -10) {
                    binding.scrollToBottomContainer.visibility = View.VISIBLE
                }
            }
        })

        binding.scrollToBottomFab.setOnClickListener {
            scrollToBottom()
            binding.scrollToBottomContainer.visibility = View.GONE
            unreadCount = 0
            binding.unreadBadge.visibility = View.GONE
        }
    }

    private fun setupToolbar() {
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        binding.galleryButton.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun setupInputBar() {
        binding.cameraButton.setOnClickListener { openCamera() }
        binding.sendButton.setOnClickListener { sendTextMessage() }
        binding.emojiButton.setOnClickListener { showEmojiPicker() }

        binding.captionInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s?.trim().isNullOrEmpty()
                crossfadeButtons(showSend = hasText)
                presenceManager?.setTyping(hasText)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.captionInput.hint = "Type a message or tap \uD83D\uDCF7"
    }

    private fun setupReplyBar() {
        binding.replyBar.closeReplyButton.setOnClickListener {
            clearReply()
        }
    }

    private fun setupSwipeToReply() {
        val swipeCallback = SwipeToReplyCallback(messages) { message, _ ->
            setReplyingTo(message)
            adapter.notifyDataSetChanged()
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.chatRoomRecyclerView)
    }

    private fun setupPresence() {
        try {
            val userId = FireBaseConnector.userUniqueId
            val roomId = FireBaseConnector.roomId
            presenceManager = PresenceManager(FirebaseDatabase.getInstance(), userId, roomId)
            presenceManager?.goOnline()
            presenceManager?.observePartnerPresence { data ->
                runOnUiThread {
                    val status = PresenceManager.formatPresenceStatus(data)
                    if (status.isNotEmpty()) {
                        binding.partnerStatusText.text = status
                        binding.partnerStatusText.visibility = View.VISIBLE
                        val color = when {
                            data.typing -> ContextCompat.getColor(this, R.color.accent)
                            data.online -> ContextCompat.getColor(this, R.color.online_green)
                            else -> ContextCompat.getColor(this, R.color.text_secondary)
                        }
                        binding.partnerStatusText.setTextColor(color)
                    } else {
                        binding.partnerStatusText.visibility = View.GONE
                    }
                }
            }
        } catch (e: Exception) {
            // Presence is non-critical
        }
    }

    private fun setupReactions() {
        try {
            val userId = FireBaseConnector.userUniqueId
            val roomId = FireBaseConnector.roomId
            reactionManager = ReactionManager(FirebaseDatabase.getInstance(), userId, roomId)
            reactionManager?.observeAllReactions { messageKey, reactions ->
                runOnUiThread {
                    adapter.updateReactions(messageKey, reactions)
                }
            }
        } catch (e: Exception) {
            // Reactions are non-critical
        }
    }

    private fun sendTextMessage() {
        val text = binding.captionInput.text.toString().trim()
        if (text.isEmpty()) return
        binding.captionInput.text?.clear()
        fbc.sendTextMessage(
            text,
            replyToKey = replyingTo?.messageKey,
            replyPreview = replyingTo?.let { buildReplyPreview(it) }
        )
        clearReply()
        scrollToBottom()
        updateEmptyState()
    }

    private fun loadHistory() {
        fbc.getAllMessageData()
    }

    private fun fetchPartnerName() {
        fbc.getPatnerUserName { name ->
            runOnUiThread {
                binding.partnerUserNameText.text = name ?: "Partner"
            }
        }
    }

    // ─── MessageUpdateCallback ─────────────────────────────────────

    override fun onMessageUpdated(message: MessageModel) {
        runOnUiThread {
            adapter.addMessage(message)
            updateEmptyState()

            val isAtBottom = !binding.chatRoomRecyclerView.canScrollVertically(1)
            if (isAtBottom || message.senderId == FireBaseConnector.userUniqueId) {
                scrollToBottom()
            } else {
                unreadCount++
                binding.scrollToBottomContainer.visibility = View.VISIBLE
                binding.unreadBadge.text = unreadCount.toString()
                binding.unreadBadge.visibility = View.VISIBLE
            }

            if (message.senderId != FireBaseConnector.userUniqueId) {
                showInfoSnackbar("Wallpaper updated!")
            }
        }
    }

    override fun onMessageGet(messages: List<MessageModel>) {
        runOnUiThread {
            adapter.addAllMessages(messages)
            scrollToBottom()
            updateEmptyState()
        }
    }

    // ─── MessageActionListener ─────────────────────────────────────

    override fun onLongPress(message: MessageModel, view: View, position: Int) {
        showContextMenu(message, view, position)
    }

    override fun onImageClick(message: MessageModel, imageView: ImageView) {
        val uri = message.imageUri ?: return
        val transitionName = imageView.transitionName
        val intent = Intent(this, PhotoViewActivity::class.java).apply {
            putExtra(PhotoViewActivity.EXTRA_IMAGE_URI, uri)
            putExtra(PhotoViewActivity.EXTRA_TRANSITION_NAME, transitionName)
        }
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(this, imageView, transitionName)
        startActivity(intent, options.toBundle())
    }

    override fun onReplyPreviewClick(replyToKey: String) {
        val position = adapter.getPositionByKey(replyToKey)
        if (position >= 0) {
            binding.chatRoomRecyclerView.smoothScrollToPosition(position)
        }
    }

    // ─── Context menu & reactions ──────────────────────────────────

    private fun showContextMenu(message: MessageModel, anchorView: View, position: Int) {
        val items = mutableListOf<String>()
        items.add("React")
        items.add("Reply")
        if (message.imageUri == null && message.message.isNotEmpty()) {
            items.add("Copy text")
        }
        if (message.imageUri != null) {
            items.add("Set as wallpaper")
        }
        if (message.senderId == FireBaseConnector.userUniqueId && message.messageKey.isNotEmpty()) {
            items.add("Delete")
        }

        MaterialAlertDialogBuilder(this)
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "React" -> showReactionPicker(message, anchorView)
                    "Reply" -> setReplyingTo(message)
                    "Copy text" -> {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Message", message.message))
                        showSuccessSnackbar("Copied to clipboard")
                    }
                    "Set as wallpaper" -> {
                        message.imageUri?.let { uri ->
                            startActivity(Intent(this, WallpaperPreviewActivity::class.java).apply {
                                putExtra(WallpaperPreviewActivity.EXTRA_IMAGE_URI, uri)
                            })
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        }
                    }
                    "Delete" -> {
                        FirebaseDatabase.getInstance()
                            .getReference("roomChat/${FireBaseConnector.roomId}/${message.messageKey}")
                            .removeValue()
                        showInfoSnackbar("Message deleted")
                    }
                }
            }
            .show()
    }

    private fun showReactionPicker(message: MessageModel, anchorView: View) {
        val pickerView = LayoutInflater.from(this).inflate(R.layout.dialog_reaction_picker, null)
        val popup = PopupWindow(
            pickerView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 8f
            animationStyle = android.R.style.Animation_Dialog
        }

        val emojiIds = listOf(
            R.id.reactionHeart, R.id.reactionLaugh, R.id.reactionWow,
            R.id.reactionCry, R.id.reactionFire, R.id.reactionThumbsUp
        )
        val emojis = ReactionManager.REACTION_EMOJIS

        emojiIds.forEachIndexed { index, viewId ->
            pickerView.findViewById<TextView>(viewId).setOnClickListener {
                if (message.messageKey.isNotEmpty()) {
                    reactionManager?.addReaction(message.messageKey, emojis[index])
                }
                popup.dismiss()
            }
        }

        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        popup.showAtLocation(anchorView, Gravity.NO_GRAVITY, location[0], location[1] - pickerView.measuredHeight - 16)
    }

    private fun showEmojiPicker() {
        val emojis = listOf(
            "\uD83D\uDE00", "\uD83D\uDE02", "\uD83D\uDE0D", "\uD83E\uDD70", "\uD83D\uDE18",
            "\uD83D\uDE1C", "\uD83E\uDD23", "\uD83D\uDE4F", "\uD83D\uDC4D", "\u2764\uFE0F",
            "\uD83D\uDD25", "\uD83C\uDF89", "\uD83C\uDF1F", "\uD83D\uDCAF", "\uD83E\uDD17",
            "\uD83D\uDE2D", "\uD83D\uDE31", "\uD83D\uDE4C", "\uD83D\uDE0E", "\uD83E\uDD29"
        )

        val emojiDisplay = emojis.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Pick an emoji")
            .setItems(emojiDisplay) { _, which ->
                val cursor = binding.captionInput.selectionStart
                val editable = binding.captionInput.text
                editable?.insert(cursor.coerceAtLeast(0), emojis[which])
            }
            .show()
    }

    // ─── Reply handling ────────────────────────────────────────────

    private fun setReplyingTo(message: MessageModel) {
        replyingTo = message
        binding.replyBar.root.visibility = View.VISIBLE
        val senderName = if (message.senderId == FireBaseConnector.userUniqueId) "You" else FireBaseConnector.partnerUserName.ifEmpty { "Partner" }
        binding.replyBar.replyToSenderName.text = senderName
        binding.replyBar.replyToMessagePreview.text = buildReplyPreview(message)
        binding.captionInput.requestFocus()
    }

    private fun clearReply() {
        replyingTo = null
        binding.replyBar.root.visibility = View.GONE
    }

    private fun buildReplyPreview(message: MessageModel): String {
        return when {
            message.imageUri != null -> "\uD83D\uDCF7 Photo"
            message.message.isNotEmpty() -> message.message.take(50)
            else -> "Message"
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────

    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    private fun setupConnectivityMonitor() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                runOnUiThread {
                    binding.offlineBanner.animate().alpha(0f).setDuration(300).withEndAction {
                        binding.offlineBanner.visibility = View.GONE
                    }.start()
                }
            }

            override fun onLost(network: android.net.Network) {
                runOnUiThread {
                    binding.offlineBanner.visibility = View.VISIBLE
                    binding.offlineBanner.alpha = 0f
                    binding.offlineBanner.animate().alpha(1f).setDuration(300).start()
                }
            }
        }
        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback!!)

        // Check initial state
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (capabilities == null || !capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            binding.offlineBanner.visibility = View.VISIBLE
            binding.offlineBanner.alpha = 1f
        }
    }

    private var isSendMode = false

    private fun crossfadeButtons(showSend: Boolean) {
        if (showSend == isSendMode) return
        isSendMode = showSend
        val fadeIn = if (showSend) binding.sendButton else binding.cameraButton
        val fadeOut = if (showSend) binding.cameraButton else binding.sendButton
        fadeIn.alpha = 0f
        fadeIn.visibility = View.VISIBLE
        fadeIn.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start()
        fadeOut.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(150).withEndAction {
            fadeOut.visibility = View.GONE
            fadeOut.scaleX = 1f
            fadeOut.scaleY = 1f
        }.start()
    }

    private fun scrollToBottom() {
        val count = adapter.getDisplayItemCount()
        if (count > 0) {
            binding.chatRoomRecyclerView.scrollToPosition(count - 1)
        }
    }

    private fun updateEmptyState() {
        binding.emptyStateLayout.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openCamera() {
        cameraLauncher.launch(Intent(this, CameraActivity::class.java))
    }

    // ─── UploadCallback ────────────────────────────────────────────

    override fun onUploadStarted() {
        isUploading = true
        binding.cameraButton.isEnabled = false
        binding.cameraButton.alpha = 0.5f
        showInfoSnackbar("Sending photo...")
    }

    override fun onUploadSuccess() {
        isUploading = false
        binding.cameraButton.isEnabled = true
        binding.cameraButton.alpha = 1.0f
        val partnerName = FireBaseConnector.partnerUserName.ifEmpty { "Partner" }
        showSuccessSnackbar("Photo sent to $partnerName!")
    }

    override fun onUploadFailure(error: String) {
        isUploading = false
        binding.cameraButton.isEnabled = true
        binding.cameraButton.alpha = 1.0f
        showErrorSnackbar(error, "Retry") {
            openCamera()
        }
    }
}
