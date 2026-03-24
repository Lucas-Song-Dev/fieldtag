package com.fieldtag.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtag.data.preferences.UserPreferencesRepository
import com.fieldtag.domain.auth.AuthRepository
import com.fieldtag.domain.auth.AuthSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val session = authRepository.session.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthSession.SignedOut,
    )

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    fun signInWithGoogleStub() {
        viewModelScope.launch {
            authRepository.signInWithGoogleStub()
            userPreferencesRepository.setWelcomeCompleted()
        }
    }
}
