package io.github.augustinavicius.nutrition.update

import android.content.Context
import android.util.Log
import io.github.augustinavicius.nutrition.BuildConfig
import io.github.augustinavicius.nutrition.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.io.File
import java.io.IOException

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus

    /** The repository has no release this channel can offer. */
    data class NoReleases(val channel: UpdateChannel) : UpdateStatus

    data class Available(val release: GhRelease, val versionCode: Int, val asset: GhAsset) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

/**
 * Reads releases from the app's own GitHub repository and installs newer builds.
 *
 * The repository is public, so every call here is anonymous: there is no sign-in, no token,
 * and nothing to keep on the device. Which releases count is decided by the user's
 * [UpdateChannel] — see [GhRelease.isOn].
 */
class UpdateRepository(
    private val context: Context,
    private val api: GitHubApi,
    private val settings: SettingsStore,
    private val client: OkHttpClient,
) {

    // ---------------------------------------------------------------- update checking

    suspend fun check(): UpdateStatus {
        val config = settings.updateSettings.first()

        val releases = try {
            api.releases(config.owner, config.repo)
        } catch (e: HttpException) {
            return when (e.code()) {
                404 -> UpdateStatus.Error("Repository ${config.owner}/${config.repo} not found.")
                // Anonymous calls are rate-limited per IP. Saying so beats a bare status code,
                // since waiting is the whole remedy.
                403, 429 -> UpdateStatus.Error("GitHub is rate-limiting this device. Try again later.")
                else -> UpdateStatus.Error("GitHub returned HTTP ${e.code()}.")
            }
        } catch (e: IOException) {
            return UpdateStatus.Error("No connection. Check your network and try again.")
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            return UpdateStatus.Error(e.message ?: "Update check failed")
        }

        settings.setLastCheckedAt(System.currentTimeMillis())

        val newest = releases
            .filterNot { it.draft }
            .filter { it.isOn(config.channel) }
            .maxByOrNull { it.versionCode ?: 0 }
            ?: return UpdateStatus.NoReleases(config.channel)

        return evaluate(newest)
    }

    private fun evaluate(release: GhRelease): UpdateStatus {
        val asset = release.apkAsset ?: return UpdateStatus.Error(
            "Release ${release.tagName} has no APK attached."
        )
        val remoteCode = release.versionCode ?: return UpdateStatus.Error(
            "Release ${release.tagName} does not declare a versionCode."
        )
        return if (remoteCode > BuildConfig.VERSION_CODE) {
            UpdateStatus.Available(release, remoteCode, asset)
        } else {
            UpdateStatus.UpToDate
        }
    }

    // ---------------------------------------------------------------- download

    /**
     * Streams a release asset to the cache directory.
     *
     * A public release asset is served straight from `browser_download_url`, which redirects
     * to storage and needs no credentials. The API asset URL is the fallback for a release
     * whose payload arrived without that field.
     */
    suspend fun download(
        asset: GhAsset,
        onProgress: (bytesRead: Long, total: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        val config = settings.updateSettings.first()
        val url = asset.browserDownloadUrl?.takeIf { it.isNotBlank() }
            ?: "${GitHubApi.BASE_URL}repos/${config.owner}/${config.repo}/releases/assets/${asset.id}"

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()

        val target = File(updatesDir(), asset.name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Download failed (HTTP ${response.code})."))
                }
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 } ?: asset.size

                target.outputStream().use { sink ->
                    body.byteStream().use { source ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER)
                        var copied = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = source.read(buffer)
                            if (read == -1) break
                            sink.write(buffer, 0, read)
                            copied += read
                            onProgress(copied, total)
                        }
                    }
                }
            }
            Result.success(target)
        } catch (e: Exception) {
            target.delete()
            Result.failure(e)
        }
    }

    /** Removes previously downloaded APKs; called once an install has been handed to the system. */
    suspend fun clearDownloads(except: File? = null) = withContext(Dispatchers.IO) {
        updatesDir().listFiles()?.forEach { file ->
            if (file != except) file.delete()
        }
        Unit
    }

    private fun updatesDir(): File = File(context.cacheDir, "updates").apply { mkdirs() }

    private companion object {
        const val TAG = "UpdateRepository"
        const val DOWNLOAD_BUFFER = 64 * 1024
    }
}
