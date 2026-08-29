package io.github.augustinavicius.nutrition.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.augustinavicius.nutrition.core.MealType
import io.github.augustinavicius.nutrition.ui.diary.DiaryScreen
import io.github.augustinavicius.nutrition.ui.entry.LogEntryScreen
import io.github.augustinavicius.nutrition.ui.food.EditFoodScreen
import io.github.augustinavicius.nutrition.ui.scan.ScanScreen
import io.github.augustinavicius.nutrition.ui.search.SearchScreen
import io.github.augustinavicius.nutrition.ui.settings.SettingsScreen
import java.time.LocalDate
import kotlin.reflect.KClass

private data class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun NutritionAppRoot(
    openUpdates: Boolean = false,
    onUpdatesHandled: () -> Unit = {},
) {
    val navController = rememberNavController()

    RequestNotificationPermission()

    LaunchedEffect(openUpdates) {
        if (openUpdates) {
            navController.navigate(Routes.Settings)
            onUpdatesHandled()
        }
    }

    NavHost(navController = navController, startDestination = Routes.Diary) {
        composable<Routes.Diary> {
            DiaryScreen(
                onAddFood = { meal, date ->
                    navController.navigate(Routes.Search(meal.name, date.toEpochDay()))
                },
                onOpenEntry = { entry ->
                    navController.navigate(
                        Routes.LogEntry(
                            entryId = entry.id,
                            dateEpochDay = entry.date.toEpochDay(),
                            meal = entry.meal.name,
                        )
                    )
                },
                onScan = { navController.navigate(Routes.Scan) },
                bottomBar = { BottomBar(navController) },
            )
        }

        composable<Routes.Search> { backStackEntry ->
            val route = backStackEntry.toRoute<Routes.Search>()
            val meal = route.meal?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
            val date = LocalDate.ofEpochDay(route.dateEpochDay)

            SearchScreen(
                meal = meal,
                date = date,
                onPickFood = { foodId ->
                    navController.navigate(
                        Routes.LogEntry(
                            foodId = foodId,
                            dateEpochDay = route.dateEpochDay,
                            meal = route.meal,
                        )
                    )
                },
                onCreateFood = { prefill ->
                    navController.navigate(Routes.EditFood(prefillName = prefill))
                },
                onScan = { navController.navigate(Routes.Scan) },
                bottomBar = { BottomBar(navController) },
            )
        }

        composable<Routes.Scan> {
            ScanScreen(
                onFoodResolved = { foodId ->
                    navController.navigate(Routes.LogEntry(foodId = foodId)) {
                        popUpTo(Routes.Scan) { inclusive = true }
                    }
                },
                onNeedsFood = { barcode, name, brand ->
                    navController.navigate(
                        Routes.EditFood(barcode = barcode, prefillName = name, prefillBrand = brand)
                    ) {
                        popUpTo(Routes.Scan) { inclusive = true }
                    }
                },
                onClose = { navController.popBackStack() },
            )
        }

        composable<Routes.LogEntry> {
            LogEntryScreen(
                onDone = { navController.popBackStack(Routes.Diary, inclusive = false) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Routes.EditFood> { backStackEntry ->
            val route = backStackEntry.toRoute<Routes.EditFood>()
            EditFoodScreen(
                onSaved = { foodId ->
                    // A brand new food goes straight to the log screen; editing an existing one
                    // just returns to wherever the user came from.
                    if (route.foodId == 0L) {
                        navController.navigate(Routes.LogEntry(foodId = foodId)) {
                            popUpTo(Routes.EditFood::class) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onDeleted = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Routes.Settings> {
            SettingsScreen(bottomBar = { BottomBar(navController) })
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val destinations = listOf(
        TopLevelDestination(Routes.Diary, Routes.Diary::class, "Diary", Icons.Default.DateRange),
        TopLevelDestination(Routes.Search(), Routes.Search::class, "Add", Icons.Default.Search),
        TopLevelDestination(Routes.Settings, Routes.Settings::class, "Settings", Icons.Default.Settings),
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination

    NavigationBar {
        destinations.forEach { destination ->
            val selected = current?.hasRoute(destination.routeClass) == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
            )
        }
    }
}

/** Android 13+ needs an explicit grant before the update notification can be shown. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
