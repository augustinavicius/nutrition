package io.github.augustinavicius.nutrition.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.augustinavicius.nutrition.R
import io.github.augustinavicius.nutrition.core.DecimalInput
import io.github.augustinavicius.nutrition.ui.components.formWindowInsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFoodScreen(
    onScanBarcode: () -> Unit,
    scannedBarcode: String?,
    onScannedBarcodeConsumed: () -> Unit,
    onSaved: (foodId: Long) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    viewModel: EditFoodViewModel = viewModel(factory = EditFoodViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    // The scanner hands its result back through the nav entry, leaving this half-filled
    // form untouched underneath.
    LaunchedEffect(scannedBarcode) {
        scannedBarcode?.takeIf { it.isNotBlank() }?.let { code ->
            viewModel.setBarcode(code)
            onScannedBarcodeConsumed()
        }
    }

    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    Scaffold(
        contentWindowInsets = formWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(if (state.editing) "Edit food" else "New food") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.editing) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete food")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Name") },
                singleLine = true,
                isError = state.name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.brand,
                onValueChange = viewModel::setBrand,
                label = { Text("Brand (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.barcode,
                onValueChange = viewModel::setBarcode,
                label = { Text("Barcode (optional)") },
                supportingText = {
                    if (state.barcodeOwner != null) {
                        Text(
                            text = "\"${state.barcodeOwner}\" already uses this barcode — " +
                                "saving will update that food.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text("Saved foods are found instantly when you scan this code.")
                    }
                },
                trailingIcon = {
                    IconButton(onClick = onScanBarcode) {
                        Icon(
                            painter = painterResource(R.drawable.ic_barcode),
                            contentDescription = "Scan the barcode",
                        )
                    }
                },
                isError = state.barcodeOwner != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Per 100 g", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Enter the figures exactly as the packaging states them per 100 g. " +
                    "Portions are weighed in grams when you log the food.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            NumberField(state.kcal, viewModel::setKcal, "Energy", "kcal", required = true)
            NumberField(state.fat, viewModel::setFat, "Fat", "g")
            NumberField(state.satFat, viewModel::setSatFat, "of which saturates (optional)", "g")
            NumberField(state.carbs, viewModel::setCarbs, "Carbohydrate", "g")
            NumberField(state.sugar, viewModel::setSugar, "of which sugars (optional)", "g")
            NumberField(state.fiber, viewModel::setFiber, "Fibre (optional)", "g")
            NumberField(state.protein, viewModel::setProtein, "Protein", "g")
            NumberField(state.sodiumMg, viewModel::setSodium, "Sodium (optional)", "mg")

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.editing) "Save changes" else "Save food") }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${state.name}?") },
            text = { Text("Days you have already logged keep their numbers — only the saved food goes away.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String,
    required: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        isError = required && DecimalInput.parse(value) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}
