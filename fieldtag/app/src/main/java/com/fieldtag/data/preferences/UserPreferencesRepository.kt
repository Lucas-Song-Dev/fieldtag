package com.fieldtag.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.fieldTagDataStore: DataStore<Preferences> by preferencesDataStore(name = "fieldtag_preferences")

private val KEY_WELCOME_COMPLETED = booleanPreferencesKey("welcome_completed")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val hasCompletedWelcome: Flow<Boolean> = context.fieldTagDataStore.data.map { prefs ->
        prefs[KEY_WELCOME_COMPLETED] ?: false
    }

    suspend fun setWelcomeCompleted() {
        context.fieldTagDataStore.edit { it[KEY_WELCOME_COMPLETED] = true }
    }
}
