package com.example.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.models.LyricAlignment
import com.example.data.models.LyricFontFamily
import com.example.data.models.LyricTextCase
import com.example.data.models.LyricsDisplayConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "yimly_preferences")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_AVATAR_URL = stringPreferencesKey("avatar_url")
        private val KEY_IS_ADMIN = booleanPreferencesKey("is_admin")
        private val KEY_REMEMBER_ME = booleanPreferencesKey("remember_me")
        private val KEY_AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        private val KEY_CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        private val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        private val KEY_SHUFFLE_MODE = booleanPreferencesKey("shuffle_mode")
        private val KEY_REPEAT_MODE = intPreferencesKey("repeat_mode")

        // Lyrics Config Keys
        private val KEY_LYRICS_FONT_FAMILY = stringPreferencesKey("lyrics_font_family")
        private val KEY_LYRICS_CURRENT_FONT_SIZE = floatPreferencesKey("lyrics_current_font_size")
        private val KEY_LYRICS_OTHER_FONT_SIZE = floatPreferencesKey("lyrics_other_font_size")
        private val KEY_LYRICS_FONT_WEIGHT_BOLD = booleanPreferencesKey("lyrics_font_weight_bold")
        private val KEY_LYRICS_OTHER_OPACITY = floatPreferencesKey("lyrics_other_opacity")
        private val KEY_LYRICS_LINE_SPACING = floatPreferencesKey("lyrics_line_spacing")
        private val KEY_LYRICS_TEXT_CASE = stringPreferencesKey("lyrics_text_case")
        private val KEY_LYRICS_ALIGNMENT = stringPreferencesKey("lyrics_alignment")
        private val KEY_LYRICS_ANIM_DURATION = intPreferencesKey("lyrics_anim_duration")
        private val KEY_LYRICS_MANUAL_OFFSET = longPreferencesKey("lyrics_manual_offset")

        const val DEFAULT_SERVER_URL = "https://yimly.robinhort.link"
    }

    val serverUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    val authTokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTH_TOKEN]
    }

    val usernameFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_USERNAME]
    }

    val userEmailFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_USER_EMAIL]
    }

    val userIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_USER_ID]
    }

    val displayNameFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_DISPLAY_NAME]
    }

    val avatarUrlFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_AVATAR_URL]
    }

    val isAdminFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_IS_ADMIN] ?: false
    }

    val rememberMeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_REMEMBER_ME] ?: true
    }

    val audioQualityFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUDIO_QUALITY] ?: "High (320 kbps)"
    }

    val crossfadeSecondsFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_CROSSFADE_SECONDS] ?: 0
    }

    val recentSearchesFlow: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_RECENT_SEARCHES] ?: ""
        if (raw.isBlank()) emptyList() else raw.split("|||")
    }

    val lyricsConfigFlow: Flow<LyricsDisplayConfig> = context.dataStore.data.map { prefs ->
        LyricsDisplayConfig(
            fontFamily = try {
                LyricFontFamily.valueOf(prefs[KEY_LYRICS_FONT_FAMILY] ?: LyricFontFamily.DEFAULT.name)
            } catch (e: Exception) {
                LyricFontFamily.DEFAULT
            },
            currentLineFontSizeSp = prefs[KEY_LYRICS_CURRENT_FONT_SIZE] ?: 24f,
            otherLineFontSizeSp = prefs[KEY_LYRICS_OTHER_FONT_SIZE] ?: 15f,
            fontWeightBold = prefs[KEY_LYRICS_FONT_WEIGHT_BOLD] ?: true,
            otherLinesOpacity = prefs[KEY_LYRICS_OTHER_OPACITY] ?: 0.35f,
            lineSpacingDp = prefs[KEY_LYRICS_LINE_SPACING] ?: 16f,
            textCase = try {
                LyricTextCase.valueOf(prefs[KEY_LYRICS_TEXT_CASE] ?: LyricTextCase.ORIGINAL.name)
            } catch (e: Exception) {
                LyricTextCase.ORIGINAL
            },
            alignment = try {
                LyricAlignment.valueOf(prefs[KEY_LYRICS_ALIGNMENT] ?: LyricAlignment.CENTER.name)
            } catch (e: Exception) {
                LyricAlignment.CENTER
            },
            animationDurationMs = prefs[KEY_LYRICS_ANIM_DURATION] ?: 250,
            manualOffsetMs = prefs[KEY_LYRICS_MANUAL_OFFSET] ?: 0L
        )
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SERVER_URL] = url.trimEnd('/')
        }
    }

    suspend fun saveAuthSession(
        token: String,
        userId: String,
        username: String,
        email: String?,
        displayName: String? = null,
        avatarUrl: String? = null,
        isAdmin: Boolean = false,
        rememberMe: Boolean = true
    ) {
        context.dataStore.edit { preferences ->
            preferences[KEY_REMEMBER_ME] = rememberMe
            if (rememberMe) {
                preferences[KEY_AUTH_TOKEN] = token
                preferences[KEY_USER_ID] = userId
                preferences[KEY_USERNAME] = username
                if (email != null) preferences[KEY_USER_EMAIL] = email else preferences.remove(KEY_USER_EMAIL)
                if (displayName != null) preferences[KEY_DISPLAY_NAME] = displayName else preferences.remove(KEY_DISPLAY_NAME)
                if (avatarUrl != null) preferences[KEY_AVATAR_URL] = avatarUrl else preferences.remove(KEY_AVATAR_URL)
                preferences[KEY_IS_ADMIN] = isAdmin
            } else {
                preferences.remove(KEY_AUTH_TOKEN)
                preferences.remove(KEY_USER_ID)
                preferences.remove(KEY_USERNAME)
                preferences.remove(KEY_USER_EMAIL)
                preferences.remove(KEY_DISPLAY_NAME)
                preferences.remove(KEY_AVATAR_URL)
                preferences.remove(KEY_IS_ADMIN)
            }
        }
    }

    suspend fun clearAuthSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_AUTH_TOKEN)
            preferences.remove(KEY_USER_ID)
            preferences.remove(KEY_USERNAME)
            preferences.remove(KEY_USER_EMAIL)
            preferences.remove(KEY_DISPLAY_NAME)
            preferences.remove(KEY_AVATAR_URL)
            preferences.remove(KEY_IS_ADMIN)
        }
    }

    suspend fun addRecentSearch(query: String) {
        if (query.isBlank()) return
        context.dataStore.edit { preferences ->
            val raw = preferences[KEY_RECENT_SEARCHES] ?: ""
            val list = (if (raw.isBlank()) emptyList() else raw.split("|||")).toMutableList()
            list.remove(query)
            list.add(0, query)
            val trimmed = list.take(15)
            preferences[KEY_RECENT_SEARCHES] = trimmed.joinToString("|||")
        }
    }

    suspend fun clearRecentSearches() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_RECENT_SEARCHES)
        }
    }

    suspend fun setAudioQuality(quality: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUDIO_QUALITY] = quality
        }
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CROSSFADE_SECONDS] = seconds
        }
    }

    suspend fun updateLyricsConfig(config: LyricsDisplayConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LYRICS_FONT_FAMILY] = config.fontFamily.name
            prefs[KEY_LYRICS_CURRENT_FONT_SIZE] = config.currentLineFontSizeSp
            prefs[KEY_LYRICS_OTHER_FONT_SIZE] = config.otherLineFontSizeSp
            prefs[KEY_LYRICS_FONT_WEIGHT_BOLD] = config.fontWeightBold
            prefs[KEY_LYRICS_OTHER_OPACITY] = config.otherLinesOpacity
            prefs[KEY_LYRICS_LINE_SPACING] = config.lineSpacingDp
            prefs[KEY_LYRICS_TEXT_CASE] = config.textCase.name
            prefs[KEY_LYRICS_ALIGNMENT] = config.alignment.name
            prefs[KEY_LYRICS_ANIM_DURATION] = config.animationDurationMs
            prefs[KEY_LYRICS_MANUAL_OFFSET] = config.manualOffsetMs
        }
    }

    suspend fun saveLyricsConfig(config: LyricsDisplayConfig) = updateLyricsConfig(config)

    suspend fun updateLyricsOffset(offsetMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LYRICS_MANUAL_OFFSET] = offsetMs
        }
    }

    suspend fun setLyricsManualOffset(offsetMs: Long) = updateLyricsOffset(offsetMs)
}
