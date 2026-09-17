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
import io.github.augustinavicius.nutrition.core.DecimalInput
import io.github.augustinavicius.nutrition.core.Food
import io.github.augustinavicius.nutrition.core.FoodSource
import io.github.augustinavicius.nutrition.core.Format
import io.github.augustinavicius.nutrition.core.Nutrients
import io.github.augustinavicius.nutrition.data.repo.FoodRepository
import io.github.augustinavicius.nutrition.ui.Routes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Every field is per 100 g, which is how packaging and food databases state nutrition. */
data class EditFoodUiState(
    val loading: Boolean = true,
    val id: Long = 0,
    val name: String = "",
    val brand: String = "",
    val barcode: String = "",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val satFat: String = "",
    val fiber: String = "",
    val sugar: String = "",
    val sodiumMg: String = "",
    val savedId: Long? = null,
    val deleted: Boolean = false,
    val error: String? = null,
    /** Name of a different saved food already using this barcode, if any. */
    val barcodeOwner: String? = null,
) {
    val editing: Boolean get() = id != 0L

    val kcalValue: Double? get() = DecimalInput.parse(kcal)

    val nameValid: Boolean get() = name.isNotBlank()

    val canSave: Boolean get() = nameValid && (kcalValue ?: -1.0) >= 0

    private fun num(text: String): Double = DecimalInput.parse(text) ?: 0.0
    private fun optional(text: String): Double? = DecimalInput.parseOptional(text)

    val per100g: Nutrients
        get() = Nutrients(
            kcal = num(kcal),
            protein = num(protein),
            carbs = num(carbs),
            fat = num(fat),
            satFat = optional(satFat),
            fiber = optional(fiber),
            sugar = optional(sugar),
            sodiumMg = optional(sodiumMg),
        )

}

class EditFoodViewModel(
    private val foods: FoodRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route: Routes.EditFood = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(EditFoodUiState())
    val state: StateFlow<EditFoodUiState> = _state.asStateFlow()

    private var barcodeCheck: Job? = null

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
            refreshBarcodeOwner()
        }

    }

    /**
     * Saving reuses the row a barcode already belongs to, so flag the collision rather than
     * letting the user think they are creating something new.
     */
    private fun refreshBarcodeOwner() {
        barcodeCheck?.cancel()
        val barcode = _state.value.barcode.trim()
        if (barcode.length < MIN_BARCODE_LENGTH) {
            _state.update { it.copy(barcodeOwner = null) }
            return
        }
        barcodeCheck = viewModelScope.launch {
            val owner = foods.foodByBarcode(barcode)?.takeIf { it.id != _state.value.id }
            _state.update { it.copy(barcodeOwner = owner?.name) }
        }
    }

    private fun Food.toUiState() = EditFoodUiState(
        loading = false,
        id = id,
        name = name,
        brand = brand.orEmpty(),
        barcode = barcode.orEmpty(),
        kcal = Format.amount(per100g.kcal),
        protein = Format.amount(per100g.protein),
        carbs = Format.amount(per100g.carbs),
        fat = Format.amount(per100g.fat),
        satFat = per100g.satFat?.let { Format.amount(it) }.orEmpty(),
        fiber = per100g.fiber?.let { Format.amount(it) }.orEmpty(),
        sugar = per100g.sugar?.let { Format.amount(it) }.orEmpty(),
        sodiumMg = per100g.sodiumMg?.let { Format.amount(it) }.orEmpty(),
    )

    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setBrand(value: String) = _state.update { it.copy(brand = value) }
    fun setBarcode(value: String) {
        _state.update { it.copy(barcode = value.filter(Char::isDigit)) }
        refreshBarcodeOwner()
    }
    fun setKcal(value: String) = _state.update { it.copy(kcal = DecimalInput.sanitize(value)) }
    fun setProtein(value: String) = _state.update { it.copy(protein = DecimalInput.sanitize(value)) }
    fun setCarbs(value: String) = _state.update { it.copy(carbs = DecimalInput.sanitize(value)) }
    fun setFat(value: String) = _state.update { it.copy(fat = DecimalInput.sanitize(value)) }
    fun setSatFat(value: String) = _state.update { it.copy(satFat = DecimalInput.sanitize(value)) }
    fun setFiber(value: String) = _state.update { it.copy(fiber = DecimalInput.sanitize(value)) }
    fun setSugar(value: String) = _state.update { it.copy(sugar = DecimalInput.sanitize(value)) }
    fun setSodium(value: String) = _state.update { it.copy(sodiumMg = DecimalInput.sanitize(value)) }

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
                    per100g = current.per100g,
                    source = FoodSource.CUSTOM,
                )
            )
            _state.update { it.copy(savedId = id, error = null) }
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
        /** EAN-8 is the shortest product code worth looking up. */
        private const val MIN_BARCODE_LENGTH = 8

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutritionApp
                EditFoodViewModel(app.container.foodRepository, createSavedStateHandle())
            }
        }
    }
}
