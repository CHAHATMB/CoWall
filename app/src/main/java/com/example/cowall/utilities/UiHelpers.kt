package com.example.cowall.utilities

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.cowall.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

// ─── Snackbar helpers ──────────────────────────────────────────────

fun Activity.showSnackbar(
    message: String,
    duration: Int = Snackbar.LENGTH_SHORT,
    actionLabel: String? = null,
    action: (() -> Unit)? = null
): Snackbar {
    val rootView = findViewById<View>(android.R.id.content)
    return Snackbar.make(rootView, message, duration).apply {
        if (actionLabel != null && action != null) {
            setAction(actionLabel) { action() }
            setActionTextColor(ContextCompat.getColor(context, R.color.accent))
        }
        show()
    }
}

fun Activity.showSuccessSnackbar(message: String) {
    val rootView = findViewById<View>(android.R.id.content)
    Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).apply {
        setBackgroundTint(ContextCompat.getColor(context, R.color.success_green))
        setTextColor(ContextCompat.getColor(context, R.color.white))
        show()
    }
}

fun Activity.showErrorSnackbar(
    message: String,
    actionLabel: String? = null,
    action: (() -> Unit)? = null
) {
    val rootView = findViewById<View>(android.R.id.content)
    Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).apply {
        setBackgroundTint(ContextCompat.getColor(context, R.color.error_red))
        setTextColor(ContextCompat.getColor(context, R.color.white))
        if (actionLabel != null && action != null) {
            setAction(actionLabel) { action() }
            setActionTextColor(ContextCompat.getColor(context, R.color.white))
        }
        show()
    }
}

fun Activity.showInfoSnackbar(message: String) {
    val rootView = findViewById<View>(android.R.id.content)
    Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).apply {
        setBackgroundTint(ContextCompat.getColor(context, R.color.info_blue))
        setTextColor(ContextCompat.getColor(context, R.color.white))
        show()
    }
}

// ─── Loading dialog ────────────────────────────────────────────────

fun Activity.showLoadingDialog(message: String = "Loading..."): AlertDialog {
    val view = LayoutInflater.from(this).inflate(R.layout.dialog_loading, null)
    view.findViewById<TextView>(R.id.loadingMessage).text = message
    return MaterialAlertDialogBuilder(this)
        .setView(view)
        .setCancelable(false)
        .show()
}

// ─── Confirmation dialog ───────────────────────────────────────────

fun Activity.showConfirmDialog(
    title: String,
    message: String,
    positiveLabel: String = "Yes",
    negativeLabel: String = "Cancel",
    onConfirm: () -> Unit
) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(positiveLabel) { _, _ -> onConfirm() }
        .setNegativeButton(negativeLabel, null)
        .show()
}

// ─── Clipboard helper ──────────────────────────────────────────────

fun Activity.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    showSuccessSnackbar("Copied to clipboard!")
}

// ─── Network check ─────────────────────────────────────────────────

fun Context.isNetworkAvailable(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
