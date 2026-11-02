package com.davidg.outlander

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val DAY_MS = 86400000L

class Service(var name: String, var every: Int, var unit: String, var baseOdo: Double, var baseTime: Long) {
    fun remaining(odo: Double, now: Long): Int =
        if (unit == "km") (every - (odo - baseOdo)).toInt()
        else (every - (now - baseTime) / DAY_MS).toInt()

    fun fraction(odo: Double, now: Long): Float =
        (remaining(odo, now).toFloat() / every.toFloat()).coerceIn(0f, 1f)

    fun markDone(odo: Double, now: Long) {
        baseOdo = odo
        baseTime = now
    }

    fun setRemaining(rem: Int, odo: Double, now: Long) {
        if (unit == "km") baseOdo = odo - (every - rem)
        else baseTime = now - (every - rem) * DAY_MS
    }
}

/** target: "@radio", "@maps", "@music", "@dial", "@apps", "@settings" o un nombre de paquete. */
class DockItem(var label: String, var target: String)

class Doc(var n: String, var date: String)          // date: yyyy-MM-dd
class Fill(val t: Long, val km: Double, val l: Double, val c: Double)
class Exp(val t: Long, val cat: String, val a: Double)
class Park(val lat: Double, val lon: Double, val t: Long)

/** Datos que captura el usuario. Se guardan completos cada vez que cambian. */
class AppData {
    val docs = mutableListOf<Doc>()
    val fuel = mutableListOf<Fill>()
    val exp = mutableListOf<Exp>()
    var favs = mutableListOf("Casa", "Trabajo", "Mamá", "Grúa")
    var nums = mutableListOf("", "", "", "")
    var home = ""
    var work = ""
    var limit = 0   // km/h; 0 = apagado

    fun toJson(): String {
        val o = JSONObject()
        o.put("docs", JSONArray().also { a -> docs.forEach { a.put(JSONObject().put("n", it.n).put("date", it.date)) } })
        o.put("fuel", JSONArray().also { a -> fuel.forEach { a.put(JSONObject().put("t", it.t).put("km", it.km).put("l", it.l).put("c", it.c)) } })
        o.put("exp", JSONArray().also { a -> exp.forEach { a.put(JSONObject().put("t", it.t).put("cat", it.cat).put("a", it.a)) } })
        o.put("favs", JSONArray(favs))
        o.put("nums", JSONArray(nums))
        o.put("home", home)
        o.put("work", work)
        o.put("limit", limit)
        return o.toString()
    }

    companion object {
        fun fromJson(s: String?): AppData {
            val d = AppData()
            if (s == null) return d
            try {
                val o = JSONObject(s)
                val da = o.optJSONArray("docs")
                if (da != null) for (i in 0 until da.length()) { val x = da.getJSONObject(i); d.docs.add(Doc(x.getString("n"), x.getString("date"))) }
                val fa = o.optJSONArray("fuel")
                if (fa != null) for (i in 0 until fa.length()) { val x = fa.getJSONObject(i); d.fuel.add(Fill(x.getLong("t"), x.getDouble("km"), x.getDouble("l"), x.getDouble("c"))) }
                val ea = o.optJSONArray("exp")
                if (ea != null) for (i in 0 until ea.length()) { val x = ea.getJSONObject(i); d.exp.add(Exp(x.getLong("t"), x.getString("cat"), x.getDouble("a"))) }
                val fv = o.optJSONArray("favs")
                if (fv != null && fv.length() == 4) d.favs = MutableList(4) { fv.getString(it) }
                val nm = o.optJSONArray("nums")
                if (nm != null && nm.length() == 4) d.nums = MutableList(4) { nm.getString(it) }
                d.home = o.optString("home", "")
                d.work = o.optString("work", "")
                d.limit = o.optInt("limit", 0)
            } catch (e: Exception) { }
            return d
        }
    }
}

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("outlander", Context.MODE_PRIVATE)

    private fun getD(k: String): Double = java.lang.Double.longBitsToDouble(p.getLong(k, 0L))
    private fun bits(v: Double): Long = java.lang.Double.doubleToRawLongBits(v)
    private fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    /** Kilometraje total en km (Double para no perder precisión al sumar metros). */
    var odo: Double
        get() = getD("odo2")
        set(v) { p.edit().putLong("odo2", bits(v)).apply() }

    fun tripKm(): Double = if (p.getString("tripDay", "") != today()) 0.0 else getD("trip2")
    fun maxKmh(): Float = if (p.getString("tripDay", "") != today()) 0f else p.getFloat("maxKmh", 0f)
    fun movingMs(): Long = if (p.getString("tripDay", "") != today()) 0L else p.getLong("movingMs", 0L)

    fun weekKm(): Double {
        return try {
            val w = JSONObject(p.getString("week", "{}") ?: "{}")
            val cut = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(System.currentTimeMillis() - 6 * DAY_MS))
            var s = 0.0
            val it = w.keys()
            while (it.hasNext()) { val k = it.next(); if (k >= cut) s += w.optDouble(k, 0.0) }
            s
        } catch (e: Exception) { 0.0 }
    }

    /** Lo llama el servicio de GPS cada segundo; guarda en disco de inmediato. */
    fun recordMotion(kmh: Float, dtMs: Long, dKm: Double) {
        val day = today()
        var trip = getD("trip2"); var maxK = p.getFloat("maxKmh", 0f); var mov = p.getLong("movingMs", 0L)
        if (p.getString("tripDay", "") != day) { trip = 0.0; maxK = 0f; mov = 0L }
        if (kmh > 0f) { mov += dtMs; if (kmh > maxK) maxK = kmh }
        trip += dKm
        val e = p.edit()
            .putString("tripDay", day)
            .putLong("trip2", bits(trip))
            .putFloat("maxKmh", maxK)
            .putLong("movingMs", mov)
            .putLong("odo2", bits(getD("odo2") + dKm))
        if (dKm > 0.0) {
            try {
                val w = JSONObject(p.getString("week", "{}") ?: "{}")
                w.put(day, w.optDouble(day, 0.0) + dKm)
                val cut = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(System.currentTimeMillis() - 8 * DAY_MS))
                val old = mutableListOf<String>()
                val it = w.keys()
                while (it.hasNext()) { val k = it.next(); if (k < cut) old.add(k) }
                old.forEach { w.remove(it) }
                e.putString("week", w.toString())
            } catch (ex: Exception) { }
        }
        e.apply()
    }

    fun park(): Park? {
        if (!p.contains("parkT")) return null
        return Park(getD("parkLat"), getD("parkLon"), p.getLong("parkT", 0L))
    }

    fun savePark(lat: Double, lon: Double, t: Long) {
        p.edit().putLong("parkLat", bits(lat)).putLong("parkLon", bits(lon)).putLong("parkT", t).apply()
    }

    fun loadServices(): MutableList<Service> {
        val raw = p.getString("services", null)
        val out = mutableListOf<Service>()
        if (raw != null) {
            try {
                val a = JSONArray(raw)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    out.add(Service(o.getString("name"), o.getInt("every"), o.getString("unit"), o.getDouble("baseOdo"), o.getLong("baseTime")))
                }
                return out
            } catch (e: Exception) { out.clear() }
        }
        val now = System.currentTimeMillis()
        val o = odo
        out.add(Service("Aceite y filtro", 5000, "km", o, now))
        out.add(Service("Rotación de llantas", 10000, "km", o, now))
        out.add(Service("Filtro de aire", 20000, "km", o, now))
        out.add(Service("Verificación vehicular", 180, "días", o, now))
        return out
    }

    fun saveServices(list: List<Service>) {
        val a = JSONArray()
        for (s in list) a.put(JSONObject().put("name", s.name).put("every", s.every).put("unit", s.unit).put("baseOdo", s.baseOdo).put("baseTime", s.baseTime))
        p.edit().putString("services", a.toString()).apply()
    }

    fun loadDock(): MutableList<DockItem> {
        val raw = p.getString("dock", null)
        val out = mutableListOf<DockItem>()
        if (raw != null) {
            try {
                val a = JSONArray(raw)
                for (i in 0 until a.length()) { val o = a.getJSONObject(i); out.add(DockItem(o.getString("label"), o.getString("target"))) }
                return out
            } catch (e: Exception) { out.clear() }
        }
        return defaultDock()
    }

    fun defaultDock(): MutableList<DockItem> = mutableListOf(
        DockItem("Radio", "@radio"), DockItem("Navegación", "@maps"), DockItem("Música", "@music"),
        DockItem("Teléfono", "@dial"), DockItem("Apps", "@apps"), DockItem("Ajustes", "@settings")
    )

    fun saveDock(list: List<DockItem>) {
        val a = JSONArray()
        for (d in list) a.put(JSONObject().put("label", d.label).put("target", d.target))
        p.edit().putString("dock", a.toString()).apply()
    }

    fun loadData(): AppData = AppData.fromJson(p.getString("data", null))
    fun saveData(d: AppData) { p.edit().putString("data", d.toJson()).apply() }

    var widgetsJson: String?
        get() = p.getString("widgets", null)
        set(v) { p.edit().putString("widgets", v).apply() }

    var themeMode: String
        get() = p.getString("themeMode", "auto") ?: "auto"
        set(v) { p.edit().putString("themeMode", v).apply() }

    /** Respaldo completo de todo lo guardado, como texto JSON. */
    fun exportAll(): String {
        val o = JSONObject()
        for ((k, v) in p.all) {
            val e = JSONObject()
            when (v) {
                is String -> { e.put("t", "s"); e.put("v", v) }
                is Long -> { e.put("t", "l"); e.put("v", v) }
                is Int -> { e.put("t", "i"); e.put("v", v) }
                is Boolean -> { e.put("t", "b"); e.put("v", v) }
                is Float -> { e.put("t", "f"); e.put("v", v.toDouble()) }
                else -> continue
            }
            o.put(k, e)
        }
        return o.toString()
    }

    fun importAll(json: String): Boolean {
        return try {
            val o = JSONObject(json)
            val ed = p.edit()
            ed.clear()
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                val e = o.getJSONObject(k)
                when (e.getString("t")) {
                    "s" -> ed.putString(k, e.getString("v"))
                    "l" -> ed.putLong(k, e.getLong("v"))
                    "i" -> ed.putInt(k, e.getInt("v"))
                    "b" -> ed.putBoolean(k, e.getBoolean("v"))
                    "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                }
            }
            ed.commit()
        } catch (e: Exception) { false }
    }
}
