package io.github.augustinavicius.nutrition.ui.entry

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
import io.github.augustinavicius.nutrition.core.Food
import io.github.augustinavicius.nutrition.core.FoodSource
import io.github.augustinavicius.nutrition.core.Format
import io.github.augustinavicius.nutrition.core.MealType
import io.github.augustinavicius.nutrition.core.Nutrients
import io.github.augustinavicius.nutrition.data.repo.DiaryRepository
import io.github.augustinavicius.nutrition.data.repo.FoodRepository
import io.github.augustinavicius.nutrition.data.repo.RecipeRepository
import io.github.augustinavicius.nutrition.ui.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class LogEntryUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val editing: Boolean = false,
    val foodId: Long = 0,
    val name: String = "",
    val brand: String? = null,
    val imageUrl: String? = null,
    val per100g: Nutrients = Nutrients(0.0, 0.0, 0.0, 0.0),
    val gramsText: String = DEFAULT_GRAMS,
    val meal: MealType = MealType.SNACK,
    val date: LocalDate = LocalDate.now(),
    val favorite: Boolean = false,
    val done: Boolean = false,
    /** Set when this food is a cooked dish, so the recipe behind it can be opened. */
    val recipeId: Long? = null,
) {
    /** The typed amount in grams, or null while the field is empty or unusable. */
    val grams: Double?
        get() = DecimalInput.parse(gramsText)?.takeIf { it > 0 }

    val preview: Nutrients get() = per100g * ((grams ?: 0.0) / 100.0)

    val canSave: Boolean get() = !loading && !notFound && grams != null

    companion object {
        /** A portion, not a gram: nobody logs a single gram of anything. */
        const val DEFAULT_GRAMS = "100"
    }
}

class LogEntryViewModel(
    private val foods: FoodRepository,
    private val diary: DiaryRepository,
    private val recipes: RecipeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route: Routes.LogEntry = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(LogEntryUiState())
    val state: StateFlow<LogEntryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (route.entryId != 0L) loadEntry(route.entryId) else loadFood(route.foodId)
        }
    }

    private suspend fun loadEntry(entryId: Long) {
        val entry = diary.entry(entryId)
        if (entry == null) {
            _state.update { it.copy(loading = false, notFound = true) }
            return
        }
        _state.value = LogEntryUiState(
            loading = false,
            editing = true,
            foodId = entry.foodId ?: 0,
            name = entry.name,
            brand = entry.brand,
            per100g = entry.per100g,
            gramsText = Format.amount(entry.grams),
            meal = entry.meal,
            date = entry.date,
        )
    }

    private suspend fun loadFood(foodId: Long) {
        val food = foods.food(foodId)
        if (food == null) {
            _state.update { it.copy(loading = false, notFound = true) }
            return
        }
        _state.value = LogEntryUiState(
            loading = false,
            editing = false,
            foodId = food.id,
            name = food.name,
            brand = food.brand,
            imageUrl = food.imageUrl,
            per100g = food.per100g,
            // An explicit choice — the diary's "add to breakfast" — wins; otherwise carry on
            // from whatever was logged last, falling back to the clock for an empty diary.
            meal = route.meal?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
                ?: diary.lastLoggedMeal()
                ?: MealType.forHour(LocalTime.now().hour),
            date = LocalDate.ofEpochDay(route.dateEpochDay),
            favorite = food.favorite,
            recipeId = if (food.source == FoodSource.RECIPE) {
                recipes.recipeIdForFood(food.id)
            } else {
                null
            },
        )
    }

    fun setGrams(text: String) = _state.update { it.copy(gramsText = DecimalInput.sanitize(text)) }

    fun setMeal(meal: MealType) = _state.update { it.copy(meal = meal) }

    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }

    fun toggleFavorite() {
        val current = _state.value
        val foodId = current.foodId.takeIf { it != 0L } ?: return
        val next = !current.favorite
        _state.update { it.copy(favorite = next) }
        viewModelScope.launch { foods.setFavorite(foodId, next) }
    }

    fun save() {
        val current = _state.value
        val grams = current.grams ?: return
        viewModelScope.launch {
            if (current.editing) {
                diary.entry(route.entryId)?.let { existing ->
                    diary.update(existing.copy(grams = grams, meal = current.meal, date = current.date))
                }
            } else {
                diary.log(
                    food = Food(
                        id = current.foodId,
                        name = current.name,
                        brand = current.brand,
                        per100g = current.per100g,
                    ),
                    grams = grams,
                    meal = current.meal,
                    date = current.date,
                )
            }
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val entryId = route.entryId.takeIf { it != 0L } ?: return
        viewModelScope.launch {
            diary.deleteById(entryId)
            _state.update { it.copy(done = true) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutritionApp
                LogEntryViewModel(
                    app.container.foodRepository,
                    app.container.diaryRepository,
                    app.container.recipeRepository,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
