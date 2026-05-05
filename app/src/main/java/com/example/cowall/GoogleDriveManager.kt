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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class GoogleDriveManager {

    companion object {
        private const val TAG = "GoogleDriveManager"
        private const val DRIVE_API_FILES = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL =
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        private const val BOUNDARY = "==CoWallBoundary=="
        private const val COWALL_FOLDER_NAME = "CoWall"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        const val PREF_FOLDER_ID = "cowallFolderId"
        const val DRIVE_FOLDER_URL_PREFIX = "https://drive.google.com/drive/folders/"
    }

    data class FolderMetadata(val fileCount: Int, val totalBytes: Long, val folderId: String?)

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

                val folderId = getOrCreateCowallFolder(accessToken, context)
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

    suspend fun getFolderMetadata(context: Context, accessToken: String): FolderMetadata {
        return withContext(Dispatchers.IO) {
            val folderId = getOrCreateCowallFolder(accessToken, context)
                ?: return@withContext FolderMetadata(0, 0L, null)
            try {
                val query = URLEncoder.encode("'$folderId' in parents and trashed=false", "UTF-8")
                val conn = (URL("$DRIVE_API_FILES?q=$query&fields=files(id,size)&pageSize=1000")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                }
                if (conn.responseCode !in 200..299) {
                    Log.e(TAG, "getFolderMetadata list failed HTTP ${conn.responseCode}")
                    return@withContext FolderMetadata(0, 0L, folderId)
                }
                val files = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    .getJSONArray("files")
                var totalBytes = 0L
                for (i in 0 until files.length()) {
                    totalBytes += files.getJSONObject(i).optLong("size", 0L)
                }
                FolderMetadata(files.length(), totalBytes, folderId)
            } catch (e: Exception) {
                Log.e(TAG, "getFolderMetadata error: $e")
                FolderMetadata(0, 0L, folderId)
            }
        }
    }

    suspend fun deleteFilesOlderThan(context: Context, accessToken: String, ageMs: Long): Int {
        return withContext(Dispatchers.IO) {
            val folderId = getOrCreateCowallFolder(accessToken, context)
                ?: return@withContext 0
            val cutoffMs = System.currentTimeMillis() - ageMs
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            try {
                val query = URLEncoder.encode("'$folderId' in parents and trashed=false", "UTF-8")
                val conn = (URL("$DRIVE_API_FILES?q=$query&fields=files(id,createdTime)&pageSize=1000")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                }
                if (conn.responseCode !in 200..299) {
                    Log.e(TAG, "deleteFilesOlderThan list failed HTTP ${conn.responseCode}")
                    return@withContext 0
                }
                val files = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    .getJSONArray("files")
                var deletedCount = 0
                for (i in 0 until files.length()) {
                    val file = files.getJSONObject(i)
                    val createdMs = try {
                        dateFormat.parse(file.optString("createdTime", ""))?.time ?: Long.MAX_VALUE
                    } catch (_: Exception) { Long.MAX_VALUE }
                    if (createdMs < cutoffMs) {
                        if (deleteFile(accessToken, file.getString("id"))) deletedCount++
                    }
                }
                deletedCount
            } catch (e: Exception) {
                Log.e(TAG, "deleteFilesOlderThan error: $e")
                0
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

    private fun getOrCreateCowallFolder(accessToken: String, context: Context? = null): String? {
        val prefs = context?.getSharedPreferences("cowall", Context.MODE_PRIVATE)
        val cachedId = prefs?.getString(PREF_FOLDER_ID, null)
        if (cachedId != null) return cachedId

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
                    prefs?.edit()?.putString(PREF_FOLDER_ID, folderId)?.apply()
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
            prefs?.edit()?.putString(PREF_FOLDER_ID, folderId)?.apply()
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

    private fun deleteFile(accessToken: String, fileId: String): Boolean {
        return try {
            val conn = (URL("$DRIVE_API_FILES/$fileId").openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            val code = conn.responseCode
            if (code in 200..299 || code == 204) {
                Log.d(TAG, "Deleted file $fileId")
                true
            } else {
                Log.e(TAG, "deleteFile failed HTTP $code for $fileId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteFile error for $fileId: $e")
            false
        }
    }

    /**
     * Returns the Drive API download URL. Requires the caller to include a valid
     * Bearer token in the request header (drive.readonly scope).
     */
    fun getDirectDownloadUrl(fileId: String): String =
        "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
}
