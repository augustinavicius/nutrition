package io.github.augustinavicius.nutrition.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.augustinavicius.nutrition.NutritionApp
import io.github.augustinavicius.nutrition.data.repo.BarcodeResult
import io.github.augustinavicius.nutrition.data.repo.FoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ScanState {
    data object Scanning : ScanState
    data class Looking(val barcode: String) : ScanState

    /** Ready to log — the caller should navigate to the log screen with [foodId]. */
    data class Resolved(val foodId: Long, val name: String) : ScanState

    /** Known barcode with no usable nutrition, or a barcode nobody has catalogued yet. */
    data class NeedsFood(val barcode: String, val name: String?, val brand: String?) : ScanState

    data class Failed(val barcode: String, val message: String) : ScanState
}

class ScanViewModel(private val foods: FoodRepository) : ViewModel() {

    private val _state = MutableStateFlow<ScanState>(ScanState.Scanning)
    val state: StateFlow<ScanState> = _state

    fun onBarcode(barcode: String) {
        if (_state.value !is ScanState.Scanning) return
        _state.value = ScanState.Looking(barcode)

        viewModelScope.launch {
            _state.value = when (val result = foods.lookupBarcode(barcode)) {
                is BarcodeResult.Found -> {
                    val id = if (result.food.id != 0L) result.food.id else foods.save(result.food)
                    ScanState.Resolved(id, result.food.name)
                }
                is BarcodeResult.NeedsDetails ->
                    ScanState.NeedsFood(barcode, result.draft.name, result.draft.brand)
                is BarcodeResult.Unknown -> ScanState.NeedsFood(barcode, null, null)
                is BarcodeResult.Error -> ScanState.Failed(barcode, result.message)
            }
        }
    }

    /** Returns the scanner to a live state after a failure or after the user backs out. */
    fun resume() {
        _state.value = ScanState.Scanning
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutritionApp
                ScanViewModel(app.container.foodRepository)
            }
        }
    }
}
