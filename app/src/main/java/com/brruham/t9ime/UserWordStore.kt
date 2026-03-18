package com.brruham.t9ime

import android.content.Context

/**
 * Persistent storage untuk:
 * - Unigram: kata yang sering dipilih (boost T9 score)
 * - Bigram:  pasangan "kata_sebelumnya:kata_ini" (konteks prediksi)
 * - Multitap words: kata yang diketik manual → jadi kandidat T9
 */
class UserWordStore(context: Context) {

    private val prefs = context.getSharedPreferences("t9_user_words", Context.MODE_PRIVATE)

    // ── Unigram ───────────────────────────────────────────────────────────────

    fun recordUsage(word: String) {
        val k = uniKey(word) ?: return
        prefs.edit().putInt(k, getBoost(word) + 1).apply()
    }

    fun getBoost(word: String): Int {
        val k = uniKey(word) ?: return 0
        return prefs.getInt(k, 0)
    }

    fun getAllWords(): Map<String, Int> =
        prefs.all
            .filter { it.key.startsWith("u:") && it.value is Int }
            .mapKeys  { it.key.removePrefix("u:") }
            .mapValues { it.value as Int }

    // ── Bigram ────────────────────────────────────────────────────────────────

    /** Catat bahwa [next] mengikuti [prev] */
    fun recordBigram(prev: String, next: String) {
        val k = biKey(prev, next) ?: return
        prefs.edit().putInt(k, getBigramScore(prev, next) + 1).apply()
    }

    /** Berapa kali [next] muncul setelah [prev] */
    fun getBigramScore(prev: String, next: String): Int {
        val k = biKey(prev, next) ?: return 0
        return prefs.getInt(k, 0)
    }

    /** Semua kata yang pernah mengikuti [prev], beserta skornya */
    fun getBigramFollowers(prev: String): Map<String, Int> {
        val prefix = "b:${prev.lowercase()}:"
        return prefs.all
            .filter { it.key.startsWith(prefix) && it.value is Int }
            .mapKeys  { it.key.removePrefix(prefix) }
            .mapValues { it.value as Int }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun uniKey(word: String): String? {
        val w = word.lowercase().trim()
        return if (w.length >= 2 && w.all { it.isLetter() }) "u:$w" else null
    }

    private fun biKey(prev: String, next: String): String? {
        val p = prev.lowercase().trim()
        val n = next.lowercase().trim()
        if (p.length < 2 || n.length < 2) return null
        if (!p.all { it.isLetter() } || !n.all { it.isLetter() }) return null
        return "b:$p:$n"
    }

    fun clearAll() = prefs.edit().clear().apply()
}
