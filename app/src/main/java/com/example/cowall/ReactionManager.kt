package com.example.cowall

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ReactionManager(
    private val database: FirebaseDatabase,
    private val userId: String,
    private val roomId: String
) {

    private var reactionsListener: ValueEventListener? = null
    private var reactionsRef: com.google.firebase.database.DatabaseReference? = null

    fun addReaction(messageKey: String, emoji: String) {
        database.getReference("reactions/$roomId/$messageKey/$userId").setValue(emoji)
    }

    fun removeReaction(messageKey: String) {
        database.getReference("reactions/$roomId/$messageKey/$userId").removeValue()
    }

    fun observeAllReactions(callback: (messageKey: String, reactions: Map<String, String>) -> Unit) {
        val ref = database.getReference("reactions/$roomId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (messageSnapshot in snapshot.children) {
                    val messageKey = messageSnapshot.key ?: continue
                    val reactions = mutableMapOf<String, String>()
                    for (reactionSnapshot in messageSnapshot.children) {
                        val reactorId = reactionSnapshot.key ?: continue
                        val emoji = reactionSnapshot.getValue(String::class.java) ?: continue
                        reactions[reactorId] = emoji
                    }
                    callback(messageKey, reactions)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CoWall", "observeAllReactions cancelled: $error")
            }
        }
        reactionsRef = ref
        reactionsListener = listener
        ref.addValueEventListener(listener)
    }

    fun stopObserving() {
        reactionsListener?.let { reactionsRef?.removeEventListener(it) }
        reactionsListener = null
        reactionsRef = null
    }

    companion object {
        val REACTION_EMOJIS = listOf("\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDD25", "\uD83D\uDC4D")
    }
}
