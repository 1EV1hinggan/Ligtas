package com.ligtas.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The app's language: English or Modern Taglish. Chosen per phone in Settings, kept in SharedPreferences.
 * Screens read [taglish] (a Compose state, so they redraw the moment it changes); Java and the push
 * service use pick() / isTaglish(context).
 */
object Lang {
    private const val PREFS = "ligtas_lang"
    private const val KEY = "lang"

    var taglish by mutableStateOf(false)
        private set

    @JvmStatic
    fun load(context: Context) {
        taglish = isTaglish(context)
    }

    @JvmStatic
    fun set(context: Context, useTaglish: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, if (useTaglish) "tl" else "en").apply()
        taglish = useTaglish
    }

    /** Safe from the push service, where no screen (and no Compose state) exists. */
    @JvmStatic
    fun isTaglish(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "en") == "tl"

    @JvmStatic
    fun pick(en: String, tl: String): String = if (taglish) tl else en
}

/** Compose: the English text, or its Taglish version when the person chose Taglish. */
fun t(en: String, tl: String): String = if (Lang.taglish) tl else en
