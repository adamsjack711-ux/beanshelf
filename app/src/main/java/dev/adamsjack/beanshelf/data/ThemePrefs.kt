package dev.adamsjack.beanshelf.data

import android.content.Context

/** Persists the chosen palette key across launches. */
object ThemePrefs {
    private const val PREFS = "theme"
    private const val KEY = "palette"

    fun loadKey(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun saveKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, key).apply()
    }
}
