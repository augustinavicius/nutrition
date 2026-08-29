package io.github.augustinavicius.nutrition.ui.food

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
import io.github.augustinavicius.nutrition.core.FoodSource
import io.github.augustinavicius.nutrition.core.Format
import io.github.augustinavicius.nutrition.core.Nutrients
import io.github.augustinavicius.nutrition.data.repo.FoodRepository
import io.github.augustinavicius.nutrition.ui.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Whether the typed numbers describe one serving or 100 g. */
enum class NutrientBasis(val label: String) {
    PER_SERVING("Per serving"),
    PER_100G("Per 100 g"),
}

data class EditFoodUiState(
    val loading: Boolean = true,
    val id: Long = 0,
    val name: String = "",
    val brand: String = "",
    val barcode: String = "",
    val servingLabel: String = "100 g",
    val servingGramsText: String = "100",
    val basis: NutrientBasis = NutrientBasis.PER_100G,
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val fiber: String = "",
    val sugar: String = "",
    val sodiumMg: String = "",
    val savedId: Long? = null,
    val deleted: Boolean = false,
    val error: String? = null,
) {
    val editing: Boolean get() = id != 0L

    val servingGrams: Double? get() = servingGramsText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

    /** Per-100 g entry only makes sense once we know what a serving weighs. */
    val basisSwitchable: Boolean get() = servingGrams != null

    val kcalValue: Double? get() = kcal.replace(',', '.').toDoubleOrNull()

    val nameValid: Boolean get() = name.isNotBlank()

    val canSave: Boolean get() = nameValid && kcalValue != null && kcalValue!! >= 0

    private fun num(text: String): Double = text.replace(',', '.').toDoubleOrNull() ?: 0.0
    private fun optional(text: String): Double? =
        text.takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()

    /** The typed figures, exactly as entered, on whichever basis is selected. */
    val entered: Nutrients
        get() = Nutrients(
            kcal = num(kcal),
            protein = num(protein),
            carbs = num(carbs),
            fat = num(fat),
            fiber = optional(fiber),
            sugar = optional(sugar),
            sodiumMg = optional(sodiumMg),
        )

    val perServing: Nutrients
        get() = when (basis) {
            NutrientBasis.PER_SERVING -> entered
            NutrientBasis.PER_100G -> entered * ((servingGrams ?: 100.0) / 100.0)
        }

    /** Energy implied by the macros, used for a gentle "these don't add up" hint. */
    val macroKcal: Double get() = entered.kcalFromMacros

    val macroMismatch: Boolean
        get() {
            val stated = kcalValue ?: return false
            if (stated <= 0 || macroKcal <= 0) return false
            return abs(stated - macroKcal) > maxOf(25.0, stated * 0.2)
        }
}

class EditFoodViewModel(
    private val foods: FoodRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route: Routes.EditFood = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(EditFoodUiState())
    val state: StateFlow<EditFoodUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = route.foodId.takeIf { it != 0L }?.let { foods.food(it) }
            _state.value = if (existing != null) {
                existing.toUiState()
            } else {
                EditFoodUiState(
                    loading = false,
                    name = route.prefillName.orEmpty(),
                    brand = route.prefillBrand.orEmpty(),
                    barcode = route.barcode.orEmpty(),
                )
            }
        }
    }

    private fun Food.toUiState(): EditFoodUiState {
        val grams = servingGrams
        // Show the food on the basis it was authored in: per 100 g when a weight is known,
        // which is how packaging almost always states it.
        val basis = if (grams != null && grams > 0) NutrientBasis.PER_100G else NutrientBasis.PER_SERVING
        val values = if (basis == NutrientBasis.PER_100G) per100g ?: perServing else perServing
        return EditFoodUiState(
            loading = false,
            id = id,
            name = name,
            brand = brand.orEmpty(),
            barcode = barcode.orEmpty(),
            servingLabel = servingLabel,
            servingGramsText = grams?.let { Format.amount(it) }.orEmpty(),
            basis = basis,
            kcal = Format.amount(values.kcal),
            protein = Format.amount(values.protein),
            carbs = Format.amount(values.carbs),
            fat = Format.amount(values.fat),
            fiber = values.fiber?.let { Format.amount(it) }.orEmpty(),
            sugar = values.sugar?.let { Format.amount(it) }.orEmpty(),
            sodiumMg = values.sodiumMg?.let { Format.amount(it) }.orEmpty(),
        )
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setBrand(value: String) = _state.update { it.copy(brand = value) }
    fun setBarcode(value: String) = _state.update { it.copy(barcode = value.filter(Char::isDigit)) }
    fun setServingLabel(value: String) = _state.update { it.copy(servingLabel = value) }
    fun setServingGrams(value: String) = _state.update { it.copy(servingGramsText = value) }
    fun setKcal(value: String) = _state.update { it.copy(kcal = value) }
    fun setProtein(value: String) = _state.update { it.copy(protein = value) }
    fun setCarbs(value: String) = _state.update { it.copy(carbs = value) }
    fun setFat(value: String) = _state.update { it.copy(fat = value) }
    fun setFiber(value: String) = _state.update { it.copy(fiber = value) }
    fun setSugar(value: String) = _state.update { it.copy(sugar = value) }
    fun setSodium(value: String) = _state.update { it.copy(sodiumMg = value) }

    /** Rescales the typed numbers so switching basis never silently changes the food. */
    fun setBasis(basis: NutrientBasis) = _state.update { current ->
        if (basis == current.basis) return@update current
        val grams = current.servingGrams ?: return@update current.copy(basis = basis)
        val factor = when (basis) {
            NutrientBasis.PER_SERVING -> grams / 100.0
            NutrientBasis.PER_100G -> 100.0 / grams
        }
        val scaled = current.entered * factor
        current.copy(
            basis = basis,
            kcal = Format.amount(scaled.kcal),
            protein = Format.amount(scaled.protein),
            carbs = Format.amount(scaled.carbs),
            fat = Format.amount(scaled.fat),
            fiber = scaled.fiber?.let { Format.amount(it) }.orEmpty(),
            sugar = scaled.sugar?.let { Format.amount(it) }.orEmpty(),
            sodiumMg = scaled.sodiumMg?.let { Format.amount(it) }.orEmpty(),
        )
    }

    fun useMacroEnergy() = _state.update { it.copy(kcal = Format.amount(it.macroKcal)) }

    fun save() {
        val current = _state.value
        if (!current.canSave) {
            _state.update { it.copy(error = "Give the food a name and an energy value.") }
            return
        }
        viewModelScope.launch {
            val id = foods.save(
                Food(
                    id = current.id,
                    name = current.name.trim(),
                    brand = current.brand.trim().takeIf { it.isNotEmpty() },
                    barcode = current.barcode.trim().takeIf { it.isNotEmpty() },
                    servingLabel = current.servingLabel.trim().ifEmpty {
                        current.servingGrams?.let { "${Format.amount(it)} g" } ?: "serving"
                    },
                    servingGrams = current.servingGrams,
                    perServing = current.perServing,
                    source = FoodSource.CUSTOM,
                )
            )
            _state.update { it.copy(savedId = if (id > 0) id else current.id, error = null) }
        }
    }

    fun delete() {
        val current = _state.value
        if (!current.editing) return
        viewModelScope.launch {
            foods.food(current.id)?.let { foods.delete(it) }
            _state.update { it.copy(deleted = true) }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutritionApp
                EditFoodViewModel(app.container.foodRepository, createSavedStateHandle())
            }
        }
    }
}
