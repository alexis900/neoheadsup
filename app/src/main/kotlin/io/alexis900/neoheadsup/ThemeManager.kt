package io.alexis900.neoheadsup

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Applies the user's theme choice through AppCompat so Material 3 and AppCompat agree on the
 * effective night mode. The choice is stored in SharedPreferences and defaults to the system.
 */
object ThemeManager {

    fun apply(context: Context) {
        val mode = when (Prefs.getThemeMode(context)) {
            Prefs.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            Prefs.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}
