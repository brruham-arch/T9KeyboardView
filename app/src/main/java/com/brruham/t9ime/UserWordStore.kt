package com.brruham.t9ime

import android.content.Context

/**
 * Menyimpan kata-kata yang sering dipilih user menggunakan SharedPreferences.
 * Kata dengan usage tinggi mendapat boost skor di PredictionEngine.
 */
class UserWordStore(context: Context) {

    private val prefs = context.getSharedPreferences("t9_user_words", Context.MODE_PRIVATE)

    /** Catat bahwa user memilih [word] — tambah hit count-nya */
    fun recordUsage(word: String) {
        if (word.isBlank()) return
        val key = word.lowercase().trim()
        val current = prefs.getInt(key, 0)
        prefs.edit().putInt(key, current + 1).apply()
    }

    /**
     * Kembalikan boost score untuk [word].
     * Setiap hit = +1; dikali faktor di PredictionEngine.
     */
    fun getBoost(word: String): Int =
        prefs.getInt(word.lowercase().trim(), 0)

    /** Semua kata user beserta hit count-nya — untuk diload ke Trie */
    fun getAllWords(): Map<String, Int> =
        prefs.all
            .filterValues { it is Int }
            .mapValues { it.value as Int }

    /** Hapus riwayat semua kata (debugging / reset) */
    fun clearAll() = prefs.edit().clear().apply()
}
