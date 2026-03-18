package com.brruham.t9ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.widget.TextView

/**
 * Entry point keyboard IME.
 * Tugas: inisialisasi semua komponen dan hubungkan event ke InputConnection.
 *
 * Flow:
 *   onCreateInputView() → inflate layout → init engine + controller
 *   KeyboardView event → controller.onKeyPressed()
 *   Controller callback → currentInputConnection.commitText / setComposingText / dll
 */
class T9IMEService : InputMethodService() {

    private lateinit var engine: PredictionEngine
    private lateinit var userStore: UserWordStore
    private lateinit var controller: T9InputController

    private lateinit var keyboardView: T9KeyboardView
    private lateinit var suggestionBar: SuggestionBarView
    private lateinit var modeIndicator: TextView

    override fun onCreateInputView(): View {
        val layout = layoutInflater.inflate(R.layout.keyboard_main, null)

        suggestionBar   = layout.findViewById(R.id.suggestion_bar)
        keyboardView    = layout.findViewById(R.id.keyboard_view)
        modeIndicator   = layout.findViewById(R.id.mode_indicator)

        // Init engine & store (engine loading kamus di background thread)
        userStore = UserWordStore(this)
        engine    = PredictionEngine(this, userStore)

        // Controller: semua event → callback ke InputConnection
        controller = T9InputController(
            engine  = engine,
            userStore = userStore,
            onSuggestionsChanged = { words ->
                runOnUiThread { suggestionBar.setSuggestions(words) }
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
            onModeChanged = { mode ->
                runOnUiThread {
                    modeIndicator.text = if (mode == InputMode.PREDICTIVE)
                        getString(R.string.mode_predictive)
                    else
                        getString(R.string.mode_multitap)
                    keyboardView.currentMode = mode
                    keyboardView.invalidate()
                }
            }
        )

        // Hubungkan key events
        keyboardView.keyListener = object : T9KeyboardView.KeyListener {
            override fun onDigitKey(digit: Char) = controller.onKeyPressed(digit)
            override fun onSpaceKey()             = controller.onSpacePressed()
            override fun onBackspace()            = controller.onBackspace()
            override fun onBackspaceLong()        = controller.onBackspaceLong()
            override fun onToggleMode()           = controller.toggleMode()
        }

        // Tap saran langsung commit
        suggestionBar.setOnSuggestionClickListener { word ->
            controller.onSuggestionSelected(word)
        }

        // Set mode awal pada view
        keyboardView.currentMode = controller.mode
        modeIndicator.text = getString(R.string.mode_predictive)

        return layout
    }

    override fun onWindowShown() {
        super.onWindowShown()
        // Refresh suggestion bar kosong saat keyboard muncul
        suggestionBar.setSuggestions(emptyList())
    }

    override fun onFinishInput() {
        super.onFinishInput()
        // Reset state saat berpindah field
        controller.onBackspaceLong()  // clear composing
    }

    // ── Helper: run on main thread ────────────────────────────────────────────

    private fun runOnUiThread(action: () -> Unit) {
        keyboardView.post(action)
    }
}
