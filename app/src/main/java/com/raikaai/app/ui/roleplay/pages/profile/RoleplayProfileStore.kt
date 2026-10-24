package com.raikaai.app.ui.roleplay.pages.profile

import android.content.Context

data class RoleplayProfile(
    val name: String = "user",
    val description: String = "",
    val gender: String = "male",
)

class RoleplayProfileStore(context: Context) {
    private val preferences = context.getSharedPreferences("roleplay_profile", Context.MODE_PRIVATE)

    fun load(): RoleplayProfile = RoleplayProfile(
        name = preferences.getString("name", "user").orEmpty().ifBlank { "user" },
        description = preferences.getString("description", "").orEmpty(),
        gender = preferences.getString("gender", "male").orEmpty().ifBlank { "male" },
    )

    fun save(profile: RoleplayProfile) {
        preferences.edit()
            .putString("name", profile.name.trim().ifBlank { "user" })
            .putString("description", profile.description.trim())
            .putString(
                "gender",
                profile.gender.lowercase().let { if (it == "female") "female" else "male" })
            .apply()
    }
}
