package com.bettercallsoul

import android.app.Activity
import android.app.LocaleManager
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** The app's theme: the phone's own, or always light or dark. */
enum class ThemeChoice { System, Light, Dark }

/**
 * The theme and language the user picked for this app alone. Android 12 keeps an app's own night
 * mode and Android 13 its own language, so there the system does the work and the choice reaches
 * every screen, dialog and widget; on older phones the activities apply them themselves.
 */
object AppLook {
    /** The languages the app is written in, by tag; the empty tag follows the phone. */
    val LANGUAGES = listOf("", "en", "tr", "ru", "ar")

    private const val PREFS = "settings"
    private const val KEY_LANGUAGE = "app_language"

    fun applyTheme(context: Context, choice: ThemeChoice) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val mode = when (choice) {
            ThemeChoice.System -> UiModeManager.MODE_NIGHT_AUTO
            ThemeChoice.Light -> UiModeManager.MODE_NIGHT_NO
            ThemeChoice.Dark -> UiModeManager.MODE_NIGHT_YES
        }
        runCatching { context.getSystemService(UiModeManager::class.java).setApplicationNightMode(mode) }
    }

    /** The picked language's tag, or "" when the app follows the phone. */
    fun language(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
                .takeUnless { it.isEmpty }?.get(0)?.language.orEmpty()
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, "").orEmpty()
        }

    fun setLanguage(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android recreates the activities in the new language by itself.
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, tag).apply()
            activity.recreate()
        }
    }

    /** Before Android 13: the activity's context in the picked language. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, "").orEmpty()
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }

    /** A language's name in that language, or [system] for the empty tag. */
    fun languageName(tag: String, system: String): String =
        if (tag.isEmpty()) system else Locale.forLanguageTag(tag).let { it.getDisplayLanguage(it).replaceFirstChar { c -> c.titlecase(it) } }
}
