package com.brruham.t9ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Bar horizontal yang menampilkan saran kata T9.
 * Kata pertama (top suggestion) diberi warna teal — ini yang akan di-commit jika user tekan spasi.
 * Kata lain abu-abu, bisa di-tap langsung.
 *
 * Bisa di-scroll horizontal jika banyak saran.
 */
class SuggestionBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {

    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        setPadding(8, 0, 8, 0)
    }

    private var onSuggestionClick: ((String) -> Unit)? = null

    init {
        addView(container)
        isHorizontalScrollBarEnabled = false
        setBackgroundColor(Color.parseColor("#080810"))
        overScrollMode = OVER_SCROLL_NEVER
    }

    fun setSuggestions(words: List<String>) {
        container.removeAllViews()

        if (words.isEmpty()) {
            // Tampilkan placeholder
            val hint = makeTextView("ketik untuk prediksi…", isTop = false).apply {
                setTextColor(Color.parseColor("#2A3050"))
                typeface = Typeface.defaultFromStyle(Typeface.ITALIC)
            }
            container.addView(hint)
            return
        }

        words.forEachIndexed { i, word ->
            if (i > 0) container.addView(makeDivider())
            container.addView(makeTextView(word, isTop = i == 0))
        }

        // Scroll ke awal
        scrollTo(0, 0)
    }

    private fun makeTextView(word: String, isTop: Boolean): TextView {
        return TextView(context).apply {
            text = word
            textSize = 14f
            setTextColor(
                if (isTop) Color.parseColor("#00D4AA") else Color.parseColor("#7A8BAA")
            )
            typeface = if (isTop) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                       else Typeface.MONOSPACE
            setPadding(28, 0, 28, 0)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setOnClickListener { onSuggestionClick?.invoke(word) }
            // Visual feedback tap
            isClickable = true
            isFocusable = false
        }
    }

    private fun makeDivider(): android.view.View {
        return android.view.View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                setMargins(0, 10, 0, 10)
            }
            setBackgroundColor(Color.parseColor("#1E2440"))
        }
    }

    fun setOnSuggestionClickListener(listener: (String) -> Unit) {
        onSuggestionClick = listener
    }
}
