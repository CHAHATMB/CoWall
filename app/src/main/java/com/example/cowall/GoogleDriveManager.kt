package com.example.cowall

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GoogleDriveManager {

    companion object {
        private const val TAG = "GoogleDriveManager"
        private const val DRIVE_API_FILES = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL =
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        private const val BOUNDARY = "==CoWallBoundary=="
    }

    suspend fun uploadImageToDrive(
        context: Context,
        accessToken: String,
        imageUri: Uri,
        fileName: String
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val imageBytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                    ?: run {
                        Log.e(TAG, "Could not open input stream for URI: $imageUri")
                        return@withContext null
                    }

                val metadata = """{"name":"$fileName","mimeType":"image/jpeg"}"""
                val body = buildMultipartBody(metadata, imageBytes)

                val conn = (URL(DRIVE_UPLOAD_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "multipart/related; boundary=$BOUNDARY")
                    doOutput = true
                }

                conn.outputStream.use { it.write(body) }

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    val error = conn.errorStream?.bufferedReader()?.readText() ?: "no body"
                    Log.e(TAG, "Upload failed HTTP $responseCode: $error")
                    return@withContext null
                }

                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val fileId = JSONObject(responseText).getString("id")
                Log.d(TAG, "Uploaded to Drive, fileId=$fileId")

                makeFilePublic(accessToken, fileId)
                fileId
            } catch (e: Exception) {
                Log.e(TAG, "uploadImageToDrive error: $e")
                null
            }
        }
    }

    private fun buildMultipartBody(metadata: String, imageBytes: ByteArray): ByteArray {
        val prefix = (
            "--$BOUNDARY\r\n" +
            "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
            "$metadata\r\n" +
            "--$BOUNDARY\r\n" +
            "Content-Type: image/jpeg\r\n\r\n"
        ).toByteArray(Charsets.UTF_8)
        val suffix = "\r\n--$BOUNDARY--".toByteArray(Charsets.UTF_8)
        return prefix + imageBytes + suffix
    }

    private fun makeFilePublic(accessToken: String, fileId: String) {
        try {
            val conn = (URL("$DRIVE_API_FILES/$fileId/permissions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            conn.outputStream.use {
                it.write("""{"role":"reader","type":"anyone"}""".toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.e(TAG, "makeFilePublic failed HTTP $code")
            } else {
                Log.d(TAG, "File $fileId is now publicly accessible")
            }
        } catch (e: Exception) {
            Log.e(TAG, "makeFilePublic error: $e")
        }
    }

    /**
     * Returns a direct download URL that works without authentication.
     * Uses drive.usercontent.google.com which serves the file directly
     * without the intermediate redirect/virus-scan confirmation page.
     */
    fun getDirectDownloadUrl(fileId: String): String =
        "https://drive.usercontent.google.com/download?id=$fileId&export=download&authuser=0"
}
