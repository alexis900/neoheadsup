package io.alexis900.neoheadsup

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic trail for detection and alert decisions.
 *
 * Every line is written to Logcat with the tag [TAG] and the latest lines are also kept in
 * private preferences so the app can display them without any external tool.
 */
object Diag {

    const val TAG = "NeoHeadsUp"
    private const val FILE = "neoheadsup_diagnostics"
    private const val KEY_LOG = "log"
    private const val MAX_ENTRIES = 30

    fun log(context: Context, message: String) {
        val entry = "${timestamp()} $message"
        Log.i(TAG, entry)

        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val previous = prefs.getString(KEY_LOG, null)
            ?.lines()
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val updated = (previous + entry).takeLast(MAX_ENTRIES)
        prefs.edit().putString(KEY_LOG, updated.joinToString("\n")).apply()
    }

    fun latest(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_LOG, null)
            ?.lines()
            ?.lastOrNull { it.isNotBlank() }

    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
