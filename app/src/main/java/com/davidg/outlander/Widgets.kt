package com.davidg.outlander

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Mini(
    val k: String,
    val v: String,
    val icon: Int,
    val tap: (() -> Unit)? = null,
    val hold: (() -> Unit)? = null
)

/** Construye el contenido de las tarjetas grandes y de los minis. */
class WidgetFactory(private val a: MainActivity) {

    val cardKeys = listOf("music", "fuel", "servicios", "viaje", "docs", "gas", "gastos", "favs", "park", "tiempo", "motor", "fallas")
    val cardLabels = mapOf(
        "music" to "Música", "fuel" to "Consumo", "servicios" to "Servicios del carro",
        "viaje" to "Resumen de viaje", "docs" to "Documentos y vencimientos", "gas" to "Registro de gasolina",
        "gastos" to "Gastos del carro", "favs" to "Marcación rápida", "park" to "Dónde estacioné",
        "tiempo" to "Clima y sol", "motor" to "Datos del motor (OBD)", "fallas" to "Lector de fallas (OBD)"
    )
    val miniKeys = listOf(
        "kilometraje", "velocidad", "clima", "lluvia", "sol", "viaje", "casa", "trabajo", "limite", "parking",
        "docs", "gas", "gastos", "autonomia", "rumbo", "bateria", "motor", "rpm", "consumo", "fallas"
    )
    val miniLabels = mapOf(
        "kilometraje" to "Kilometraje", "velocidad" to "Velocidad", "clima" to "Clima", "lluvia" to "Pronóstico de lluvia",
        "sol" to "Amanecer y atardecer", "viaje" to "Viaje de hoy", "casa" to "Ir a casa", "trabajo" to "Ir al trabajo",
        "limite" to "Aviso de velocidad", "parking" to "Dónde estacioné", "docs" to "Próximo vencimiento",
        "gas" to "Rendimiento de gasolina", "gastos" to "Gastos (30 días)", "autonomia" to "Autonomía", "rumbo" to "Rumbo",
        "bateria" to "Batería (OBD)", "motor" to "Temp. motor (OBD)", "rpm" to "RPM (OBD)",
        "consumo" to "Consumo instantáneo (OBD)", "fallas" to "Fallas del motor (OBD)"
    )

    // ------------------------------------------------------------ ayudas

    private fun head(box: LinearLayout, title: String, hint: String? = null, badge: String? = null, onHint: (() -> Unit)? = null) {
        val r = LinearLayout(a)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.add(a.tv(title, 18f, C.INK, "sans-serif-medium"), WC, WC)
        if (badge != null) {
            val b = a.tv(badge, 11f, C.MUTE)
            b.setPadding(6, 2, 6, 2)
            b.background = C.outline(8f)
            r.add(b, WC, WC, 0, 8)
        }
        if (hint != null) {
            r.add(View(a), 0, 1, 0, 0, 1f)
            val h = a.tv(hint, 13f, if (onHint != null) C.INK else C.MUTE)
            if (onHint != null) {
                h.setPadding(9, 3, 9, 3)
                h.background = C.outline(10f)
                h.setOnClickListener { onHint() }
            }
            r.add(h, WC, WC)
        }
        box.add(r, MP, 28)
    }

    private fun row(
        box: LinearLayout, left: String, right: String, warn: Boolean = false,
        h: Int = 36, size: Float = 16f, divider: Boolean = true, onClick: (() -> Unit)? = null
    ) {
        val r = LinearLayout(a)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        val l = a.tv(left, size, C.INK)
        l.maxLines = 1
        l.ellipsize = TextUtils.TruncateAt.END
        r.add(l, 0, WC, 0, 0, 1f)
        val rt = a.tv(right, size, if (warn) C.ACCENT else C.MUTE, "sans-serif-condensed", if (warn) Typeface.BOLD else Typeface.NORMAL)
        rt.maxLines = 1
        r.add(rt, WC, WC, 0, 8)
        if (onClick != null) r.setOnClickListener { onClick() }
        box.add(r, MP, h)
        if (divider) {
            val d = View(a)
            d.setBackgroundColor(C.LINE)
            box.add(d, MP, 1)
        }
    }

    private fun bigValue(box: LinearLayout, value: String, unit: String, top: Int = 0) {
        val r = LinearLayout(a)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.BOTTOM
        r.add(a.tv(value, 32f, C.INK, "sans-serif-light"), WC, WC)
        if (unit.isNotEmpty()) r.add(a.tv(unit, 15f, C.MUTE), WC, WC, 0, 6)
        box.add(r, MP, WC, top)
    }

    private fun kv(box: LinearLayout, items: List<Pair<String, String>>) {
        var i = 0
        while (i < items.size) {
            val r = LinearLayout(a)
            r.orientation = LinearLayout.HORIZONTAL
            for (j in i until minOf(i + 2, items.size)) {
                val c = LinearLayout(a)
                c.orientation = LinearLayout.VERTICAL
                c.add(a.tv(items[j].first, 22f, C.INK, "sans-serif-medium"))
                c.add(a.tv(items[j].second, 13f, C.MUTE))
                r.add(c, 0, WC, 0, 0, 1f)
            }
            box.add(r, MP, WC, 8)
            i += 2
        }
    }

    private fun button(box: LinearLayout, label: String, primary: Boolean, top: Int = 8, onClick: () -> Unit) {
        val b = a.tv(label, 16f, if (primary) C.BG2 else C.INK, gravity = Gravity.CENTER)
        b.background = if (primary) GradientDrawable().apply { setColor(C.INK); cornerRadius = 12f } else C.outline(12f)
        b.setOnClickListener { onClick() }
        box.add(b, MP, 36, top)
    }

    // ------------------------------------------------------------ tarjetas

    fun buildCard(key: String, size: Char): LinearLayout {
        val box = LinearLayout(a)
        box.orientation = LinearLayout.VERTICAL
        when (key) {
            "music" -> music(box)
            "fuel" -> fuel(box)
            "servicios" -> servicios(box, size)
            "viaje" -> viaje(box)
            "docs" -> docs(box, size)
            "gas" -> gas(box, size)
            "gastos" -> gastos(box, size)
            "favs" -> favs(box)
            "park" -> park(box)
            "tiempo" -> tiempo(box)
            "motor" -> motor(box)
            "fallas" -> fallas(box)
        }
        return box
    }

    private fun music(box: LinearLayout) {
        box.gravity = Gravity.CENTER_VERTICAL
        val top = LinearLayout(a)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val art = View(a)
        art.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF1C1C1C.toInt(), 0xFF5C5C5C.toInt(), 0xFF121212.toInt())).apply { cornerRadius = 12f }
        top.add(art, 70, 70)
        val names = LinearLayout(a)
        names.orientation = LinearLayout.VERTICAL
        val t = a.tv("Sin música", 21f, C.INK, "sans-serif-medium")
        t.maxLines = 1; t.ellipsize = TextUtils.TruncateAt.END
        val ar = a.tv("", 15f, C.MUTE)
        ar.maxLines = 1; ar.ellipsize = TextUtils.TruncateAt.END
        names.add(t)
        names.add(ar, MP, WC, 3)
        top.add(names, 0, WC, 0, 14, 1f)
        box.add(top, MP, 70)
        val bar = BarView(a)
        bar.strong = true
        box.add(bar, MP, 4, 14)
        val ctrl = LinearLayout(a)
        ctrl.orientation = LinearLayout.HORIZONTAL
        ctrl.gravity = Gravity.CENTER
        val prev = TransportButton(a, 0).apply { setOnClickListener { a.music.previous() } }
        val play = TransportButton(a, 1).apply { setOnClickListener { a.onPlayPause() } }
        val next = TransportButton(a, 3).apply { setOnClickListener { a.music.next() } }
        ctrl.add(prev, 52, 40)
        ctrl.add(play, 66, 40, 0, 22)
        ctrl.add(next, 52, 40, 0, 22)
        box.add(ctrl, MP, 40, 10)
        names.setOnClickListener { if (!a.music.hasAccess) a.openListenerSettings() }
        a.musicTitle = t; a.musicArtist = ar; a.musicBar = bar; a.playBtn = play
        a.renderMusic()
    }

    private fun fuel(box: LinearLayout) {
        box.gravity = Gravity.CENTER_VERTICAL
        val avg = a.fuelAvg()
        val top = LinearLayout(a)
        top.orientation = LinearLayout.HORIZONTAL
        val left = LinearLayout(a)
        left.orientation = LinearLayout.VERTICAL
        left.add(a.tv("Consumo promedio", 14f, C.MUTE))
        bigValue(left, if (avg != null) String.format(Locale.US, "%.1f", avg) else "--", "km/l")
        top.add(left, 0, WC, 0, 0, 1f)
        val right = LinearLayout(a)
        right.orientation = LinearLayout.VERTICAL
        right.add(a.tv("Autonomía", 14f, C.MUTE, gravity = Gravity.END))
        val range = if (avg != null) (Math.round(avg * 35.0 / 10.0) * 10).toString() + " km" else "--"
        right.add(a.tv(range, 22f, C.INK, "sans-serif-medium", gravity = Gravity.END))
        top.add(right, WC, WC)
        box.add(top, MP, WC)
        val bars = BarsView(a)
        bars.values = a.fuelIntervals().takeLast(7)
        box.add(bars, MP, 60, 12)
        if (bars.values.isEmpty()) box.add(a.tv("Registra tus cargas en Gasolina", 13f, C.MUTE), MP, WC, 6)
    }

    private fun servicios(box: LinearLayout, size: Char) {
        head(box, "Servicios", "Toca para gestionar")
        val rows = if (size == 'L') 4 else 3
        val odo = a.store.odo
        val now = System.currentTimeMillis()
        a.services.sortedBy { it.fraction(odo, now) }.take(rows).forEach { s ->
            val f = s.fraction(odo, now)
            val rem = s.remaining(odo, now)
            val warn = f < 0.3f
            val col = LinearLayout(a)
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(0, 5, 0, 5)
            val line = LinearLayout(a)
            line.orientation = LinearLayout.HORIZONTAL
            val n = a.tv(s.name, 16f, C.INK)
            n.maxLines = 1; n.ellipsize = TextUtils.TruncateAt.END
            line.add(n, 0, WC, 0, 0, 1f)
            val txt = if (rem < 0) "Vencido" else (if (warn) "Faltan " else "") + C.fmt(rem) + " " + s.unit
            line.add(a.tv(txt, 16f, if (warn) C.ACCENT else C.MUTE, "sans-serif-condensed", if (warn) Typeface.BOLD else Typeface.NORMAL), WC, WC, 0, 8)
            col.add(line)
            val b = BarView(a)
            b.frac = f; b.warn = warn
            col.add(b, MP, 4, 5)
            col.setOnClickListener { a.serviceOptions(s) }
            box.add(col, MP, 40)
        }
    }

    private fun viaje(box: LinearLayout) {
        head(box, "Resumen de viaje", "Hoy")
        val trip = a.store.tripKm()
        val movMs = a.store.movingMs()
        val avg = if (movMs > 60000L) trip / (movMs / 3600000.0) else 0.0
        kv(box, listOf(
            Math.round(a.store.maxKmh()).toString() to "Vel. máx. km/h",
            Math.round(avg).toString() to "Promedio km/h",
            "${movMs / 60000L} min" to "En movimiento",
            String.format(Locale.US, "%.0f km", a.store.weekKm()) to "Últimos 7 días"
        ))
    }

    private fun docs(box: LinearLayout, size: Char) {
        head(box, "Documentos", "+ Agregar") { a.docForm(-1) }
        val rows = if (size == 'L') 4 else 3
        if (a.data.docs.isEmpty()) {
            box.add(a.tv("Agrega verificación, seguro, tenencia o licencia.", 14f, C.MUTE), MP, WC, 8)
            return
        }
        val list = a.data.docs.mapIndexed { i, d -> Triple(i, d, a.daysTo(d.date)) }.sortedBy { it.third }.take(rows)
        list.forEachIndexed { idx, (i, d, days) ->
            row(box, d.n, if (days < 0) "Vencido" else "$days días", days < 30, 36, 16f, idx < list.size - 1) { a.docForm(i) }
        }
    }

    private fun gas(box: LinearLayout, size: Char) {
        head(box, "Gasolina", "+ Registrar") { a.gasForm() }
        val avg = a.fuelAvg()
        bigValue(box, if (avg != null) String.format(Locale.US, "%.1f", avg) else "--", "km/l promedio")
        val n = if (size == 'L') 4 else 2
        val sorted = a.data.fuel.sortedByDescending { it.t }.take(n)
        val fmt = SimpleDateFormat("d MMM", Locale("es", "MX"))
        sorted.forEachIndexed { idx, f ->
            row(box, fmt.format(Date(f.t)) + " · " + String.format(Locale.US, "%.1f", f.l) + " L", C.money(f.c), false, 30, 15f, idx < sorted.size - 1)
        }
        if (sorted.isEmpty()) box.add(a.tv("Toca “+ Registrar” en cada carga.", 14f, C.MUTE), MP, WC, 8)
    }

    private fun gastos(box: LinearLayout, size: Char) {
        head(box, "Gastos · 30 días", "+ Agregar") { a.expForm() }
        val t = a.expTotals()
        val sum = t.values.sum()
        bigValue(box, C.money(sum), "")
        val n = if (size == 'L') 4 else 3
        t.entries.sortedByDescending { it.value }.take(n).forEach { e ->
            val r = LinearLayout(a)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.add(a.tv(e.key, 14f, C.INK), 86, WC)
            val b = BarView(a)
            b.frac = if (sum > 0) (e.value / sum).toFloat() else 0f
            r.add(b, 0, 4, 0, 0, 1f)
            r.add(a.tv(C.money(e.value), 14f, C.MUTE, gravity = Gravity.END), 70, WC, 0, 8)
            box.add(r, MP, 24, 2)
        }
    }

    private fun favs(box: LinearLayout) {
        head(box, "Marcación rápida", "Editar") { a.favsForm() }
        val r = LinearLayout(a)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER
        for (i in 0 until 4) {
            val name = a.data.favs[i]
            val c = LinearLayout(a)
            c.orientation = LinearLayout.VERTICAL
            c.gravity = Gravity.CENTER_HORIZONTAL
            val ini = a.tv((name.firstOrNull() ?: '?').toString().uppercase(), 20f, C.INK, "sans-serif-medium", gravity = Gravity.CENTER)
            ini.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(C.PANEL); setStroke(1, C.LINE) }
            c.add(ini, 46, 46)
            val nm = a.tv(name, 13f, C.INK, gravity = Gravity.CENTER)
            nm.maxLines = 1; nm.ellipsize = TextUtils.TruncateAt.END
            c.add(nm, 62, WC, 6)
            c.setOnClickListener { a.callFav(i) }
            r.add(c, 0, WC, 0, 0, 1f)
        }
        box.add(r, MP, WC, 12)
    }

    private fun park(box: LinearLayout) {
        head(box, "Dónde estacioné", "Guardar") { a.saveParkHere() }
        val p = a.store.park()
        val r = LinearLayout(a)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        val pin = a.iconView(R.drawable.ic_pin, C.ACCENT)
        pin.setPadding(24, 14, 24, 14)
        pin.background = C.outline(12f)
        r.add(pin, 96, 70)
        val txt = LinearLayout(a)
        txt.orientation = LinearLayout.VERTICAL
        txt.add(a.tv(if (p != null) a.ago(p.t) else "Sin guardar", 17f, C.INK, "sans-serif-medium"))
        txt.add(a.tv(if (p != null) String.format(Locale.US, "%.4f, %.4f", p.lat, p.lon) else "Se guarda solo al manejar", 13f, C.MUTE), MP, WC, 3)
        r.add(txt, 0, WC, 0, 12, 1f)
        box.add(r, MP, 70, 2)
        button(box, "Volver al auto", true) { a.goToCar() }
    }

    private fun tiempo(box: LinearLayout) {
        head(box, "Clima y sol")
        val w = a.wx
        bigValue(box, if (w != null) "${w.temp}°" else "--", w?.cond ?: "Sin datos")
        val sf = SimpleDateFormat("h:mm a", Locale.US)
        row(box, "Lluvia próxima hora", if (w != null) "${w.rain} %" else "--", false, 26, 15f)
        row(box, "Amanece", if (w != null && w.sunrise > 0) sf.format(Date(w.sunrise)) else "--", false, 26, 15f)
        row(box, "Atardece", if (w != null && w.sunset > 0) sf.format(Date(w.sunset)) else "--", false, 26, 15f, false)
    }

    private fun motor(box: LinearLayout) {
        head(box, "Datos del motor", null, "OBD · ejemplo")
        kv(box, listOf("13.8 V" to "Batería", "88 °C" to "Temp. motor", "2,150" to "RPM", "9.1 L/100" to "Consumo"))
    }

    private fun fallas(box: LinearLayout) {
        head(box, "Lector de fallas", null, "OBD · ejemplo")
        box.add(a.tv("Sin fallas activas", 19f, C.INK, "sans-serif-medium"), MP, WC, 6)
        box.add(a.tv("Última lectura: hace 3 días", 13f, C.MUTE), MP, WC, 4)
        button(box, "Leer ahora", false, 10) { a.toast("Necesita un adaptador ELM327 conectado") }
    }

    // ------------------------------------------------------------ minis

    fun mini(key: String): Mini {
        val d = a.data
        return when (key) {
            "kilometraje" -> Mini("Kilometraje (km)", C.fmt(Math.round(a.store.odo).toInt()), R.drawable.ic_route, { a.askOdometer() })
            "velocidad" -> Mini("Velocidad", "${Math.round(a.displaySpeed)} km/h", R.drawable.ic_gauge)
            "clima" -> Mini("Clima hoy", a.wx?.let { "${it.temp}° ${it.cond}" } ?: "--", R.drawable.ic_sun, { a.loadWeather(true) })
            "lluvia" -> Mini("Lluvia · 1 h", a.wx?.let { "${it.rain} %" } ?: "--", R.drawable.ic_rain)
            "sol" -> {
                val w = a.wx
                val now = System.currentTimeMillis()
                val sf = SimpleDateFormat("h:mm a", Locale.US)
                if (w == null || w.sunrise == 0L) Mini("Sol", "--", R.drawable.ic_sunset)
                else if (now in w.sunrise until w.sunset) Mini("Atardece", sf.format(Date(w.sunset)), R.drawable.ic_sunset)
                else Mini("Amanece", sf.format(Date(w.sunrise)), R.drawable.ic_sunset)
            }
            "viaje" -> Mini("Viaje de hoy", String.format(Locale.US, "%.1f km", a.store.tripKm()), R.drawable.ic_route)
            "casa" -> Mini("Ir a casa", if (d.home.isEmpty()) "Configurar" else "Navegar", R.drawable.ic_home,
                { a.navigateTo("home") }, { a.editDestination("home") })
            "trabajo" -> Mini("Ir al trabajo", if (d.work.isEmpty()) "Configurar" else "Navegar", R.drawable.ic_brief,
                { a.navigateTo("work") }, { a.editDestination("work") })
            "limite" -> {
                val over = d.limit > 0 && a.displaySpeed > d.limit
                Mini(if (d.limit > 0) "Límite ${d.limit}" else "Aviso de velocidad",
                    if (d.limit > 0) (if (over) "¡Excedido!" else "Activo") else "Apagado",
                    R.drawable.ic_alert, { a.cycleLimit() })
            }
            "parking" -> {
                val p = a.store.park()
                Mini("Estacionado", if (p != null) a.ago(p.t).removePrefix("hace ") else "--", R.drawable.ic_pin, { a.goToCar() })
            }
            "docs" -> {
                var best: Pair<String, Int>? = null
                d.docs.forEach { x -> val n = a.daysTo(x.date); if (best == null || n < best!!.second) best = Pair(x.n, n) }
                val b = best
                if (b == null) Mini("Documentos", "--", R.drawable.ic_doc)
                else Mini(b.first, if (b.second < 0) "Vencido" else "${b.second} días", R.drawable.ic_doc)
            }
            "gas" -> {
                val avg = a.fuelAvg()
                Mini("Rendimiento", if (avg != null) String.format(Locale.US, "%.1f km/l", avg) else "--", R.drawable.ic_fuel)
            }
            "gastos" -> Mini("Gastos 30 d", C.money(a.expTotals().values.sum()), R.drawable.ic_coin)
            "autonomia" -> {
                val avg = a.fuelAvg()
                Mini("Autonomía", if (avg != null) (Math.round(avg * 35.0 / 10.0) * 10).toString() + " km" else "--", R.drawable.ic_fuel)
            }
            "rumbo" -> Mini("Rumbo", a.headingText(), R.drawable.ic_compass)
            "bateria" -> Mini("Batería · OBD", "13.8 V", R.drawable.ic_battery)
            "motor" -> Mini("Motor · OBD", "88 °C", R.drawable.ic_thermo)
            "rpm" -> Mini("RPM · OBD", "2,150", R.drawable.ic_gauge)
            "consumo" -> Mini("Consumo · OBD", "9.1 L/100", R.drawable.ic_fuel)
            "fallas" -> Mini("Fallas · OBD", "Ninguna", R.drawable.ic_alert, { a.toast("Necesita un adaptador ELM327 conectado") })
            else -> Mini("", "", R.drawable.ic_gauge)
        }
    }
}
