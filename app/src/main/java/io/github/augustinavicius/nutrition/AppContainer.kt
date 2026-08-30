package io.github.augustinavicius.nutrition

import android.content.Context
import io.github.augustinavicius.nutrition.data.Network
import io.github.augustinavicius.nutrition.data.db.NutritionDatabase
import io.github.augustinavicius.nutrition.data.off.OpenFoodFactsApi
import io.github.augustinavicius.nutrition.data.prefs.SecretStore
import io.github.augustinavicius.nutrition.data.prefs.SettingsStore
import io.github.augustinavicius.nutrition.data.repo.DiaryRepository
import io.github.augustinavicius.nutrition.data.repo.FoodRepository
import io.github.augustinavicius.nutrition.data.repo.RecipeRepository
import io.github.augustinavicius.nutrition.sync.SyncJson
import io.github.augustinavicius.nutrition.sync.SyncRepository
import io.github.augustinavicius.nutrition.sync.WebDavClient
import io.github.augustinavicius.nutrition.update.ApkInstaller
import io.github.augustinavicius.nutrition.update.GitHubApi
import io.github.augustinavicius.nutrition.update.GitHubOAuthApi
import io.github.augustinavicius.nutrition.update.UpdateRepository

/**
 * Hand-rolled dependency graph. The app has one module and a handful of singletons, so a DI
 * framework would cost build time and indirection without buying anything here.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database by lazy { NutritionDatabase.build(appContext) }

    private val offApi: OpenFoodFactsApi by lazy {
        Network.retrofit(OpenFoodFactsApi.BASE_URL).create(OpenFoodFactsApi::class.java)
    }

    private val gitHubApi: GitHubApi by lazy {
        Network.retrofit(GitHubApi.BASE_URL).create(GitHubApi::class.java)
    }

    private val gitHubOAuthApi: GitHubOAuthApi by lazy {
        Network.retrofit(GitHubOAuthApi.BASE_URL).create(GitHubOAuthApi::class.java)
    }

    val secretStore: SecretStore by lazy { SecretStore(appContext) }

    val settingsStore: SettingsStore by lazy {
        SettingsStore(appContext, BuildConfig.GITHUB_OWNER, BuildConfig.GITHUB_REPO)
    }

    val foodRepository: FoodRepository by lazy {
        FoodRepository(database.foodDao(), database.syncDao(), offApi)
    }

    val diaryRepository: DiaryRepository by lazy { DiaryRepository(database.diaryDao(), foodRepository) }

    val recipeRepository: RecipeRepository by lazy {
        RecipeRepository(database.recipeDao(), database.syncDao(), foodRepository)
    }

    val syncRepository: SyncRepository by lazy {
        SyncRepository(
            syncDao = database.syncDao(),
            foodDao = database.foodDao(),
            recipeDao = database.recipeDao(),
            secrets = secretStore,
            settings = settingsStore,
            webdav = WebDavClient(Network.client),
            json = SyncJson,
        )
    }

    val updateRepository: UpdateRepository by lazy {
        UpdateRepository(
            context = appContext,
            api = gitHubApi,
            oauth = gitHubOAuthApi,
            secrets = secretStore,
            settings = settingsStore,
            client = Network.client,
        )
    }

    val apkInstaller: ApkInstaller by lazy { ApkInstaller(appContext) }
}
