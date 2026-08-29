package io.github.augustinavicius.nutrition.update

import android.content.Context
import android.util.Log
import io.github.augustinavicius.nutrition.BuildConfig
import io.github.augustinavicius.nutrition.data.prefs.SecretStore
import io.github.augustinavicius.nutrition.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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

    /** No GitHub credentials stored, so a private repository cannot be read. */
    data object NeedsSignIn : UpdateStatus

    data object UpToDate : UpdateStatus
    data object NoReleases : UpdateStatus
    data class Available(val release: GhRelease, val versionCode: Int, val asset: GhAsset) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

sealed interface SignInStep {
    data object Idle : SignInStep
    data object Starting : SignInStep
    data class AwaitingUser(val userCode: String, val verificationUri: String) : SignInStep
    data class Failed(val message: String) : SignInStep
    data class Success(val login: String?) : SignInStep
}

/**
 * Reads releases from the app's own (private) GitHub repository and installs newer builds.
 *
 * Authentication is the OAuth **device flow**: the app shows a short code, the user approves
 * it on github.com, and the resulting token lands in [SecretStore] encrypted by an Android
 * Keystore key. That requires the build to carry an OAuth client id — see
 * [deviceFlowAvailable].
 */
class UpdateRepository(
    private val context: Context,
    private val api: GitHubApi,
    private val oauth: GitHubOAuthApi,
    private val secrets: SecretStore,
    private val settings: SettingsStore,
    private val client: OkHttpClient,
    private val clientId: String = BuildConfig.GITHUB_OAUTH_CLIENT_ID,
) {

    val deviceFlowAvailable: Boolean get() = clientId.isNotBlank()

    fun token(): String? = secrets.get(SecretStore.GITHUB_TOKEN)

    val isSignedIn: Boolean get() = !token().isNullOrBlank()

    fun signOut() = secrets.remove(SecretStore.GITHUB_TOKEN)

    // ---------------------------------------------------------------- authentication

    suspend fun startDeviceFlow(): Result<GhDeviceCode> {
        if (!deviceFlowAvailable) {
            return Result.failure(
                IllegalStateException(
                    "This build has no OAuth client id, so it cannot sign in to GitHub."
                )
            )
        }
        return runCatching { oauth.requestDeviceCode(clientId, GitHubOAuthApi.SCOPE) }
            .mapCatching { code ->
                if (code.error != null || code.deviceCode.isEmpty()) {
                    throw IOException(code.errorDescription ?: code.error ?: "GitHub rejected the request")
                }
                code
            }
            .recoverCatching { error ->
                // A 404 from the device-code endpoint means GitHub does not know this client
                // id — by far the likeliest cause, and worth saying plainly.
                val message = if (error is HttpException && error.code() == 404) {
                    "GitHub does not recognise this build's OAuth client id. Check " +
                        "APP_GITHUB_OAUTH_CLIENT_ID and that the OAuth app has device flow enabled."
                } else {
                    describe(error, "Could not start GitHub sign-in")
                }
                throw IOException(message, error)
            }
    }

    /**
     * Polls GitHub until the user approves the device code, honouring the server's backoff.
     *
     * Resolves to the signed-in login when it can be read, or null when it cannot — the
     * profile lookup is only for display, and the token works regardless.
     */
    suspend fun awaitDeviceAuthorization(code: GhDeviceCode): Result<String?> {
        var intervalSeconds = code.interval.coerceAtLeast(MIN_POLL_SECONDS).toLong()
        val deadline = System.currentTimeMillis() + code.expiresIn * 1000L

        while (System.currentTimeMillis() < deadline) {
            currentCoroutineContext().ensureActive()
            delay(intervalSeconds * 1000)

            val response = try {
                oauth.pollAccessToken(clientId, code.deviceCode)
            } catch (e: IOException) {
                continue // transient network trouble: keep waiting until the code expires
            } catch (e: Exception) {
                return Result.failure(IOException(describe(e, "Sign-in failed"), e))
            }

            response.accessToken?.takeIf { it.isNotBlank() }?.let { token ->
                secrets.put(SecretStore.GITHUB_TOKEN, token)
                // Sign-in has already succeeded at this point. Failing to read the profile
                // costs a display name and nothing else, so it must not undo that — reporting
                // failure here would leave the app signed in while telling the user it is not.
                return Result.success(runCatching { api.user("Bearer $token").login }.getOrNull())
            }

            when (response.error) {
                "authorization_pending" -> Unit
                "slow_down" -> intervalSeconds += (response.interval ?: MIN_POLL_SECONDS).toLong()
                "expired_token" -> return Result.failure(IOException("The code expired. Start again."))
                "access_denied" -> return Result.failure(IOException("Authorization was declined."))
                else -> return Result.failure(
                    IOException(response.errorDescription ?: response.error ?: "Sign-in failed")
                )
            }
        }
        return Result.failure(IOException("The code expired. Start again."))
    }

    // ---------------------------------------------------------------- update checking

    suspend fun check(): UpdateStatus {
        val auth = token()?.takeIf { it.isNotBlank() } ?: return UpdateStatus.NeedsSignIn
        val config = settings.updateSettings.first()

        val release = try {
            api.latestRelease("Bearer $auth", config.owner, config.repo)
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> {
                    signOut()
                    return UpdateStatus.NeedsSignIn
                }
                403 -> return UpdateStatus.Error("That token cannot read ${config.owner}/${config.repo}.")
                404 -> return findLatestFallback(auth, config.owner, config.repo)
                else -> return UpdateStatus.Error("GitHub returned HTTP ${e.code()}.")
            }
        } catch (e: IOException) {
            return UpdateStatus.Error("No connection. Check your network and try again.")
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            return UpdateStatus.Error(e.message ?: "Update check failed")
        }

        settings.setLastCheckedAt(System.currentTimeMillis())
        return evaluate(release)
    }

    /**
     * `releases/latest` 404s when a repository only has pre-releases (or none at all), so fall
     * back to the full list before reporting failure.
     */
    private suspend fun findLatestFallback(auth: String, owner: String, repo: String): UpdateStatus = try {
        val newest = api.releases("Bearer $auth", owner, repo)
            .filterNot { it.draft }
            .maxByOrNull { it.versionCode ?: 0 }
        settings.setLastCheckedAt(System.currentTimeMillis())
        if (newest == null) UpdateStatus.NoReleases else evaluate(newest)
    } catch (e: HttpException) {
        if (e.code() == 404) {
            UpdateStatus.Error("Repository $owner/$repo not found, or the token cannot see it.")
        } else {
            UpdateStatus.Error("GitHub returned HTTP ${e.code()}.")
        }
    } catch (e: IOException) {
        UpdateStatus.Error("No connection. Check your network and try again.")
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
     * Private assets are served from `api.github.com` with `Accept: application/octet-stream`,
     * which redirects to a signed storage URL. OkHttp drops the `Authorization` header across
     * that host change, which is exactly what the storage backend requires.
     */
    suspend fun download(
        asset: GhAsset,
        onProgress: (bytesRead: Long, total: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        val auth = token()?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(IllegalStateException("Not signed in to GitHub"))
        val config = settings.updateSettings.first()

        val request = Request.Builder()
            .url("${GitHubApi.BASE_URL}repos/${config.owner}/${config.repo}/releases/assets/${asset.id}")
            .header("Accept", "application/octet-stream")
            .header("Authorization", "Bearer $auth")
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

    private fun describe(error: Throwable, fallback: String): String = when {
        error is HttpException && error.code() == 401 -> "GitHub rejected those credentials."
        error is HttpException && error.code() == 403 -> "That token does not have access."
        error is HttpException -> "GitHub returned HTTP ${error.code()}."
        error is IOException -> "No connection. Check your network and try again."
        else -> error.message ?: fallback
    }

    private companion object {
        const val TAG = "UpdateRepository"
        const val MIN_POLL_SECONDS = 5
        const val DOWNLOAD_BUFFER = 64 * 1024
    }
}
