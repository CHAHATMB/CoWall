package com.example.cowall

import android.content.Context

interface AuthProvider {
    fun isSignedIn(context: Context): Boolean
}
