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

<<<<<<< HEAD
    // ── Predictive ────────────────────────────────────────────────────────────
=======
>>>>>>> d3d4eb2b957c2e925c33ed6a8490bd66292ee34e
    private var digitSequence = ""
    private var lastWord = ""
    private var currentSuggestions = listOf<String>()

<<<<<<< HEAD
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

=======
    private val mtWordBuffer = StringBuilder()

    private val handler = Handler(Looper.getMainLooper())
    private val mtCommitTimer = Runnable { finalizeMultitap() }
    private val MT_DELAY = 800L

>>>>>>> d3d4eb2b957c2e925c33ed6a8490bd66292ee34e
    fun onKeyPressed(digit: Char) {
        when (mode) {
            InputMode.PREDICTIVE -> handlePredictive(digit)
            InputMode.MULTITAP   -> handleMultitap(digit)
        }
    }

    fun onDigitLong(digit: Char) {
        handler.removeCallbacks(mtCommitTimer)
        clearAll()
        onCommitText(digit.toString())
    }

    fun onSuggestionSelected(word: String) {
<<<<<<< HEAD
        when (mode) {
            InputMode.PREDICTIVE -> recordAndCommit(word, appendSpace = true)
            InputMode.MULTITAP   -> commitMultitapSuggestion(word)
        }
=======
        commitWord(word, true)
>>>>>>> d3d4eb2b957c2e925c33ed6a8490bd66292ee34e
    }

    fun onSpacePressed() {
        when (mode) {
            InputMode.PREDICTIVE -> {
                if (digitSequence.isNotEmpty()) commitTop()
                onCommitText(" ")
                onSuggestionsChanged(emptyList())
            }
            InputMode.MULTITAP -> {
                handler.removeCallbacks(mtCommitTimer)
<<<<<<< HEAD
                flushMultitapChar()
                learnAndResetMtBuffer()
=======
                finalizeMultitap()
>>>>>>> d3d4eb2b957c2e925c33ed6a8490bd66292ee34e
                onCommitText(" ")
                onSuggestionsChanged(emptyList())
            }
        }
    }

    fun onEnterPressed() {
        handler.removeCallbacks(mtCommitTimer)
<<<<<<< HEAD
        when (mode) {
            InputMode.PREDICTIVE -> if (digitSequence.isNotEmpty()) commitTopSilent()
            InputMode.MULTITAP   -> { flushMultitapChar(); learnAndResetMtBuffer() }
        }
=======
        if (mode == InputMode.MULTITAP) finalizeMultitap()
>>>>>>> d3d4eb2b957c2e925c33ed6a8490bd66292ee34e
        onEnter()
        onSuggestionsChanged(emptyList())
    }

    fun onBackspace() {
        when (mode) {
<<<<<<< HEAD
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
=======
            InputMode.MULTITAP -> {
                if (mtWordBuffer.isNotEmpty()) {
                    mtWordBuffer.deleteCharAt(mtWordBuffer.length - 1)
                    onDeleteChar()
                    updateMultitapUI()
                } else {
>>>>>>> d3d4eb2b957c2e925c33ed6a8490bd66292ee34e
                    onDeleteChar()
                    // Update saran dari buffer yang tersisa
                    updateMultitapSuggestions()
                }
            }
            else -> onDeleteChar()
        }
    }

    fun onBackspaceLong() {
        handler.removeCallbacks(mtCommitTimer)
        clearAll()
        onDeleteWord()
    }

    fun toggleMode() {
        handler.removeCallbacks(mtCommitTimer)
        mode = if (mode == InputMode.PREDICTIVE) InputMode.MULTITAP else InputMode.PREDICTIVE
        clearAll()
        onModeChanged(mode)
    }

    fun toggleShift() {
        isShift = !isShift
        onShiftChanged(isShift)
    }

<<<<<<< HEAD
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
=======
    // ================= PREDICTIVE =================

    private fun handlePredictive(digit: Char) {
        digitSequence += digit
        val suggestions = engine.predict(digitSequence, lastWord)
        currentSuggestions = suggestions
        onSuggestionsChanged(suggestions)
        onSetComposing(suggestions.firstOrNull() ?: digitSequence)
    }

    private fun commitTop() {
        val word = currentSuggestions.firstOrNull() ?: digitSequence
        commitWord(word, false)
>>>>>>> d3d4eb2b957c2e925c33ed6a8490bd66292ee34e
    }

    private fun commitWord(word: String, withSpace: Boolean) {
        clearComposing()
<<<<<<< HEAD
        val out = applyShift(word)
        // Auto-spasi setelah commit T9 — tidak perlu tap spasi manual
        onCommitText(if (appendSpace) "$out " else "$out ")
        if (lastWord.isNotBlank()) userStore.recordBigram(lastWord, word)
        userStore.recordUsage(word)
        engine.addWord(word)
        lastWord = word
=======

        val finalWord = if (isShift) word.replaceFirstChar { it.uppercase() } else word

        if (withSpace) onCommitText("$finalWord ")
        else onCommitText(finalWord)

        userStore.recordUsage(finalWord)
        engine.addWord(finalWord)

        if (lastWord.isNotEmpty()) {
            userStore.recordBigram(lastWord, finalWord)
        }

        lastWord = finalWord
>>>>>>> d3d4eb2b957c2e925c33ed6a8490bd66292ee34e
        digitSequence = ""
        currentSuggestions = emptyList()
        onSuggestionsChanged(emptyList())
    }

<<<<<<< HEAD
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
=======
    // ================= MULTITAP =================
>>>>>>> d3d4eb2b957c2e925c33ed6a8490bd66292ee34e

    private val lastTapMap = mutableMapOf<Char, Int>()
    private val lastTapTime = mutableMapOf<Char, Long>()
    private val MT_CYCLE_DELAY = 600L

    private fun handleMultitap(digit: Char) {
        handler.removeCallbacks(mtCommitTimer)

<<<<<<< HEAD
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
=======
        val chars = engine.getCharsForKey(digit)
        if (chars.isEmpty()) return

        val now = System.currentTimeMillis()
        val lastTime = lastTapTime[digit] ?: 0L
        val sameKey = now - lastTime < MT_CYCLE_DELAY

        val index = if (sameKey) {
            ((lastTapMap[digit] ?: 0) + 1) % chars.length
        } else {
            0
>>>>>>> d3d4eb2b957c2e925c33ed6a8490bd66292ee34e
        }

        lastTapMap[digit] = index
        lastTapTime[digit] = now

        val char = chars[index]

        if (sameKey && mtWordBuffer.isNotEmpty()) {
            mtWordBuffer.setCharAt(mtWordBuffer.length - 1, char)
        } else {
            mtWordBuffer.append(char)
        }

        updateMultitapUI()
        handler.postDelayed(mtCommitTimer, MT_DELAY)
    }

<<<<<<< HEAD
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
=======
    private fun updateMultitapUI() {
        val rawWord = mtWordBuffer.toString()
        val word = if (isShift) rawWord.replaceFirstChar { it.uppercase() } else rawWord

        onSetComposing(word)

        // 🔥 FIX: pakai kata langsung untuk prediksi (bukan fakeDigits)
        val predictions = if (word.length >= 2)
            engine.predictWordPrefix(word, lastWord)
        else emptyList()

        val finalSuggestions = mutableListOf<String>()
        finalSuggestions.add(word)
        finalSuggestions.addAll(predictions.filter { it != word })

        onSuggestionsChanged(finalSuggestions)
    }

    private fun finalizeMultitap() {
        val rawWord = mtWordBuffer.toString()
        if (rawWord.isEmpty()) return

        val finalWord = if (isShift)
            rawWord.replaceFirstChar { it.uppercase() }
        else rawWord

        onFinishComposing()

        userStore.recordUsage(finalWord)
        engine.addWord(finalWord)

        if (lastWord.isNotEmpty()) {
            userStore.recordBigram(lastWord, finalWord)
>>>>>>> d3d4eb2b957c2e925c33ed6a8490bd66292ee34e
        }

        lastWord = finalWord

        mtWordBuffer.clear()
<<<<<<< HEAD
        onSuggestionsChanged(emptyList())
=======
        lastTapMap.clear()
        lastTapTime.clear()

        // 🔥 JANGAN kosongkan suggestion di sini
>>>>>>> d3d4eb2b957c2e925c33ed6a8490bd66292ee34e
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun clearComposing() {
        onFinishComposing()
    }

    private fun clearAll() {
        digitSequence = ""
        mtWordBuffer.clear()
        lastTapMap.clear()
        lastTapTime.clear()
        currentSuggestions = emptyList()
        onSuggestionsChanged(emptyList())
    }
}
