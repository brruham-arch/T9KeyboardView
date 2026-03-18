package com.brruham.t9ime

import android.os.Handler
import android.os.Looper

enum class InputMode { PREDICTIVE, MULTITAP }

class T9InputController(
    private val engine: PredictionEngine,
    private val userStore: UserWordStore,
    private val onSuggestionsChanged: (List<String>) -> Unit,
    private val onCommitText: (String) -> Unit,
    private val onSetComposing: (String) -> Unit,
    private val onFinishComposing: () -> Unit,
    private val onDeleteChar: () -> Unit,
    private val onEnter: () -> Unit,
    private val onModeChanged: (InputMode) -> Unit,
    private val onShiftChanged: (Boolean) -> Unit
) {
    var mode = InputMode.PREDICTIVE
        private set

    var isShift = false
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
    private val MT_DELAY = 800L

    // ── Key Events ────────────────────────────────────────────────────────────

    fun onKeyPressed(digit: Char) {
        if (digit == '1') {
            if (mode == InputMode.PREDICTIVE && digitSequence.isNotEmpty()) commitTopSilent()
            doMultitap('1')
            return
        }
        when (mode) {
            InputMode.PREDICTIVE -> handlePredictive(digit)
            InputMode.MULTITAP   -> doMultitap(digit)
        }
    }

    /** Long-press tombol → langsung ketik angka itu */
    fun onDigitLong(digit: Char) {
        handler.removeCallbacks(mtCommitTimer)
        clearComposing()
        digitSequence = ""
        onCommitText(digit.toString())
    }

    fun onSuggestionSelected(word: String) = recordAndCommit(word, appendSpace = true)

    fun onSpacePressed() {
        when (mode) {
            InputMode.PREDICTIVE -> {
                if (digitSequence.isNotEmpty()) commitTopSilent()
                onCommitText(" ")
            }
            InputMode.MULTITAP -> {
                handler.removeCallbacks(mtCommitTimer)
                commitMultitap()
                onCommitText(" ")
            }
        }
    }

    fun onEnterPressed() {
        handler.removeCallbacks(mtCommitTimer)
        if (digitSequence.isNotEmpty()) commitTopSilent()
        else if (isComposing) commitMultitap()
        onEnter()
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
                        refreshSuggestions()
                    }
                } else {
                    onDeleteChar()
                }
            }
            InputMode.MULTITAP -> {
                handler.removeCallbacks(mtCommitTimer)
                if (isComposing) clearComposing() else onDeleteChar()
            }
        }
    }

    fun toggleMode() {
        handler.removeCallbacks(mtCommitTimer)
        clearComposing()
        digitSequence = ""
        mtKey = ' '; mtIndex = 0
        currentSuggestions = emptyList()
        onSuggestionsChanged(emptyList())
        mode = if (mode == InputMode.PREDICTIVE) InputMode.MULTITAP else InputMode.PREDICTIVE
        onModeChanged(mode)
    }

    fun toggleShift() {
        isShift = !isShift
        onShiftChanged(isShift)
    }

    // ── Predictive ────────────────────────────────────────────────────────────

    private fun handlePredictive(digit: Char) {
        digitSequence += digit
        refreshSuggestions()
    }

    private fun refreshSuggestions() {
        val sugg = engine.predict(digitSequence, lastWord)
        currentSuggestions = sugg
        onSuggestionsChanged(sugg)
        val composing = sugg.firstOrNull() ?: digitSequence
        onSetComposing(if (isShift) composing.replaceFirstChar { it.uppercase() } else composing)
        isComposing = true
    }

    private fun commitTopSilent() {
        val word = currentSuggestions.firstOrNull() ?: digitSequence
        recordAndCommit(word, appendSpace = false)
    }

    private fun recordAndCommit(word: String, appendSpace: Boolean) {
        clearComposing()
        val out = if (isShift) word.replaceFirstChar { it.uppercase() } else word
        onCommitText(if (appendSpace) "$out " else out)
        userStore.recordUsage(word)
        engine.addWord(word)
        lastWord = word
        digitSequence = ""
        currentSuggestions = emptyList()
        onSuggestionsChanged(emptyList())
        // Auto-reset shift setelah huruf pertama kalimat
        if (isShift) { isShift = false; onShiftChanged(false) }
    }

    // ── Multitap ──────────────────────────────────────────────────────────────

    private fun doMultitap(digit: Char) {
        handler.removeCallbacks(mtCommitTimer)
        if (digit == mtKey && isComposing) {
            val chars = engine.getCharsForKey(digit)
            if (chars.isEmpty()) return
            mtIndex = (mtIndex + 1) % chars.length
            val ch = chars[mtIndex].let { if (isShift) it.uppercaseChar() else it }
            onSetComposing(ch.toString())
        } else {
            if (isComposing) commitMultitap()
            mtKey = digit; mtIndex = 0
            val chars = engine.getCharsForKey(digit)
            if (chars.isEmpty()) return
            val ch = chars[0].let { if (isShift) it.uppercaseChar() else it }
            onSetComposing(ch.toString())
            isComposing = true
        }
        handler.postDelayed(mtCommitTimer, MT_DELAY)
    }

    private fun commitMultitap() {
        if (isComposing) { onFinishComposing(); isComposing = false }
        mtKey = ' '; mtIndex = 0
        if (isShift) { isShift = false; onShiftChanged(false) }
    }

    private fun clearComposing() {
        if (isComposing) { onSetComposing(""); onFinishComposing(); isComposing = false }
    }
}
