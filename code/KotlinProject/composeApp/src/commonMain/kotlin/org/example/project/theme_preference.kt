package org.example.project

/**
 * Persists whether the user prefers the dark dashboard palette (true) or the
 * clinical light palette (false). Defaults to dark when unset.
 */
expect object ThemePreferenceStore {
    fun isDarkTheme(): Boolean
    fun setDarkTheme(value: Boolean)
}
