package com.brruham.t9ime

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

        suggestionBar  = layout.findViewById(R.id.suggestion_bar)
        keyboardView   = layout.findViewById(R.id.keyboard_view)
        modeIndicator  = layout.findViewById(R.id.mode_indicator)

        userStore = UserWordStore(this)
        engine    = PredictionEngine(this, userStore)

        controller = T9InputController(
            engine    = engine,
            userStore = userStore,
            onSuggestionsChanged = { words ->
                ui { suggestionBar.setSuggestions(words) }
            },
            onCommitText = { text ->
                currentInputConnection?.commitText(text, 1)
            },
            onSetComposing = { text ->
                currentInputConnection?.setComposingText(text, 1)
            },
            onFinishComposing = {
                currentInputConnection?.finishComposingText()
            },
            onDeleteChar = {
                currentInputConnection?.deleteSurroundingText(1, 0)
            },
            onEnter = {
                // Kirim Enter sesuai action dari field aktif (Search / Send / Go / newline)
                val ei = currentInputEditorInfo
                val action = ei?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    currentInputConnection?.performEditorAction(action)
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
            },
            onModeChanged = { mode ->
                ui {
                    modeIndicator.text = if (mode == InputMode.PREDICTIVE)
                        getString(R.string.mode_predictive)
                    else
                        getString(R.string.mode_multitap)
                    keyboardView.currentMode = mode
                    keyboardView.invalidate()
                }
            },
            onShiftChanged = { shift ->
                ui {
                    keyboardView.isShift = shift
                    keyboardView.invalidate()
                    modeIndicator.text = buildModeLabel(shift)
                }
            }
        )

        keyboardView.keyListener = object : T9KeyboardView.KeyListener {
            override fun onDigitKey(digit: Char)  = controller.onKeyPressed(digit)
            override fun onDigitLong(digit: Char) = controller.onDigitLong(digit)
            override fun onSpaceKey()             = controller.onSpacePressed()
            override fun onEnterKey()             = controller.onEnterPressed()
            override fun onBackspace()            = controller.onBackspace()
            override fun onToggleMode()           = controller.toggleMode()
            override fun onToggleShift()          = controller.toggleShift()
        }

        suggestionBar.setOnSuggestionClickListener { word ->
            controller.onSuggestionSelected(word)
        }

        keyboardView.currentMode = controller.mode
        modeIndicator.text = getString(R.string.mode_predictive)

        return layout
    }

    private fun buildModeLabel(shift: Boolean): String {
        val modeStr = if (controller.mode == InputMode.PREDICTIVE) "✦ T9" else "• abc"
        return if (shift) "$modeStr  ⇧CAPS" else modeStr
    }

    override fun onWindowShown() {
        super.onWindowShown()
        suggestionBar.setSuggestions(emptyList())
    }

    override fun onFinishInput() {
        super.onFinishInput()
    }

    private fun ui(action: () -> Unit) = keyboardView.post(action)
}
