package io.github.augustinavicius.nutrition

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.augustinavicius.nutrition.ui.NutritionAppRoot
import io.github.augustinavicius.nutrition.ui.theme.NutritionTheme

class MainActivity : ComponentActivity() {

    /** Drives navigation to the update controls when the update notification is tapped. */
    private var openUpdates by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        openUpdates = intent?.getBooleanExtra(EXTRA_OPEN_UPDATES, false) == true

        setContent {
            NutritionTheme {
                NutritionAppRoot(
                    openUpdates = openUpdates,
                    onUpdatesHandled = { openUpdates = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_UPDATES, false)) openUpdates = true
    }

    companion object {
        /** Set by the update notification so tapping it lands on the update controls. */
        const val EXTRA_OPEN_UPDATES = "open_updates"
    }
}
