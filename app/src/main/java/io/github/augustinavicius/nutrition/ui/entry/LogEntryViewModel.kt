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
import io.github.augustinavicius.nutrition.core.Food
import io.github.augustinavicius.nutrition.core.Format
import io.github.augustinavicius.nutrition.core.MealType
import io.github.augustinavicius.nutrition.core.Nutrients
import io.github.augustinavicius.nutrition.data.repo.DiaryRepository
import io.github.augustinavicius.nutrition.data.repo.FoodRepository
import io.github.augustinavicius.nutrition.ui.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

enum class AmountUnit { SERVING, GRAM }

data class LogEntryUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val editing: Boolean = false,
    val foodId: Long = 0,
    val name: String = "",
    val brand: String? = null,
    val imageUrl: String? = null,
    val servingLabel: String = "serving",
    val servingGrams: Double? = null,
    val perServing: Nutrients = Nutrients(0.0, 0.0, 0.0, 0.0),
    val amountText: String = "1",
    val unit: AmountUnit = AmountUnit.SERVING,
    val meal: MealType = MealType.SNACK,
    val date: LocalDate = LocalDate.now(),
    val favorite: Boolean = false,
    val done: Boolean = false,
) {
    val gramsSupported: Boolean get() = (servingGrams ?: 0.0) > 0

    /** How many servings the typed amount represents, or null while the field is unusable. */
    val servings: Double?
        get() {
            val amount = amountText.replace(',', '.').toDoubleOrNull() ?: return null
            if (amount <= 0) return null
            return when (unit) {
                AmountUnit.SERVING -> amount
                AmountUnit.GRAM -> servingGrams?.takeIf { it > 0 }?.let { amount / it }
            }
        }

    val preview: Nutrients get() = perServing * (servings ?: 0.0)

    val canSave: Boolean get() = !loading && !notFound && servings != null

    val amountSuffix: String
        get() = when (unit) {
            AmountUnit.GRAM -> "g"
            AmountUnit.SERVING -> "× $servingLabel"
        }

    val secondaryAmountLabel: String?
        get() = when {
            !gramsSupported -> null
            unit == AmountUnit.SERVING -> servings?.let { "${Format.amount(it * servingGrams!!)} g" }
            else -> servings?.let { "${Format.amount(it)} × $servingLabel" }
        }
}

class LogEntryViewModel(
    private val foods: FoodRepository,
    private val diary: DiaryRepository,
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
        val gramsBased = (entry.servingGrams ?: 0.0) > 0
        _state.value = LogEntryUiState(
            loading = false,
            editing = true,
            foodId = entry.foodId ?: 0,
            name = entry.name,
            brand = entry.brand,
            servingLabel = entry.servingLabel,
            servingGrams = entry.servingGrams,
            perServing = entry.perServing,
            unit = if (gramsBased) AmountUnit.GRAM else AmountUnit.SERVING,
            amountText = if (gramsBased) {
                Format.amount(entry.servings * entry.servingGrams!!)
            } else {
                Format.amount(entry.servings)
            },
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
        val gramsBased = (food.servingGrams ?: 0.0) > 0
        _state.value = LogEntryUiState(
            loading = false,
            editing = false,
            foodId = food.id,
            name = food.name,
            brand = food.brand,
            imageUrl = food.imageUrl,
            servingLabel = food.servingLabel,
            servingGrams = food.servingGrams,
            perServing = food.perServing,
            unit = if (gramsBased) AmountUnit.GRAM else AmountUnit.SERVING,
            amountText = if (gramsBased) Format.amount(food.servingGrams!!) else "1",
            meal = route.meal?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
                ?: MealType.forHour(LocalTime.now().hour),
            date = LocalDate.ofEpochDay(route.dateEpochDay),
            favorite = food.favorite,
        )
    }

    fun setAmount(text: String) = _state.update { it.copy(amountText = text) }

    /** Switching units keeps the same real quantity so the numbers never jump. */
    fun setUnit(unit: AmountUnit) = _state.update { current ->
        if (unit == current.unit) return@update current
        val servings = current.servings
        val grams = current.servingGrams
        val text = when {
            servings == null || grams == null || grams <= 0 -> current.amountText
            unit == AmountUnit.GRAM -> Format.amount(servings * grams)
            else -> Format.amount(servings)
        }
        current.copy(unit = unit, amountText = text)
    }

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
        val servings = current.servings ?: return
        viewModelScope.launch {
            if (current.editing) {
                diary.entry(route.entryId)?.let { existing ->
                    diary.update(
                        existing.copy(
                            servings = servings,
                            meal = current.meal,
                            date = current.date,
                        )
                    )
                }
            } else {
                diary.log(
                    food = Food(
                        id = current.foodId,
                        name = current.name,
                        brand = current.brand,
                        servingLabel = current.servingLabel,
                        servingGrams = current.servingGrams,
                        perServing = current.perServing,
                    ),
                    servings = servings,
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
                    createSavedStateHandle(),
                )
            }
        }
    }
}
