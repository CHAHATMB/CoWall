package com.example.cowall

import android.content.Context
import kotlin.random.Random

class MockAuthProvider : AuthProvider {

    override fun isSignedIn(context: Context): Boolean {
        populateMockSession(context)
        return true
    }

    private fun populateMockSession(context: Context) {
        val sharedPref = context.getSharedPreferences("cowall", Context.MODE_PRIVATE)
        if (!sharedPref.contains("userUniqueId")) {
            sharedPref.edit()
                .putString("userUniqueId", "mock_user_${Random.nextInt(10000000, 99999999)}")
                .apply()
        }
        if (!sharedPref.contains("joinedRoomId")) {
            val roomId = sharedPref.getString("roomId", null)
                ?: "mock_room_${Random.nextInt(10000000, 99999999)}"
            sharedPref.edit()
                .putString("roomId", roomId)
                .putString("joinedRoomId", roomId)
                .putString("userName", "Mock User")
                .putBoolean("onboardingComplete", true)
                .apply()
        }
        val userId = sharedPref.getString("userUniqueId", "mock_user")!!
        val roomId = sharedPref.getString("joinedRoomId", "mock_room")!!
        FireBaseConnector.setUniqueIds(userId, roomId)
    }
}
