package io.github.augustinavicius.nutrition.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.File

sealed interface InstallEvent {
    /** The system is showing (or about to show) its confirmation dialog. */
    data object AwaitingConfirmation : InstallEvent
    data object Succeeded : InstallEvent
    data class Failed(val message: String) : InstallEvent
}

/** Process-wide bridge from the install [BroadcastReceiver] back to whatever UI is on screen. */
object InstallEvents {
    private val _events = MutableSharedFlow<InstallEvent>(
        replay = 1,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<InstallEvent> = _events

    fun emit(event: InstallEvent) {
        _events.tryEmit(event)
    }

}

/**
 * Installs a downloaded APK through [PackageInstaller].
 *
 * The system still shows its own confirmation dialog, and it refuses the install outright if
 * the new APK is not signed with the same key as the installed app — which is why the release
 * workflow must always sign with the same keystore.
 */
class ApkInstaller(private val context: Context) {

    /** Android requires an explicit per-app grant before an app may install other packages. */
    val canInstallPackages: Boolean
        get() = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    suspend fun install(apk: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(apk.exists() && apk.length() > 0) { "Downloaded file is missing or empty" }

            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(context.packageName)
                setInstallReason(PackageManager.INSTALL_REASON_USER)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }

            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite(APK_NAME, 0, apk.length()).use { output ->
                    apk.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                }

                val intent = Intent(context, InstallResultReceiver::class.java)
                    .setAction(InstallResultReceiver.ACTION_INSTALL_RESULT)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                InstallEvents.emit(InstallEvent.AwaitingConfirmation)
                session.commit(pendingIntent.intentSender)
            }
        }.onFailure { Log.e(TAG, "Install failed", it) }
    }

    private companion object {
        const val TAG = "ApkInstaller"
        const val APK_NAME = "nutrition-update.apk"
    }
}

/** Receives the outcome of a [PackageInstaller] session, including the "confirm this" step. */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (confirmation == null) {
                    InstallEvents.emit(InstallEvent.Failed("Android did not return an install prompt."))
                    return
                }
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                InstallEvents.emit(InstallEvent.AwaitingConfirmation)
                context.startActivity(confirmation)
            }

            PackageInstaller.STATUS_SUCCESS -> InstallEvents.emit(InstallEvent.Succeeded)

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                InstallEvents.emit(InstallEvent.Failed(describe(status, message)))
            }
        }
    }

    private fun describe(status: Int, message: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "Install cancelled."
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the install."
        PackageInstaller.STATUS_FAILURE_CONFLICT ->
            "Signature mismatch: this build was signed with a different key than the installed app."
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This build is not compatible with your device."
        PackageInstaller.STATUS_FAILURE_INVALID -> "The downloaded APK is not valid."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage to install the update."
        else -> message ?: "Install failed (status $status)."
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "io.github.augustinavicius.nutrition.INSTALL_RESULT"
    }
}
