package io.github.augustinavicius.nutrition.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.augustinavicius.nutrition.BuildConfig
import io.github.augustinavicius.nutrition.NutritionApp
import io.github.augustinavicius.nutrition.core.Goals
import io.github.augustinavicius.nutrition.data.prefs.SettingsStore
import io.github.augustinavicius.nutrition.data.prefs.UpdateSettings
import io.github.augustinavicius.nutrition.update.ApkInstaller
import io.github.augustinavicius.nutrition.update.UpdateChannel
import io.github.augustinavicius.nutrition.update.InstallEvent
import io.github.augustinavicius.nutrition.update.InstallEvents
import io.github.augustinavicius.nutrition.update.UpdateNotifications
import io.github.augustinavicius.nutrition.update.UpdateRepository
import io.github.augustinavicius.nutrition.update.UpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class DownloadProgress(val bytesRead: Long, val total: Long) {
    val fraction: Float get() = if (total > 0) (bytesRead.toFloat() / total).coerceIn(0f, 1f) else 0f
}

data class GoalsDraft(
    val kcal: String = "2000",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
) {
    val kcalValue: Int? get() = kcal.toIntOrNull()?.takeIf { it in 500..15000 }
    fun macro(text: String): Int? = text.takeIf { it.isNotBlank() }?.toIntOrNull()?.takeIf { it in 0..2000 }

    val kcalFromMacros: Int?
        get() {
            val p = macro(protein) ?: return null
            val c = macro(carbs) ?: return null
            val f = macro(fat) ?: return null
            return p * 4 + c * 4 + f * 9
        }
}

data class SettingsUiState(
    val goals: GoalsDraft = GoalsDraft(),
    val update: UpdateSettings = UpdateSettings(),
    val canInstallPackages: Boolean = false,
    val status: UpdateStatus = UpdateStatus.Idle,
    val download: DownloadProgress? = null,
    val installing: Boolean = false,
    val message: String? = null,
    val downloadedApk: File? = null,
)

class SettingsViewModel(
    private val settings: SettingsStore,
    private val updates: UpdateRepository,
    private val installer: ApkInstaller,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val goals = settings.goals.first()
            val update = settings.updateSettings.first()
            _state.update {
                it.copy(
                    goals = GoalsDraft(
                        kcal = goals.kcal.toString(),
                        protein = goals.proteinG?.toString().orEmpty(),
                        carbs = goals.carbsG?.toString().orEmpty(),
                        fat = goals.fatG?.toString().orEmpty(),
                    ),
                    update = update,
                    canInstallPackages = installer.canInstallPackages,
                )
            }
        }

        viewModelScope.launch {
            InstallEvents.events.collect { event ->
                when (event) {
                    InstallEvent.AwaitingConfirmation ->
                        _state.update { it.copy(installing = true, message = "Confirm the install when Android asks.") }
                    InstallEvent.Succeeded ->
                        _state.update { it.copy(installing = false, message = "Update installed.") }
                    is InstallEvent.Failed ->
                        _state.update { it.copy(installing = false, message = event.message) }
                }
            }
        }
    }

    // ---------------------------------------------------------------- goals

    fun setGoal(transform: (GoalsDraft) -> GoalsDraft) {
        _state.update { it.copy(goals = transform(it.goals)) }
        persistGoals()
    }

    private fun persistGoals() {
        val draft = _state.value.goals
        val kcal = draft.kcalValue ?: return
        viewModelScope.launch {
            settings.setGoals(
                Goals(
                    kcal = kcal,
                    proteinG = draft.macro(draft.protein),
                    carbsG = draft.macro(draft.carbs),
                    fatG = draft.macro(draft.fat),
                )
            )
        }
    }

    // ---------------------------------------------------------------- update settings

    fun setAutoCheck(enabled: Boolean) {
        _state.update { it.copy(update = it.update.copy(autoCheck = enabled)) }
        viewModelScope.launch { settings.setAutoCheck(enabled) }
    }

    /** Switching channel re-checks straight away, since the answer usually changes. */
    fun setChannel(channel: UpdateChannel) {
        if (_state.value.update.channel == channel) return
        _state.update {
            it.copy(update = it.update.copy(channel = channel, skippedVersionCode = 0))
        }
        viewModelScope.launch {
            settings.setChannel(channel)
            checkForUpdates()
        }
    }

    fun refreshInstallPermission() {
        _state.update { it.copy(canInstallPackages = installer.canInstallPackages) }
    }

    fun unknownSourcesIntent() = installer.unknownSourcesSettingsIntent()

    // ---------------------------------------------------------------- updates

    fun checkForUpdates() {
        viewModelScope.launch {
            _state.update { it.copy(status = UpdateStatus.Checking, message = null) }
            val status = updates.check()
            _state.update {
                it.copy(
                    status = status,
                    update = it.update.copy(lastCheckedAt = System.currentTimeMillis()),
                )
            }
        }
    }

    fun downloadAndInstall() {
        val available = _state.value.status as? UpdateStatus.Available ?: return
        viewModelScope.launch {
            _state.update { it.copy(download = DownloadProgress(0, available.asset.size), message = null) }
            updates.download(available.asset) { read, total ->
                _state.update { it.copy(download = DownloadProgress(read, total)) }
            }.fold(
                onSuccess = { file ->
                    updates.clearDownloads(except = file)
                    _state.update { it.copy(download = null, downloadedApk = file) }
                    install(file)
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(download = null, message = error.message ?: "Download failed")
                    }
                },
            )
        }
    }

    fun install(file: File) {
        if (!installer.canInstallPackages) {
            _state.update {
                it.copy(
                    canInstallPackages = false,
                    message = "Allow this app to install apps, then tap Install again.",
                )
            }
            return
        }
        viewModelScope.launch {
            installer.install(file).onFailure { error ->
                _state.update { it.copy(message = error.message ?: "Install could not be started") }
            }
        }
    }

    fun skipThisVersion() {
        val available = _state.value.status as? UpdateStatus.Available ?: return
        viewModelScope.launch {
            settings.setSkippedVersionCode(available.versionCode)
            _state.update { it.copy(status = UpdateStatus.UpToDate, message = "Skipped ${available.release.tagName}.") }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** Called when the update controls come on screen: the notification has done its job. */
    fun dismissUpdateNotification(context: Context) = UpdateNotifications.cancel(context)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutritionApp
                SettingsViewModel(
                    app.container.settingsStore,
                    app.container.updateRepository,
                    app.container.apkInstaller,
                )
            }
        }
    }
}
