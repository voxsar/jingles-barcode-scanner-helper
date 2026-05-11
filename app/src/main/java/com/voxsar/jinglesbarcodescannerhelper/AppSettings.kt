package com.voxsar.jinglesbarcodescannerhelper

import android.content.Context

data class AppSettings(
    val submissionUrl: String = "",
    val locationsUrl: String = "",
)

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        submissionUrl = preferences.getString(KEY_SUBMISSION_URL, "").orEmpty(),
        locationsUrl = preferences.getString(KEY_LOCATIONS_URL, "").orEmpty(),
    )

    fun save(settings: AppSettings) {
        preferences.edit()
            .putString(KEY_SUBMISSION_URL, settings.submissionUrl.trim())
            .putString(KEY_LOCATIONS_URL, settings.locationsUrl.trim())
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "scanner_settings"
        const val KEY_SUBMISSION_URL = "submission_url"
        const val KEY_LOCATIONS_URL = "locations_url"
    }
}
