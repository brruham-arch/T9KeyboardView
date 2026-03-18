package com.brruham.t9ime

import android.os.Handler
import android.os.Looper

enum class InputMode { PREDICTIVE, MULTITAP }

/**
 * State machine untuk input T9.
 *
 * ── PREDICTIVE mode ──────────────────────────────────────────────────────────
 *  Tombol angka dikumpulkan → query Trie → tampil saran di bar.
 *  Composing text = saran teratas (underline).
 *  User tap saran → commit + catat di UserWordStore.
 *  Spasi → auto-commit saran teratas.
 *  Backspace → hapus digit terakhir, perbarui saran.
 *
 * ── MULTITAP mode ────────────────────────────────────────────────────────────
 *  Tap tombol sama berturut → cycle huruf A→B→C→A.
 *  Composing text = huruf aktif (underline).
 *  Timer 800ms tanpa input → finishComposing (commit huruf).
 *  Tap tombol beda → finishComposing, mulai huruf baru.
 *  Backspace saat composing → cancel composing.
 */
class T9InputController(
    private val engine: PredictionEngine,
    private val userStore: UserWordStore,
    private val onSuggestionsChanged: (List<String>) -> Unit,
    private val onCommitText: (String) -> Unit,
    private val onSetComposing: (String) -> Unit,
    private val onFinishComposing: () -> Unit,
    private val onDeleteChar: () -> Unit,
    private val onModeChanged: (InputMode) -> Unit
) {
    var mode = InputMode.PREDICTIVE
        private set

    // ── Predictive state ──────────────────────────────────────────────────────
    private var digitSequence = ""
    private var lastWord = ""
    private var currentSuggestions = listOf<String>()

    // ── Multitap state ────────────────────────────────────────────────────────
    private var mtKey = ' '
    private var mtIndex = 0
    private var isComposing = false

    private val handler = Handler(Looper.getMainLooper())
    private val mtCommitTimer = Runnable { commitMultitap() }
    private val MT_DELAY = 800L   // ms sebelum auto-commit huruf

    // ── Public API ────────────────────────────────────────────────────────────

    fun onKeyPressed(digit: Char) {
        // Tombol '1' = tanda baca → selalu multitap style, commit dulu jika ada composing predictive
        if (digit == '1') {
            if (mode == InputMode.PREDICTIVE && digitSequence.isNotEmpty()) {
                commitTopSuggestionSilent()
            }
            doMultitap('1')
            return
        }

        when (mode) {
            InputMode.PREDICTIVE -> handlePredictive(digit)
            InputMode.MULTITAP   -> doMultitap(digit)
        }
    }

    fun onSuggestionSelected(word: String) {
        recordAndCommit(word, appendSpace = true)
    }

    fun onSpacePressed() {
        when (mode) {
            InputMode.PREDICTIVE -> {
                if (digitSequence.isNotEmpty()) {
                    commitTopSuggestionSilent()
                }
                onCommitText(" ")
            }
            InputMode.MULTITAP -> {
                handler.removeCallbacks(mtCommitTimer)
                commitMultitap()
                onCommitText(" ")
            }
        }
    }

    fun onBackspace() {
        when (mode) {
            InputMode.PREDICTIVE -> {
                if (digitSequence.isNotEmpty()) {
                    digitSequence = digitSequence.dropLast(1)
                    if (digitSequence.isEmpty()) {
                        clearComposing()
                        onSuggestionsChanged(emptyList())
                    } else {
                        val sugg = engine.predict(digitSequence, lastWord)
                        currentSuggestions = sugg
                        onSuggestionsChanged(sugg)
                        onSetComposing(sugg.firstOrNull() ?: digitSequence)
                        isComposing = true
                    }
                } else {
                    onDeleteChar()
                }
            }
            InputMode.MULTITAP -> {
                handler.removeCallbacks(mtCommitTimer)
                if (isComposing) {
                    clearComposing()
                } else {
                    onDeleteChar()
                }
            }
        }
    }

    fun onBackspaceLong() {
        handler.removeCallbacks(mtCommitTimer)
        clearComposing()
        digitSequence = ""
        currentSuggestions = emptyList()
        onSuggestionsChanged(emptyList())
        // Hapus sampai spasi (hapus satu kata)
        repeat(6) { onDeleteChar() }
    }

    fun toggleMode() {
        handler.removeCallbacks(mtCommitTimer)
        clearComposing()
        digitSequence = ""
        mtKey = ' '
        mtIndex = 0
        currentSuggestions = emptyList()
        onSuggestionsChanged(emptyList())
        mode = if (mode == InputMode.PREDICTIVE) InputMode.MULTITAP else InputMode.PREDICTIVE
        onModeChanged(mode)
    }

    // ── Predictive ────────────────────────────────────────────────────────────

    private fun handlePredictive(digit: Char) {
        digitSequence += digit
        val sugg = engine.predict(digitSequence, lastWord)
        currentSuggestions = sugg
        onSuggestionsChanged(sugg)
        val composing = sugg.firstOrNull() ?: digitSequence
        onSetComposing(composing)
        isComposing = true
    }

    private fun commitTopSuggestionSilent() {
        val word = currentSuggestions.firstOrNull() ?: digitSequence
        recordAndCommit(word, appendSpace = false)
    }

    private fun recordAndCommit(word: String, appendSpace: Boolean) {
        clearComposing()
        onCommitText(if (appendSpace) "$word " else word)
        userStore.recordUsage(word)
        engine.addWord(word)          // tambah ke trie kalau kata baru/asing
        lastWord = word
        digitSequence = ""
        currentSuggestions = emptyList()
        onSuggestionsChanged(emptyList())
    }

    // ── Multitap ──────────────────────────────────────────────────────────────

    private fun doMultitap(digit: Char) {
        handler.removeCallbacks(mtCommitTimer)

        if (digit == mtKey && isComposing) {
            // Cycle ke huruf berikutnya
            val chars = engine.getCharsForKey(digit)
            if (chars.isEmpty()) return
            mtIndex = (mtIndex + 1) % chars.length
            onSetComposing(chars[mtIndex].toString())
        } else {
            // Tombol berbeda → commit sebelumnya, mulai baru
            if (isComposing) commitMultitap()
            mtKey = digit
            mtIndex = 0
            val chars = engine.getCharsForKey(digit)
            if (chars.isEmpty()) return
            onSetComposing(chars[0].toString())
            isComposing = true
        }

        handler.postDelayed(mtCommitTimer, MT_DELAY)
    }

    private fun commitMultitap() {
        if (isComposing) {
            onFinishComposing()
            isComposing = false
        }
        mtKey = ' '
        mtIndex = 0
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun clearComposing() {
        if (isComposing) {
            onSetComposing("")
            onFinishComposing()
            isComposing = false
        }
    }
}
