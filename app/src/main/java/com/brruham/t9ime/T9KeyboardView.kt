package com.brruham.t9ime

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Layout 5 baris:
 *  0-2: 9 digit keys
 *  3:   ⇧ CAPS | ⎵ SPASI | ⌫
 *  4:   ⚡ ACTION BAR (tap=Enter, swipe-up=numrow, hold=overlay)
 *
 * Overlays:
 *  ACTION overlay: [⇄ MODE]  [📋 PASTE]  [😊 EMOJI]
 *  EMOJI overlay:  grid 4×4 emoji umum
 *  NUM ROW:        [1][2][3][4][5][6][7][8][9][0] di atas keyboard
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
        fun onEmojiSelected(emoji: String)
    }

    var keyListener: KeyListener? = null
    var currentMode: InputMode = InputMode.PREDICTIVE
    var isShift: Boolean = false

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(18)
            }
        } catch (_: Exception) {}
    }

    // ── Key defs ──────────────────────────────────────────────────────────────
    private enum class KType { NORMAL, SPACE, DELETE, SHIFT, ACTION }
    private data class Key(val id: Char, val main: String, val sub: String, val type: KType = KType.NORMAL, var rect: RectF = RectF())

    private val keyDefs = listOf(
        Key('1',"1",".,!?"), Key('2',"2","ABC"), Key('3',"3","DEF"),
        Key('4',"4","GHI"),  Key('5',"5","JKL"), Key('6',"6","MNO"),
        Key('7',"7","PQRS"), Key('8',"8","TUV"), Key('9',"9","WXYZ"),
        Key('S',"⇧","CAPS", KType.SHIFT),
        Key('0',"⎵","SPASI",KType.SPACE),
        Key('B',"⌫","",     KType.DELETE),
        Key('A',"",  "",    KType.ACTION)
    )

    // ── Overlay: action menu ──────────────────────────────────────────────────
    private data class OKey(val id: Char, val label: String, var rect: RectF = RectF())
    private val actionOverlay = listOf(
        OKey('M', "⇄  MODE"),
        OKey('P', "📋 PASTE"),
        OKey('E', "😊 EMOJI")
    )

    // ── Emoji grid ────────────────────────────────────────────────────────────
    private val emojis = listOf(
        "😊","😂","🥰","😍","😎","🤔","😅","😭",
        "🔥","❤️","👍","🙏","✨","💯","🎉","😁",
        "🤣","😢","😡","🥺","👋","💪","🤝","😴"
    )
    private data class EKey(val emoji: String, var rect: RectF = RectF())
    private val emojiKeys = emojis.map { EKey(it) }

    // ── Number row ────────────────────────────────────────────────────────────
    private data class NKey(val digit: Char, var rect: RectF = RectF())
    private val numRow = "1234567890".map { NKey(it) }

    // ── Overlay state ─────────────────────────────────────────────────────────
    private enum class Overlay { NONE, ACTION, EMOJI, NUMROW }
    private var overlay = Overlay.NONE
    private var overlayPressedId: Any? = null  // Char for action, String for emoji, Char for numrow

    // ── Paints ────────────────────────────────────────────────────────────────
    private val bgP      = Paint().apply { color = Color.parseColor("#0D0D1A") }
    private val normP    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#14192E"); style = Paint.Style.FILL }
    private val pressP   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#243060"); style = Paint.Style.FILL }
    private val shiftP   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1A2E"); style = Paint.Style.FILL }
    private val shiftOnP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2A2A60"); style = Paint.Style.FILL }
    private val delP     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111825"); style = Paint.Style.FILL }
    private val spaceP   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0E1528"); style = Paint.Style.FILL }
    private val actP     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0A1E12"); style = Paint.Style.FILL }
    private val actPrP   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#163324"); style = Paint.Style.FILL }
    private val ovBgP    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1F35"); style = Paint.Style.FILL }
    private val ovPrP    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2A3560"); style = Paint.Style.FILL }
    private val numBgP   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0E1528"); style = Paint.Style.FILL }
    private val numPrP   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E3050"); style = Paint.Style.FILL }

    private val borN  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E2440"); style = Paint.Style.STROKE; strokeWidth = 1f }
    private val borT  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00D4AA55"); style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val borG  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00CC6655"); style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val borOv = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00D4AA88"); style = Paint.Style.STROKE; strokeWidth = 1.5f }

    private val mainTx  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8EAF0"); textAlign = Paint.Align.CENTER; isFakeBoldText = true; typeface = Typeface.MONOSPACE }
    private val subTx   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#505878"); textAlign = Paint.Align.CENTER }
    private val delTx   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6B7A99"); textAlign = Paint.Align.CENTER }
    private val spTx    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A4560"); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val shTx    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7A8BCC"); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val shOnTx  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00D4AA"); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val actTx   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00CC66"); textAlign = Paint.Align.CENTER; isFakeBoldText = true; typeface = Typeface.MONOSPACE }
    private val ovTx    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CCDDFF"); textAlign = Paint.Align.CENTER }
    private val numTx   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8EAF0"); textAlign = Paint.Align.CENTER; isFakeBoldText = true; typeface = Typeface.MONOSPACE }
    private val emTx    = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val cr = 10f; private val gap = 5f

    // ── Touch ─────────────────────────────────────────────────────────────────
    private var pressedId: Char? = null
    private val handler = Handler(Looper.getMainLooper())
    private var bsHeld = false; private var longFired = false
    private var touchDownY = 0f

    // ── Layout ────────────────────────────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val W = w.toFloat(); val H = h.toFloat()
        val actionH = H * 0.16f
        val mainH   = H - actionH - gap * 7
        val rowH    = (mainH - gap * 3) / 4f
        val colW    = (W - gap * 4) / 3f

        fun r(col: Int, row: Int) = RectF(
            gap + col * (colW + gap), gap + row * (rowH + gap),
            gap + col * (colW + gap) + colW, gap + row * (rowH + gap) + rowH
        )

        for (i in 0..8) keyDefs[i].rect = r(i % 3, i / 3)
        keyDefs[9].rect  = r(0, 3); keyDefs[10].rect = r(1, 3); keyDefs[11].rect = r(2, 3)
        val actTop = gap * 5 + rowH * 4
        keyDefs[12].rect = RectF(gap, actTop, W - gap, actTop + actionH)

        // Action overlay: 3 tombol
        val ovH = rowH * 0.8f; val ovTop = actTop - ovH - gap * 2
        val ovW = (W - gap * 4) / 3f
        actionOverlay[0].rect = RectF(gap, ovTop, gap + ovW, ovTop + ovH)
        actionOverlay[1].rect = RectF(gap * 2 + ovW, ovTop, gap * 2 + ovW * 2, ovTop + ovH)
        actionOverlay[2].rect = RectF(gap * 3 + ovW * 2, ovTop, W - gap, ovTop + ovH)

        // Emoji grid 4×6 di atas keyboard
        val eRows = 4; val eCols = 6
        val eW = (W - gap * (eCols + 1)) / eCols
        val eH = rowH * 0.85f
        val eTop = actTop - (eH + gap) * eRows - gap
        emojiKeys.forEachIndexed { i, ek ->
            val col = i % eCols; val row = i / eCols
            ek.rect = RectF(gap + col * (eW + gap), eTop + row * (eH + gap),
                            gap + col * (eW + gap) + eW, eTop + row * (eH + gap) + eH)
        }

        // Number row di atas keyboard
        val nW = (W - gap * 11) / 10f; val nH = rowH * 0.75f
        val nTop = actTop - nH - gap * 2
        numRow.forEachIndexed { i, nk ->
            nk.rect = RectF(gap + i * (nW + gap), nTop, gap + i * (nW + gap) + nW, nTop + nH)
        }

        val base = minOf(rowH, colW)
        mainTx.textSize = base * 0.28f; subTx.textSize = base * 0.16f
        delTx.textSize  = base * 0.30f; spTx.textSize  = base * 0.20f
        shTx.textSize   = base * 0.22f; shOnTx.textSize = base * 0.22f
        actTx.textSize  = actionH * 0.36f
        ovTx.textSize   = ovH * 0.34f
        numTx.textSize  = nH * 0.48f
        emTx.textSize   = eH * 0.52f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgP)
        for (k in keyDefs) drawKey(canvas, k)
        when (overlay) {
            Overlay.ACTION  -> drawActionOverlay(canvas)
            Overlay.EMOJI   -> drawEmojiOverlay(canvas)
            Overlay.NUMROW  -> drawNumRow(canvas)
            Overlay.NONE    -> Unit
        }
    }

    private fun cy(p: Paint, r: RectF) = r.centerY() - (p.ascent() + p.descent()) / 2

    private fun drawKey(canvas: Canvas, k: Key) {
        val pressed = k.id == pressedId
        val r = k.rect; val cx = r.centerX()
        val fill = when (k.type) {
            KType.SHIFT  -> if (isShift) shiftOnP else shiftP
            KType.DELETE -> if (pressed) pressP else delP
            KType.SPACE  -> if (pressed) pressP else spaceP
            KType.ACTION -> if (pressed && overlay == Overlay.NONE) actPrP else actP
            KType.NORMAL -> if (pressed) pressP else normP
        }
        canvas.drawRoundRect(r, cr, cr, fill)
        val bor = when (k.type) {
            KType.ACTION -> borG
            KType.SHIFT  -> if (isShift) borT else borN
            else         -> if (pressed) borT else borN
        }
        canvas.drawRoundRect(r, cr, cr, bor)
        when (k.type) {
            KType.DELETE -> canvas.drawText("⌫", cx, cy(delTx, r), delTx)
            KType.SPACE  -> canvas.drawText("SPASI", cx, cy(spTx, r), spTx)
            KType.SHIFT  -> canvas.drawText("⇧ CAPS", cx, cy(if (isShift) shOnTx else shTx, r), if (isShift) shOnTx else shTx)
            KType.ACTION -> {
                val mStr = if (currentMode == InputMode.PREDICTIVE) "T9" else "abc"
                val ovHint = when (overlay) {
                    Overlay.NONE   -> "↵  ·hold:menu  ·swipe↑:angka  [$mStr]"
                    else           -> "▲ tutup"
                }
                canvas.drawText(ovHint, cx, cy(actTx, r), actTx)
            }
            KType.NORMAL -> {
                canvas.drawText(k.main, cx, cy(mainTx, r) - r.height() * 0.07f, mainTx)
                if (k.sub.isNotEmpty()) canvas.drawText(k.sub, cx, r.bottom - r.height() * 0.18f, subTx)
            }
        }
    }

    private fun drawActionOverlay(canvas: Canvas) {
        for (ok in actionOverlay) {
            val fill = if (ok.id == overlayPressedId) ovPrP else ovBgP
            canvas.drawRoundRect(ok.rect, cr, cr, fill)
            canvas.drawRoundRect(ok.rect, cr, cr, borOv)
            canvas.drawText(ok.label, ok.rect.centerX(), cy(ovTx, ok.rect), ovTx)
        }
    }

    private fun drawEmojiOverlay(canvas: Canvas) {
        // Semi-transparent background
        val bgRect = RectF(0f, emojiKeys.first().rect.top - gap, width.toFloat(), keyDefs[12].rect.top - gap)
        val bgFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CC0D0D1A"); style = Paint.Style.FILL }
        canvas.drawRect(bgRect, bgFill)
        for (ek in emojiKeys) {
            val fill = if (ek.emoji == overlayPressedId) ovPrP else ovBgP
            canvas.drawRoundRect(ek.rect, cr, cr, fill)
            canvas.drawRoundRect(ek.rect, cr, cr, borN)
            canvas.drawText(ek.emoji, ek.rect.centerX(), cy(emTx, ek.rect), emTx)
        }
    }

    private fun drawNumRow(canvas: Canvas) {
        for (nk in numRow) {
            val fill = if (nk.digit == overlayPressedId) numPrP else numBgP
            canvas.drawRoundRect(nk.rect, cr, cr, fill)
            canvas.drawRoundRect(nk.rect, cr, cr, borT)
            canvas.drawText(nk.digit.toString(), nk.rect.centerX(), cy(numTx, nk.rect), numTx)
        }
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownY = y
                // Overlay intercept
                when (overlay) {
                    Overlay.ACTION -> {
                        overlayPressedId = actionOverlay.find { it.rect.contains(x, y) }?.id
                        invalidate(); return true
                    }
                    Overlay.EMOJI -> {
                        overlayPressedId = emojiKeys.find { it.rect.contains(x, y) }?.emoji
                        invalidate(); return true
                    }
                    Overlay.NUMROW -> {
                        overlayPressedId = numRow.find { it.rect.contains(x, y) }?.digit
                        invalidate(); return true
                    }
                    Overlay.NONE -> Unit
                }
                val k = hitTest(x, y) ?: return true
                pressedId = k.id; longFired = false; invalidate(); vibrate()
                when (k.id) {
                    'B' -> { keyListener?.onBackspace(); startBsRepeat() }
                    'A' -> handler.postDelayed({ longFired = true; overlay = Overlay.ACTION; invalidate() }, 500)
                    else -> if (k.type == KType.NORMAL) {
                        handler.postDelayed({ longFired = true; keyListener?.onDigitLong(k.id) }, 600)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val deltaY = y - touchDownY
                when (overlay) {
                    Overlay.ACTION -> {
                        val ok = actionOverlay.find { it.rect.contains(x, y) }
                        if (ok != null && ok.id == overlayPressedId) {
                            when (ok.id) {
                                'M' -> { keyListener?.onToggleMode(); vibrate() }
                                'P' -> { keyListener?.onPaste(); vibrate() }
                                'E' -> { overlay = Overlay.EMOJI; overlayPressedId = null; invalidate(); return true }
                            }
                            overlay = Overlay.NONE
                        } else {
                            // Tap di action bar → tutup
                            if (keyDefs[12].rect.contains(x, y)) overlay = Overlay.NONE
                        }
                        overlayPressedId = null; pressedId = null; invalidate(); return true
                    }
                    Overlay.EMOJI -> {
                        val ek = emojiKeys.find { it.rect.contains(x, y) }
                        if (ek != null && ek.emoji == overlayPressedId) {
                            keyListener?.onEmojiSelected(ek.emoji); vibrate()
                            overlay = Overlay.NONE
                        } else if (keyDefs[12].rect.contains(x, y)) overlay = Overlay.NONE
                        overlayPressedId = null; pressedId = null; invalidate(); return true
                    }
                    Overlay.NUMROW -> {
                        val nk = numRow.find { it.rect.contains(x, y) }
                        if (nk != null && nk.digit == overlayPressedId) {
                            keyListener?.onDigitLong(nk.digit); vibrate()
                        }
                        overlay = Overlay.NONE; overlayPressedId = null; pressedId = null; invalidate(); return true
                    }
                    Overlay.NONE -> Unit
                }

                stopBsRepeat()
                // Swipe up dari action bar → numrow
                if (pressedId == 'A' && deltaY < -40f && !longFired) {
                    overlay = Overlay.NUMROW; pressedId = null; invalidate(); return true
                }

                val upKey = hitTest(x, y)
                if (!longFired && pressedId != null && upKey?.id == pressedId) {
                    vibrate()
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
                keyListener?.onBackspace(); vibrate()
                handler.postDelayed(this, 80)
            }
        }, 350)
    }
    private fun stopBsRepeat() { bsHeld = false; handler.removeCallbacksAndMessages(null) }
    override fun performClick(): Boolean { super.performClick(); return true }
}
