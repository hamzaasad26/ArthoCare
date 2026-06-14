package org.example.project

import platform.Foundation.NSUserDefaults

private const val KEY_DARK = "prefers_dark_theme"

actual object ThemePreferenceStore {
    actual fun isDarkTheme(): Boolean {
        val defaults = NSUserDefaults.standardUserDefaults
        if (defaults.objectForKey(KEY_DARK) == null) return true
        return defaults.boolForKey(KEY_DARK)
    }

    actual fun setDarkTheme(value: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(value, forKey = KEY_DARK)
    }
}
