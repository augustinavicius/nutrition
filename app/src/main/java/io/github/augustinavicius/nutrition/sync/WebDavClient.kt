package io.github.augustinavicius.nutrition.sync

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class WebDavConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
    val folder: String,
) {
    val isComplete: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    /** Full URL of the library document, tolerating whatever slashes the user pasted. */
    val documentUrl: String
        get() {
            val base = serverUrl.trim().trimEnd('/')
            val dir = folder.trim().trim('/')
            return if (dir.isEmpty()) "$base/$FILE_NAME" else "$base/$dir/$FILE_NAME"
        }

    val folderUrl: String?
        get() {
            val dir = folder.trim().trim('/')
            return if (dir.isEmpty()) null else "${serverUrl.trim().trimEnd('/')}/$dir"
        }

    companion object {
        const val FILE_NAME = "library.json"
    }
}

/** What the server currently holds, and the tag identifying that exact version. */
data class RemoteDocument(val body: String?, val etag: String?)

/** Raised when the document changed underneath us between reading and writing. */
class WebDavConflictException : IOException("The library changed on the server while syncing")

/**
 * The smallest slice of WebDAV this needs: read a file, write it back, and create the folder
 * if it is missing. Writes are conditional on the version that was read, so two devices
 * syncing at once cannot silently overwrite each other.
 */
class WebDavClient(private val client: OkHttpClient) {

    fun get(config: WebDavConfig): Result<RemoteDocument> = runCatching {
        val request = Request.Builder()
            .url(config.documentUrl)
            .header("Authorization", credentials(config))
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> RemoteDocument(null, null)
                response.isSuccessful ->
                    RemoteDocument(response.body.string(), response.header("ETag"))
                response.code == 401 -> throw IOException("The server rejected those credentials.")
                else -> throw IOException(describe(response.code))
            }
        }
    }

    /**
     * Writes the document back, but only if the server still holds [ifMatch]. A missing
     * [ifMatch] means "only create it if nothing is there", so a first sync cannot clobber a
     * library another device published a moment earlier.
     */
    fun put(config: WebDavConfig, body: String, ifMatch: String?): Result<String?> = runCatching {
        val attempt = { ->
            val builder = Request.Builder()
                .url(config.documentUrl)
                .header("Authorization", credentials(config))
                .put(body.toRequestBody(JSON_MEDIA_TYPE))
            if (ifMatch != null) builder.header("If-Match", ifMatch) else builder.header("If-None-Match", "*")
            client.newCall(builder.build()).execute()
        }

        var response = attempt()
        // 409 means the collection does not exist yet; make it and try once more.
        if (response.code == 409) {
            response.close()
            config.folderUrl?.let { createFolder(config, it) }
            response = attempt()
        }

        response.use {
            when {
                it.isSuccessful -> it.header("ETag")
                it.code == 412 -> throw WebDavConflictException()
                it.code == 401 -> throw IOException("The server rejected those credentials.")
                else -> throw IOException(describe(it.code))
            }
        }
    }

    private fun createFolder(config: WebDavConfig, url: String) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credentials(config))
            .method("MKCOL", null)
            .build()
        // 405 means it already exists, which is exactly what we wanted.
        client.newCall(request).execute().close()
    }

    private fun credentials(config: WebDavConfig) =
        Credentials.basic(config.username.trim(), config.password)

    private fun describe(code: Int): String = when (code) {
        403 -> "The server refused access to that folder."
        405 -> "That URL does not accept WebDAV requests. Check the path."
        507 -> "The server is out of space."
        in 500..599 -> "The server is having trouble (HTTP $code)."
        else -> "The server returned HTTP $code."
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
