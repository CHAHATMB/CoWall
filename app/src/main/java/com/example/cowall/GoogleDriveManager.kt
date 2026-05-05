package com.example.cowall

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GoogleDriveManager {

    companion object {
        private const val TAG = "GoogleDriveManager"
        private const val DRIVE_API_FILES = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL =
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        private const val BOUNDARY = "==CoWallBoundary=="
        private const val COWALL_FOLDER_NAME = "CoWall"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    }

    suspend fun uploadImageToDrive(
        context: Context,
        accessToken: String,
        imageUri: Uri,
        fileName: String,
        partnerEmail: String?
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val imageBytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                    ?: run {
                        Log.e(TAG, "Could not open input stream for URI: $imageUri")
                        return@withContext null
                    }

                val folderId = getOrCreateCowallFolder(accessToken)
                val metadata = buildMetadata(fileName, folderId)
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
                Log.d(TAG, "Uploaded to Drive, fileId=$fileId, folder=$folderId")

                if (!partnerEmail.isNullOrEmpty()) {
                    shareFileWithUser(accessToken, fileId, partnerEmail)
                } else {
                    makeFilePublic(accessToken, fileId)
                }
                fileId
            } catch (e: java.io.IOException) {
                Log.e(TAG, "uploadImageToDrive network error: $e")
                throw e  // propagate to caller for specific handling
            } catch (e: Exception) {
                Log.e(TAG, "uploadImageToDrive error: $e")
                null
            }
        }
    }

    private fun buildMetadata(fileName: String, folderId: String?): String {
        val json = JSONObject().apply {
            put("name", fileName)
            put("mimeType", "image/jpeg")
            if (folderId != null) put("parents", JSONArray().put(folderId))
        }
        return json.toString()
    }

    private fun getOrCreateCowallFolder(accessToken: String): String? {
        return try {
            val query = URLEncoder.encode(
                "name='$COWALL_FOLDER_NAME' and mimeType='$FOLDER_MIME_TYPE' and trashed=false",
                "UTF-8"
            )
            val searchConn = (URL("$DRIVE_API_FILES?q=$query&fields=files(id)").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
            }

            if (searchConn.responseCode in 200..299) {
                val files = JSONObject(searchConn.inputStream.bufferedReader().use { it.readText() })
                    .getJSONArray("files")
                if (files.length() > 0) {
                    val folderId = files.getJSONObject(0).getString("id")
                    Log.d(TAG, "Found existing CoWall folder: $folderId")
                    return folderId
                }
            }

            val createConn = (URL(DRIVE_API_FILES).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            val folderMetadata = JSONObject().apply {
                put("name", COWALL_FOLDER_NAME)
                put("mimeType", FOLDER_MIME_TYPE)
            }.toString()
            createConn.outputStream.use { it.write(folderMetadata.toByteArray(Charsets.UTF_8)) }

            val createCode = createConn.responseCode
            if (createCode !in 200..299) {
                Log.e(TAG, "Failed to create CoWall folder HTTP $createCode")
                return null
            }
            val folderId = JSONObject(createConn.inputStream.bufferedReader().use { it.readText() }).getString("id")
            Log.d(TAG, "Created CoWall folder: $folderId")
            folderId
        } catch (e: Exception) {
            Log.e(TAG, "getOrCreateCowallFolder error: $e")
            null
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

    private fun shareFileWithUser(accessToken: String, fileId: String, partnerEmail: String) {
        try {
            val conn = (URL("$DRIVE_API_FILES/$fileId/permissions?sendNotificationEmail=false").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            val permission = JSONObject().apply {
                put("role", "reader")
                put("type", "user")
                put("emailAddress", partnerEmail)
            }.toString()
            conn.outputStream.use { it.write(permission.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "no body"
                Log.e(TAG, "shareFileWithUser failed HTTP $code: $error")
            } else {
                Log.d(TAG, "File $fileId shared with $partnerEmail")
            }
        } catch (e: Exception) {
            Log.e(TAG, "shareFileWithUser error: $e")
        }
    }

    private fun makeFilePublic(accessToken: String, fileId: String) {
        try {
            val conn = (URL("$DRIVE_API_FILES/$fileId/permissions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            val permission = JSONObject().apply {
                put("role", "reader")
                put("type", "anyone")
            }.toString()
            conn.outputStream.use { it.write(permission.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "no body"
                Log.e(TAG, "makeFilePublic failed HTTP $code: $error")
            } else {
                Log.d(TAG, "File $fileId made public")
            }
        } catch (e: Exception) {
            Log.e(TAG, "makeFilePublic error: $e")
        }
    }

    /**
     * Returns the Drive API download URL. Requires the caller to include a valid
     * Bearer token in the request header (drive.readonly scope).
     */
    fun getDirectDownloadUrl(fileId: String): String =
        "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
}
