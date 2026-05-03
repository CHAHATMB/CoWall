package com.example.cowall

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.cowall.activities.ChatRoomActivity
import com.example.cowall.databinding.ActivityLoginBinding
import com.example.cowall.utilities.isNetworkAvailable
import com.example.cowall.utilities.showErrorSnackbar
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    private val DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        binding.progressBar.visibility = View.GONE
        binding.btnGoogleSignIn.isEnabled = true
        binding.btnGoogleSignIn.text = "Continue with Google"

        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            Log.d("LoginActivity", "Signed in as: ${account.email}")
            navigateNext(account.displayName)
        } catch (e: ApiException) {
            Log.e("LoginActivity", "Google Sign-In failed: statusCode=${e.statusCode}")
            val errorMsg = when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Sign-in cancelled."
                GoogleSignInStatusCodes.NETWORK_ERROR -> "Network error. Please check your connection."
                GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS -> "Sign-in already in progress."
                GoogleSignInStatusCodes.SIGN_IN_FAILED -> "Sign-in failed. Please try again."
                12501 -> "Sign-in cancelled."
                else -> "Sign-in failed (error ${e.statusCode}). Please try again."
            }
            showErrorSnackbar(errorMsg)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGoogleSignIn.setOnClickListener { startGoogleSignIn() }
    }

    private fun startGoogleSignIn() {
        if (!isNetworkAvailable()) {
            showErrorSnackbar("No internet connection. Please check your network and try again.")
            return
        }
        binding.progressBar.visibility = View.VISIBLE
        binding.btnGoogleSignIn.isEnabled = false
        binding.btnGoogleSignIn.text = "Signing in..."

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_FILE_SCOPE), Scope(DRIVE_READONLY_SCOPE))
            .build()

        signInLauncher.launch(GoogleSignIn.getClient(this, gso).signInIntent)
    }

    private fun navigateNext(googleDisplayName: String? = null) {
        val sharedPref = getSharedPreferences("cowall", MODE_PRIVATE)
        val destination = if (sharedPref.contains("joinedRoomId")) {
            // Mark that we need a fresh Firebase sync — user just logged back in.
            sharedPref.edit().putBoolean("needsChatSync", true).apply()
            Intent(this, ChatRoomActivity::class.java)
        } else {
            Intent(this, CreateOrJoinRoom::class.java).apply {
                googleDisplayName?.let { putExtra(CreateOrJoinRoom.EXTRA_GOOGLE_NAME, it) }
            }
        }
        destination.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(destination)
        finish()
    }
}
