package com.brruham.t9ime

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Layout baru — 5 baris:
 *
 * ┌─────┬─────┬─────┐
 * │  1  │  2  │  3  │  baris 0
 * │ .,! │ ABC │ DEF │
 * ├─────┼─────┼─────┤
 * │  4  │  5  │  6  │  baris 1
 * │ GHI │ JKL │ MNO │
 * ├─────┼─────┼─────┤
 * │  7  │  8  │  9  │  baris 2
 * │PQRS │ TUV │WXYZ │
 * ├─────┼─────┼─────┤
 * │ ⇧   │  ⎵  │  ⌫  │  baris 3
 * │CAPS │SPASI│     │
 * ├─────────────────┤
 * │  ⚡  ACTION BAR  │  baris 4 — full width
 * │ tap=Enter  long=overlay(MODE | PASTE) │
 * └─────────────────┘
 *
 * Action bar overlay (muncul saat long-press ⚡):
 *   [⇄ MODE]  [📋 PASTE]
 */
class T9KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface KeyListener {
        fun onDigitKey(digit: Char)
        fun onDigitLong(digit: Char)
        fun onSpaceKey()
        fun onEnterKey()
        fun onBackspace()
        fun onBackspaceLong()
        fun onToggleMode()
        fun onToggleShift()
        fun onPaste()
    }

    var keyListener: KeyListener? = null
    var currentMode: InputMode = InputMode.PREDICTIVE
    var isShift: Boolean = false

    private enum class KeyType {
        NORMAL, SPECIAL_SPACE, SPECIAL_DELETE, SPECIAL_SHIFT, SPECIAL_ACTION
    }

    private data class Key(
        val id: Char,
        val main: String,
        val sub: String,
        val type: KeyType = KeyType.NORMAL,
        var rect: RectF = RectF()
    )

    // 12 tombol utama + 1 action bar
    private val keyDefs = listOf(
        Key('1',"1",".,!?"),  Key('2',"2","ABC"),  Key('3',"3","DEF"),
        Key('4',"4","GHI"),   Key('5',"5","JKL"),  Key('6',"6","MNO"),
        Key('7',"7","PQRS"),  Key('8',"8","TUV"),  Key('9',"9","WXYZ"),
        Key('S',"⇧","CAPS",  KeyType.SPECIAL_SHIFT),
        Key('0',"⎵","SPASI", KeyType.SPECIAL_SPACE),
        Key('B',"⌫","",      KeyType.SPECIAL_DELETE),
        Key('A',"⚡","",     KeyType.SPECIAL_ACTION)   // Action bar
    )

    // Overlay tombol (muncul saat long-press Action)
    private data class OverlayKey(val id: Char, val label: String, var rect: RectF = RectF())
    private val overlayKeys = listOf(
        OverlayKey('M', "⇄  MODE"),
        OverlayKey('P', "📋 PASTE")
    )

    private var overlayVisible = false
    private var overlayPressedId: Char? = null

    // ── Paints ────────────────────────────────────────────────────────────────
    private val bgPaint       = Paint().apply { color = Color.parseColor("#0D0D1A") }
    private val normalPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#14192E"); style = Paint.Style.FILL }
    private val pressedPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#243060"); style = Paint.Style.FILL }
    private val shiftPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1A2E"); style = Paint.Style.FILL }
    private val shiftOnPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2A2A60"); style = Paint.Style.FILL }
    private val deletePaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111825"); style = Paint.Style.FILL }
    private val spacePaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0E1528"); style = Paint.Style.FILL }
    private val actionPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0A1E12"); style = Paint.Style.FILL }
    private val actionPressP  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#163324"); style = Paint.Style.FILL }
    private val overlayBgPt   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1F35"); style = Paint.Style.FILL }
    private val overlayBorderP= Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00D4AA88"); style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val overlayPressP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#243060"); style = Paint.Style.FILL }

    private val borderNormal  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E2440"); style = Paint.Style.STROKE; strokeWidth = 1f }
    private val borderTeal    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00D4AA55"); style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val borderGreen   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00CC6655"); style = Paint.Style.STROKE; strokeWidth = 1.5f }

    private val mainTxt    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8EAF0"); textAlign = Paint.Align.CENTER; isFakeBoldText = true; typeface = Typeface.MONOSPACE }
    private val subTxt     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#505878"); textAlign = Paint.Align.CENTER }
    private val deleteTxt  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6B7A99"); textAlign = Paint.Align.CENTER }
    private val spaceTxt   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A4560"); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val shiftTxt   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7A8BCC"); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val shiftOnTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00D4AA"); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val actionTxt  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00CC66"); textAlign = Paint.Align.CENTER; isFakeBoldText = true; typeface = Typeface.MONOSPACE }
    private val overlayTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CCDDFF"); textAlign = Paint.Align.CENTER }

    private val cr = 10f; private val gap = 5f

    // ── Touch ─────────────────────────────────────────────────────────────────
    private var pressedId: Char? = null
    private val handler = Handler(Looper.getMainLooper())
    private var bsHeld = false
    private var longFired = false

    // ── Layout ────────────────────────────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val W = w.toFloat(); val H = h.toFloat()
        val actionH = H * 0.18f                        // action bar lebih tipis
        val mainH   = H - actionH - gap * 6            // sisa untuk 4 baris
        val rowH    = (mainH - gap * 4) / 4f
        val colW    = (W - gap * 4) / 3f

        fun rect(col: Int, row: Int) = RectF(
            gap + col * (colW + gap), gap + row * (rowH + gap),
            gap + col * (colW + gap) + colW, gap + row * (rowH + gap) + rowH
        )

        // 9 digit keys (baris 0-2)
        for (i in 0..8) keyDefs[i].rect = rect(i % 3, i / 3)

        // Baris 3: Shift | Spasi | Delete
        keyDefs[9].rect  = rect(0, 3)
        keyDefs[10].rect = rect(1, 3)
        keyDefs[11].rect = rect(2, 3)

        // Action bar — full width, baris 4
        val actionTop = gap * 5 + rowH * 4
        keyDefs[12].rect = RectF(gap, actionTop, W - gap, actionTop + actionH)

        // Overlay keys — muncul di atas action bar
        val ovW = (W - gap * 3) / 2f
        val ovH = rowH * 0.85f
        val ovTop = actionTop - ovH - gap * 2
        overlayKeys[0].rect = RectF(gap,          ovTop, gap + ovW,     ovTop + ovH)
        overlayKeys[1].rect = RectF(gap * 2 + ovW, ovTop, W - gap,       ovTop + ovH)

        // Text sizes
        val base = minOf(rowH, colW)
        mainTxt.textSize   = base * 0.28f
        subTxt.textSize    = base * 0.16f
        deleteTxt.textSize = base * 0.30f
        spaceTxt.textSize  = base * 0.20f
        shiftTxt.textSize  = base * 0.22f
        shiftOnTxt.textSize= base * 0.22f
        actionTxt.textSize = actionH * 0.38f
        overlayTxt.textSize= ovH * 0.35f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        for (key in keyDefs) drawKey(canvas, key)
        if (overlayVisible) drawOverlay(canvas)
    }

    private fun cy(paint: Paint, r: RectF) = r.centerY() - (paint.ascent() + paint.descent()) / 2

    private fun drawKey(canvas: Canvas, key: Key) {
        val pressed = key.id == pressedId
        val r = key.rect; val cx = r.centerX()

        val fill = when (key.type) {
            KeyType.SPECIAL_SHIFT  -> if (isShift) shiftOnPaint else shiftPaint
            KeyType.SPECIAL_DELETE -> if (pressed) pressedPaint else deletePaint
            KeyType.SPECIAL_SPACE  -> if (pressed) pressedPaint else spacePaint
            KeyType.SPECIAL_ACTION -> if (pressed && !overlayVisible) actionPressP else actionPaint
            KeyType.NORMAL         -> if (pressed) pressedPaint else normalPaint
        }
        canvas.drawRoundRect(r, cr, cr, fill)

        val border = when (key.type) {
            KeyType.SPECIAL_ACTION -> borderGreen
            KeyType.SPECIAL_SHIFT  -> if (isShift) borderTeal else borderNormal
            else -> if (pressed) borderTeal else borderNormal
        }
        canvas.drawRoundRect(r, cr, cr, border)

        when (key.type) {
            KeyType.SPECIAL_DELETE -> canvas.drawText("⌫", cx, cy(deleteTxt, r), deleteTxt)
            KeyType.SPECIAL_SPACE  -> canvas.drawText("SPASI", cx, cy(spaceTxt, r), spaceTxt)
            KeyType.SPECIAL_SHIFT  -> {
                val tx = if (isShift) shiftOnTxt else shiftTxt
                canvas.drawText("⇧ CAPS", cx, cy(tx, r), tx)
            }
            KeyType.SPECIAL_ACTION -> {
                val modeStr = if (currentMode == InputMode.PREDICTIVE) "T9" else "abc"
                val label = if (overlayVisible) "▲ tutup" else "↵ enter    ·hold: mode / paste  [$modeStr]"
                canvas.drawText(label, cx, cy(actionTxt, r), actionTxt)
            }
            KeyType.NORMAL -> {
                canvas.drawText(key.main, cx, cy(mainTxt, r) - r.height() * 0.07f, mainTxt)
                if (key.sub.isNotEmpty())
                    canvas.drawText(key.sub, cx, r.bottom - r.height() * 0.18f, subTxt)
            }
        }
    }

    private fun drawOverlay(canvas: Canvas) {
        for (ok in overlayKeys) {
            val fill = if (ok.id == overlayPressedId) overlayPressP else overlayBgPt
            canvas.drawRoundRect(ok.rect, cr, cr, fill)
            canvas.drawRoundRect(ok.rect, cr, cr, overlayBorderP)
            canvas.drawText(ok.label, ok.rect.centerX(), cy(overlayTxt, ok.rect), overlayTxt)
        }
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Overlay intercept
                if (overlayVisible) {
                    val ok = overlayKeys.find { it.rect.contains(x, y) }
                    overlayPressedId = ok?.id
                    invalidate()
                    return true
                }
                val key = hitTest(x, y) ?: return true
                pressedId = key.id; longFired = false; invalidate()

                when (key.id) {
                    'B' -> { keyListener?.onBackspace(); startBsRepeat() }
                    'A' -> handler.postDelayed({
                        longFired = true
                        overlayVisible = true
                        invalidate()
                    }, 500)
                    else -> if (key.type == KeyType.NORMAL) {
                        handler.postDelayed({
                            longFired = true
                            keyListener?.onDigitLong(key.id)
                        }, 600)
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                // Overlay intercept
                if (overlayVisible) {
                    val ok = overlayKeys.find { it.rect.contains(x, y) }
                    if (ok != null && ok.id == overlayPressedId) {
                        when (ok.id) {
                            'M' -> keyListener?.onToggleMode()
                            'P' -> keyListener?.onPaste()
                        }
                        overlayVisible = false
                    } else {
                        // Tap di luar overlay → tutup
                        val actionKey = keyDefs.find { it.id == 'A' }
                        if (actionKey?.rect?.contains(x, y) == true) overlayVisible = false
                    }
                    overlayPressedId = null
                    pressedId = null
                    invalidate()
                    return true
                }

                stopBsRepeat()
                val upKey = hitTest(x, y)
                if (!longFired && pressedId != null && upKey?.id == pressedId) {
                    when (pressedId) {
                        'B' -> Unit
                        '0' -> keyListener?.onSpaceKey()
                        'S' -> keyListener?.onToggleShift()
                        'A' -> keyListener?.onEnterKey()
                        else -> pressedId?.let { keyListener?.onDigitKey(it) }
                    }
                }
                pressedId = null; invalidate(); performClick()
            }

            MotionEvent.ACTION_CANCEL -> {
                stopBsRepeat(); overlayPressedId = null; pressedId = null; invalidate()
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
        bsHeld = false; handler.removeCallbacksAndMessages(null)
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
