package io.github.augustinavicius.nutrition.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.augustinavicius.nutrition.R
import io.github.augustinavicius.nutrition.core.Food
import io.github.augustinavicius.nutrition.core.Format
import io.github.augustinavicius.nutrition.core.MealType
import io.github.augustinavicius.nutrition.ui.components.EmptyState
import io.github.augustinavicius.nutrition.ui.components.FoodListItem
import io.github.augustinavicius.nutrition.ui.components.SectionHeader
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    meal: MealType?,
    date: LocalDate,
    onPickFood: (foodId: Long) -> Unit,
    onCreateFood: (prefillName: String?) -> Unit,
    onCreateRecipe: () -> Unit,
    onScan: () -> Unit,
    bottomBar: @Composable () -> Unit,
    pickIngredient: Boolean = false,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val local by viewModel.localResults.collectAsStateWithLifecycle()
    val remote by viewModel.remoteResults.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    val pick: (Food) -> Unit = { food -> viewModel.resolveForLogging(food, onPickFood) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (pickIngredient) "Pick an ingredient" else "Add food")
                        meal?.let {
                            Text(
                                text = "${it.label} · ${Format.day(date)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (!pickIngredient) {
                        IconButton(onClick = onScan) {
                            Icon(painterResource(R.drawable.ic_barcode), contentDescription = "Scan a barcode")
                        }
                    }
                },
            )
        },
        bottomBar = { if (!pickIngredient) bottomBar() },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search foods and brands") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                // Creating from inside a pick would nest one editor in another; the
                // ingredient list is for choosing what already exists.
                if (!pickIngredient) {
                    item("create") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { onCreateFood(query.trim().takeIf { it.isNotEmpty() }) }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text(
                                    text = if (query.isBlank()) "Create a food" else "Create \"${query.trim()}\"",
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            TextButton(onClick = onCreateRecipe) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text("Create a recipe", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }

                item("local-header") {
                    SectionHeader(if (query.isBlank()) "Recent and saved" else "Your foods")
                }
                if (local.isEmpty()) {
                    item("local-empty") {
                        Text(
                            text = if (query.isBlank()) {
                                "Foods you log will show up here."
                            } else {
                                "No saved food matches that."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    items(local, key = { "local-${it.id}" }) { food ->
                        FoodListItem(food = food, onClick = { pick(food) })
                    }
                }

                item("remote-divider") { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item("remote-header") {
                    SectionHeader("Open Food Facts") {
                        if (remote is RemoteSearchState.Loading) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                }

                when (val state = remote) {
                    RemoteSearchState.Idle -> item("remote-idle") {
                        Text(
                            text = "Type at least two letters to search the global food database.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    RemoteSearchState.Loading -> item("remote-loading") {
                        Text(
                            text = "Searching…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    is RemoteSearchState.Failed -> item("remote-error") {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    is RemoteSearchState.Results -> {
                        if (state.foods.isEmpty()) {
                            item("remote-empty") {
                                EmptyState(
                                    title = "No matches",
                                    body = "Nothing in Open Food Facts matched \"${query.trim()}\". " +
                                        "You can add it yourself.",
                                )
                            }
                        } else {
                            items(state.foods, key = { "remote-${it.barcode}" }) { food ->
                                FoodListItem(food = food, onClick = { pick(food) })
                            }
                        }
                    }
                }
            }
        }
    }
}
