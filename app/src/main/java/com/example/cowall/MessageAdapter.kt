package com.example.cowall

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cowall.activities.PhotoViewActivity
import com.example.cowall.data.MessageModel
import com.example.cowall.data.MessageStatus
import com.example.cowall.utilities.EmojiUtils
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val context: Context,
    private val messageList: ArrayList<MessageModel>,
    private val actionListener: MessageActionListener? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface MessageActionListener {
        fun onLongPress(message: MessageModel, view: View, position: Int)
        fun onImageClick(message: MessageModel, imageView: ImageView)
        fun onReplyPreviewClick(replyToKey: String)
    }

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    private val bubblePadding = (6 * context.resources.displayMetrics.density).toInt()

    private fun applyBubbleBackground(container: LinearLayout, drawableRes: Int) {
        container.setBackgroundResource(drawableRes)
        container.setPadding(bubblePadding, bubblePadding, bubblePadding, bubblePadding)
    }

    // Display list includes date headers interleaved with messages
    private data class DisplayItem(
        val type: Int,
        val message: MessageModel? = null,
        val dateLabel: String? = null
    )

    private val displayItems = mutableListOf<DisplayItem>()

    private fun rebuildDisplayList() {
        displayItems.clear()
        val calendar = Calendar.getInstance()
        var lastDateKey = ""

        for (msg in messageList) {
            calendar.timeInMillis = msg.timestamp
            val dateKey = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
            if (dateKey != lastDateKey) {
                val label = formatDateLabel(msg.timestamp)
                displayItems.add(DisplayItem(VIEW_TYPE_DATE_HEADER, dateLabel = label))
                lastDateKey = dateKey
            }
            val userId = FireBaseConnector.getUserUniqueID()
            val viewType = if (msg.senderId == userId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
            displayItems.add(DisplayItem(viewType, message = msg))
        }
    }

    private fun formatDateLabel(timestamp: Long): String {
        return when {
            DateUtils.isToday(timestamp) -> "Today"
            DateUtils.isToday(timestamp + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
            else -> dateFormat.format(Date(timestamp))
        }
    }

    inner class SendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.sentImageView)
        val messageText: TextView = itemView.findViewById(R.id.textViewSend)
        val captionText: TextView = itemView.findViewById(R.id.captionText)
        val timestamp: TextView = itemView.findViewById(R.id.timestampText)
        val uploadProgress: ProgressBar = itemView.findViewById(R.id.uploadProgress)
        val statusIcon: ImageView = itemView.findViewById(R.id.statusIcon)
        val reactionsText: TextView = itemView.findViewById(R.id.reactionsText)
        val replyPreviewContainer: LinearLayout = itemView.findViewById(R.id.replyPreviewContainer)
        val replyPreviewText: TextView = itemView.findViewById(R.id.replyPreviewText)
        val bubbleContainer: LinearLayout = itemView.findViewById(R.id.bubbleContainer)
    }

    inner class ReceiveViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.receiveImageView)
        val messageText: TextView = itemView.findViewById(R.id.textViewReceive)
        val captionText: TextView = itemView.findViewById(R.id.captionText)
        val senderName: TextView = itemView.findViewById(R.id.senderNameText)
        val timestamp: TextView = itemView.findViewById(R.id.timestampText)
        val reactionsText: TextView = itemView.findViewById(R.id.reactionsText)
        val replyPreviewContainer: LinearLayout = itemView.findViewById(R.id.replyPreviewContainer)
        val replyPreviewText: TextView = itemView.findViewById(R.id.replyPreviewText)
        val bubbleContainer: LinearLayout = itemView.findViewById(R.id.bubbleContainer)
        val partnerAvatar: ImageView = itemView.findViewById(R.id.partnerAvatarImage)
    }

    inner class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateText: TextView = itemView.findViewById(R.id.dateHeaderText)
    }

    override fun getItemViewType(position: Int): Int {
        return displayItems[position].type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> SendViewHolder(LayoutInflater.from(context).inflate(R.layout.sent, parent, false))
            VIEW_TYPE_RECEIVED -> ReceiveViewHolder(LayoutInflater.from(context).inflate(R.layout.receive, parent, false))
            VIEW_TYPE_DATE_HEADER -> DateHeaderViewHolder(LayoutInflater.from(context).inflate(R.layout.item_date_header, parent, false))
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = displayItems[position]

        when (holder) {
            is DateHeaderViewHolder -> {
                holder.dateText.text = item.dateLabel
            }
            is SendViewHolder -> {
                val msg = item.message ?: return
                val timeStr = timeFormat.format(Date(msg.timestamp))
                holder.timestamp.text = timeStr
                bindReplyPreview(msg, holder.replyPreviewContainer, holder.replyPreviewText)
                bindReactions(msg, holder.reactionsText)
                bindUploadStatus(msg, holder.uploadProgress, holder.statusIcon)

                if (msg.imageUri != null) {
                    holder.image.visibility = View.VISIBLE
                    holder.messageText.visibility = View.GONE
                    holder.image.transitionName = "photo_${msg.messageKey.ifEmpty { position.toString() }}"
                    Glide.with(context)
                        .load(msg.imageUri)
                        .placeholder(R.drawable.new_wallpaper)
                        .centerCrop()
                        .into(holder.image)
                    holder.image.setOnClickListener {
                        if (actionListener != null) {
                            actionListener.onImageClick(msg, holder.image)
                        } else {
                            openFullscreen(msg, holder.image)
                        }
                    }
                    if (!msg.caption.isNullOrEmpty()) {
                        holder.captionText.text = msg.caption
                        holder.captionText.visibility = View.VISIBLE
                    } else {
                        holder.captionText.visibility = View.GONE
                    }
                    applyBubbleBackground(holder.bubbleContainer, R.drawable.sent_background)
                } else {
                    holder.image.visibility = View.GONE
                    holder.captionText.visibility = View.GONE
                    holder.messageText.visibility = View.VISIBLE
                    holder.messageText.text = msg.message
                    if (EmojiUtils.isEmojiOnly(msg.message)) {
                        holder.messageText.textSize = 40f
                        holder.bubbleContainer.background = null
                        holder.bubbleContainer.setPadding(bubblePadding, 0, bubblePadding, 0)
                    } else {
                        holder.messageText.textSize = 15f
                        applyBubbleBackground(holder.bubbleContainer, R.drawable.sent_background)
                    }
                }

                holder.bubbleContainer.setOnLongClickListener {
                    actionListener?.onLongPress(msg, it, position)
                    true
                }
            }

            is ReceiveViewHolder -> {
                val msg = item.message ?: return
                val timeStr = timeFormat.format(Date(msg.timestamp))
                holder.timestamp.text = timeStr
                val partnerName = FireBaseConnector.partnerUserName
                holder.senderName.text = partnerName
                val avatarUrl = FireBaseConnector.partnerAvatarUrl
                if (avatarUrl.isNotEmpty()) {
                    holder.partnerAvatar.visibility = View.VISIBLE
                    Glide.with(context).load(avatarUrl).circleCrop().into(holder.partnerAvatar)
                } else {
                    holder.partnerAvatar.visibility = View.GONE
                }
                bindReplyPreview(msg, holder.replyPreviewContainer, holder.replyPreviewText)
                bindReactions(msg, holder.reactionsText)

                if (msg.imageUri != null) {
                    holder.image.visibility = View.VISIBLE
                    holder.messageText.visibility = View.GONE
                    holder.image.transitionName = "photo_${msg.messageKey.ifEmpty { position.toString() }}"
                    Glide.with(context)
                        .load(msg.imageUri)
                        .placeholder(R.drawable.new_wallpaper)
                        .centerCrop()
                        .into(holder.image)
                    holder.image.setOnClickListener {
                        if (actionListener != null) {
                            actionListener.onImageClick(msg, holder.image)
                        } else {
                            openFullscreen(msg, holder.image)
                        }
                    }
                    if (!msg.caption.isNullOrEmpty()) {
                        holder.captionText.text = msg.caption
                        holder.captionText.visibility = View.VISIBLE
                    } else {
                        holder.captionText.visibility = View.GONE
                    }
                    applyBubbleBackground(holder.bubbleContainer, R.drawable.receive_background)
                } else {
                    holder.image.visibility = View.GONE
                    holder.captionText.visibility = View.GONE
                    holder.messageText.visibility = View.VISIBLE
                    holder.messageText.text = msg.message
                    if (EmojiUtils.isEmojiOnly(msg.message)) {
                        holder.messageText.textSize = 40f
                        holder.bubbleContainer.background = null
                        holder.bubbleContainer.setPadding(bubblePadding, 0, bubblePadding, 0)
                    } else {
                        holder.messageText.textSize = 15f
                        applyBubbleBackground(holder.bubbleContainer, R.drawable.receive_background)
                    }
                }

                holder.bubbleContainer.setOnLongClickListener {
                    actionListener?.onLongPress(msg, it, position)
                    true
                }
            }
        }
    }

    private fun bindReplyPreview(msg: MessageModel, container: LinearLayout, textView: TextView) {
        if (msg.replyPreview != null) {
            container.visibility = View.VISIBLE
            textView.text = msg.replyPreview
            container.setOnClickListener {
                msg.replyToKey?.let { key -> actionListener?.onReplyPreviewClick(key) }
            }
        } else {
            container.visibility = View.GONE
        }
    }

    private fun bindUploadStatus(msg: MessageModel, spinner: ProgressBar, icon: ImageView) {
        when {
            msg.imageUri != null && msg.status == MessageStatus.SENDING -> {
                spinner.visibility = View.VISIBLE
                icon.visibility = View.GONE
            }
            msg.imageUri != null && msg.status == MessageStatus.FAILED -> {
                spinner.visibility = View.GONE
                icon.visibility = View.VISIBLE
                icon.setImageResource(R.drawable.ic_upload_failed)
                icon.imageTintList = null
            }
            else -> {
                spinner.visibility = View.GONE
                icon.visibility = View.VISIBLE
                icon.setImageResource(R.drawable.ic_done)
                icon.imageTintList = ContextCompat.getColorStateList(context, R.color.status_tick)
            }
        }
    }

    private fun bindReactions(msg: MessageModel, reactionsView: TextView) {
        if (msg.reactions.isNotEmpty()) {
            val emojiCounts = msg.reactions.values.groupingBy { it }.eachCount()
            val display = emojiCounts.entries.joinToString("  ") { "${it.key} ${it.value}" }
            reactionsView.text = display
            reactionsView.visibility = View.VISIBLE
        } else {
            reactionsView.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = displayItems.size

    fun addMessage(message: MessageModel) {
        if (message.messageKey.isNotEmpty()) {
            val existingIndex = messageList.indexOfFirst { it.messageKey == message.messageKey }
            if (existingIndex >= 0) {
                // Update in place — image URI may have resolved after initial placeholder add
                messageList[existingIndex] = message
            } else {
                messageList.add(message)
            }
        } else {
            messageList.add(message)
        }
        rebuildDisplayList()
        notifyDataSetChanged()
    }

    fun addAllMessages(messages: List<MessageModel>) {
        for (message in messages) {
            if (message.messageKey.isNotEmpty()) {
                val existingIndex = messageList.indexOfFirst { it.messageKey == message.messageKey }
                if (existingIndex < 0) messageList.add(message)
            } else {
                messageList.add(message)
            }
        }
        rebuildDisplayList()
        notifyDataSetChanged()
    }

    fun updateReactions(messageKey: String, reactions: Map<String, String>) {
        val index = messageList.indexOfFirst { it.messageKey == messageKey }
        if (index >= 0) {
            messageList[index] = messageList[index].copy(reactions = reactions)
            rebuildDisplayList()
            // Find the display index for this message
            val displayIndex = displayItems.indexOfFirst { it.message?.messageKey == messageKey }
            if (displayIndex >= 0) notifyItemChanged(displayIndex)
        }
    }

    fun getMessageByKey(messageKey: String): MessageModel? {
        return messageList.firstOrNull { it.messageKey == messageKey }
    }

    fun getPositionByKey(messageKey: String): Int {
        return displayItems.indexOfFirst { it.message?.messageKey == messageKey }
    }

    fun getDisplayItemCount(): Int = displayItems.size

    fun getMessageAtDisplayPosition(position: Int): MessageModel? =
        displayItems.getOrNull(position)?.message

    fun updateImageUploadStatus(timestamp: Long, status: MessageStatus) {
        val index = messageList.indexOfFirst {
            it.imageUri != null && it.timestamp == timestamp && it.messageKey.isEmpty()
        }
        if (index >= 0) {
            messageList[index] = messageList[index].copy(status = status)
            rebuildDisplayList()
            notifyDataSetChanged()
        }
    }

    private fun openFullscreen(msg: MessageModel, imageView: ImageView) {
        val uri = msg.imageUri ?: return
        val transitionName = imageView.transitionName
        val intent = Intent(context, PhotoViewActivity::class.java).apply {
            putExtra(PhotoViewActivity.EXTRA_IMAGE_URI, uri)
            putExtra(PhotoViewActivity.EXTRA_TRANSITION_NAME, transitionName)
        }
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            context as Activity,
            imageView,
            transitionName
        )
        context.startActivity(intent, options.toBundle())
    }

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_DATE_HEADER = 3
    }
}
