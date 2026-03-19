package com.brruham.t9ime

import android.os.Handler
import android.os.Looper

enum class InputMode { PREDICTIVE, MULTITAP }

/**
 * State machine T9 keyboard.
 *
 * ── PREDICTIVE ────────────────────────────────────────────────────────────────
 *  Digit → query Trie → composing = top suggestion (underline)
 *  Tap saran / spasi → commit + learn + bigram
 *
 * ── MULTITAP "Composing Word" ─────────────────────────────────────────────────
 *  Seluruh kata yang sedang diketik disimpan sebagai COMPOSING TEXT.
 *  Format composing: [kata_selesai][huruf_aktif_cycle]
 *  Contoh: sudah ketik "ha", sedang cycle tombol 5 (J/K/L):
 *    composing = "haj" / "hak" / "hal"
 *
 *  Saran bar = predictFromPrefix(composing_full)
 *  → prediksi selalu konsisten karena keyboard tahu full kata
 *
 *  Spasi / Enter → commit composing → learn word → reset
 *  Backspace     → hapus huruf terakhir composing → update saran
 *  Tap saran     → replace composing dengan kata saran → commit + spasi
 */
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
    private var digitSeq = ""
    private var lastWord = ""
    private var predSuggestions = listOf<String>()

    // ── Multitap "Composing Word" state ───────────────────────────────────────
    // mtDone  = bagian kata yang hurufnya sudah dikonfirmasi (tidak sedang di-cycle)
    // mtKey   = tombol yang sedang di-cycle
    // mtIndex = index huruf aktif pada tombol itu
    // composingFull = mtDone + chars[mtKey][mtIndex]
    private val mtDone = StringBuilder()   // "ha"
    private var mtKey = ' '
    private var mtIndex = 0
    private var mtActive = false           // sedang ada huruf yang di-cycle?

    private val handler = Handler(Looper.getMainLooper())
    private val mtTimer = Runnable { lockCurrentMtChar() }
    private val MT_DELAY = 750L

    // ── Computed property: full composing string ──────────────────────────────
    private val mtComposingFull: String get() {
        if (!mtActive) return mtDone.toString()
        val chars = engine.getCharsForKey(mtKey)
        val ch = if (chars.isNotEmpty() && mtIndex < chars.length) chars[mtIndex].toString() else ""
        return mtDone.toString() + ch
    }

    // ── Input events ──────────────────────────────────────────────────────────

    fun onKeyPressed(digit: Char) {
        if (digit == '1') {
            // Tanda baca — pakai multitap style di kedua mode
            if (mode == InputMode.PREDICTIVE && digitSeq.isNotEmpty()) commitPredTop(addSpace = false)
            doMtKey('1')
            return
        }
        when (mode) {
            InputMode.PREDICTIVE -> handlePredictive(digit)
            InputMode.MULTITAP   -> doMtKey(digit)
        }
    }

    fun onDigitLong(digit: Char) {
        handler.removeCallbacks(mtTimer)
        when (mode) {
            InputMode.PREDICTIVE -> {
                if (digitSeq.isNotEmpty()) commitPredTop(addSpace = false)
                onFinishComposing()
            }
            InputMode.MULTITAP -> {
                lockCurrentMtChar()
                onSetComposing(mtComposingFull)  // refresh
            }
        }
        onCommitText(digit.toString())
    }

    fun onSuggestionSelected(word: String) {
        when (mode) {
            InputMode.PREDICTIVE -> recordAndCommit(word, addSpace = true)
            InputMode.MULTITAP   -> replaceMtWithSuggestion(word)
        }
    }

    fun onSpacePressed() {
        when (mode) {
            InputMode.PREDICTIVE -> {
                if (digitSeq.isNotEmpty()) commitPredTop(addSpace = false)
                onCommitText(" ")
            }
            InputMode.MULTITAP -> {
                handler.removeCallbacks(mtTimer)
                lockCurrentMtChar()
                commitMtWord(addSpace = true)
            }
        }
    }

    fun onEnterPressed() {
        handler.removeCallbacks(mtTimer)
        when (mode) {
            InputMode.PREDICTIVE -> if (digitSeq.isNotEmpty()) commitPredTop(addSpace = false)
            InputMode.MULTITAP   -> { lockCurrentMtChar(); commitMtWord(addSpace = false) }
        }
        onEnter()
    }

    fun onBackspace() {
        when (mode) {
            InputMode.PREDICTIVE -> {
                if (digitSeq.isNotEmpty()) {
                    digitSeq = digitSeq.dropLast(1)
                    if (digitSeq.isEmpty()) { clearPredComposing(); onSuggestionsChanged(emptyList()) }
                    else refreshPred()
                } else onDeleteChar()
            }
            InputMode.MULTITAP -> {
                handler.removeCallbacks(mtTimer)
                if (mtActive) {
                    // Cancel huruf yang sedang di-cycle, kembali ke sebelumnya
                    mtActive = false; mtKey = ' '; mtIndex = 0
                    onSetComposing(applyShift(mtComposingFull))
                    updateMtSuggestions()
                } else if (mtDone.isNotEmpty()) {
                    // Hapus satu huruf dari kata composing
                    mtDone.deleteCharAt(mtDone.length - 1)
                    onSetComposing(applyShift(mtComposingFull))
                    updateMtSuggestions()
                } else {
                    // Tidak ada composing — hapus karakter sebelumnya di field
                    onFinishComposing()
                    onDeleteChar()
                }
            }
        }
    }

    fun onBackspaceLong() {
        handler.removeCallbacks(mtTimer)
        clearAll()
        onDeleteWord()
    }

    fun toggleMode() {
        handler.removeCallbacks(mtTimer)
        // Commit apapun yang sedang composing sebelum ganti mode
        when (mode) {
            InputMode.PREDICTIVE -> if (digitSeq.isNotEmpty()) commitPredTop(addSpace = false)
            InputMode.MULTITAP   -> { lockCurrentMtChar(); if (mtDone.isNotEmpty()) commitMtWord(addSpace = false) }
        }
        clearAll()
        mode = if (mode == InputMode.PREDICTIVE) InputMode.MULTITAP else InputMode.PREDICTIVE
        onModeChanged(mode)
    }

    fun toggleShift() {
        isShift = !isShift
        onShiftChanged(isShift)
        // Update composing display dengan shift baru
        when (mode) {
            InputMode.MULTITAP   -> if (mtDone.isNotEmpty() || mtActive) onSetComposing(applyShiftMt(mtComposingFull))
            InputMode.PREDICTIVE -> if (digitSeq.isNotEmpty()) {
                val top = predSuggestions.firstOrNull() ?: digitSeq
                onSetComposing(applyShiftMt(top))
            }
        }
    }

    // ── Predictive internal ───────────────────────────────────────────────────

    private fun handlePredictive(digit: Char) {
        digitSeq += digit
        refreshPred()
    }

    private fun refreshPred() {
        val sugg = engine.predict(digitSeq, lastWord)
        predSuggestions = sugg
        onSuggestionsChanged(sugg)
        val top = sugg.firstOrNull() ?: digitSeq
        onSetComposing(applyShiftMt(top))
    }

    private fun commitPredTop(addSpace: Boolean) {
        val word = predSuggestions.firstOrNull() ?: digitSeq
        recordAndCommit(word, addSpace)
    }

    private fun recordAndCommit(word: String, addSpace: Boolean) {
        val out = applyShiftMt(word)
        // commitText otomatis replace composing region — jangan finishComposing dulu
        onCommitText("$out ")
        if (lastWord.isNotBlank()) userStore.recordBigram(lastWord, word)
        userStore.recordUsage(word)
        engine.addWord(word)
        lastWord = word
        digitSeq = ""; predSuggestions = emptyList()
        onSuggestionsChanged(emptyList())
        if (isShift) { isShift = false; onShiftChanged(false) }
    }

    private fun clearPredComposing() {
        onSetComposing(""); onFinishComposing()
    }

    // ── Multitap internal ─────────────────────────────────────────────────────

    private fun doMtKey(digit: Char) {
        handler.removeCallbacks(mtTimer)
        if (digit == mtKey && mtActive) {
            // Cycle huruf berikutnya pada tombol yang sama
            val chars = engine.getCharsForKey(digit)
            if (chars.isEmpty()) return
            mtIndex = (mtIndex + 1) % chars.length
        } else {
            // Tombol berbeda — lock huruf sebelumnya, mulai huruf baru
            lockCurrentMtChar()
            mtKey = digit; mtIndex = 0; mtActive = true
            val chars = engine.getCharsForKey(digit)
            if (chars.isEmpty()) { mtActive = false; return }
        }
        // Update composing dengan full word + huruf aktif
        onSetComposing(applyShiftMt(mtComposingFull))
        updateMtSuggestions()
        handler.postDelayed(mtTimer, MT_DELAY)
    }

    /** Timer habis → konfirmasi huruf aktif ke mtDone, tetap composing */
    private fun lockCurrentMtChar() {
        if (!mtActive) return
        val chars = engine.getCharsForKey(mtKey)
        if (chars.isNotEmpty() && mtIndex < chars.length) {
            mtDone.append(chars[mtIndex])
        }
        mtActive = false; mtKey = ' '; mtIndex = 0
        // Masih composing — update display tanpa huruf cycle
        onSetComposing(applyShiftMt(mtComposingFull))
        updateMtSuggestions()
        if (isShift && mtDone.length == 1) { isShift = false; onShiftChanged(false) }
    }

    /** Commit seluruh kata composing, learn, reset */
    private fun commitMtWord(addSpace: Boolean) {
        val word = mtComposingFull.lowercase().trim()
        val out = applyShiftMt(mtComposingFull)
        // commitText replace composing langsung, tidak perlu finishComposing dulu
        onCommitText(if (addSpace) "$out " else out)
        if (word.length >= 2 && word.all { it.isLetter() }) {
            if (lastWord.isNotBlank()) userStore.recordBigram(lastWord, word)
            userStore.recordUsage(word)
            engine.addWord(word)
            lastWord = word
        }
        mtDone.clear(); mtKey = ' '; mtIndex = 0; mtActive = false
        onSuggestionsChanged(emptyList())
        if (isShift) { isShift = false; onShiftChanged(false) }
    }

    /** Tap saran di multitap → ganti seluruh composing dengan kata saran */
    private fun replaceMtWithSuggestion(word: String) {
        handler.removeCallbacks(mtTimer)
        val out = applyShiftMt(word)
        // commitText replace composing region langsung
        onCommitText("$out ")
        if (lastWord.isNotBlank()) userStore.recordBigram(lastWord, word)
        userStore.recordUsage(word)
        engine.addWord(word)
        lastWord = word
        mtDone.clear(); mtKey = ' '; mtIndex = 0; mtActive = false
        onSuggestionsChanged(emptyList())
        if (isShift) { isShift = false; onShiftChanged(false) }
    }

    /** Update saran berdasarkan full composing word saat ini */
    private fun updateMtSuggestions() {
        val full = mtComposingFull
        if (full.length < 2) { onSuggestionsChanged(emptyList()); return }
        val sugg = engine.predictFromPrefix(full.lowercase(), lastWord)
        onSuggestionsChanged(sugg)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun clearAll() {
        digitSeq = ""; predSuggestions = emptyList()
        mtDone.clear(); mtKey = ' '; mtIndex = 0; mtActive = false
        onSuggestionsChanged(emptyList())
    }

    /** Shift hanya berlaku untuk huruf pertama kata */
    private fun applyShiftMt(text: String): String {
        if (!isShift || text.isEmpty()) return text
        return text[0].uppercaseChar() + text.drop(1)
    }

    private fun applyShift(text: String) = applyShiftMt(text)
}
