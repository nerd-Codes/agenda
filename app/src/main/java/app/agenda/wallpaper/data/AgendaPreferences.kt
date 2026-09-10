package app.agenda.wallpaper.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.agenda.wallpaper.model.DeviceCanvasProfile
import app.agenda.wallpaper.model.WallpaperComposition
import app.agenda.wallpaper.model.minimalComposition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.agendaDataStore by preferencesDataStore(name = "agenda_settings")

class AgendaPreferences(private val context: Context) {
    private val compositionKey = stringPreferencesKey("wallpaper_composition")
    private val canvasKey = stringPreferencesKey("device_canvas_profile")
    private val userNameKey = stringPreferencesKey("user_name")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val userName: Flow<String> = context.agendaDataStore.data.map { prefs ->
        prefs[userNameKey] ?: ""
    }

    val canvasProfile: Flow<DeviceCanvasProfile> = context.agendaDataStore.data.map { prefs ->
        prefs[canvasKey]?.let { runCatching { json.decodeFromString<DeviceCanvasProfile>(it) }.getOrNull() }
            ?: DeviceCanvasProfile()
    }

    val composition: Flow<WallpaperComposition> = context.agendaDataStore.data.map { prefs ->
        prefs[compositionKey]?.let { runCatching { json.decodeFromString<WallpaperComposition>(it) }.getOrNull() }
            ?: minimalComposition(
                prefs[canvasKey]?.let { runCatching { json.decodeFromString<DeviceCanvasProfile>(it) }.getOrNull() }
                    ?: DeviceCanvasProfile(),
            )
    }

    suspend fun saveUserName(name: String) {
        context.agendaDataStore.edit { prefs ->
            prefs[userNameKey] = name
        }
    }

    suspend fun saveCanvasProfile(profile: DeviceCanvasProfile) {
        context.agendaDataStore.edit { prefs ->
            prefs[canvasKey] = json.encodeToString(profile)
        }
    }

    suspend fun saveComposition(composition: WallpaperComposition) {
        context.agendaDataStore.edit { prefs ->
            prefs[compositionKey] = json.encodeToString(composition)
            prefs[canvasKey] = json.encodeToString(composition.baseCanvas)
        }
    }
}
