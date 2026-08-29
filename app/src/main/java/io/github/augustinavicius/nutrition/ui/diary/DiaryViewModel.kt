package io.github.augustinavicius.nutrition.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.augustinavicius.nutrition.NutritionApp
import io.github.augustinavicius.nutrition.core.DiaryEntry
import io.github.augustinavicius.nutrition.core.Goals
import io.github.augustinavicius.nutrition.core.MealType
import io.github.augustinavicius.nutrition.core.Nutrients
import io.github.augustinavicius.nutrition.core.sum
import io.github.augustinavicius.nutrition.data.prefs.SettingsStore
import io.github.augustinavicius.nutrition.data.repo.DiaryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DiaryUiState(
    val date: LocalDate = LocalDate.now(),
    val goals: Goals = Goals(),
    val entriesByMeal: Map<MealType, List<DiaryEntry>> = emptyMap(),
    val totals: Nutrients = Nutrients(0.0, 0.0, 0.0, 0.0),
    /** Energy for the seven days ending on [date], oldest first, for the trend strip. */
    val week: List<Pair<LocalDate, Double>> = emptyList(),
) {
    val isEmpty: Boolean get() = entriesByMeal.values.all { it.isEmpty() }

    fun mealTotal(meal: MealType): Nutrients =
        entriesByMeal[meal].orEmpty().map { it.total }.sum()
}

class DiaryViewModel(
    private val diary: DiaryRepository,
    settings: SettingsStore,
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now())

    /** Set when an entry is deleted so the UI can offer an undo. */
    private val _lastDeleted = MutableStateFlow<DiaryEntry?>(null)
    val lastDeleted: StateFlow<DiaryEntry?> = _lastDeleted

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dayData = selectedDate.flatMapLatest { date ->
        combine(
            diary.observeDay(date),
            diary.observeEnergyBetween(date.minusDays(6), date),
        ) { entries, energy ->
            Triple(date, entries, energy)
        }
    }

    val state: StateFlow<DiaryUiState> = combine(dayData, settings.goals) { (date, entries, energy), goals ->
        DiaryUiState(
            date = date,
            goals = goals,
            entriesByMeal = MealType.entries.associateWith { meal -> entries.filter { it.meal == meal } },
            totals = entries.map { it.total }.sum(),
            week = (6 downTo 0).map { back ->
                val day = date.minusDays(back.toLong())
                day to (energy[day] ?: 0.0)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiaryUiState())

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun shiftDay(days: Long) {
        selectedDate.value = selectedDate.value.plusDays(days)
    }

    fun delete(entry: DiaryEntry) {
        viewModelScope.launch {
            diary.delete(entry)
            _lastDeleted.value = entry
        }
    }

    fun undoDelete() {
        val entry = _lastDeleted.value ?: return
        _lastDeleted.value = null
        viewModelScope.launch { diary.restore(entry) }
    }

    fun clearUndo() {
        _lastDeleted.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutritionApp
                DiaryViewModel(app.container.diaryRepository, app.container.settingsStore)
            }
        }
    }
}
