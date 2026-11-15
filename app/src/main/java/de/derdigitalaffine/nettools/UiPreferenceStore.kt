package de.derdigitalaffine.nettools

import android.content.Context

/**
 * Lightweight, local-only UI preferences. Contains neither scan targets nor credentials.
 * Writes are asynchronous and never block the user interface on disk I/O.
 */
internal object UiPreferenceStore {
    private const val PREFS="nettools_ui"
    private const val KEY_DARK="theme_dark"
    private const val KEY_FAVORITES="tool_favorites"
    private const val DEFAULT_FAVORITES="quick,ping,dns,ports"

    fun isDark(context:Context):Boolean =
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(KEY_DARK,true)

    fun setDark(context:Context,dark:Boolean) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK,dark).apply()
    }

    fun favorites(context:Context):String =
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getString(KEY_FAVORITES,DEFAULT_FAVORITES) ?: DEFAULT_FAVORITES

    fun setFavorites(context:Context,ids:Set<String>) {
        val allowed=ids.filter { id -> diagnosticTools.any { it.id==id } }
            .sorted().joinToString(",")
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .edit().putString(KEY_FAVORITES,allowed).apply()
    }
}
