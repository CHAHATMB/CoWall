package com.example.cowall.activities

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.cowall.FireBaseConnector
import com.example.cowall.GoogleDriveManager
import com.example.cowall.widget.CoWallWidgetProvider
import com.example.cowall.R
import com.example.cowall.RunningService
import com.example.cowall.databinding.ActivitySettingsBinding
import com.example.cowall.CreateOrJoinRoom
import com.example.cowall.LoginActivity
import com.example.cowall.utilities.copyToClipboard
import com.example.cowall.utilities.showEmotionalConfirmDialog
import com.example.cowall.utilities.showConfirmDialog
import com.example.cowall.utilities.showErrorSnackbar
import com.example.cowall.utilities.showSuccessSnackbar
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { saveDefaultWallpaper(it) } }

    companion object {
        const val PREF_WALLPAPER_TARGET = "wallpaperTarget"
        const val TARGET_LOCK = "lock"
        const val TARGET_HOME = "home"
        const val TARGET_BOTH = "both"
        const val TARGET_STOP = "stop"

        const val PREF_AUTO_RESET_ENABLED = "autoResetEnabled"
        const val PREF_AUTO_RESET_DELAY_MINUTES = "autoResetDelayMinutes"
        const val PREF_DEFAULT_WALLPAPER_PATH = "defaultWallpaperPath"
        const val PREF_WALLPAPER_PENDING_RESET = "wallpaperPendingReset"
        const val DEFAULT_RESET_DELAY_MINUTES = 5
        const val DEFAULT_WALLPAPER_FILENAME = "default_wallpaper.jpg"

        const val PREF_UPDATES_PAUSED = "updatesPaused"
        const val PREF_SECURE_SHARE = "secureImageShare"

        const val PREF_LAST_PHOTO_SENT_TIMESTAMP = "last_photo_sent_timestamp"
        const val PREF_LAST_NOTIFICATION_SENT_TIMESTAMP = "last_notification_sent_timestamp"
        const val PREF_PARTNER_NAME = "partnerName"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadProfile()
        loadRoomInfo()
        loadWallpaperTarget()
        loadPauseState()
        loadSecureShareState()
        loadAutoResetSettings()
        loadAppearanceSettings()
        loadWidgetState()
        loadDriveStorageInfo()
        setupListeners()
    }

    private fun loadProfile() {
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        val userName = sharedPref.getString("userName", "Unknown") ?: "Unknown"
        binding.userNameText.text = userName

        val account = GoogleSignIn.getLastSignedInAccount(this)
        binding.userEmailText.text = account?.email ?: "Not signed in"

        val avatarUrl = sharedPref.getString("avatarUrl", null)
        if (avatarUrl != null) {
            binding.profileAvatarInitial.visibility = View.INVISIBLE
            Glide.with(this).load(avatarUrl).circleCrop().into(binding.profileAvatarImage)
        } else {
            binding.profileAvatarInitial.text = userName.firstOrNull()?.uppercase() ?: "?"
            binding.profileAvatarInitial.visibility = View.VISIBLE
        }

        try {
            val version = packageManager.getPackageInfo(packageName, 0).versionName
            binding.appVersionText.text = "CoWall v$version"
        } catch (_: Exception) {}
    }

    private fun loadRoomInfo() {
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        val roomCode = sharedPref.getString("joinedRoomId", null)
            ?: sharedPref.getString("roomId", null)
            ?: "N/A"
        binding.roomCodeText.text = roomCode

        val partnerName = FireBaseConnector.partnerUserName.ifEmpty { "Unknown" }
        binding.partnerNameText.text = "Partner: $partnerName"
    }

    private fun loadWallpaperTarget() {
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        when (sharedPref.getString(PREF_WALLPAPER_TARGET, TARGET_LOCK)) {
            TARGET_LOCK -> binding.wallpaperTargetGroup.check(R.id.btnLockScreen)
            TARGET_HOME -> binding.wallpaperTargetGroup.check(R.id.btnHomeScreen)
            TARGET_BOTH -> binding.wallpaperTargetGroup.check(R.id.btnBothScreens)
            TARGET_STOP -> binding.wallpaperTargetGroup.check(R.id.btnStopWallpaper)
        }
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.profileRow.setOnClickListener { openEditProfile() }

        binding.copyCodeButton.setOnClickListener {
            val code = binding.roomCodeText.text.toString()
            if (code != "N/A") copyToClipboard("Room Code", code)
        }

        binding.wallpaperTargetGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val target = when (checkedId) {
                R.id.btnLockScreen -> TARGET_LOCK
                R.id.btnHomeScreen -> TARGET_HOME
                R.id.btnBothScreens -> TARGET_BOTH
                R.id.btnStopWallpaper -> TARGET_STOP
                else -> TARGET_LOCK
            }
            getSharedPreferences("cowall", Context.MODE_PRIVATE)
                .edit().putString(PREF_WALLPAPER_TARGET, target).apply()
            val label = if (target == TARGET_STOP) "Stopped" else target.replaceFirstChar { it.uppercase() }
            showSuccessSnackbar("Wallpaper target: $label")
            updateAutoResetAvailability(target)
        }

        binding.leaveRoomButton.setOnClickListener {
            showEmotionalConfirmDialog(
                emoji = "\uD83D\uDC94",
                title = "Leave the Space?",
                message = "This will close the space for both of you. You'll need a new code to reconnect.",
                positiveLabel = "Leave",
                onConfirm = { leaveRoom() }
            )
        }

        binding.logoutButton.setOnClickListener {
            showConfirmDialog(
                title = "Sign Out",
                message = "You will need to sign in again to use CoWall.",
                positiveLabel = "Sign Out",
                onConfirm = { signOut() }
            )
        }

        binding.wallpaperHistoryRow.setOnClickListener {
            startActivity(Intent(this, WallpaperHistoryActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.spaceHistoryRow.setOnClickListener {
            startActivity(Intent(this, RoomJoinHistoryActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.themeRow.setOnClickListener {
            showThemeDialog()
        }

        binding.notificationSoundRow.setOnClickListener {
            showNotificationSoundDialog()
        }

        binding.pauseUpdatesSwitch.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("cowall", Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_UPDATES_PAUSED, isChecked).apply()
            notifyServiceRefresh()
            if (isChecked) {
                showSuccessSnackbar("Wallpaper updates paused")
            } else {
                showSuccessSnackbar("Wallpaper updates resumed")
            }
        }

        binding.secureShareSwitch.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("cowall", Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_SECURE_SHARE, isChecked).apply()
            if (isChecked) {
                showSuccessSnackbar("Secure sharing on — partner's Google sign-in required")
            } else {
                showSuccessSnackbar("Secure sharing off — images accessible via private link")
            }
        }

        binding.openDriveFolderRow.setOnClickListener { openDriveFolder() }

        binding.deleteOldImagesRow.setOnClickListener { showDeleteOldImagesDialog() }

        binding.autoResetSwitch.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("cowall", Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_AUTO_RESET_ENABLED, isChecked).apply()
            setAutoResetControlsVisible(isChecked)

            if (isChecked) {
                val path = getSharedPreferences("cowall", Context.MODE_PRIVATE)
                    .getString(PREF_DEFAULT_WALLPAPER_PATH, null)
                if (path == null) captureCurrentWallpaper()
            }
        }

        binding.delaySlider.addOnChangeListener { _, value, _ ->
            val minutes = value.toInt()
            binding.delayValueText.text = "$minutes min"
            getSharedPreferences("cowall", Context.MODE_PRIVATE)
                .edit().putInt(PREF_AUTO_RESET_DELAY_MINUTES, minutes).apply()
        }

        binding.chooseDefaultButton.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        binding.useCurrentButton.setOnClickListener {
            captureCurrentWallpaper()
        }

        binding.widgetControlGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val action = when (checkedId) {
                R.id.btnWidgetUpdate -> CoWallWidgetProvider.ACTION_WIDGET_UPDATE
                R.id.btnWidgetClear  -> CoWallWidgetProvider.ACTION_WIDGET_CLEAR
                R.id.btnWidgetStop   -> CoWallWidgetProvider.ACTION_WIDGET_STOP
                else -> return@addOnButtonCheckedListener
            }
            sendBroadcast(Intent(action).apply { setPackage(packageName) })
            val message = when (checkedId) {
                R.id.btnWidgetUpdate -> "Widget will show new wallpapers"
                R.id.btnWidgetClear  -> "Widget cleared"
                R.id.btnWidgetStop   -> "Widget updates stopped"
                else -> return@addOnButtonCheckedListener
            }
            showSuccessSnackbar(message)
        }
    }

    private fun leaveRoom() {
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        val roomId = sharedPref.getString("joinedRoomId", null)
            ?: sharedPref.getString("roomId", null)
        val userId = sharedPref.getString("userUniqueId", null)

        if (roomId != null && userId != null) {
            val db = FirebaseDatabase.getInstance().reference
            // Remove from participants — triggers the partner's room-monitor to auto-leave.
            db.child("chatRooms/$roomId/participants/$userId").removeValue()
            // Clear email mapping so re-sign-in doesn't restore a dissolved room.
            val email = GoogleSignIn.getLastSignedInAccount(this)?.email
            if (email != null) {
                val sanitized = email.replace(".", ",")
                db.child("emailToUserId/$sanitized").removeValue()
                db.child("emailToRoomId/$sanitized").removeValue()
            }
        }

        sharedPref.edit()
            .remove("joinedRoomId")
            .remove("roomId")
            .remove("waitingStatus")
            .remove("partnerName")
            .apply()

        startActivity(Intent(this, CreateOrJoinRoom::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    private fun signOut() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        val client = GoogleSignIn.getClient(this, gso)
        client.signOut().addOnCompleteListener {
            getSharedPreferences("cowall", Context.MODE_PRIVATE).edit().clear().apply()
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun loadAutoResetSettings() {
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        val enabled = sharedPref.getBoolean(PREF_AUTO_RESET_ENABLED, false)
        val delay = sharedPref.getInt(PREF_AUTO_RESET_DELAY_MINUTES, DEFAULT_RESET_DELAY_MINUTES)
        val defaultPath = sharedPref.getString(PREF_DEFAULT_WALLPAPER_PATH, null)
        val target = sharedPref.getString(PREF_WALLPAPER_TARGET, TARGET_LOCK) ?: TARGET_LOCK

        binding.autoResetSwitch.isChecked = enabled
        binding.delaySlider.value = delay.toFloat()
        binding.delayValueText.text = "$delay min"
        setAutoResetControlsVisible(enabled)
        updateAutoResetAvailability(target)

        if (defaultPath != null) {
            val bitmap = BitmapFactory.decodeFile(defaultPath)
            if (bitmap != null) binding.defaultWallpaperPreview.setImageBitmap(bitmap)
        }
    }

    private fun setAutoResetControlsVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        binding.delayContainer.visibility = visibility
        binding.defaultWallpaperContainer.visibility = visibility
    }

    private fun updateAutoResetAvailability(target: String) {
        val supported = target != TARGET_HOME && target != TARGET_STOP
        binding.autoResetSwitch.isEnabled = supported
        if (!supported && binding.autoResetSwitch.isChecked) {
            binding.autoResetSwitch.isChecked = false
        }
        binding.autoResetSubtitle.text = when {
            target == TARGET_STOP -> "Not available when wallpaper updates are stopped."
            target == TARGET_HOME -> "Not available when wallpaper target is Home only."
            else -> "Lock screen only. Resets wallpaper after you unlock."
        }
    }

    private fun captureCurrentWallpaper() {
        try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            val drawable = wallpaperManager.drawable
            if (drawable == null) {
                showErrorSnackbar("Unable to read current wallpaper. Choose from gallery.")
                return
            }
            val bitmap = when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                else -> {
                    val width = drawable.intrinsicWidth.coerceAtLeast(1)
                    val height = drawable.intrinsicHeight.coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
            }
            saveDefaultWallpaperBitmap(bitmap)
            showSuccessSnackbar("Current wallpaper saved as default")
        } catch (e: Exception) {
            Log.e("CoWall", "Failed to capture current wallpaper: $e")
            showErrorSnackbar("Failed to capture wallpaper. Choose from gallery.")
        }
    }

    private fun saveDefaultWallpaper(uri: android.net.Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) {
                showErrorSnackbar("Failed to decode selected image")
                return
            }
            saveDefaultWallpaperBitmap(bitmap)
            showSuccessSnackbar("Default wallpaper saved")
        } catch (e: Exception) {
            Log.e("CoWall", "Failed to save default wallpaper: $e")
            showErrorSnackbar("Failed to save wallpaper")
        }
    }

    private fun openEditProfile() {
        startActivity(Intent(this, EditProfileActivity::class.java))
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun showThemeDialog() {
        val themes = arrayOf("Dark", "Light", "System default")
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        val currentTheme = sharedPref.getInt("themeMode", 0)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Choose Theme")
            .setSingleChoiceItems(themes, currentTheme) { dialog, which ->
                sharedPref.edit().putInt("themeMode", which).apply()
                binding.currentThemeText.text = themes[which]
                val mode = when (which) {
                    1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                    2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                }
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
                dialog.dismiss()
            }
            .show()
    }

    private fun showNotificationSoundDialog() {
        val sounds = arrayOf("Default", "Gentle", "Pop", "Chime", "Silent")
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        val currentSound = sharedPref.getInt("notificationSound", 0)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Notification Sound")
            .setSingleChoiceItems(sounds, currentSound) { dialog, which ->
                sharedPref.edit().putInt("notificationSound", which).apply()
                binding.currentSoundText.text = sounds[which]
                dialog.dismiss()
                showSuccessSnackbar("Sound: ${sounds[which]}")
            }
            .show()
    }

    private fun loadAppearanceSettings() {
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        val themes = arrayOf("Dark", "Light", "System default")
        val sounds = arrayOf("Default", "Gentle", "Pop", "Chime", "Silent")
        binding.currentThemeText.text = themes[sharedPref.getInt("themeMode", 0)]
        binding.currentSoundText.text = sounds[sharedPref.getInt("notificationSound", 0)]
    }

    private fun loadPauseState() {
        val paused = getSharedPreferences("cowall", Context.MODE_PRIVATE)
            .getBoolean(PREF_UPDATES_PAUSED, false)
        binding.pauseUpdatesSwitch.isChecked = paused
    }

    private fun loadSecureShareState() {
        val enabled = getSharedPreferences("cowall", Context.MODE_PRIVATE)
            .getBoolean(PREF_SECURE_SHARE, true)
        binding.secureShareSwitch.isChecked = enabled
    }

    private fun loadWidgetState() {
        val state = getSharedPreferences("cowall", Context.MODE_PRIVATE)
            .getString(CoWallWidgetProvider.PREF_WIDGET_STATE, CoWallWidgetProvider.STATE_ACTIVE)
        val buttonId = when (state) {
            CoWallWidgetProvider.STATE_CLEARED -> R.id.btnWidgetClear
            CoWallWidgetProvider.STATE_STOPPED -> R.id.btnWidgetStop
            else -> R.id.btnWidgetUpdate
        }
        binding.widgetControlGroup.check(buttonId)
    }

    private fun notifyServiceRefresh() {
        Intent(this, RunningService::class.java).apply {
            action = RunningService.Actions.REFRESH_NOTIFICATION.toString()
        }.also { startService(it) }
    }

    private fun saveDefaultWallpaperBitmap(bitmap: Bitmap) {
        val file = File(filesDir, DEFAULT_WALLPAPER_FILENAME)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        getSharedPreferences("cowall", Context.MODE_PRIVATE)
            .edit().putString(PREF_DEFAULT_WALLPAPER_PATH, file.absolutePath).apply()
        binding.defaultWallpaperPreview.setImageBitmap(bitmap)
    }

    private fun loadDriveStorageInfo() {
        binding.driveFolderMetaText.text = "Loading..."
        lifecycleScope.launch {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(this@SettingsActivity) ?: run {
                    binding.driveFolderMetaText.text = "Sign in required"
                    return@launch
                }
                val token = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(
                        this@SettingsActivity,
                        account.account!!,
                        "oauth2:https://www.googleapis.com/auth/drive.file"
                    )
                }
                val meta = GoogleDriveManager().getFolderMetadata(this@SettingsActivity, token)
                binding.driveFolderMetaText.text = "${meta.fileCount} images · ${formatBytes(meta.totalBytes)}"
            } catch (e: Exception) {
                Log.e("CoWall", "loadDriveStorageInfo error: $e")
                binding.driveFolderMetaText.text = "Unable to load"
            }
        }
    }

    private fun openDriveFolder() {
        val folderId = getSharedPreferences("cowall", Context.MODE_PRIVATE)
            .getString(GoogleDriveManager.PREF_FOLDER_ID, null)
        if (folderId == null) {
            showErrorSnackbar("Folder not found. Share an image first.")
            return
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${GoogleDriveManager.DRIVE_FOLDER_URL_PREFIX}$folderId")))
    }

    private fun showDeleteOldImagesDialog() {
        val labels = arrayOf("Older than 1 day", "Older than 7 days", "Older than 30 days", "Older than 90 days", "Older than 1 year")
        val ageMillis = longArrayOf(
            86_400_000L,
            7 * 86_400_000L,
            30 * 86_400_000L,
            90 * 86_400_000L,
            365 * 86_400_000L
        )
        var selectedIndex = -1
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Delete Drive Images")
            .setSingleChoiceItems(labels, selectedIndex) { _, which -> selectedIndex = which }
            .setPositiveButton("Delete") { _, _ ->
                if (selectedIndex < 0) return@setPositiveButton
                showEmotionalConfirmDialog(
                    emoji = "\uD83D\uDDD1\uFE0F",
                    title = "Delete Images?",
                    message = "Permanently delete all CoWall images ${labels[selectedIndex].lowercase()} from your Drive. This cannot be undone.",
                    positiveLabel = "Delete",
                    onConfirm = { deleteOldDriveImages(ageMillis[selectedIndex], labels[selectedIndex]) }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteOldDriveImages(ageMs: Long, label: String) {
        setDeleteRowLoading(true)
        lifecycleScope.launch {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(this@SettingsActivity) ?: run {
                    setDeleteRowLoading(false)
                    return@launch
                }
                val token = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(
                        this@SettingsActivity,
                        account.account!!,
                        "oauth2:https://www.googleapis.com/auth/drive.file"
                    )
                }
                val deleted = GoogleDriveManager().deleteFilesOlderThan(this@SettingsActivity, token, ageMs)
                if (deleted > 0) {
                    showSuccessSnackbar("Deleted $deleted image${if (deleted == 1) "" else "s"} ($label)")
                    loadDriveStorageInfo()
                } else {
                    showSuccessSnackbar("No images found $label")
                }
            } catch (e: Exception) {
                Log.e("CoWall", "deleteOldDriveImages error: $e")
                showErrorSnackbar("Failed to delete images")
            } finally {
                setDeleteRowLoading(false)
            }
        }
    }

    private fun setDeleteRowLoading(isLoading: Boolean) {
        binding.deleteOldImagesRow.isEnabled = !isLoading
        binding.deleteOldImagesRow.alpha = if (isLoading) 0.5f else 1f
        binding.deleteOldImagesChevron.visibility = if (isLoading) View.GONE else View.VISIBLE
        binding.deleteOldImagesProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}
