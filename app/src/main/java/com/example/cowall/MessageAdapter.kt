package com.example.cowall

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter(val context: Context, val messageList:ArrayList<MessageModel>): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    class SendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val sendMess = itemView.findViewById<TextView>(R.id.textViewSend)
        val sentImageView = itemView.findViewById<ImageView>(R.id.sentImageView)


    }
    class ReceiveViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val RecMess = itemView.findViewById<TextView>(R.id.textViewReceive)
        val receiveImageView = itemView.findViewById<ImageView>(R.id.receiveImageView)


    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if(viewType == 1){
            val view = LayoutInflater.from(context).inflate(R.layout.receive, parent, false)
            return ReceiveViewHolder(view)
        } else {
            val view = LayoutInflater.from(context).inflate(R.layout.sent, parent, false)
            return SendViewHolder(view)
        }

    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val currMess = messageList[position]
        if(holder.javaClass == SendViewHolder::class.java){
            val view = holder as SendViewHolder
            view.sendMess.text = currMess.message
            view.sentImageView.setImageURI(currMess.imageUri)
        } else{
            val view = holder as ReceiveViewHolder
            view.RecMess.text = currMess.message
            view.receiveImageView.setImageURI(currMess.imageUri)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val userId = FireBaseConnector.getUserUniqueID()
        val message = messageList[position]
        Log.d("Walld","type checking ${message.senderId}, $userId")
        if(message.senderId == userId){
            Log.d("Walld","matched!!")
            return 2
        } else {
            return 1
        }
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    fun addMessage(message: MessageModel) {
        messageList.add(message)
        notifyItemInserted(messageList.size - 1)
    }

    fun addAllMessages(messages: List<MessageModel>) {
        messageList.addAll(messages)
        notifyDataSetChanged()
    }

}