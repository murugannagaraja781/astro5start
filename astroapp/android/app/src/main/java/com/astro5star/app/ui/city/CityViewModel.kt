package com.astro5star.app.ui.city

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astro5star.app.data.remote.nominatim.CityRepository
import com.astro5star.app.data.remote.nominatim.NominatimResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CityUiState(
    val query: String = "",
    val results: List<NominatimResult> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class CityViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CityUiState())
    val uiState: StateFlow<CityUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        searchJob?.cancel()

        if (newQuery.length < 3) {
            _uiState.value = _uiState.value.copy(results = emptyList())
            return
        }

        searchJob = viewModelScope.launch {
            delay(400) // Debounce
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = CityRepository.searchCities(newQuery)
            result.onSuccess { cities ->
                _uiState.value = _uiState.value.copy(results = cities, isLoading = false)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to fetch cities"
                )
            }
        }
    }
}
