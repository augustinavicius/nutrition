package io.github.augustinavicius.nutrition.ui.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.augustinavicius.nutrition.core.Format
import io.github.augustinavicius.nutrition.ui.components.NutrientTable
import kotlin.math.roundToInt
import io.github.augustinavicius.nutrition.ui.components.formWindowInsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditScreen(
    onAddIngredient: () -> Unit,
    pickedFoodId: Long?,
    onPickedFoodConsumed: () -> Unit,
    onSaved: (recipeId: Long) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    viewModel: RecipeEditViewModel = viewModel(factory = RecipeEditViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(pickedFoodId) {
        pickedFoodId?.let {
            viewModel.addIngredient(it)
            onPickedFoodConsumed()
        }
    }
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    Scaffold(
        contentWindowInsets = formWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(if (state.editing) "Edit recipe" else "New recipe") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.editing) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete recipe")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

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
                placeholder = { Text("Sunday chilli") },
                singleLine = true,
                isError = state.name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Raw ingredients",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAddIngredient) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Add", modifier = Modifier.padding(start = 4.dp))
                }
            }

            if (state.ingredients.isEmpty()) {
                Text(
                    text = "Add everything that goes in the pan, weighed raw.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.ingredients.forEach { row ->
                IngredientRowEditor(
                    row = row,
                    onGramsChange = { viewModel.setIngredientGrams(row.key, it) },
                    onRemove = { viewModel.removeIngredient(row.key) },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("After cooking", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Weigh the finished dish and put that here. Cooking changes what food " +
                    "weighs but not how much energy is in it, so this is what makes a portion " +
                    "of the cooked dish come out right.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.cookedGramsText,
                onValueChange = viewModel::setCookedGrams,
                label = { Text("Cooked weight") },
                suffix = { Text("g") },
                placeholder = { Text(Format.amount(state.rawGrams)) },
                supportingText = {
                    val ratio = state.yieldRatio
                    Text(
                        when {
                            ratio == null -> "Leave blank for anything not cooked — a salad, a smoothie."
                            ratio < 1 -> "Lost ${((1 - ratio) * 100).roundToInt()}% of its raw weight."
                            ratio > 1 -> "Gained ${((ratio - 1) * 100).roundToInt()}% over its raw weight."
                            else -> "Same as raw."
                        }
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            RecipeSummary(state)

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.editing) "Save changes" else "Save recipe") }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${state.name}?") },
            text = {
                Text(
                    "The recipe and the food it produced both go away. Days you have already " +
                        "logged keep their numbers."
                )
            },
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
private fun IngredientRowEditor(
    row: IngredientRow,
    onGramsChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${Format.kcal(row.nutrients.kcal)} kcal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = row.gramsText,
            onValueChange = onGramsChange,
            suffix = { Text("g") },
            singleLine = true,
            isError = row.grams == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(120.dp),
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Clear,
                contentDescription = "Remove ${row.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecipeSummary(state: RecipeEditUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("The whole dish", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Raw weight", style = MaterialTheme.typography.bodyMedium)
                Text(Format.grams(state.rawGrams), style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Portioned from", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = Format.grams(state.yieldGrams),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            NutrientTable(state.total)

            state.per100g?.let { per100g ->
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Per 100 g cooked", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "${Format.kcal(per100g.kcal)} kcal",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "Log a portion by weighing what you serve yourself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
