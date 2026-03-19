package com.brruham.t9ime

import android.content.Context

/**
 * Semua konfigurasi keyboard — disimpan SharedPreferences.
 * Dibaca oleh T9IMEService & T9InputController saat init.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("t9_settings", Context.MODE_PRIVATE)

    // ── Keys ─────────────────────────────────────────────────────────────────
    companion object {
        const val KEY_VIBRATE        = "vibrate"
        const val KEY_AUTO_CAPS      = "auto_caps"
        const val KEY_AUTO_SPACE     = "auto_space"
        const val KEY_MT_DELAY       = "mt_delay"
        const val KEY_SHOW_SUB_LABEL = "show_sub_label"
        const val KEY_PREDICTION_ON  = "prediction_on"
        const val KEY_THEME          = "theme"   // "dark" | "amoled" | "light"
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    var vibrate: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(v) = prefs.edit().putBoolean(KEY_VIBRATE, v).apply()

    var autoCaps: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CAPS, true)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_CAPS, v).apply()

    var autoSpace: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SPACE, true)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_SPACE, v).apply()

    /** Multi-tap delay dalam ms: 500 / 750 / 1000 */
    var mtDelay: Long
        get() = prefs.getLong(KEY_MT_DELAY, 750L)
        set(v) = prefs.edit().putLong(KEY_MT_DELAY, v).apply()

    var showSubLabel: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SUB_LABEL, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_SUB_LABEL, v).apply()

    var predictionOn: Boolean
        get() = prefs.getBoolean(KEY_PREDICTION_ON, true)
        set(v) = prefs.edit().putBoolean(KEY_PREDICTION_ON, v).apply()

    var theme: String
        get() = prefs.getString(KEY_THEME, "dark") ?: "dark"
        set(v) = prefs.edit().putString(KEY_THEME, v).apply()
}
