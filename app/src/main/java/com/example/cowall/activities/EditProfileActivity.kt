package com.example.cowall.activities

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.cowall.FireBaseConnector
import com.example.cowall.R
import com.example.cowall.databinding.ActivityEditProfileBinding
import com.example.cowall.utilities.showSuccessSnackbar
import com.example.cowall.utilities.showErrorSnackbar
import com.google.firebase.database.FirebaseDatabase

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private var selectedAvatarUri: Uri? = null

    private val avatarPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedAvatarUri = it
            Glide.with(this).load(it).circleCrop().into(binding.avatarImage)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadCurrentProfile()
        setupListeners()
    }

    private fun loadCurrentProfile() {
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        val userName = sharedPref.getString("userName", "") ?: ""
        val statusText = sharedPref.getString("statusText", "") ?: ""
        val avatarUrl = sharedPref.getString("avatarUrl", null)

        binding.nameInput.setText(userName)
        binding.statusInput.setText(statusText)

        if (avatarUrl != null) {
            Glide.with(this).load(avatarUrl).circleCrop().into(binding.avatarImage)
        }
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.avatarImage.setOnClickListener { avatarPicker.launch("image/*") }
        binding.avatarCameraOverlay.setOnClickListener { avatarPicker.launch("image/*") }

        binding.saveButton.setOnClickListener { saveProfile() }
    }

    private fun saveProfile() {
        val name = binding.nameInput.text.toString().trim()
        val status = binding.statusInput.text.toString().trim()

        if (name.isEmpty()) {
            showErrorSnackbar("Name cannot be empty")
            return
        }

        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        sharedPref.edit()
            .putString("userName", name)
            .putString("statusText", status)
            .apply()

        // Update Firebase
        try {
            val userId = FireBaseConnector.userUniqueId
            val database = FirebaseDatabase.getInstance()
            database.getReference("userName/$userId").setValue(name)
            database.getReference("userProfiles/$userId/statusText").setValue(status)
            database.getReference("userProfiles/$userId/displayName").setValue(name)

            if (selectedAvatarUri != null) {
                sharedPref.edit().putString("avatarUrl", selectedAvatarUri.toString()).apply()
                database.getReference("userProfiles/$userId/avatarUrl").setValue(selectedAvatarUri.toString())
            }
        } catch (e: Exception) {
            // Non-critical — local save already succeeded
        }

        showSuccessSnackbar("Profile updated!")
        binding.saveButton.postDelayed({ finish() }, 800)
    }
}
