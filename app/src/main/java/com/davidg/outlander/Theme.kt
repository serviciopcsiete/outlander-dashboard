package com.davidg.outlander

import android.graphics.drawable.GradientDrawable
import java.util.Locale

/** Colores neutros (negros y grises) con modo día y noche. */
object C {
    var day = false

    var INK = 0
    var MUTE = 0
    var ACCENT = 0
    var LINE = 0
    var PANEL = 0
    var TRACK = 0
    var BAR = 0
    var SOLID = 0
    var BG1 = 0
    var BG2 = 0
    var BG3 = 0
    var GLOW = 0
    var SHADE = 0

    init { apply(false) }

    fun apply(isDay: Boolean) {
        day = isDay
        if (isDay) {
            INK = 0xFF151515.toInt(); MUTE = 0xFF6B6B6B.toInt(); ACCENT = 0xFF000000.toInt()
            LINE = 0x21000000; PANEL = 0x99FFFFFF.toInt(); TRACK = 0x1F000000; BAR = 0x6B000000
            SOLID = 0xFFFAFAFA.toInt()
            BG1 = 0xFFF4F4F4.toInt(); BG2 = 0xFFE6E6E6.toInt(); BG3 = 0xFFD4D4D4.toInt()
            GLOW = 0xCCFFFFFF.toInt(); SHADE = 0x4D000000
        } else {
            INK = 0xFFE9E9E9.toInt(); MUTE = 0xFF8C8C8C.toInt(); ACCENT = 0xFFFFFFFF.toInt()
            LINE = 0x1FFFFFFF; PANEL = 0x0DFFFFFF; TRACK = 0x29FFFFFF; BAR = 0x80FFFFFF.toInt()
            SOLID = 0xFF151515.toInt()
            BG1 = 0xFF1D1D1D.toInt(); BG2 = 0xFF0E0E0E.toInt(); BG3 = 0xFF050505.toInt()
            GLOW = 0x1FFFFFFF; SHADE = 0xD9000000.toInt()
        }
    }

    fun card(radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(PANEL)
        setStroke(1, LINE)
        cornerRadius = radius
    }

    fun solid(radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(SOLID)
        setStroke(1, LINE)
        cornerRadius = radius
    }

    fun outline(radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(0)
        setStroke(1, LINE)
        cornerRadius = radius
    }

    fun stageBackground(): GradientDrawable = GradientDrawable().apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT
        colors = intArrayOf(BG1, BG2, BG3)
        gradientRadius = 780f
        setGradientCenter(0.5f, 0.52f)
    }

    fun fmt(n: Int): String = String.format(Locale("es", "MX"), "%,d", n)
    fun money(n: Double): String = "$ " + fmt(Math.round(n).toInt())
}
