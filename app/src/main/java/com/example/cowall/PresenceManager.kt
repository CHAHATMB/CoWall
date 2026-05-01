package com.example.cowall

import android.text.format.DateUtils
import android.util.Log
import com.example.cowall.data.PresenceData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class PresenceManager(
    private val database: FirebaseDatabase,
    private val userId: String,
    private val roomId: String
) {

    private var partnerListener: ValueEventListener? = null
    private var partnerRef: com.google.firebase.database.DatabaseReference? = null

    private val myPresenceRef get() = database.getReference("presence/$roomId/$userId")

    fun goOnline() {
        myPresenceRef.child("online").setValue(true)
        myPresenceRef.child("lastSeen").setValue(System.currentTimeMillis())
        myPresenceRef.child("online").onDisconnect().setValue(false)
        myPresenceRef.child("lastSeen").onDisconnect().setValue(System.currentTimeMillis())
        myPresenceRef.child("typing").onDisconnect().setValue(false)
    }

    fun goOffline() {
        myPresenceRef.child("online").setValue(false)
        myPresenceRef.child("lastSeen").setValue(System.currentTimeMillis())
        myPresenceRef.child("typing").setValue(false)
    }

    fun setTyping(isTyping: Boolean) {
        myPresenceRef.child("typing").setValue(isTyping)
    }

    fun observePartnerPresence(callback: (PresenceData) -> Unit) {
        database.getReference("chatRooms/$roomId/participants")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val partnerId = child.key ?: continue
                        if (partnerId == userId) continue
                        startObservingPartner(partnerId, callback)
                        return
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("CoWall", "observePartnerPresence cancelled: $error")
                }
            })
    }

    private fun startObservingPartner(partnerId: String, callback: (PresenceData) -> Unit) {
        val ref = database.getReference("presence/$roomId/$partnerId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val online = snapshot.child("online").getValue(Boolean::class.java) ?: false
                val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                val typing = snapshot.child("typing").getValue(Boolean::class.java) ?: false
                callback(PresenceData(online, lastSeen, typing))
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CoWall", "partner presence cancelled: $error")
            }
        }
        partnerRef = ref
        partnerListener = listener
        ref.addValueEventListener(listener)
    }

    fun stopObserving() {
        partnerListener?.let { partnerRef?.removeEventListener(it) }
        partnerListener = null
        partnerRef = null
    }

    companion object {
        fun formatPresenceStatus(data: PresenceData): String {
            return when {
                data.typing -> "typing..."
                data.online -> "Online"
                data.lastSeen > 0 -> {
                    val relative = DateUtils.getRelativeTimeSpanString(
                        data.lastSeen,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE
                    )
                    "Last seen $relative"
                }
                else -> ""
            }
        }
    }
}
