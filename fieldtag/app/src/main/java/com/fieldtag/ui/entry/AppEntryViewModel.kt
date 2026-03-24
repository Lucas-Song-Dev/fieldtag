package com.fieldtag.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtag.data.preferences.UserPreferencesRepository
import com.fieldtag.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface EntryState {
    data object Loading : EntryState
    data class Ready(val startDestination: String) : EntryState
}

@HiltViewModel
class AppEntryViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val entryState = userPreferencesRepository.hasCompletedWelcome.map { completed ->
        EntryState.Ready(
            startDestination = if (completed) Routes.PROJECT_LIST else Routes.WELCOME,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EntryState.Loading,
    )
}
