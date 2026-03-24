package com.fieldtag.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtag.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    fun completeWelcome(onDone: () -> Unit) {
        viewModelScope.launch {
            userPreferencesRepository.setWelcomeCompleted()
            onDone()
        }
    }
}
