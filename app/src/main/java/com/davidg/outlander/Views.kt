package com.davidg.outlander

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View

/** Tres rombos, como el emblema. */
class DiamondLogoView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path().apply {
        moveTo(0f, 0f); lineTo(9f, -15f); lineTo(0f, -30f); lineTo(-9f, -15f); close()
    }

    override fun onDraw(c: Canvas) {
        p.color = C.INK
        val sc = height / 50f
        c.save()
        c.translate(width * 0.5f, height * (32f / 50f))
        c.scale(sc, sc)
        for (k in 0 until 3) {
            c.save(); c.rotate(120f * k); c.drawPath(path, p); c.restore()
        }
        c.restore()
    }
}

/** Botones de transporte de música: 0 anterior, 1 reproducir, 2 pausa, 3 siguiente. */
class TransportButton(ctx: Context, var kind: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    init { isClickable = true }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        val main = kind == 1 || kind == 2
        if (main) {
            p.color = C.INK
            c.drawRoundRect(RectF(0f, 0f, w, h), h / 2f, h / 2f, p)
            p.color = C.BG2
        } else {
            p.color = C.INK
        }
        val u = h * 0.19f
        val path = Path()
        when (kind) {
            1 -> { path.moveTo(cx - u * 0.7f, cy - u); path.lineTo(cx + u * 1.1f, cy); path.lineTo(cx - u * 0.7f, cy + u); path.close() }
            2 -> { path.addRect(cx - u * 0.9f, cy - u, cx - u * 0.25f, cy + u, Path.Direction.CW)
                   path.addRect(cx + u * 0.25f, cy - u, cx + u * 0.9f, cy + u, Path.Direction.CW) }
            0 -> { path.addRect(cx - u * 1.1f, cy - u, cx - u * 0.7f, cy + u, Path.Direction.CW)
                   path.moveTo(cx + u * 1.1f, cy - u); path.lineTo(cx - u * 0.6f, cy); path.lineTo(cx + u * 1.1f, cy + u); path.close() }
            else -> { path.addRect(cx + u * 0.7f, cy - u, cx + u * 1.1f, cy + u, Path.Direction.CW)
                   path.moveTo(cx - u * 1.1f, cy - u); path.lineTo(cx + u * 0.6f, cy); path.lineTo(cx - u * 1.1f, cy + u); path.close() }
        }
        c.drawPath(path, p)
    }
}

/** Brillo, sombra o aro bajo la camioneta. mode: 0 brillo, 1 sombra, 2 aro. */
class FloorView(ctx: Context, private val mode: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (mode == 2) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = C.LINE; p.shader = null
            c.drawOval(RectF(1f, 1f, w - 1f, h - 1f), p)
            return
        }
        p.style = Paint.Style.FILL
        c.save()
        c.scale(1f, h / w)
        val col = if (mode == 0) C.GLOW else C.SHADE
        val transparent = col and 0x00FFFFFF
        p.shader = RadialGradient(w / 2f, w / 2f, w / 2f, intArrayOf(col, transparent), floatArrayOf(0f, 0.7f), Shader.TileMode.CLAMP)
        c.drawCircle(w / 2f, w / 2f, w / 2f, p)
        c.restore()
    }
}

/** Velocímetro digital con barra y viaje de hoy. */
class DigitalSpeedView(ctx: Context) : View(ctx) {
    private var speed = 0f
    private var trip = 0.0
    private var over = false

    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 64f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val unit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f; typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    }
    private val lab = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f; textAlign = Paint.Align.RIGHT; typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    }
    private val tv = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 19f; textAlign = Paint.Align.RIGHT; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)

    fun set(kmh: Float, tripKm: Double, isOver: Boolean) {
        val changed = Math.round(kmh) != Math.round(speed) || Math.abs(tripKm - trip) > 0.04 || isOver != over
        speed = kmh; trip = tripKm; over = isOver
        if (changed) invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        if (over) {
            bar.style = Paint.Style.STROKE; bar.strokeWidth = 2f; bar.color = C.ACCENT
            c.drawRoundRect(RectF(1f, 1f, w - 1f, height - 1f), 22f, 22f, bar)
            bar.style = Paint.Style.FILL
        }
        big.color = C.INK
        val n = Math.round(speed).toString()
        c.drawText(n, 20f, 68f, big)
        unit.color = C.MUTE
        c.drawText("km/h", 20f + maxOf(96f, big.measureText(n)) + 4f, 68f, unit)
        lab.color = C.MUTE
        c.drawText("Viaje de hoy", w - 20f, 30f, lab)
        tv.color = C.INK
        c.drawText(String.format(java.util.Locale.US, "%.1f km", trip), w - 20f, 52f, tv)
        bar.color = C.TRACK
        c.drawRoundRect(RectF(20f, 82f, w - 20f, 86f), 2f, 2f, bar)
        bar.color = if (speed >= 120f) C.ACCENT else C.INK
        val f = (speed / 200f).coerceIn(0f, 1f)
        if (f > 0f) c.drawRoundRect(RectF(20f, 82f, 20f + (w - 40f) * f, 86f), 2f, 2f, bar)
    }
}

/** Barra horizontal de avance. */
class BarView(ctx: Context) : View(ctx) {
    var frac = 0f
    var warn = false
    var strong = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.color = C.TRACK
        c.drawRoundRect(RectF(0f, 0f, w, h), h / 2f, h / 2f, p)
        p.color = if (warn) C.ACCENT else if (strong) C.INK else C.BAR
        if (frac > 0f) c.drawRoundRect(RectF(0f, 0f, w * frac.coerceIn(0f, 1f), h), h / 2f, h / 2f, p)
    }
}

/** Barras de consumo por tramo. */
class BarsView(ctx: Context) : View(ctx) {
    var values: List<Float> = emptyList()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        if (values.isEmpty()) return
        val n = values.size
        val gap = 8f
        val bw = (width - gap * (n - 1)) / n
        val lo = 8f; val hi = 14f
        for (i in 0 until n) {
            val f = ((values[i] - lo) / (hi - lo)).coerceIn(0.1f, 1f)
            val h = height * f
            p.color = if (i == n - 1) C.INK else C.TRACK
            val x = i * (bw + gap)
            c.drawRoundRect(RectF(x, height - h, x + bw, height.toFloat()), 4f, 4f, p)
        }
    }
}
