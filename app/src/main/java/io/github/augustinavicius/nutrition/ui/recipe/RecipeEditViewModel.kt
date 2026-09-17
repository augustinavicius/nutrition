package io.github.augustinavicius.nutrition.ui.recipe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import io.github.augustinavicius.nutrition.NutritionApp
import io.github.augustinavicius.nutrition.core.DecimalInput
import io.github.augustinavicius.nutrition.core.Format
import io.github.augustinavicius.nutrition.core.Nutrients
import io.github.augustinavicius.nutrition.core.Recipe
import io.github.augustinavicius.nutrition.core.RecipeIngredient
import io.github.augustinavicius.nutrition.core.sum
import io.github.augustinavicius.nutrition.data.repo.FoodRepository
import io.github.augustinavicius.nutrition.data.repo.RecipeRepository
import io.github.augustinavicius.nutrition.ui.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** An ingredient as the editor holds it: grams stay text while being typed. */
data class IngredientRow(
    val key: Long,
    val id: Long = 0,
    val foodId: Long?,
    val name: String,
    val brand: String?,
    val gramsText: String,
    val per100g: Nutrients,
) {
    val grams: Double? get() = DecimalInput.parse(gramsText)?.takeIf { it > 0 }

    val nutrients: Nutrients get() = per100g * ((grams ?: 0.0) / 100.0)
}

data class RecipeEditUiState(
    val loading: Boolean = true,
    val id: Long = 0,
    val foodId: Long? = null,
    val name: String = "",
    val ingredients: List<IngredientRow> = emptyList(),
    val cookedGramsText: String = "",
    val savedId: Long? = null,
    val deleted: Boolean = false,
) {
    val editing: Boolean get() = id != 0L

    val rawGrams: Double get() = ingredients.sumOf { it.grams ?: 0.0 }

    val total: Nutrients get() = ingredients.map { it.nutrients }.sum()

    val cookedGrams: Double?
        get() = DecimalInput.parse(cookedGramsText)?.takeIf { it > 0 }

    /** What portions are measured against: the cooked weight if given, else the raw total. */
    val yieldGrams: Double get() = cookedGrams ?: rawGrams

    val per100g: Nutrients?
        get() = yieldGrams.takeIf { it > 0 }?.let { total * (100.0 / it) }

    /** Share of the raw weight left after cooking, e.g. 0.78 for a dish that lost a fifth. */
    val yieldRatio: Double?
        get() = cookedGrams?.let { cooked -> rawGrams.takeIf { it > 0 }?.let { cooked / it } }

    val canSave: Boolean
        get() = name.isNotBlank() && ingredients.any { it.grams != null } && yieldGrams > 0

    fun toRecipe() = Recipe(
        id = id,
        name = name.trim(),
        cookedGrams = cookedGrams,
        foodId = foodId,
        ingredients = ingredients.mapNotNull { row ->
            row.grams?.let {
                RecipeIngredient(
                    id = row.id,
                    foodId = row.foodId,
                    name = row.name,
                    brand = row.brand,
                    grams = it,
                    per100g = row.per100g,
                )
            }
        },
    )
}

class RecipeEditViewModel(
    private val recipes: RecipeRepository,
    private val foods: FoodRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route: Routes.EditRecipe = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(RecipeEditUiState())
    val state: StateFlow<RecipeEditUiState> = _state.asStateFlow()

    private var nextKey = 0L

    init {
        viewModelScope.launch {
            val existing = route.recipeId.takeIf { it != 0L }?.let { recipes.recipe(it) }
            _state.value = if (existing == null) {
                RecipeEditUiState(loading = false)
            } else {
                RecipeEditUiState(
                    loading = false,
                    id = existing.id,
                    foodId = existing.foodId,
                    name = existing.name,
                    cookedGramsText = existing.cookedGrams?.let { Format.amount(it) }.orEmpty(),
                    ingredients = existing.ingredients.map { ingredient ->
                        IngredientRow(
                            key = nextKey++,
                            id = ingredient.id,
                            foodId = ingredient.foodId,
                            name = ingredient.name,
                            brand = ingredient.brand,
                            gramsText = Format.amount(ingredient.grams),
                            per100g = ingredient.per100g,
                        )
                    },
                )
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }

    fun setCookedGrams(value: String) =
        _state.update { it.copy(cookedGramsText = DecimalInput.sanitize(value)) }

    fun setIngredientGrams(key: Long, value: String) = _state.update { current ->
        current.copy(
            ingredients = current.ingredients.map {
                if (it.key == key) it.copy(gramsText = DecimalInput.sanitize(value)) else it
            }
        )
    }

    fun removeIngredient(key: Long) = _state.update { current ->
        current.copy(ingredients = current.ingredients.filterNot { it.key == key })
    }

    /** Called when the picker hands back a food id. Defaults to 100 g, which is easy to edit. */
    fun addIngredient(foodId: Long) {
        viewModelScope.launch {
            val food = foods.food(foodId) ?: return@launch
            _state.update { current ->
                current.copy(
                    ingredients = current.ingredients + IngredientRow(
                        key = nextKey++,
                        foodId = food.id,
                        name = food.name,
                        brand = food.brand,
                        gramsText = "100",
                        per100g = food.per100g,
                    )
                )
            }
        }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            val id = recipes.save(current.toRecipe())
            _state.update { it.copy(savedId = id) }
        }
    }

    fun delete() {
        val current = _state.value
        if (!current.editing) return
        viewModelScope.launch {
            recipes.delete(current.id)
            _state.update { it.copy(deleted = true) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutritionApp
                RecipeEditViewModel(
                    app.container.recipeRepository,
                    app.container.foodRepository,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
