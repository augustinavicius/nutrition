package io.github.augustinavicius.nutrition.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.augustinavicius.nutrition.NutritionApp
import io.github.augustinavicius.nutrition.core.Food
import io.github.augustinavicius.nutrition.data.repo.FoodRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RemoteSearchState {
    /** Query too short to be worth a network round trip. */
    data object Idle : RemoteSearchState
    data object Loading : RemoteSearchState
    data class Results(val foods: List<Food>) : RemoteSearchState
    data class Failed(val message: String) : RemoteSearchState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(private val foods: FoodRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    /** Recents when the box is empty, otherwise matches from the on-device catalogue. */
    val localResults: StateFlow<List<Food>> = _query
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { text ->
            if (text.isEmpty()) foods.observeRecent() else foods.searchLocal(text)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Open Food Facts results, debounced so typing does not fire a request per keystroke —
     * both to stay well inside their rate limits and to keep the list from thrashing.
     */
    val remoteResults: StateFlow<RemoteSearchState> = _query
        .map { it.trim() }
        .distinctUntilChanged()
        .debounce(350)
        .flatMapLatest { text ->
            if (text.length < MIN_REMOTE_QUERY) {
                flowOf(RemoteSearchState.Idle)
            } else {
                flow {
                    val result = foods.searchRemote(text)
                    emit(
                        result.fold(
                            onSuccess = { RemoteSearchState.Results(it) },
                            onFailure = { RemoteSearchState.Failed(it.message ?: "Search failed") },
                        )
                    )
                }.onStart { emit(RemoteSearchState.Loading) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemoteSearchState.Idle)

    fun setQuery(value: String) {
        _query.value = value
    }

    /**
     * Makes sure a food has a database id before it is logged. Results coming straight from
     * Open Food Facts are cached on the way through, so the same product is instant next time.
     */
    fun resolveForLogging(food: Food, onResolved: (Long) -> Unit) {
        if (food.id != 0L) {
            onResolved(food.id)
            return
        }
        viewModelScope.launch { onResolved(foods.cacheRemote(food)) }
    }

    companion object {
        private const val MIN_REMOTE_QUERY = 2

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutritionApp
                SearchViewModel(app.container.foodRepository)
            }
        }
    }
}
