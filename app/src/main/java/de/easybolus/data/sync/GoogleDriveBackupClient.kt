package de.easybolus.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class DriveFileMeta(
    val id: String,
    val name: String,
    val modifiedTime: String? = null
)

/**
 * Lightweight Google Drive REST API v3 client using HttpURLConnection.
 * Stores a visible `easybolus_backup.json` file in the user's Drive (`drive.file` scope).
 */
object GoogleDriveBackupClient {

    const val BACKUP_FILE_NAME = "easybolus_backup.json"
    private const val FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"

    suspend fun findBackupFile(accessToken: String): Result<DriveFileMeta?> = withContext(Dispatchers.IO) {
        runCatching {
            val query = URLEncoder.encode(
                "name='$BACKUP_FILE_NAME' and trashed=false",
                Charsets.UTF_8.name()
            )
            val url = URL("$FILES_ENDPOINT?q=$query&spaces=drive&orderBy=modifiedTime desc&fields=files(id,name,modifiedTime)")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            try {
                val code = conn.responseCode
                val body = readResponse(conn, code)
                if (code !in 200..299) {
                    error("Drive HTTP $code: $body")
                }
                val root = JSONObject(body)
                val files = root.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val first = files.getJSONObject(0)
                    DriveFileMeta(
                        id = first.getString("id"),
                        name = first.optString("name", BACKUP_FILE_NAME),
                        modifiedTime = first.optString("modifiedTime").takeIf { it.isNotBlank() }
                    )
                } else {
                    null
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun uploadBackup(
        accessToken: String,
        existingFileId: String?,
        jsonContent: String
    ): Result<DriveFileMeta> = withContext(Dispatchers.IO) {
        runCatching {
            val targetId = existingFileId ?: findBackupFile(accessToken).getOrNull()?.id
            if (targetId != null) {
                patchFileMedia(accessToken, targetId, jsonContent)
            } else {
                createMultipartFile(accessToken, jsonContent)
            }
        }
    }

    suspend fun downloadBackup(accessToken: String, fileId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("$FILES_ENDPOINT/$fileId?alt=media")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            try {
                val code = conn.responseCode
                val body = readResponse(conn, code)
                if (code !in 200..299) {
                    error("Drive Download HTTP $code: $body")
                }
                body
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun patchFileMedia(
        accessToken: String,
        fileId: String,
        jsonContent: String
    ): DriveFileMeta {
        val url = URL("$UPLOAD_ENDPOINT/$fileId?uploadType=media&fields=id,name,modifiedTime")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(jsonContent) }
            val code = conn.responseCode
            val body = readResponse(conn, code)
            if (code !in 200..299) {
                error("Drive Update HTTP $code: $body")
            }
            val obj = JSONObject(body)
            return DriveFileMeta(
                id = obj.getString("id"),
                name = obj.optString("name", BACKUP_FILE_NAME),
                modifiedTime = obj.optString("modifiedTime").takeIf { it.isNotBlank() }
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun createMultipartFile(
        accessToken: String,
        jsonContent: String
    ): DriveFileMeta {
        val boundary = "easybolus_${System.currentTimeMillis()}"
        val metadata = JSONObject().apply {
            put("name", BACKUP_FILE_NAME)
            put("mimeType", "application/json")
        }.toString()

        val payload = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(jsonContent).append("\r\n")
            append("--").append(boundary).append("--")
        }

        val url = URL("$UPLOAD_ENDPOINT?uploadType=multipart&fields=id,name,modifiedTime")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }
            val code = conn.responseCode
            val body = readResponse(conn, code)
            if (code !in 200..299) {
                error("Drive Create HTTP $code: $body")
            }
            val obj = JSONObject(body)
            return DriveFileMeta(
                id = obj.getString("id"),
                name = obj.optString("name", BACKUP_FILE_NAME),
                modifiedTime = obj.optString("modifiedTime").takeIf { it.isNotBlank() }
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun readResponse(conn: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }
}
