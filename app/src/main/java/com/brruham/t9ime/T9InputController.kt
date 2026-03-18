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
    private val onDeleteWord: () -> Unit,
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
    private val mtWordBuffer = StringBuilder()   // buffer kata multitap yg sedang dibentuk

    private val handler = Handler(Looper.getMainLooper())
    private val mtCommitTimer = Runnable { commitMultitap(autoCommit = true) }
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
                commitMultitap(autoCommit = false)
                // Catat kata dari buffer sebelum reset
                learnMultitapWord()
                onCommitText(" ")
            }
        }
    }

    fun onEnterPressed() {
        handler.removeCallbacks(mtCommitTimer)
        when (mode) {
            InputMode.PREDICTIVE -> if (digitSequence.isNotEmpty()) commitTopSilent()
            InputMode.MULTITAP   -> { commitMultitap(autoCommit = false); learnMultitapWord() }
        }
        onEnter()
    }

    fun onBackspace() {
        when (mode) {
            InputMode.PREDICTIVE -> {
                if (digitSequence.isNotEmpty()) {
                    digitSequence = digitSequence.dropLast(1)
                    if (digitSequence.isEmpty()) { clearComposing(); onSuggestionsChanged(emptyList()) }
                    else refreshSuggestions()
                } else onDeleteChar()
            }
            InputMode.MULTITAP -> {
                handler.removeCallbacks(mtCommitTimer)
                if (isComposing) {
                    // Hapus huruf composing, backtrack mtWordBuffer
                    clearComposing()
                    if (mtWordBuffer.isNotEmpty()) mtWordBuffer.deleteCharAt(mtWordBuffer.length - 1)
                } else {
                    if (mtWordBuffer.isNotEmpty()) mtWordBuffer.deleteCharAt(mtWordBuffer.length - 1)
                    onDeleteChar()
                }
            }
        }
    }

    fun onBackspaceLong() {
        handler.removeCallbacks(mtCommitTimer)
        clearComposing()
        digitSequence = ""
        mtWordBuffer.clear()
        currentSuggestions = emptyList()
        onSuggestionsChanged(emptyList())
        onDeleteWord()
    }

    fun toggleMode() {
        handler.removeCallbacks(mtCommitTimer)
        clearComposing()
        digitSequence = ""
        mtKey = ' '; mtIndex = 0
        mtWordBuffer.clear()
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
        onSetComposing(applyShift(composing))
        isComposing = true
    }

    private fun commitTopSilent() {
        recordAndCommit(currentSuggestions.firstOrNull() ?: digitSequence, appendSpace = false)
    }

    private fun recordAndCommit(word: String, appendSpace: Boolean) {
        clearComposing()
        val out = applyShift(word)
        onCommitText(if (appendSpace) "$out " else out)
        // Bigram: catat pasangan lastWord → word
        if (lastWord.isNotBlank()) userStore.recordBigram(lastWord, word)
        userStore.recordUsage(word)
        engine.addWord(word)
        lastWord = word
        digitSequence = ""
        currentSuggestions = emptyList()
        onSuggestionsChanged(emptyList())
        if (isShift) { isShift = false; onShiftChanged(false) }
    }

    // ── Multitap ──────────────────────────────────────────────────────────────

    private fun doMultitap(digit: Char) {
        handler.removeCallbacks(mtCommitTimer)
        if (digit == mtKey && isComposing) {
            val chars = engine.getCharsForKey(digit)
            if (chars.isEmpty()) return
            mtIndex = (mtIndex + 1) % chars.length
            onSetComposing(applyShift(chars[mtIndex].toString()))
        } else {
            if (isComposing) commitMultitap(autoCommit = true)
            mtKey = digit; mtIndex = 0
            val chars = engine.getCharsForKey(digit)
            if (chars.isEmpty()) return
            onSetComposing(applyShift(chars[0].toString()))
            isComposing = true
        }
        handler.postDelayed(mtCommitTimer, MT_DELAY)
    }

    private fun commitMultitap(autoCommit: Boolean) {
        if (isComposing) {
            // Tambah huruf aktif ke word buffer
            val chars = engine.getCharsForKey(mtKey)
            if (chars.isNotEmpty() && mtIndex < chars.length) {
                mtWordBuffer.append(chars[mtIndex])
            }
            onFinishComposing()
            isComposing = false
        }
        mtKey = ' '; mtIndex = 0
        if (isShift) { isShift = false; onShiftChanged(false) }
        // Kalau auto-commit (timer), pelajari kata otomatis
        if (autoCommit) learnMultitapWord()
    }

    /**
     * Pelajari kata yang baru selesai diketik manual.
     * Masukkan ke UserWordStore + engine → jadi kandidat T9 berikutnya.
     */
    private fun learnMultitapWord() {
        val word = mtWordBuffer.toString().lowercase().trim()
        if (word.length >= 2 && word.all { it.isLetter() }) {
            userStore.recordUsage(word)
            engine.addWord(word)
            if (lastWord.isNotBlank()) userStore.recordBigram(lastWord, word)
            lastWord = word
        }
        mtWordBuffer.clear()
    }

    private fun clearComposing() {
        if (isComposing) { onSetComposing(""); onFinishComposing(); isComposing = false }
    }

    private fun applyShift(text: String): String =
        if (isShift && text.isNotEmpty()) text.replaceFirstChar { it.uppercase() } else text
}
