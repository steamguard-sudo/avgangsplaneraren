package com.avgangsplaneraren.app

import android.content.Context
import androidx.compose.runtime.mutableStateOf

object AppLanguageState {
    private const val PREFS_NAME = "app_language_prefs"
    private const val KEY_LANGUAGE = "language_tag"

    val current = mutableStateOf("sv")

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        current.value = prefs.getString(KEY_LANGUAGE, "sv") ?: "sv"
    }

    fun select(context: Context, languageTag: String) {
        current.value = languageTag
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, languageTag)
            .apply()
    }

    fun currentTagSync(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "sv") ?: "sv"
    }
}
