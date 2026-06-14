package org.example.project

import android.content.Context

private const val PREFS_NAME = "arthocare_theme"
private const val KEY_DARK = "prefers_dark_theme"

actual object ThemePreferenceStore {
    actual fun isDarkTheme(): Boolean {
        val ctx = RaLensDebugAndroidContext.appContext ?: return true
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK, true)
    }

    actual fun setDarkTheme(value: Boolean) {
        val ctx = RaLensDebugAndroidContext.appContext ?: return
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_DARK, value)
            .apply()
    }
}
