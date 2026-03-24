package com.fieldtag.data.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented persistence checks for DataStore. With Android Test Orchestrator +
 * `clearPackageData` in `defaultConfig`, each test method starts from a clean app state.
 */
@RunWith(AndroidJUnit4::class)
class UserPreferencesRepositoryInstrumentedTest {

    @Test
    fun hasCompletedWelcome_defaultsFalse_thenPersistsTrue() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = UserPreferencesRepository(context)
        assertThat(repo.hasCompletedWelcome.first()).isFalse()
        repo.setWelcomeCompleted()
        assertThat(repo.hasCompletedWelcome.first()).isTrue()
    }
}
