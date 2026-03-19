package com.brruham.t9ime

import android.content.ClipboardManager
import android.view.inputmethod.InputConnection
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView

class T9IMEService : InputMethodService() {

    private lateinit var engine: PredictionEngine
    private lateinit var userStore: UserWordStore
    private lateinit var settings: SettingsStore
    private lateinit var controller: T9InputController
    private lateinit var keyboardView: T9KeyboardView
    private lateinit var suggestionBar: SuggestionBarView
    private lateinit var modeIndicator: TextView

    override fun onCreateInputView(): View {
        val layout = layoutInflater.inflate(R.layout.keyboard_main, null)
        suggestionBar = layout.findViewById(R.id.suggestion_bar)
        keyboardView  = layout.findViewById(R.id.keyboard_view)
        modeIndicator = layout.findViewById(R.id.mode_indicator)

        userStore = UserWordStore(this)
        settings  = SettingsStore(this)
        engine    = PredictionEngine(this, userStore)

        controller = T9InputController(
            engine    = engine,
            userStore = userStore,
            onSuggestionsChanged = { words -> ui { suggestionBar.setSuggestions(words) } },
            onCommitText    = { text -> currentInputConnection?.commitText(text, 1) },
            onSetComposing  = { text -> currentInputConnection?.setComposingText(text, 1) },
            onFinishComposing = { currentInputConnection?.finishComposingText() },
            onDeleteChar    = { smartDelete() },
            onDeleteWord    = { smartDeleteWord() },
            onEnter = {
                val ei = currentInputEditorInfo
                val action = ei?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED)
                    currentInputConnection?.performEditorAction(action)
                else
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            },
            onModeChanged = { mode ->
                ui {
                    modeIndicator.text = modeLabel(mode, controller.isShift)
                    keyboardView.currentMode = mode
                    keyboardView.invalidate()
                }
            },
            onShiftChanged = { shift ->
                ui {
                    keyboardView.isShift = shift
                    keyboardView.invalidate()
                    modeIndicator.text = modeLabel(controller.mode, shift)
                }
            }
        )

        keyboardView.keyListener = object : T9KeyboardView.KeyListener {
            override fun onDigitKey(digit: Char)      = controller.onKeyPressed(digit)
            override fun onDigitLong(digit: Char)     = controller.onDigitLong(digit)
            override fun onSpaceKey()                 = controller.onSpacePressed()
            override fun onEnterKey()                 = controller.onEnterPressed()
            override fun onBackspace()                = controller.onBackspace()
            override fun onBackspaceLong()            = controller.onBackspaceLong()
            override fun onToggleMode()               { controller.toggleMode(); keyboardView.invalidate() }
            override fun onToggleShift()              = controller.toggleShift()
            override fun onPaste()                    = pasteFromClipboard()
            override fun onEmojiSelected(emoji: String) {
                currentInputConnection?.commitText(emoji, 1)
            }
        }

        suggestionBar.setOnSuggestionClickListener { word -> controller.onSuggestionSelected(word) }
        keyboardView.currentMode = controller.mode
        modeIndicator.text = modeLabel(controller.mode, false)
        return layout
    }

    private fun pasteFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = cm.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        if (text.isEmpty()) return
        currentInputConnection?.commitText(text, 1)
    }

    private fun modeLabel(mode: InputMode, shift: Boolean): String {
        val m = if (mode == InputMode.PREDICTIVE) "✦ T9" else "• abc"
        return if (shift) "$m  ⇧" else m
    }

    override fun onWindowShown() {
        super.onWindowShown()
        suggestionBar.setSuggestions(emptyList())
    }

    /**
     * Dipanggil Android setiap kali selection/cursor berubah di field.
     * oldSelStart != newSelStart → user tap/klik pindahkan cursor.
     * Reset state keyboard agar tidak ada composing hantu.
     */


    // ── Smart delete ─────────────────────────────────────────────────────────────

    private fun smartDelete() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun smartDeleteWord() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1); return
        }
        val before = ic.getTextBeforeCursor(200, 0)?.toString() ?: return
        if (before.isEmpty()) return
        var end = before.length
        while (end > 0 && before[end - 1] == ' ') end--
        var start = end
        while (start > 0 && before[start - 1] != ' ') start--
        val del = before.length - start
        if (del > 0) ic.deleteSurroundingText(del, 0)
    }

    // ── Cursor tracking ───────────────────────────────────────────────────────

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (candidatesStart == -1 && candidatesEnd == -1) {
            if (::controller.isInitialized) controller.resetState()
        }
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting && ::controller.isInitialized) {
            controller.resetState()
            ui { suggestionBar.setSuggestions(emptyList()) }
        }
    }

    private fun ui(action: () -> Unit) = keyboardView.post(action)
}
