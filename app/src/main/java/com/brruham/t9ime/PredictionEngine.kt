package com.brruham.t9ime

import android.content.Context
import android.util.Log

class PredictionEngine(
    private val context: Context,
    private val userStore: UserWordStore
) {
    private inner class TrieNode {
        val children = HashMap<Char, TrieNode>(4)
        var frequency = 0
        var isEnd = false
    }

    private val root = TrieNode()

    @Volatile var isReady = false
        private set

    private val T9_MAP: Map<Char, String> = mapOf(
        '1' to ".,!?'-",
        '2' to "abc", '3' to "def",
        '4' to "ghi", '5' to "jkl", '6' to "mno",
        '7' to "pqrs", '8' to "tuv", '9' to "wxyz"
    )

    private val ADJACENT: Map<Char, String> = mapOf(
        '1' to "24", '2' to "1345", '3' to "246",
        '4' to "1257", '5' to "24689", '6' to "359",
        '7' to "458", '8' to "5790", '9' to "68"
    )

    companion object {
        private const val TAG = "PredictionEngine"
        private const val BOOST        = 150
        private const val BIGRAM_MULT  = 200
        private const val MAX_RESULTS  = 8
        private const val MAX_COLLECT  = 40
    }

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
                    val p = line.trim().split(Regex("\\s+"))
                    if (p.size >= 2) {
                        val w = p[0].lowercase()
                        val f = p[1].toIntOrNull()?.coerceAtMost(999_999) ?: 1
                        if (w.length >= 2 && w.all { it.isLetter() }) insertWord(w, f)
                    }
                }
            }
            Log.d(TAG, "Dict loaded")
        } catch (e: Exception) {
            Log.e(TAG, "Dict fail: ${e.message}")
        }
    }

    private fun loadUserWords() {
        userStore.getAllWords().forEach { (w, h) ->
            if (w.all { it.isLetter() }) insertWord(w, h * BOOST)
        }
    }

    @Synchronized
    private fun insertWord(word: String, frequency: Int) {
        var n = root
        for (c in word) n = n.children.getOrPut(c) { TrieNode() }
        if (frequency > n.frequency) n.frequency = frequency
        n.isEnd = true
    }

    @Synchronized
    fun addWord(word: String) {
        val w = word.lowercase().trim()
        if (w.length > 1 && w.all { it.isLetter() }) insertWord(w, BOOST)
    }

    // ================= T9 PREDICT =================

    fun predict(digits: String, prevWord: String = ""): List<String> {
        if (!isReady || digits.isEmpty()) return emptyList()

        val scores = HashMap<String, Int>()

        searchExact(root, digits, 0, StringBuilder(), scores)
        collectPrefix(digits, scores)

        if (scores.size < 4)
            searchFuzzy(root, digits, 0, StringBuilder(), scores, 1)

        val bigrams = if (prevWord.isNotBlank())
            userStore.getBigramFollowers(prevWord) else emptyMap()

        return scores
            .map { (word, base) ->
                val uni    = userStore.getBoost(word) * BOOST
                val bigram = (bigrams[word] ?: 0) * BIGRAM_MULT
                word to (base + uni + bigram)
            }
            .sortedByDescending { it.second }
            .take(MAX_RESULTS)
            .map { it.first }
    }

    // ================= PREFIX (MULTITAP FIX) =================

    fun predictWordPrefix(prefix: String, prevWord: String = ""): List<String> {
        if (!isReady || prefix.isEmpty()) return emptyList()

        var node = root
        for (c in prefix.lowercase()) {
            node = node.children[c] ?: return emptyList()
        }

        val results = HashMap<String, Int>()
        collectAll(node, StringBuilder(prefix.lowercase()), results, intArrayOf(0))

        val bigrams = if (prevWord.isNotBlank())
            userStore.getBigramFollowers(prevWord) else emptyMap()

        return results
            .map { (word, base) ->
                val uni    = userStore.getBoost(word) * BOOST
                val bigram = (bigrams[word] ?: 0) * BIGRAM_MULT
                word to (base + uni + bigram)
            }
            .sortedByDescending { it.second }
            .take(MAX_RESULTS)
            .map { it.first }
    }

    // ================= CORE SEARCH =================

    private fun searchExact(node: TrieNode, digits: String, d: Int, cur: StringBuilder, res: MutableMap<String, Int>) {
        if (d == digits.length) {
            if (node.isEnd) res.merge(cur.toString(), node.frequency.coerceAtLeast(1), ::maxOf)
            return
        }
        for (c in T9_MAP[digits[d]] ?: return) {
            val ch = node.children[c] ?: continue
            cur.append(c)
            searchExact(ch, digits, d + 1, cur, res)
            cur.deleteCharAt(cur.length - 1)
        }
    }

    private fun collectPrefix(digits: String, res: MutableMap<String, Int>) {
        data class S(val node: TrieNode, val d: Int, val prefix: String)
        val q = ArrayDeque<S>()
        q.add(S(root, 0, ""))

        while (q.isNotEmpty()) {
            val (node, d, prefix) = q.removeFirst()

            if (d == digits.length) {
                collectAll(node, StringBuilder(prefix), res, intArrayOf(0))
                continue
            }

            for (c in T9_MAP[digits[d]] ?: continue) {
                val ch = node.children[c] ?: continue
                q.add(S(ch, d + 1, prefix + c))
            }
        }
    }

    private fun collectAll(node: TrieNode, cur: StringBuilder, res: MutableMap<String, Int>, cnt: IntArray) {
        if (cnt[0] >= MAX_COLLECT) return

        if (node.isEnd) {
            res.merge(cur.toString(), (node.frequency * 0.6).toInt().coerceAtLeast(1), ::maxOf)
            cnt[0]++
        }

        node.children.entries
            .sortedByDescending { it.value.frequency }
            .forEach { (c, ch) ->
                if (cnt[0] >= MAX_COLLECT) return
                cur.append(c)
                collectAll(ch, cur, res, cnt)
                cur.deleteCharAt(cur.length - 1)
            }
    }

    private fun searchFuzzy(node: TrieNode, digits: String, d: Int, cur: StringBuilder, res: MutableMap<String, Int>, err: Int) {
        if (d == digits.length) {
            if (node.isEnd) res.merge(cur.toString(), (node.frequency * 0.3).toInt().coerceAtLeast(1), ::maxOf)
            return
        }

        val exact = T9_MAP[digits[d]] ?: ""

        for (c in exact) {
            val ch = node.children[c] ?: continue
            cur.append(c)
            searchFuzzy(ch, digits, d + 1, cur, res, err)
            cur.deleteCharAt(cur.length - 1)
        }

        if (err > 0) {
            for (adj in ADJACENT[digits[d]] ?: "") {
                for (c in T9_MAP[adj] ?: "") {
                    if (c in exact) continue
                    val ch = node.children[c] ?: continue
                    cur.append(c)
                    searchFuzzy(ch, digits, d + 1, cur, res, err - 1)
                    cur.deleteCharAt(cur.length - 1)
                }
            }
        }
    }

    fun getCharsForKey(digit: Char): String = T9_MAP[digit] ?: ""
}
