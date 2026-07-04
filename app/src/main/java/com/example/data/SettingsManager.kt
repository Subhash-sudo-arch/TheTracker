package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "thetracker_settings")

class SettingsManager(private val context: Context) {

    companion object {
        val THEME_MODE = intPreferencesKey("theme_mode") // 0 = System, 1 = Light, 2 = Dark
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        
        // Goals
        val GOAL_SLEEP = floatPreferencesKey("goal_sleep")
        val GOAL_STUDY = floatPreferencesKey("goal_study")
        val GOAL_PRODUCTIVITY = floatPreferencesKey("goal_productivity")
        val GOAL_WORKOUT = floatPreferencesKey("goal_workout")
        val GOAL_ACTIVITY = floatPreferencesKey("goal_activity")
        val GOAL_TRAINING = floatPreferencesKey("goal_training")
        val GOAL_HABIT = floatPreferencesKey("goal_habit")

        // Reminders
        val REMINDER_SLEEP = booleanPreferencesKey("reminder_sleep")
        val REMINDER_STUDY = booleanPreferencesKey("reminder_study")
        val REMINDER_WORKOUT = booleanPreferencesKey("reminder_workout")
        val REMINDER_HABIT = booleanPreferencesKey("reminder_habit")
    }

    val themeModeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: 0
    }

    val dynamicColorsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DYNAMIC_COLORS] ?: true
    }

    // Goals Flows
    val goalSleepFlow: Flow<Float> = context.dataStore.data.map { preferences -> preferences[GOAL_SLEEP] ?: 8.0f }
    val goalStudyFlow: Flow<Float> = context.dataStore.data.map { preferences -> preferences[GOAL_STUDY] ?: 4.0f }
    val goalProductivityFlow: Flow<Float> = context.dataStore.data.map { preferences -> preferences[GOAL_PRODUCTIVITY] ?: 6.0f }
    val goalWorkoutFlow: Flow<Float> = context.dataStore.data.map { preferences -> preferences[GOAL_WORKOUT] ?: 1.0f }
    val goalActivityFlow: Flow<Float> = context.dataStore.data.map { preferences -> preferences[GOAL_ACTIVITY] ?: 1.0f }
    val goalTrainingFlow: Flow<Float> = context.dataStore.data.map { preferences -> preferences[GOAL_TRAINING] ?: 1.0f }
    val goalHabitFlow: Flow<Float> = context.dataStore.data.map { preferences -> preferences[GOAL_HABIT] ?: 1.0f }

    // Reminders Flows
    val reminderSleepFlow: Flow<Boolean> = context.dataStore.data.map { preferences -> preferences[REMINDER_SLEEP] ?: false }
    val reminderStudyFlow: Flow<Boolean> = context.dataStore.data.map { preferences -> preferences[REMINDER_STUDY] ?: false }
    val reminderWorkoutFlow: Flow<Boolean> = context.dataStore.data.map { preferences -> preferences[REMINDER_WORKOUT] ?: false }
    val reminderHabitFlow: Flow<Boolean> = context.dataStore.data.map { preferences -> preferences[REMINDER_HABIT] ?: false }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun setDynamicColors(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DYNAMIC_COLORS] = enabled
        }
    }

    suspend fun setGoal(key: Preferences.Key<Float>, value: Float) {
        context.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    suspend fun setReminder(key: Preferences.Key<Boolean>, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[key] = enabled
        }
    }
}
