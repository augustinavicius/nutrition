package io.github.augustinavicius.nutrition

import android.app.Application
import io.github.augustinavicius.nutrition.update.UpdateCheckWorker
import io.github.augustinavicius.nutrition.update.UpdateNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NutritionApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        UpdateNotifications.ensureChannel(this)

        appScope.launch { container.foodRepository.ensureSeeded() }
        forgetSyncCredentials()

        appScope.launch {
            container.settingsStore.updateSettings
                .map { it.autoCheck }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) {
                        UpdateCheckWorker.schedule(this@NutritionApp)
                    } else {
                        UpdateCheckWorker.cancel(this@NutritionApp)
                    }
                }
        }
    }

    /**
     * Removes the encrypted store that held the WebDAV password and the old GitHub token.
     *
     * Nothing writes it any more, but an install that predates their removal still has the
     * file sitting on disk. Deleting it is a one-off on the first launch after the upgrade;
     * afterwards there is nothing to delete and this costs a stat call.
     */
    private fun forgetSyncCredentials() {
        deleteSharedPreferences(LEGACY_SECRETS_PREFS)
    }

    private companion object {
        const val LEGACY_SECRETS_PREFS = "nutrition_secrets"
    }
}
