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

    private var digitSequence = ""
    private var lastWord = ""
    private var currentSuggestions = listOf<String>()

    private val mtWordBuffer = StringBuilder()

    private val handler = Handler(Looper.getMainLooper())
    private val mtCommitTimer = Runnable { finalizeMultitap() }
    private val MT_DELAY = 800L

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
        commitWord(word, true)
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
                finalizeMultitap()
                onCommitText(" ")
                onSuggestionsChanged(emptyList())
            }
        }
    }

    fun onEnterPressed() {
        handler.removeCallbacks(mtCommitTimer)
        if (mode == InputMode.MULTITAP) finalizeMultitap()
        onEnter()
        onSuggestionsChanged(emptyList())
    }

    fun onBackspace() {
        when (mode) {
            InputMode.MULTITAP -> {
                if (mtWordBuffer.isNotEmpty()) {
                    mtWordBuffer.deleteCharAt(mtWordBuffer.length - 1)
                    onDeleteChar()
                    updateMultitapUI()
                } else {
                    onDeleteChar()
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
    }

    private fun commitWord(word: String, withSpace: Boolean) {
        clearComposing()

        val finalWord = if (isShift) word.replaceFirstChar { it.uppercase() } else word

        if (withSpace) onCommitText("$finalWord ")
        else onCommitText(finalWord)

        userStore.recordUsage(finalWord)
        engine.addWord(finalWord)

        if (lastWord.isNotEmpty()) {
            userStore.recordBigram(lastWord, finalWord)
        }

        lastWord = finalWord
        digitSequence = ""
        currentSuggestions = emptyList()
        onSuggestionsChanged(emptyList())
    }

    // ================= MULTITAP =================

    private val lastTapMap = mutableMapOf<Char, Int>()
    private val lastTapTime = mutableMapOf<Char, Long>()
    private val MT_CYCLE_DELAY = 600L

    private fun handleMultitap(digit: Char) {
        handler.removeCallbacks(mtCommitTimer)

        val chars = engine.getCharsForKey(digit)
        if (chars.isEmpty()) return

        val now = System.currentTimeMillis()
        val lastTime = lastTapTime[digit] ?: 0L
        val sameKey = now - lastTime < MT_CYCLE_DELAY

        val index = if (sameKey) {
            ((lastTapMap[digit] ?: 0) + 1) % chars.length
        } else {
            0
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
        }

        lastWord = finalWord

        mtWordBuffer.clear()
        lastTapMap.clear()
        lastTapTime.clear()

        // 🔥 JANGAN kosongkan suggestion di sini
    }

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
