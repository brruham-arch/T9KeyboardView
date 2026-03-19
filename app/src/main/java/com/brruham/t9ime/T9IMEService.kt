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
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Kalau cursor pindah bukan karena keyboard sendiri yang commit
        // (candidatesStart/End == -1 artinya tidak ada composing aktif dari kita)
        if (candidatesStart == -1 && candidatesEnd == -1) {
            if (::controller.isInitialized) controller.resetState()
        }
    }

    /** Field baru / app baru → reset total */
    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting && ::controller.isInitialized) {
            controller.resetState()
            ui { suggestionBar.setSuggestions(emptyList()) }
        }
    }

    private fun ui(action: () -> Unit) = keyboardView.post(action)
}
