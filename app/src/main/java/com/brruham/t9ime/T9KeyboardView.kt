package com.brruham.t9ime

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Custom-drawn T9 keyboard — 3×4 grid.
 *
 * Baris 1: [1 .,!?] [2 ABC] [3 DEF]
 * Baris 2: [4 GHI]  [5 JKL] [6 MNO]
 * Baris 3: [7 PQRS] [8 TUV] [9 WXYZ]
 * Baris 4: [⇄ MODE]  [0 SPASI] [⌫]
 *
 * Estetika: dark navy dengan aksen teal & merah.
 * Setiap tombol punya main digit (besar) + sub label (kecil di bawah).
 * Pressed state: highlight biru.
 * Backspace: hold = repeat delete.
 */
class T9KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Key Listener ──────────────────────────────────────────────────────────

    interface KeyListener {
        fun onDigitKey(digit: Char)
        fun onSpaceKey()
        fun onBackspace()
        fun onBackspaceLong()
        fun onToggleMode()
    }

    var keyListener: KeyListener? = null
    var currentMode: InputMode = InputMode.PREDICTIVE

    // ── Key Definitions ───────────────────────────────────────────────────────

    private data class Key(
        val id: Char,
        val main: String,
        val sub: String,
        val type: KeyType = KeyType.NORMAL,
        var rect: RectF = RectF()
    )

    private enum class KeyType { NORMAL, SPECIAL_MODE, SPECIAL_DELETE, SPECIAL_SPACE }

    private val keyDefs = listOf(
        Key('1', "1", ".,!?",  KeyType.NORMAL),
        Key('2', "2", "ABC",   KeyType.NORMAL),
        Key('3', "3", "DEF",   KeyType.NORMAL),
        Key('4', "4", "GHI",   KeyType.NORMAL),
        Key('5', "5", "JKL",   KeyType.NORMAL),
        Key('6', "6", "MNO",   KeyType.NORMAL),
        Key('7', "7", "PQRS",  KeyType.NORMAL),
        Key('8', "8", "TUV",   KeyType.NORMAL),
        Key('9', "9", "WXYZ",  KeyType.NORMAL),
        Key('M', "⇄", "MODE",  KeyType.SPECIAL_MODE),
        Key('0', "⎵", "SPASI", KeyType.SPECIAL_SPACE),
        Key('B', "⌫", "",      KeyType.SPECIAL_DELETE)
    )

    // ── Paint Objects ─────────────────────────────────────────────────────────

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#0D0D1A")
        isAntiAlias = false
    }

    private val normalKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#14192E")
        style = Paint.Style.FILL
    }
    private val pressedKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#243060")
        style = Paint.Style.FILL
    }
    private val modePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A1525")
        style = Paint.Style.FILL
    }
    private val modePressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B2040")
        style = Paint.Style.FILL
    }
    private val deletePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111825")
        style = Paint.Style.FILL
    }
    private val deletePressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E2A40")
        style = Paint.Style.FILL
    }
    private val spacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0E1528")
        style = Paint.Style.FILL
    }

    // Border paints
    private val borderNormal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E2440")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val borderAccentTeal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00D4AA44")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val borderAccentRed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E9456044")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    // Text paints (sized in onSizeChanged)
    private val mainTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8EAF0")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        typeface = Typeface.MONOSPACE
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#505878")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val modeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E94560")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val modeSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#883040")
        textAlign = Paint.Align.CENTER
    }
    private val deleteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B7A99")
        textAlign = Paint.Align.CENTER
    }
    private val spaceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A4560")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val cornerRadius = 10f
    private val gap = 5f

    // ── Touch State ───────────────────────────────────────────────────────────

    private var pressedId: Char? = null
    private val bsHandler = Handler(Looper.getMainLooper())
    private var bsHeld = false

    // ── Layout ────────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutKeys(w.toFloat(), h.toFloat())

        val keyH = h / 4f
        val keyW = w / 3f
        val baseSize = minOf(keyH, keyW)

        mainTextPaint.textSize    = baseSize * 0.30f
        modeTextPaint.textSize    = baseSize * 0.22f
        deleteTextPaint.textSize  = baseSize * 0.28f
        spaceTextPaint.textSize   = baseSize * 0.26f
        subTextPaint.textSize     = baseSize * 0.17f
        modeSubPaint.textSize     = baseSize * 0.15f
    }

    private fun layoutKeys(w: Float, h: Float) {
        val colW = (w - gap * 4) / 3f
        val rowH = (h - gap * 5) / 4f

        keyDefs.forEachIndexed { i, key ->
            val col = i % 3
            val row = i / 3
            key.rect = RectF(
                gap + col * (colW + gap),
                gap + row * (rowH + gap),
                gap + col * (colW + gap) + colW,
                gap + row * (rowH + gap) + rowH
            )
        }
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        for (key in keyDefs) drawKey(canvas, key)
    }

    private fun drawKey(canvas: Canvas, key: Key) {
        val pressed = (key.id == pressedId)
        val r = key.rect
        val cx = r.centerX()
        val cy = r.centerY()
        val cr = cornerRadius

        // ── Background ────────────────────────────────────────────────────────
        val fillPaint = when (key.type) {
            KeyType.SPECIAL_MODE   -> if (pressed) modePressedPaint   else modePaint
            KeyType.SPECIAL_DELETE -> if (pressed) deletePressedPaint  else deletePaint
            KeyType.SPECIAL_SPACE  -> if (pressed) pressedKeyPaint     else spacePaint
            KeyType.NORMAL         -> if (pressed) pressedKeyPaint     else normalKeyPaint
        }
        canvas.drawRoundRect(r, cr, cr, fillPaint)

        // ── Border ────────────────────────────────────────────────────────────
        val borderPaint = when (key.type) {
            KeyType.SPECIAL_MODE   -> borderAccentRed
            KeyType.SPECIAL_DELETE -> borderNormal
            else                   -> if (pressed) borderAccentTeal else borderNormal
        }
        canvas.drawRoundRect(r, cr, cr, borderPaint)

        // ── Labels ────────────────────────────────────────────────────────────
        when (key.type) {
            KeyType.SPECIAL_MODE -> {
                // Mode icon + current mode label
                val modeLabel = if (currentMode == InputMode.PREDICTIVE) "T9" else "abc"
                val subLabel  = if (currentMode == InputMode.PREDICTIVE) "prediktif" else "multi-tap"
                canvas.drawText(modeLabel, cx, cy - subTextPaint.textSize * 0.3f + mainY(modeTextPaint, r), modeTextPaint)
                canvas.drawText(subLabel, cx, cy + modeSubPaint.textSize + mainY(modeTextPaint, r) * 0.6f, modeSubPaint)
            }
            KeyType.SPECIAL_DELETE -> {
                canvas.drawText("⌫", cx, centerY(deleteTextPaint, r), deleteTextPaint)
            }
            KeyType.SPECIAL_SPACE -> {
                canvas.drawText("SPASI", cx, centerY(spaceTextPaint, r), spaceTextPaint)
            }
            KeyType.NORMAL -> {
                // Digit besar di atas-tengah, sub label kecil di bawah
                val mainOff = if (key.sub.isNotEmpty()) -r.height() * 0.08f else 0f
                canvas.drawText(
                    key.main,
                    cx,
                    cy + mainOff - (mainTextPaint.ascent() + mainTextPaint.descent()) / 2,
                    mainTextPaint
                )
                if (key.sub.isNotEmpty()) {
                    canvas.drawText(
                        key.sub,
                        cx,
                        cy + r.height() * 0.30f - subTextPaint.ascent() / 2,
                        subTextPaint
                    )
                }
            }
        }
    }

    private fun centerY(paint: Paint, rect: RectF): Float =
        rect.centerY() - (paint.ascent() + paint.descent()) / 2

    private fun mainY(paint: Paint, rect: RectF): Float =
        -(paint.ascent() + paint.descent()) / 2

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val key = hitTest(event.x, event.y) ?: return true
                pressedId = key.id
                invalidate()

                if (key.id == 'B') {
                    keyListener?.onBackspace()
                    startBsRepeat()
                }
            }

            MotionEvent.ACTION_UP -> {
                stopBsRepeat()
                val upKey = hitTest(event.x, event.y)

                if (pressedId != null && upKey?.id == pressedId) {
                    when (pressedId) {
                        'B' -> { /* handled by repeat */ }
                        'M' -> keyListener?.onToggleMode()
                        '0' -> keyListener?.onSpaceKey()
                        else -> pressedId?.let { keyListener?.onDigitKey(it) }
                    }
                }

                pressedId = null
                invalidate()
                performClick()
            }

            MotionEvent.ACTION_CANCEL -> {
                stopBsRepeat()
                pressedId = null
                invalidate()
            }
        }
        return true
    }

    private fun hitTest(x: Float, y: Float): Key? = keyDefs.find { it.rect.contains(x, y) }

    // Backspace long-hold repeat
    private fun startBsRepeat() {
        bsHeld = true
        bsHandler.postDelayed(object : Runnable {
            override fun run() {
                if (!bsHeld) return
                keyListener?.onBackspace()
                bsHandler.postDelayed(this, 100)
            }
        }, 400)  // 400ms delay sebelum repeat mulai
    }

    private fun stopBsRepeat() {
        bsHeld = false
        bsHandler.removeCallbacksAndMessages(null)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
