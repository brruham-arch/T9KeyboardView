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

    // ── Predictive ────────────────────────────────────────────────────────────
    private var digitSequence = ""
    private var lastWord = ""
    private var currentSuggestions = listOf<String>()

    // ── Multitap ──────────────────────────────────────────────────────────────
    private var mtKey = ' '
    private var mtIndex = 0
    private var isComposing = false

    // Buffer kata yang sedang diketik di multitap
    // Setiap char yang sudah di-finishComposing masuk sini
    private val mtWordBuffer = StringBuilder()

    private val handler = Handler(Looper.getMainLooper())
    private val mtCommitTimer = Runnable { autoCommitMultitapChar() }
    private val MT_DELAY = 800L

    // ── Digit keys ────────────────────────────────────────────────────────────

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

    fun onSuggestionSelected(word: String) {
        when (mode) {
            InputMode.PREDICTIVE -> recordAndCommit(word, appendSpace = true)
            InputMode.MULTITAP   -> commitMultitapSuggestion(word)
        }
    }

    fun onSpacePressed() {
        when (mode) {
            InputMode.PREDICTIVE -> {
                if (digitSequence.isNotEmpty()) commitTopSilent()
                onCommitText(" ")
            }
            InputMode.MULTITAP -> {
                handler.removeCallbacks(mtCommitTimer)
                flushMultitapChar()
                learnAndResetMtBuffer()
                onCommitText(" ")
            }
        }
    }

    fun onEnterPressed() {
        handler.removeCallbacks(mtCommitTimer)
        when (mode) {
            InputMode.PREDICTIVE -> if (digitSequence.isNotEmpty()) commitTopSilent()
            InputMode.MULTITAP   -> { flushMultitapChar(); learnAndResetMtBuffer() }
        }
        onEnter()
    }

    fun onBackspace() {
        when (mode) {
            InputMode.PREDICTIVE -> {
                if (digitSequence.isNotEmpty()) {
                    digitSequence = digitSequence.dropLast(1)
                    if (digitSequence.isEmpty()) { clearComposing(); onSuggestionsChanged(emptyList()) }
                    else refreshPredictiveSuggestions()
                } else onDeleteChar()
            }
            InputMode.MULTITAP -> {
                handler.removeCallbacks(mtCommitTimer)
                if (isComposing) {
                    // Cancel composing tanpa commit
                    clearComposing()
                } else {
                    // Hapus char terakhir dari buffer juga
                    if (mtWordBuffer.isNotEmpty()) mtWordBuffer.deleteCharAt(mtWordBuffer.length - 1)
                    onDeleteChar()
                    // Update saran dari buffer yang tersisa
                    updateMultitapSuggestions()
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

    // ── Predictive logic ──────────────────────────────────────────────────────

    private fun handlePredictive(digit: Char) {
        digitSequence += digit
        refreshPredictiveSuggestions()
    }

    private fun refreshPredictiveSuggestions() {
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
        // Auto-spasi setelah commit T9 — tidak perlu tap spasi manual
        onCommitText(if (appendSpace) "$out " else "$out ")
        if (lastWord.isNotBlank()) userStore.recordBigram(lastWord, word)
        userStore.recordUsage(word)
        engine.addWord(word)
        lastWord = word
        digitSequence = ""
        currentSuggestions = emptyList()
        onSuggestionsChanged(emptyList())
        if (isShift) { isShift = false; onShiftChanged(false) }
    }

    // ── Multitap suggestion (dari suggestion bar tap) ─────────────────────────

    private fun commitMultitapSuggestion(word: String) {
        handler.removeCallbacks(mtCommitTimer)
        clearComposing()

        // Hapus karakter yang sudah diketik (isi mtWordBuffer)
        val bufLen = mtWordBuffer.length
        if (bufLen > 0) {
            repeat(bufLen) { onDeleteChar() }
        }

        // Commit kata saran
        val out = applyShift(word)
        onCommitText("$out ")
        if (lastWord.isNotBlank()) userStore.recordBigram(lastWord, word)
        userStore.recordUsage(word)
        engine.addWord(word)
        lastWord = word
        mtWordBuffer.clear()
        onSuggestionsChanged(emptyList())
        if (isShift) { isShift = false; onShiftChanged(false) }
    }

    // ── Multitap logic ────────────────────────────────────────────────────────

    private fun doMultitap(digit: Char) {
        handler.removeCallbacks(mtCommitTimer)

        if (digit == mtKey && isComposing) {
            // Cycle ke huruf berikutnya
            val chars = engine.getCharsForKey(digit)
            if (chars.isEmpty()) return
            mtIndex = (mtIndex + 1) % chars.length
            onSetComposing(applyShift(chars[mtIndex].toString()))
        } else {
            // Tombol beda — commit char sebelumnya dulu
            if (isComposing) flushMultitapChar()
            mtKey = digit; mtIndex = 0
            val chars = engine.getCharsForKey(digit)
            if (chars.isEmpty()) return
            onSetComposing(applyShift(chars[0].toString()))
            isComposing = true
        }
        handler.postDelayed(mtCommitTimer, MT_DELAY)
    }

    /** Timer habis → commit char aktif otomatis */
    private fun autoCommitMultitapChar() {
        flushMultitapChar()
        updateMultitapSuggestions()
    }

    /** Commit huruf yang sedang composing ke teks + catat ke buffer */
    private fun flushMultitapChar() {
        if (!isComposing) return
        val chars = engine.getCharsForKey(mtKey)
        if (chars.isNotEmpty() && mtIndex < chars.length) {
            val committed = chars[mtIndex]
            mtWordBuffer.append(committed)
        }
        onFinishComposing()
        isComposing = false
        mtKey = ' '; mtIndex = 0
        if (isShift) { isShift = false; onShiftChanged(false) }
    }

    /** Update suggestion bar berdasarkan isi mtWordBuffer (prefix search) */
    private fun updateMultitapSuggestions() {
        val prefix = mtWordBuffer.toString()
        if (prefix.length < 2) {
            onSuggestionsChanged(emptyList())
            return
        }
        val sugg = engine.predictFromPrefix(prefix, lastWord)
        currentSuggestions = sugg
        onSuggestionsChanged(sugg)
    }

    /** Pelajari kata dari buffer, reset buffer */
    private fun learnAndResetMtBuffer() {
        val word = mtWordBuffer.toString().lowercase().trim()
        if (word.length >= 2 && word.all { it.isLetter() }) {
            userStore.recordUsage(word)
            engine.addWord(word)
            if (lastWord.isNotBlank()) userStore.recordBigram(lastWord, word)
            lastWord = word
        }
        mtWordBuffer.clear()
        onSuggestionsChanged(emptyList())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun clearComposing() {
        if (isComposing) { onSetComposing(""); onFinishComposing(); isComposing = false }
    }

    private fun applyShift(text: String): String =
        if (isShift && text.isNotEmpty()) text.replaceFirstChar { it.uppercase() } else text
}
