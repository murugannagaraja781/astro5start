package com.astro5star.app.ui.astrologerlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astro5star.app.data.model.Astrologer
import kotlinx.coroutines.launch

sealed class UiState {
    object Loading : UiState()
    data class Success(val data: List<Astrologer>, val isPagination: Boolean = false) : UiState()
    data class Error(val message: String) : UiState()
}

class AstrologerViewModel : ViewModel() {

    private val repository = AstrologerRepository()
    
    private val _uiState = MutableLiveData<UiState>()
    val uiState: LiveData<UiState> = _uiState

    private var currentPage = 1
    private var isLastPage = false
    private val fullList = mutableListOf<Astrologer>()

    fun loadAstrologers(isInitial: Boolean = true) {
        if (isInitial) {
            currentPage = 1
            isLastPage = false
            fullList.clear()
            _uiState.value = UiState.Loading
        }

        if (isLastPage) return

        viewModelScope.launch {
            try {
                val newData = repository.getAstrologers(currentPage, 20)
                if (newData.isEmpty()) {
                    isLastPage = true
                } else {
                    fullList.addAll(newData)
                    currentPage++
                    _uiState.value = UiState.Success(fullList.toList(), !isInitial)
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unknown Error")
            }
        }
    }
}
