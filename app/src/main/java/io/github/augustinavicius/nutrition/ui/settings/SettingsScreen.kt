package io.github.augustinavicius.nutrition.ui.settings

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.augustinavicius.nutrition.BuildConfig
import io.github.augustinavicius.nutrition.core.Format
import io.github.augustinavicius.nutrition.update.UpdateChannel
import io.github.augustinavicius.nutrition.update.UpdateStatus
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    bottomBar: @Composable () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.dismissUpdateNotification(context) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            GoalsSection(state, viewModel)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            UpdatesSection(
                state = state,
                viewModel = viewModel,
                onAllowInstalls = { context.startActivity(viewModel.unknownSourcesIntent()) },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("About", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Food data comes from Open Food Facts, an open database licensed under " +
                    "the Open Database Licence.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { context.openUrl("https://world.openfoodfacts.org") }) {
                Text("Open Food Facts")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GoalsSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    Text("Daily goals", style = MaterialTheme.typography.titleMedium)

    OutlinedTextField(
        value = state.goals.kcal,
        onValueChange = { value -> viewModel.setGoal { it.copy(kcal = value.filter(Char::isDigit).take(5)) } },
        label = { Text("Energy") },
        suffix = { Text("kcal") },
        singleLine = true,
        isError = state.goals.kcalValue == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MacroGoalField("Protein", state.goals.protein, Modifier.weight(1f)) { value ->
            viewModel.setGoal { it.copy(protein = value) }
        }
        MacroGoalField("Carbs", state.goals.carbs, Modifier.weight(1f)) { value ->
            viewModel.setGoal { it.copy(carbs = value) }
        }
        MacroGoalField("Fat", state.goals.fat, Modifier.weight(1f)) { value ->
            viewModel.setGoal { it.copy(fat = value) }
        }
    }

    state.goals.kcalFromMacros?.let { fromMacros ->
        val target = state.goals.kcalValue
        val drift = target?.let { fromMacros - it }
        Text(
            text = when {
                drift == null -> "Those macros come to $fromMacros kcal."
                kotlin.math.abs(drift) <= 50 -> "Those macros come to $fromMacros kcal — that lines up."
                drift > 0 -> "Those macros come to $fromMacros kcal, $drift over your energy goal."
                else -> "Those macros come to $fromMacros kcal, ${-drift} under your energy goal."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MacroGoalField(
    label: String,
    value: String,
    modifier: Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        suffix = { Text("g") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun UpdatesSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onAllowInstalls: () -> Unit,
) {
    Text("App updates", style = MaterialTheme.typography.titleMedium)

    Text(
        text = "Installed: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = "Source: ${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (BuildConfig.DEBUG) {
        Text(
            text = "This is a debug build, so a downloaded release installs alongside it rather " +
                "than replacing it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }

    ChannelPicker(state.update.channel, viewModel::setChannel)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Check automatically", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Looks for a new release a few times a day and notifies you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = state.update.autoCheck, onCheckedChange = viewModel::setAutoCheck)
    }

    if (state.update.lastCheckedAt > 0) {
        Text(
            text = "Last checked ${
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(state.update.lastCheckedAt))
            }",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(
            onClick = viewModel::checkForUpdates,
            enabled = state.status !is UpdateStatus.Checking,
        ) { Text("Check for updates") }

        if (state.status is UpdateStatus.Checking) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }

    UpdateStatusBlock(state, viewModel, onAllowInstalls)
}

@Composable
private fun ChannelPicker(selected: UpdateChannel, onSelect: (UpdateChannel) -> Unit) {
    Text("Update channel", style = MaterialTheme.typography.bodyLarge)

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        UpdateChannel.entries.forEachIndexed { index, channel ->
            SegmentedButton(
                selected = channel == selected,
                onClick = { onSelect(channel) },
                shape = SegmentedButtonDefaults.itemShape(index, UpdateChannel.entries.size),
            ) { Text(channel.label) }
        }
    }

    Text(
        text = when (selected) {
            UpdateChannel.STABLE ->
                "Releases built from the master branch. Fewer updates, each one finished."
            UpdateChannel.DEVELOPMENT ->
                "Every build, including those from the development branch, as soon as it is " +
                    "pushed. Newer, and rougher."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun UpdateStatusBlock(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onAllowInstalls: () -> Unit,
) {
    when (val status = state.status) {
        // A neutral container, like the other cards on this screen: buttons, text buttons and
        // the progress bar all derive their colours from `surface`, so a primaryContainer
        // behind them washes out — a disabled button especially, whose greys are onSurface
        // alphas. The border carries the "something is waiting for you" signal instead.
        is UpdateStatus.Available -> Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${status.release.tagName} is available",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${Format.bytes(status.asset.size)} · build ${status.versionCode}",
                    style = MaterialTheme.typography.bodySmall,
                )
                status.release.notes.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(notes.lineSequence().take(8).joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                }

                if (!state.canInstallPackages) {
                    Text(
                        text = "Android needs your permission to let this app install updates.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onAllowInstalls) { Text("Allow installs") }
                }

                state.download?.let { progress ->
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${Format.bytes(progress.bytesRead)} of ${Format.bytes(progress.total)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.refreshInstallPermission()
                            state.downloadedApk?.let(viewModel::install) ?: viewModel.downloadAndInstall()
                        },
                        enabled = state.download == null && !state.installing,
                    ) {
                        // A disabled button still has to say what it is waiting for.
                        Text(
                            when {
                                state.download != null -> "Downloading…"
                                state.installing -> "Installing…"
                                state.downloadedApk != null -> "Install"
                                else -> "Download and install"
                            }
                        )
                    }
                    TextButton(onClick = viewModel::skipThisVersion) { Text("Skip") }
                }
            }
        }

        UpdateStatus.UpToDate -> Text(
            text = "You are on the latest release.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is UpdateStatus.NoReleases -> Text(
            text = "No releases on the ${status.channel.label.lowercase()} channel yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is UpdateStatus.Error -> Text(
            text = status.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )

        else -> Unit
    }
}

private fun android.content.Context.openUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
