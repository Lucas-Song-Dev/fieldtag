package com.fieldtag.domain.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthSession {
    data object SignedOut : AuthSession
    data class SignedIn(val displayName: String?) : AuthSession
}

interface AuthRepository {
    val session: Flow<AuthSession>
    suspend fun signOut()
    /** Stub until Supabase / Credential Manager — debug-only implementation in [StubAuthRepository]. */
    suspend fun signInWithGoogleStub()
}

@Singleton
class StubAuthRepository @Inject constructor() : AuthRepository {
    private val _session = MutableStateFlow<AuthSession>(AuthSession.SignedOut)
    override val session: Flow<AuthSession> = _session.asStateFlow()

    override suspend fun signOut() {
        _session.value = AuthSession.SignedOut
    }

    override suspend fun signInWithGoogleStub() {
        _session.value = AuthSession.SignedIn(displayName = "Preview user")
    }
}
