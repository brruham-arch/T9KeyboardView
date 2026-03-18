package com.brruham.t9ime

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class T9KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface KeyListener {
        fun onDigitKey(digit: Char)
        fun onDigitLong(digit: Char)   // long-press → input angka langsung
        fun onSpaceKey()
        fun onEnterKey()
        fun onBackspace()
        fun onToggleMode()
        fun onToggleShift()
    }

    var keyListener: KeyListener? = null
    var currentMode: InputMode = InputMode.PREDICTIVE
    var isShift: Boolean = false      // capslock state

    private enum class KeyType {
        NORMAL, SPECIAL_MODE, SPECIAL_DELETE, SPECIAL_SPACE, SPECIAL_ENTER, SPECIAL_SHIFT
    }

    private data class Key(
        val id: Char,
        val main: String,
        val sub: String,
        val type: KeyType = KeyType.NORMAL,
        var rect: RectF = RectF()
    )

    // ── 3×5 grid: 3 kolom angka + baris bawah 4 tombol ──────────────────────
    // Baris terakhir: [⇧ SHIFT] [0 SPASI] [↵ ENTER] [⌫]
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
        Key('E', "↵", "ENTER", KeyType.SPECIAL_ENTER),
        // Baris ke-5: Shift & Delete (span 2 kolom masing-masing)
        Key('S', "⇧", "CAPS",  KeyType.SPECIAL_SHIFT),
        Key('B', "⌫", "",      KeyType.SPECIAL_DELETE)
    )

    // ── Baris ke-5: Shift (kiri, 50%) + Delete (kanan, 50%) ──────────────────
    // Key index 12 = Shift, 13 = Delete — layout manual di onSizeChanged

    // ── Paints ────────────────────────────────────────────────────────────────
    private val bgPaint        = Paint().apply { color = Color.parseColor("#0D0D1A") }
    private val normalPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#14192E"); style = Paint.Style.FILL }
    private val pressedPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#243060"); style = Paint.Style.FILL }
    private val modePaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A1525"); style = Paint.Style.FILL }
    private val modePressedP   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6B2040"); style = Paint.Style.FILL }
    private val enterPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0E2E1A"); style = Paint.Style.FILL }
    private val enterPressedP  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1C5530"); style = Paint.Style.FILL }
    private val shiftPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1A2E"); style = Paint.Style.FILL }
    private val shiftOnPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2A2A60"); style = Paint.Style.FILL }
    private val deletePaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111825"); style = Paint.Style.FILL }
    private val spacePaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0E1528"); style = Paint.Style.FILL }

    private val borderNormal   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E2440"); style = Paint.Style.STROKE; strokeWidth = 1f }
    private val borderTeal     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00D4AA55"); style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val borderRed      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E9456055"); style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val borderGreen    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00CC6655"); style = Paint.Style.STROKE; strokeWidth = 1.5f }

    private val mainText  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8EAF0"); textAlign = Paint.Align.CENTER; isFakeBoldText = true; typeface = Typeface.MONOSPACE }
    private val subText   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#505878"); textAlign = Paint.Align.CENTER }
    private val modeText  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E94560"); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val modeSub   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#883040"); textAlign = Paint.Align.CENTER }
    private val enterText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00CC66"); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val shiftText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7A8BCC"); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val shiftOnTx = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00D4AA"); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val deleteText= Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6B7A99"); textAlign = Paint.Align.CENTER }
    private val spaceText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A4560"); textAlign = Paint.Align.CENTER; isFakeBoldText = true }

    private val cr = 10f
    private val gap = 5f

    // ── Touch ─────────────────────────────────────────────────────────────────
    private var pressedId: Char? = null
    private val handler = Handler(Looper.getMainLooper())
    private var bsHeld = false
    private var longFired = false

    // ── Layout ────────────────────────────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val W = w.toFloat(); val H = h.toFloat()

        // 5 baris: 3 baris angka + baris mode/spasi/enter + baris shift/delete
        val rows = 5
        val rowH = (H - gap * (rows + 1)) / rows
        val colW = (W - gap * 4) / 3f

        fun rect(col: Int, row: Int, colSpan: Int = 1) = RectF(
            gap + col * (colW + gap),
            gap + row * (rowH + gap),
            gap + col * (colW + gap) + colW * colSpan + gap * (colSpan - 1),
            gap + row * (rowH + gap) + rowH
        )

        // Baris 0–2: 9 tombol angka (3×3)
        for (i in 0..8) {
            keyDefs[i].rect = rect(i % 3, i / 3)
        }
        // Baris 3: Mode | Spasi | Enter
        keyDefs[9].rect  = rect(0, 3)   // ⇄ MODE
        keyDefs[10].rect = rect(1, 3)   // ⎵ SPASI
        keyDefs[11].rect = rect(2, 3)   // ↵ ENTER
        // Baris 4: Shift (1.5 col) | Delete (1.5 col) — bagi rata
        val halfW = (W - gap * 3) / 2f
        keyDefs[12].rect = RectF(gap, gap + 4 * (rowH + gap), gap + halfW, gap + 4 * (rowH + gap) + rowH)
        keyDefs[13].rect = RectF(gap * 2 + halfW, gap + 4 * (rowH + gap), W - gap, gap + 4 * (rowH + gap) + rowH)

        val base = minOf(rowH, colW)
        mainText.textSize  = base * 0.28f
        modeText.textSize  = base * 0.22f
        enterText.textSize = base * 0.28f
        shiftText.textSize = base * 0.26f
        shiftOnTx.textSize = base * 0.26f
        deleteText.textSize= base * 0.28f
        spaceText.textSize = base * 0.20f
        subText.textSize   = base * 0.16f
        modeSub.textSize   = base * 0.14f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        for (key in keyDefs) drawKey(canvas, key)
    }

    private fun cy(paint: Paint, rect: RectF) = rect.centerY() - (paint.ascent() + paint.descent()) / 2

    private fun drawKey(canvas: Canvas, key: Key) {
        val pressed = key.id == pressedId
        val r = key.rect
        val cx = r.centerX()

        val fill = when (key.type) {
            KeyType.SPECIAL_MODE   -> if (pressed) modePressedP  else modePaint
            KeyType.SPECIAL_DELETE -> if (pressed) pressedPaint  else deletePaint
            KeyType.SPECIAL_SPACE  -> if (pressed) pressedPaint  else spacePaint
            KeyType.SPECIAL_ENTER  -> if (pressed) enterPressedP else enterPaint
            KeyType.SPECIAL_SHIFT  -> if (isShift) shiftOnPaint  else shiftPaint
            KeyType.NORMAL         -> if (pressed) pressedPaint  else normalPaint
        }
        canvas.drawRoundRect(r, cr, cr, fill)

        val border = when (key.type) {
            KeyType.SPECIAL_MODE   -> borderRed
            KeyType.SPECIAL_ENTER  -> borderGreen
            KeyType.SPECIAL_SHIFT  -> if (isShift) borderTeal else borderNormal
            else -> if (pressed) borderTeal else borderNormal
        }
        canvas.drawRoundRect(r, cr, cr, border)

        when (key.type) {
            KeyType.SPECIAL_MODE -> {
                val ml = if (currentMode == InputMode.PREDICTIVE) "T9" else "abc"
                val sl = if (currentMode == InputMode.PREDICTIVE) "prediktif" else "multi-tap"
                canvas.drawText(ml, cx, cy(modeText, r) - subText.textSize * 0.5f, modeText)
                canvas.drawText(sl, cx, cy(modeSub, r) + modeText.textSize * 0.8f, modeSub)
            }
            KeyType.SPECIAL_ENTER  -> canvas.drawText("↵", cx, cy(enterText, r), enterText)
            KeyType.SPECIAL_DELETE -> canvas.drawText("⌫", cx, cy(deleteText, r), deleteText)
            KeyType.SPECIAL_SPACE  -> canvas.drawText("SPASI", cx, cy(spaceText, r), spaceText)
            KeyType.SPECIAL_SHIFT  -> {
                val tx = if (isShift) shiftOnTx else shiftText
                canvas.drawText("⇧ CAPS", cx, cy(tx, r), tx)
            }
            KeyType.NORMAL -> {
                canvas.drawText(key.main, cx, cy(mainText, r) - r.height() * 0.07f, mainText)
                if (key.sub.isNotEmpty())
                    canvas.drawText(key.sub, cx, r.bottom - r.height() * 0.18f, subText)
            }
        }
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val key = hitTest(event.x, event.y) ?: return true
                pressedId = key.id
                longFired = false
                invalidate()
                when (key.id) {
                    'B' -> {
                        keyListener?.onBackspace()
                        startBsRepeat()
                    }
                    else -> if (key.type == KeyType.NORMAL || key.id == '0') {
                        // Long press → input angka langsung
                        handler.postDelayed({
                            longFired = true
                            val d = if (key.id == '0') '0' else key.id
                            keyListener?.onDigitLong(d)
                            invalidate()
                        }, 600)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                stopBsRepeat()
                val upKey = hitTest(event.x, event.y)
                if (!longFired && pressedId != null && upKey?.id == pressedId) {
                    when (pressedId) {
                        'B' -> Unit
                        'M' -> keyListener?.onToggleMode()
                        '0' -> keyListener?.onSpaceKey()
                        'E' -> keyListener?.onEnterKey()
                        'S' -> keyListener?.onToggleShift()
                        else -> pressedId?.let { keyListener?.onDigitKey(it) }
                    }
                }
                pressedId = null
                invalidate()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                stopBsRepeat()
                handler.removeCallbacksAndMessages(null)
                pressedId = null
                invalidate()
            }
        }
        return true
    }

    private fun hitTest(x: Float, y: Float) = keyDefs.find { it.rect.contains(x, y) }

    private fun startBsRepeat() {
        bsHeld = true
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!bsHeld) return
                keyListener?.onBackspace()
                handler.postDelayed(this, 80)
            }
        }, 350)
    }

    private fun stopBsRepeat() {
        bsHeld = false
        handler.removeCallbacksAndMessages(null)
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
