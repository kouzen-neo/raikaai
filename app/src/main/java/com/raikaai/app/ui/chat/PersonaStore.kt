package com.raikaai.app.ui.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.raikaai.app.data.AppLimits
import com.raikaai.app.data.SettingsRepository
import com.raikaai.app.util.KLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns character/persona state (built-in presets + user customs) previously
 * embedded in ChatViewModel. The ViewModel keeps thin delegating accessors
 * so UI call sites are untouched.
 */
class PersonaStore(
    private val app: Application,
    private val settingsRepo: SettingsRepository,
    private val scope: CoroutineScope,
) {
    val customPersonas: SnapshotStateList<PersonaPreset> = mutableStateListOf()

    val activePersona = MutableStateFlow<PersonaPreset>(PERSONA_PRESETS.first())

    var pendingPersonaRestoreId: String? = null

    /**
     * Looks up a persona by id across builtins and customs.
     * Returns General for null ids (legacy chats) or deleted personas.
     */
    fun resolvePersona(personaId: String?): PersonaPreset {
        if (personaId == null) return PERSONA_PRESETS.first()
        return (PERSONA_PRESETS + customPersonas).firstOrNull { it.id == personaId }
            ?: PERSONA_PRESETS.first()
    }

    fun loadCustomPersonasFromJson(json: String) {
        if (json.isBlank()) {
            customPersonas.clear()
            return
        }
        try {
            val listType = object : TypeToken<List<PersonaPreset>>() {}.type
            val list = Gson().fromJson<List<PersonaPreset>>(json, listType) ?: emptyList()
            customPersonas.clear()
            customPersonas.addAll(list.map { it.normalize() })
        } catch (_: Exception) {
        }
    }

    fun addCustomPersona(
        name: String,
        iconType: String = "custom",
        systemPrompt: String,
        description: String = "",
        greeting: String = "",
        avatarUri: String = "",
        temperature: Float? = null,
        topK: Int? = null,
        personality: String = "",
        scenario: String = "",
        exampleDialogues: String = "",
    ) {
        val newP =
            PersonaPreset(
                id = "custom_" + System.currentTimeMillis(),
                name = name.trim().ifBlank { "Custom Persona" },
                iconType = iconType.trim().ifBlank { "custom" },
                description = description.trim(),
                greeting = greeting.trim(),
                systemPrompt = systemPrompt.trim(),
                avatarUri = avatarUri.trim(),
                temperature = temperature,
                topK = topK,
                personality = personality.trim(),
                scenario = scenario.trim(),
                exampleDialogues = exampleDialogues.trim(),
            )
        val updated = customPersonas.toList() + newP
        saveCustomPersonas(updated)
    }

    fun deleteCustomPersona(id: String) {
        val updated = customPersonas.filter { it.id != id }
        saveCustomPersonas(updated)
    }

    /**
     * Inserts a new custom persona or replaces the existing one with the same
     * id (edit = update, no duplicates). avatarUri is authoritative: blank
     * means "no photo" (the editor manages avatars explicitly).
     * Refreshes [activePersona] when the edited persona is currently active.
     * Never touches global settings.
     */
    fun upsertCustomPersona(persona: PersonaPreset) {
        val existing = customPersonas.firstOrNull { it.id == persona.id }
        val updated =
            if (existing != null) {
                customPersonas.map { if (it.id == persona.id) persona else it }
            } else {
                customPersonas.toList() + persona
            }
        saveCustomPersonas(updated)
        if (activePersona.value.id == persona.id) {
            activePersona.value = persona
        }
    }

    /**
     * Copies a picked profile photo into internal storage (downscaled) and
     * returns its absolute path for [PersonaPreset.avatarUri]. Null on failure.
     * Internal copy keeps the photo readable across restarts without
     * gallery URI permission issues.
     */
    suspend fun savePersonaAvatar(source: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                app.contentResolver.openInputStream(source)?.use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                }
                var sampleSize = 1
                val maxOriginal = maxOf(options.outWidth, options.outHeight)
                while ((maxOriginal / (sampleSize * 2)) >= 512) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bitmap = app.contentResolver.openInputStream(source)?.use { input ->
                    BitmapFactory.decodeStream(input, null, decodeOptions)
                } ?: return@withContext null
                val dir = File(app.filesDir, "avatars").apply { if (!exists()) mkdirs() }
                val dest = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
                dest.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                bitmap.recycle()
                dest.absolutePath
            } catch (t: Throwable) {
                KLog.e("PersonaStore", "Failed to save persona avatar", t)
                null
            }
        }

    fun saveCustomPersonas(list: List<PersonaPreset>) {
        val json = Gson().toJson(list)
        scope.launch { settingsRepo.setCustomPersonasJson(json) }
    }

    fun exportPersonaJson(persona: PersonaPreset): String {
        return Gson().toJson(persona)
    }

    fun importPersonaJson(jsonString: String): Boolean {
        // Bound untrusted clipboard input before parsing/serializing (mirrors
        // the skill import caps): one giant paste must not bloat DataStore or
        // the per-emission settings payload.
        if (jsonString.isBlank() || jsonString.length > AppLimits.PERSONA_IMPORT_MAX_CHARS) return false
        return try {
            val parsed = Gson().fromJson(jsonString, PersonaPreset::class.java)?.normalize() ?: return false
            if (parsed.name.isBlank()) return false
            val persona = parsed.copy(
                id = "imported_" + System.currentTimeMillis(),
                name = parsed.name.take(80),
                iconType = parsed.iconType.take(40).ifBlank { "custom" },
                description = parsed.description.take(500),
                greeting = parsed.greeting.take(2_000),
                systemPrompt = parsed.systemPrompt.take(20_000),
                avatarUri = parsed.avatarUri.take(2_000),
                personality = parsed.personality.take(2_000),
                scenario = parsed.scenario.take(2_000),
                exampleDialogues = parsed.exampleDialogues.take(8_000),
            )
            saveCustomPersonas(customPersonas.toList() + persona)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun importPersonaFromJson(jsonString: String): Boolean = importPersonaJson(jsonString)

    /**
     * Selects the active character for the current chat. Deliberately does NOT
     * touch global settings (systemPrompt, temperature, topK): per-character
     * values are resolved at generation time.
     */
    fun applyPersona(persona: PersonaPreset) {
        activePersona.value = persona
    }

    /** StateFlow accessor for UI layers that only observe. */
    fun observeActivePersona(): StateFlow<PersonaPreset> = activePersona
}
