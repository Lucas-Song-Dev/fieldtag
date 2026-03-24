package com.fieldtag.domain.auth

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StubAuthRepositoryTest {

    @Test
    fun initialSession_isSignedOut() = runTest {
        val repo = StubAuthRepository()
        assertThat(repo.session.first()).isEqualTo(AuthSession.SignedOut)
    }

    @Test
    fun signInWithGoogleStub_emitsSignedIn() = runTest {
        val repo = StubAuthRepository()
        repo.signInWithGoogleStub()
        val session = repo.session.first()
        assertThat(session).isInstanceOf(AuthSession.SignedIn::class.java)
        assertThat((session as AuthSession.SignedIn).displayName).isEqualTo("Preview user")
    }

    @Test
    fun signOut_afterSignIn_returnsSignedOut() = runTest {
        val repo = StubAuthRepository()
        repo.signInWithGoogleStub()
        repo.signOut()
        assertThat(repo.session.first()).isEqualTo(AuthSession.SignedOut)
    }
}
