package com.brruham.t9ime

import android.content.Context
import android.util.Log

/**
 * Mesin prediksi kata T9 berbasis Trie.
 *
 * Alur:
 *  1. Load kamus id_50k.txt dari assets (background thread)
 *  2. Juga load kata user dari UserWordStore
 *  3. predict(digits) → DFS Trie dengan constraint karakter per digit
 *  4. Score = base_freq + user_boost * BOOST_FACTOR
 *  5. Return top-N sorted by score
 */
class PredictionEngine(
    private val context: Context,
    private val userStore: UserWordStore
) {

    // ── Trie ──────────────────────────────────────────────────────────────────

    private inner class TrieNode {
        val children = HashMap<Char, TrieNode>(4)
        var frequency = 0
        var isEnd = false
    }

    private val root = TrieNode()

    @Volatile var isReady = false
        private set

    // ── T9 Mapping ────────────────────────────────────────────────────────────

    private val T9_MAP: Map<Char, String> = mapOf(
        '1' to ".,!?'-",
        '2' to "abc",
        '3' to "def",
        '4' to "ghi",
        '5' to "jkl",
        '6' to "mno",
        '7' to "pqrs",
        '8' to "tuv",
        '9' to "wxyz"
    )

    companion object {
        private const val TAG = "PredictionEngine"
        private const val BOOST_FACTOR = 150   // per hit
        private const val MAX_RESULTS = 8
        private const val MAX_FREQ = 999_999   // cap agar tidak overflow
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        Thread {
            loadDictionary()
            loadUserWords()
            isReady = true
            Log.d(TAG, "Engine ready")
        }.start()
    }

    private fun loadDictionary() {
        try {
            context.assets.open("id_50k.txt").bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        val word = parts[0].lowercase()
                        val freq = parts[1].toIntOrNull()?.coerceAtMost(MAX_FREQ) ?: 1
                        if (word.all { it.isLetter() }) {
                            insertWord(word, freq)
                        }
                    }
                }
            }
            Log.d(TAG, "Dictionary loaded")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load dictionary: ${e.message}")
        }
    }

    private fun loadUserWords() {
        userStore.getAllWords().forEach { (word, hits) ->
            if (word.all { it.isLetter() || it == '\'' }) {
                // Simpan sebagai entry trie dengan freq khusus agar tetap bisa dicari
                insertWord(word, hits * BOOST_FACTOR)
            }
        }
    }

    // ── Trie ops ──────────────────────────────────────────────────────────────

    @Synchronized
    private fun insertWord(word: String, frequency: Int) {
        var node = root
        for (ch in word.lowercase()) {
            node = node.children.getOrPut(ch) { TrieNode() }
        }
        // Ambil freq tertinggi jika sudah ada (misal dari user + dict)
        if (frequency > node.frequency) node.frequency = frequency
        node.isEnd = true
    }

    /**
     * Tambah kata baru yang belum ada di kamus (kata asing, nama, dll).
     * Dipanggil setelah user commit kata manual.
     */
    @Synchronized
    fun addWord(word: String) {
        val clean = word.lowercase().trim()
        if (clean.length > 1 && clean.all { it.isLetter() }) {
            insertWord(clean, BOOST_FACTOR)
        }
    }

    // ── Prediction ────────────────────────────────────────────────────────────

    /**
     * Cari semua kata yang cocok dengan urutan digit T9 [digits].
     * @param digits  contoh "4663" untuk kata "home"/"gone"/dll
     * @param prevWord  kata sebelumnya (untuk bigram di masa depan)
     * @return daftar kata diurutkan skor tertinggi
     */
    fun predict(digits: String, prevWord: String = ""): List<String> {
        if (!isReady || digits.isEmpty()) return emptyList()

        val results = ArrayList<Pair<String, Int>>(32)
        searchTrie(root, digits, 0, StringBuilder(), results)

        return results
            .map { (word, baseFreq) ->
                // Boost kata yang sering dipilih user
                val boost = userStore.getBoost(word) * BOOST_FACTOR
                word to (baseFreq + boost)
            }
            .sortedByDescending { it.second }
            .take(MAX_RESULTS)
            .map { it.first }
    }

    /**
     * DFS pada Trie dengan constraint: di kedalaman [depth],
     * karakter harus ada dalam set digit[depth].
     */
    private fun searchTrie(
        node: TrieNode,
        digits: String,
        depth: Int,
        current: StringBuilder,
        results: MutableList<Pair<String, Int>>
    ) {
        if (depth == digits.length) {
            if (node.isEnd) results.add(current.toString() to node.frequency)
            // Juga collect semua kata yang dimulai dengan prefix ini (bukan T9 tapi berguna)
            return
        }

        val digit = digits[depth]
        val validChars = T9_MAP[digit] ?: return

        for (ch in validChars) {
            val child = node.children[ch] ?: continue
            current.append(ch)
            searchTrie(child, digits, depth + 1, current, results)
            current.deleteCharAt(current.length - 1)
        }
    }

    /** Karakter yang tersedia untuk tombol digit (untuk mode multi-tap) */
    fun getCharsForKey(digit: Char): String = T9_MAP[digit] ?: ""
}
