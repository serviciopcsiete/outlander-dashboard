package com.davidg.outlander

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min

class AppInfo(val label: String, val pkg: String, val icon: Drawable)

class MainActivity : Activity() {

    lateinit var store: Store
    lateinit var data: AppData
    lateinit var music: MusicController
    private lateinit var factory: WidgetFactory

    var services = mutableListOf<Service>()
    private var dockItems = mutableListOf<DockItem>()
    var wx: WeatherData? = null
    var displaySpeed = 0f

    // widgets elegidos
    private var wa = "music"
    private var wb = "servicios"
    private var wm1 = "kilometraje"
    private var wm2 = "clima"

    // vistas principales
    private lateinit var stage: FrameLayout
    private lateinit var ui: FrameLayout
    private lateinit var car: ImageView
    private lateinit var slotA: FrameLayout
    private lateinit var slotB: FrameLayout
    private lateinit var miniA: FrameLayout
    private lateinit var miniB: FrameLayout
    private lateinit var dock: LinearLayout
    private lateinit var digital: DigitalSpeedView
    private lateinit var tempTop: TextView

    // referencias de la tarjeta de música (si está visible)
    var musicTitle: TextView? = null
    var musicArtist: TextView? = null
    var musicBar: BarView? = null
    var playBtn: TransportButton? = null

    // mapa
    private var map: MapView? = null
    private var mapMarker: Marker? = null
    private var markerAdded = false
    private var lastMapLat = 0.0
    private var lastMapLon = 0.0
    private var mapZoom = 16.0

    private val handler = Handler(Looper.getMainLooper())
    private var simulating = false
    private var simSpeed = 0f
    private var simTarget = 60f
    private var tickCount = 0
    private var firstStart = true
    private var overFlag = false
    private var pendingStorage: (() -> Unit)? = null

    private val ticker = object : Runnable {
        override fun run() {
            tickUi()
            handler.postDelayed(this, 500L)
        }
    }
    private val weatherTimer = object : Runnable {
        override fun run() {
            loadWeather(false)
            handler.postDelayed(this, 30 * 60 * 1000L)
        }
    }

    // ================================================================ ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        data = store.loadData()
        services = store.loadServices()
        dockItems = store.loadDock()
        factory = WidgetFactory(this)
        loadWidgetConfig()

        val cfg = Configuration.getInstance()
        cfg.userAgentValue = packageName
        cfg.osmdroidBasePath = File(cacheDir, "osmdroid")
        cfg.osmdroidTileCache = File(cacheDir, "osmdroid/tiles")

        music = MusicController(this) { runOnUiThread { renderMusic() } }

        C.apply(wantDay())
        setContentView(buildRoot())
        renderAll()

        if (Build.VERSION.SDK_INT >= 23 && !hasLocationPermission(this)) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 7)
        } else {
            startTracking(this)
        }
    }

    override fun onResume() {
        super.onResume()
        map?.onResume()
        music.stop()
        music.start()
        startTracking(this)
        handler.post(ticker)
        handler.post(weatherTimer)
        if (firstStart) {
            firstStart = false
            playIntro()
        }
    }

    override fun onPause() {
        super.onPause()
        map?.onPause()
        music.stop()
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(weatherTimer)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Un launcher no se cierra con Atrás.
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7) {
            startTracking(this)
            loadWeather(false)
        } else if (requestCode == 8) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            val a = pendingStorage
            pendingStorage = null
            if (granted && a != null) a() else toastLong("Sin permiso de almacenamiento no puedo guardar el respaldo")
        }
    }

    // ================================================================ construcción de la pantalla

    private fun wantDay(): Boolean = when (store.themeMode) {
        "day" -> true
        "night" -> false
        else -> {
            val w = wx
            val now = System.currentTimeMillis()
            if (w != null && w.sunrise > 0L && w.sunset > 0L) now in w.sunrise until w.sunset
            else Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 6..18
        }
    }

    private fun checkTheme() {
        val d = wantDay()
        if (d != C.day) {
            C.apply(d)
            rebuild()
        }
    }

    private fun rebuild() {
        map?.onDetach()
        markerAdded = false
        lastMapLat = 0.0
        setContentView(buildRoot())
        ui.alpha = 1f
        renderAll()
        map?.onResume()
    }

    private fun buildRoot(): FrameLayout {
        val root = FrameLayout(this)
        root.setBackgroundColor(C.BG3)
        stage = FrameLayout(this)
        stage.background = C.stageBackground()
        root.addView(stage, FrameLayout.LayoutParams(1280, 720))
        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val w = (r - l).toFloat(); val h = (b - t).toFloat()
            if (w > 0f && h > 0f) {
                val s = min(w / 1280f, h / 720f)
                stage.pivotX = 0f; stage.pivotY = 0f
                stage.scaleX = s; stage.scaleY = s
                stage.translationX = (w - 1280f * s) / 2f
                stage.translationY = (h - 720f * s) / 2f
            }
        }

        // bajo la camioneta
        stage.put(FloorView(this, 0), 380, 360, 560, 150)
        stage.put(FloorView(this, 1), 450, 404, 440, 40)

        car = ImageView(this)
        car.setImageResource(R.drawable.outlander)
        car.scaleType = ImageView.ScaleType.FIT_CENTER
        car.cameraDistance = 14000f
        car.setOnClickListener { playIntro() }
        stage.put(car, 410, 146, 520, 286)

        ui = FrameLayout(this)
        ui.alpha = 0f
        stage.put(ui, 0, 0, 1280, 720)

        buildTopBar()
        ui.put(buildMapCard(), 48, 100, 362, 488)

        val name = tv("Outlander", 34f, C.INK, "sans-serif-light", gravity = Gravity.CENTER)
        name.setOnClickListener { playIntro() }
        ui.put(name, 410, 452, 520, 42)
        ui.put(tv("2006 · primera generación", 17f, C.MUTE, gravity = Gravity.CENTER), 410, 496, 520, 24)

        slotA = slot(); slotB = slot()
        ui.put(slotA, 930, 100, 306, 176)
        ui.put(slotB, 930, 374, 306, 216)
        miniA = miniFrame(); miniB = miniFrame()
        ui.put(miniA, 930, 288, 149, 74)
        ui.put(miniB, 1087, 288, 149, 74)

        digital = DigitalSpeedView(this)
        digital.background = C.card(22f)
        digital.setOnLongClickListener { toggleSim(); true }
        ui.put(digital, 48, 604, 300, 98)

        dock = LinearLayout(this)
        dock.orientation = LinearLayout.HORIZONTAL
        dock.gravity = Gravity.CENTER
        ui.put(dock, 372, 604, 758, 96)

        val pers = tv("Personalizar", 16f, C.MUTE, gravity = Gravity.CENTER)
        pers.setOnClickListener { customizeMenu() }
        ui.put(pers, 1136, 640, 110, 40)

        return root
    }

    private fun slot(): FrameLayout = FrameLayout(this).apply {
        background = C.card(22f)
        setPadding(20, 14, 20, 14)
        clipChildren = true
    }

    private fun miniFrame(): FrameLayout = FrameLayout(this).apply {
        background = C.card(18f)
        setPadding(14, 12, 14, 8)
    }

    private fun buildTopBar() {
        val clock = TextClock(this)
        clock.format12Hour = "h:mm a"
        clock.format24Hour = "h:mm a"
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 38f)
        clock.setTextColor(C.INK)
        clock.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        clock.includeFontPadding = false
        ui.put(clock, 48, 20, 300, 48)

        ui.put(DiamondLogoView(this), 452, 16, 52, 46)
        val brand = tv("MITSUBISHI", 30f, C.INK, "sans-serif-medium")
        brand.letterSpacing = 0.3f
        ui.put(brand, 516, 22, 320, 40)

        val themeBtn = LinearLayout(this)
        themeBtn.orientation = LinearLayout.VERTICAL
        themeBtn.gravity = Gravity.CENTER
        themeBtn.add(iconView(if (C.day) R.drawable.ic_sun else R.drawable.ic_moon, C.ACCENT), 24, 24)
        val mode = when (store.themeMode) { "day" -> "Día"; "night" -> "Noche"; else -> "Auto" }
        themeBtn.add(tv(mode, 12f, C.MUTE, gravity = Gravity.CENTER), WC, WC, 4)
        themeBtn.setOnClickListener {
            store.themeMode = when (store.themeMode) { "auto" -> "day"; "day" -> "night"; else -> "auto" }
            C.apply(wantDay())
            rebuild()
            toast("Tema: " + when (store.themeMode) { "day" -> "día"; "night" -> "noche"; else -> "automático" })
        }
        ui.put(themeBtn, 1074, 14, 66, 62)

        tempTop = tv("--°C", 38f, C.INK, gravity = Gravity.END)
        ui.put(tempTop, 900, 20, 332, 48)

        val line = View(this)
        line.setBackgroundColor(C.LINE)
        ui.put(line, 48, 84, 1184, 1)
    }

    private fun buildMapCard(): FrameLayout {
        val box = FrameLayout(this)
        box.background = C.card(22f)
        box.clipToOutline = true
        box.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 22f)
            }
        }
        val m = MapView(this)
        m.setTileSource(TileSourceFactory.MAPNIK)
        m.setMultiTouchControls(false)
        m.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        m.controller.setZoom(mapZoom)
        val p = store.park()
        m.controller.setCenter(GeoPoint(p?.lat ?: 19.4326, p?.lon ?: -99.1332))
        m.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) openNavApp()
            true
        }
        map = m
        val mk = Marker(m)
        mk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        mk.icon = BitmapDrawable(resources, arrowBitmap())
        mk.setInfoWindow(null)
        mk.setOnMarkerClickListener { _, _ -> true }
        mapMarker = mk
        applyMapFilter()
        box.addView(m, FrameLayout.LayoutParams(MP, MP))

        val chip = tv("Abrir navegación", 15f, C.INK)
        chip.setPadding(14, 9, 14, 9)
        chip.background = C.solid(14f)
        chip.setOnClickListener { openNavApp() }
        box.put(chip, 12, 440, 170, 36)

        val zin = tv("+", 24f, C.INK, gravity = Gravity.CENTER)
        zin.background = C.solid(12f)
        zin.setOnClickListener { zoomMap(1.0) }
        box.put(zin, 312, 390, 38, 38)
        val zout = tv("−", 24f, C.INK, gravity = Gravity.CENTER)
        zout.background = C.solid(12f)
        zout.setOnClickListener { zoomMap(-1.0) }
        box.put(zout, 312, 436, 38, 38)
        return box
    }

    private fun zoomMap(d: Double) {
        mapZoom = (mapZoom + d).coerceIn(12.0, 19.0)
        map?.controller?.setZoom(mapZoom)
    }

    /** Mapa en gris: de día casi sin color, de noche invertido y oscuro. */
    private fun applyMapFilter() {
        val m = map ?: return
        val cm = ColorMatrix()
        cm.setSaturation(0.08f)
        if (!C.day) {
            cm.postConcat(ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f)))
            cm.postConcat(ColorMatrix(floatArrayOf(
                0.85f, 0f, 0f, 0f, 0f,
                0f, 0.85f, 0f, 0f, 0f,
                0f, 0f, 0.85f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f)))
        }
        m.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(cm))
    }

    private fun arrowBitmap(): Bitmap {
        val s = 64
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = C.ACCENT
        p.alpha = 50
        c.drawCircle(s / 2f, s / 2f, 26f, p)
        val path = Path()
        path.moveTo(32f, 10f); path.lineTo(44f, 50f); path.lineTo(32f, 42f); path.lineTo(20f, 50f); path.close()
        p.alpha = 255
        p.style = Paint.Style.FILL
        p.color = C.ACCENT
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = if (C.day) 0xFFFFFFFF.toInt() else 0xFF0B0B0B.toInt()
        c.drawPath(path, p)
        return bmp
    }

    private fun updateMap() {
        val m = map ?: return
        val mk = mapMarker ?: return
        if (!Live.hasFix) return
        if (Live.lat == lastMapLat && Live.lon == lastMapLon) return
        lastMapLat = Live.lat
        lastMapLon = Live.lon
        val gp = GeoPoint(Live.lat, Live.lon)
        mk.position = gp
        mk.rotation = -Live.bearing
        if (!markerAdded) {
            m.overlays.add(mk)
            markerAdded = true
        }
        m.controller.setCenter(gp)
        m.invalidate()
    }

    private fun openNavApp() {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS))
        } catch (e: ActivityNotFoundException) {
            toast("No encontré una app de navegación instalada")
        }
    }

    // ================================================================ animación de entrada

    private fun playIntro() {
        if (car.alpha < 1f && ui.alpha < 1f) { /* primera vez */ }
        val set = AnimatorSet()
        set.playTogether(
            ObjectAnimator.ofFloat(car, "alpha", 0f, 1f),
            ObjectAnimator.ofFloat(car, "rotationY", 34f, 0f),
            ObjectAnimator.ofFloat(car, "translationX", 80f, 0f),
            ObjectAnimator.ofFloat(car, "scaleX", 0.9f, 1f),
            ObjectAnimator.ofFloat(car, "scaleY", 0.9f, 1f)
        )
        set.duration = 1900
        set.interpolator = DecelerateInterpolator(2.2f)
        set.start()
        if (ui.alpha < 1f) {
            ObjectAnimator.ofFloat(ui, "alpha", 0f, 1f).apply { duration = 900; startDelay = 1300; start() }
        }
    }

    // ================================================================ pintado de datos

    private fun renderAll() {
        renderDock()
        renderSlots()
        renderMinis(true)
        updateTempTop()
    }

    fun renderSlots() {
        musicTitle = null; musicArtist = null; musicBar = null; playBtn = null
        slotA.removeAllViews(); slotB.removeAllViews()
        slotA.addView(factory.buildCard(wa, 'S'), FrameLayout.LayoutParams(MP, MP))
        slotB.addView(factory.buildCard(wb, 'L'), FrameLayout.LayoutParams(MP, MP))
    }

    fun renderMinis(force: Boolean) {
        renderMini(miniA, wm1, force)
        renderMini(miniB, wm2, force)
    }

    private fun renderMini(frame: FrameLayout, key: String, force: Boolean) {
        val m = factory.mini(key)
        val sig = key + "|" + m.k + "|" + m.v
        if (!force && frame.tag == sig) return
        frame.tag = sig
        frame.removeAllViews()
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val k = tv(m.k, 14f, C.MUTE)
        k.maxLines = 1
        k.ellipsize = TextUtils.TruncateAt.END
        col.add(k)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.add(iconView(m.icon, C.ACCENT), 24, 24)
        val v = tv(m.v, 19f, C.INK, "sans-serif-medium")
        v.maxLines = 1
        v.ellipsize = TextUtils.TruncateAt.END
        row.add(v, 0, WC, 0, 7, 1f)
        col.add(row, MP, WC, 6)
        frame.addView(col, FrameLayout.LayoutParams(MP, MP))
        val tap = m.tap
        val hold = m.hold
        frame.setOnClickListener(if (tap != null) View.OnClickListener { tap() } else null)
        frame.setOnLongClickListener(if (hold != null) View.OnLongClickListener { hold(); true } else null)
        frame.isClickable = tap != null
        frame.isLongClickable = hold != null
    }

    private fun updateTempTop() {
        val w = wx
        tempTop.text = if (w != null) "${w.temp}°C" else "--°C"
    }

    fun renderMusic() {
        val t = musicTitle ?: return
        val ar = musicArtist ?: return
        val pb = playBtn
        if (!music.hasAccess) {
            t.text = "Sin música"
            ar.text = "Toca aquí para activar el acceso"
            pb?.kind = 1
            pb?.invalidate()
            return
        }
        t.text = if (music.title.isEmpty()) "Nada en reproducción" else music.title
        ar.text = music.artist
        pb?.kind = if (music.playing) 2 else 1
        pb?.invalidate()
        updateMusicProgress()
    }

    private fun updateMusicProgress() {
        val b = musicBar ?: return
        b.frac = music.progress().coerceAtLeast(0f)
        b.invalidate()
    }

    fun onPlayPause() {
        if (!music.hasAccess) { openListenerSettings(); return }
        if (music.hasSession()) music.playPause() else startTarget(DockItem("Música", "@music"))
    }

    fun openListenerSettings() {
        try {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        } catch (e: Exception) {
            toastLong("Abre Ajustes > Acceso a notificaciones y activa Launcher Outlander")
        }
    }

    // ================================================================ ciclo de actualización

    private fun tickUi() {
        tickCount++
        if (simulating) stepSim()
        displaySpeed = if (simulating) simSpeed else Live.speed
        digital.set(displaySpeed, store.tripKm(), data.limit > 0 && displaySpeed > data.limit)
        limitAlert()
        updateMap()
        if (tickCount % 2 == 0) {
            renderMinis(false)
            updateMusicProgress()
        }
        if (tickCount % 120 == 0) {
            renderSlots()
            checkTheme()
        }
    }

    private fun limitAlert() {
        val lim = data.limit
        if (lim > 0 && displaySpeed > lim) {
            if (!overFlag) {
                overFlag = true
                try {
                    val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP)
                    handler.postDelayed({ tg.release() }, 600L)
                } catch (e: Exception) { }
            }
        } else if (displaySpeed <= lim - 3) {
            overFlag = false
        }
    }

    private fun toggleSim() {
        simulating = !simulating
        Live.simulating = simulating
        if (!simulating) simSpeed = 0f
        toast(if (simulating) "Modo demo activado" else "Modo demo desactivado")
    }

    private fun stepSim() {
        if (Math.abs(simSpeed - simTarget) < 4f) {
            simTarget = if (Math.random() < 0.15) 0f else (20 + Math.random() * 110).toFloat()
        }
        simSpeed += (simTarget - simSpeed) * 0.3f
    }

    // ================================================================ utilidades para los widgets

    fun toast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    fun toastLong(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
    fun saveData() { store.saveData(data) }

    fun ago(ms: Long): String {
        val m = (System.currentTimeMillis() - ms) / 60000L
        if (m < 1) return "hace un momento"
        if (m < 60) return "hace $m min"
        val h = m / 60
        if (h < 24) return "hace $h h" + (if (m % 60 > 0 && h < 6) " ${m % 60} min" else "")
        return "hace ${h / 24} d"
    }

    fun daysTo(date: String): Int {
        val t = try { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)?.time ?: 0L } catch (e: Exception) { 0L }
        return Math.ceil((t - System.currentTimeMillis()).toDouble() / DAY_MS).toInt()
    }

    fun fuelAvg(): Double? {
        val f = data.fuel.sortedBy { it.km }
        if (f.size < 2) return null
        var l = 0.0
        for (i in 1 until f.size) l += f[i].l
        return if (l > 0.0) (f.last().km - f.first().km) / l else null
    }

    fun fuelIntervals(): List<Float> {
        val f = data.fuel.sortedBy { it.km }
        val out = mutableListOf<Float>()
        for (i in 1 until f.size) if (f[i].l > 0.0) out.add(((f[i].km - f[i - 1].km) / f[i].l).toFloat())
        return out
    }

    fun expTotals(): Map<String, Double> {
        val cut = System.currentTimeMillis() - 30 * DAY_MS
        val t = linkedMapOf("Gasolina" to 0.0, "Servicios" to 0.0, "Refacciones" to 0.0, "Otros" to 0.0)
        data.fuel.forEach { if (it.t >= cut) t["Gasolina"] = (t["Gasolina"] ?: 0.0) + it.c }
        data.exp.forEach { if (it.t >= cut) t[it.cat] = (t[it.cat] ?: 0.0) + it.a }
        return t
    }

    fun headingText(): String {
        if (!Live.hasFix) return "--"
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        val b = ((Live.bearing % 360f) + 360f) % 360f
        return dirs[((b + 22.5f) / 45f).toInt() % 8] + " " + b.toInt() + "°"
    }

    fun loadWeather(manual: Boolean) {
        val lat: Double
        val lon: Double
        if (Live.hasFix) {
            lat = Live.lat; lon = Live.lon
        } else {
            val p = store.park()
            if (p != null) { lat = p.lat; lon = p.lon }
            else { if (manual) toast("Todavía no tengo ubicación"); return }
        }
        Weather.fetch(this, lat, lon) { w ->
            wx = w
            updateTempTop()
            renderMinis(true)
            renderSlots()
            checkTheme()
        }
    }

    // ================================================================ acciones de los widgets

    fun askOdometer() {
        form("Kilometraje total del carro (km)",
            listOf(Field("km", "Kilometraje del tablero", "number", Math.round(store.odo).toString()))) { v ->
            val n = v["km"]?.toDoubleOrNull()
            if (n == null || n < 0) { toast("Escribe un número"); false }
            else { store.odo = n; renderMinis(true); renderSlots(); true }
        }
    }

    fun serviceOptions(s: Service) {
        AlertDialog.Builder(this)
            .setTitle(s.name)
            .setItems(arrayOf("Marcar como hecho", "Ajustar lo que falta", "Quitar servicio")) { _, which ->
                val now = System.currentTimeMillis()
                when (which) {
                    0 -> { s.markDone(store.odo, now); saveServices() }
                    1 -> form("Cuánto falta (${s.unit})",
                        listOf(Field("n", "Faltan", "number", s.remaining(store.odo, now).toString()))) { v ->
                        val n = v["n"]?.toDoubleOrNull()
                        if (n == null) { toast("Escribe un número"); false }
                        else { s.setRemaining(n.toInt(), store.odo, now); saveServices(); true }
                    }
                    else -> { services.remove(s); saveServices() }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun saveServices() {
        store.saveServices(services)
        renderSlots()
    }

    private fun addServiceForm() {
        form("Agregar servicio", listOf(
            Field("n", "Nombre, por ejemplo Frenos"),
            Field("e", "Repetir cada", "number", "5000"),
            Field("u", "Unidad", "select", "km", listOf("km", "días"))
        )) { v ->
            val n = (v["n"] ?: "").trim()
            val e = v["e"]?.toDoubleOrNull()?.toInt() ?: 0
            if (n.isEmpty() || e <= 0) { toast("Escribe el nombre y cada cuánto se repite"); false }
            else {
                services.add(Service(n, e, v["u"] ?: "km", store.odo, System.currentTimeMillis()))
                saveServices(); true
            }
        }
    }

    fun docForm(i: Int) {
        val d = if (i >= 0) data.docs[i] else null
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(System.currentTimeMillis() + 365 * DAY_MS)
        form(if (d != null) "Editar documento" else "Agregar documento",
            listOf(Field("n", "Nombre", "text", d?.n ?: ""), Field("d", "Vence el", "date", d?.date ?: today)),
            danger = if (d != null) "Quitar" else null,
            onDanger = { if (i >= 0) { data.docs.removeAt(i); saveData(); renderSlots(); renderMinis(true) } }) { v ->
            val n = (v["n"] ?: "").trim()
            val date = v["d"] ?: ""
            if (n.isEmpty() || date.isEmpty()) { toast("Completa nombre y fecha"); false }
            else {
                if (i >= 0) { data.docs[i].n = n; data.docs[i].date = date } else data.docs.add(Doc(n, date))
                saveData(); renderSlots(); renderMinis(true); true
            }
        }
    }

    fun gasForm() {
        var last = 0.0
        data.fuel.forEach { if (it.km > last) last = it.km }
        val start = if (store.odo > last) Math.round(store.odo).toString() else Math.round(last).toString()
        form("Registrar carga de gasolina", listOf(
            Field("km", "Kilometraje actual", "number", start),
            Field("l", "Litros", "number"),
            Field("c", "Costo total ($)", "number")
        )) { v ->
            val km = v["km"]?.toDoubleOrNull() ?: 0.0
            val l = v["l"]?.toDoubleOrNull() ?: 0.0
            val c = v["c"]?.toDoubleOrNull() ?: 0.0
            if (km <= 0 || l <= 0 || c <= 0) { toast("Completa kilometraje, litros y costo"); false }
            else if (km <= last) { toast("El kilometraje debe ser mayor al de la última carga"); false }
            else {
                data.fuel.add(Fill(System.currentTimeMillis(), km, l, c))
                saveData(); renderSlots(); renderMinis(true); toast("Carga registrada"); true
            }
        }
    }

    fun expForm() {
        form("Agregar gasto", listOf(
            Field("cat", "Categoría", "select", "Servicios", listOf("Servicios", "Refacciones", "Otros")),
            Field("a", "Monto ($)", "number")
        )) { v ->
            val a = v["a"]?.toDoubleOrNull() ?: 0.0
            if (a <= 0) { toast("Escribe el monto"); false }
            else {
                data.exp.add(Exp(System.currentTimeMillis(), v["cat"] ?: "Otros", a))
                saveData(); renderSlots(); renderMinis(true); toast("Gasto agregado"); true
            }
        }
    }

    fun favsForm() {
        val fields = mutableListOf<Field>()
        for (i in 0 until 4) {
            fields.add(Field("n$i", "Contacto ${i + 1}: nombre", "text", data.favs[i]))
            fields.add(Field("t$i", "Contacto ${i + 1}: número", "number", data.nums[i]))
        }
        form("Marcación rápida", fields) { v ->
            for (i in 0 until 4) {
                data.favs[i] = (v["n$i"] ?: "").trim().ifEmpty { "—" }
                data.nums[i] = (v["t$i"] ?: "").trim()
            }
            saveData(); renderSlots(); true
        }
    }

    fun callFav(i: Int) {
        val num = data.nums[i]
        if (num.isBlank()) { toast("Agrega el número en Editar"); return }
        try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num)))) }
        catch (e: ActivityNotFoundException) { toast("No hay app de teléfono") }
    }

    fun saveParkHere() {
        if (!Live.hasFix) { toast("Todavía no hay señal de GPS"); return }
        store.savePark(Live.lat, Live.lon, System.currentTimeMillis())
        renderSlots(); renderMinis(true)
        toast("Ubicación guardada aquí")
    }

    fun goToCar() {
        val p = store.park()
        if (p == null) { toast("Todavía no hay una ubicación guardada"); return }
        val uri = Uri.parse("geo:${p.lat},${p.lon}?q=${p.lat},${p.lon}(Auto)")
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (e: ActivityNotFoundException) { toast("No encontré una app de navegación") }
    }

    fun navigateTo(kind: String) {
        val dest = if (kind == "home") data.home else data.work
        if (dest.isEmpty()) { editDestination(kind); return }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(dest))))
        } catch (e: ActivityNotFoundException) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(dest)))) }
            catch (e2: ActivityNotFoundException) { toast("No encontré una app de navegación") }
        }
    }

    fun editDestination(kind: String) {
        val home = kind == "home"
        form(if (home) "Dirección de Casa" else "Dirección del Trabajo",
            listOf(Field("a", "Dirección", "text", if (home) data.home else data.work))) { v ->
            val a = (v["a"] ?: "").trim()
            if (home) data.home = a else data.work = a
            saveData(); renderMinis(true); true
        }
    }

    fun cycleLimit() {
        val limits = listOf(0, 60, 80, 90, 100, 110)
        val i = limits.indexOf(data.limit)
        data.limit = limits[(i + 1) % limits.size]
        saveData()
        renderMinis(true)
        toast(if (data.limit > 0) "Aviso a ${data.limit} km/h" else "Aviso de velocidad apagado")
    }

    // ================================================================ dock de accesos

    private fun renderDock() {
        dock.removeAllViews()
        dockItems.forEachIndexed { idx, item ->
            val t = tile(item.label, iconFor(item))
            t.setOnClickListener { startTarget(item) }
            t.setOnLongClickListener { itemOptions(idx); true }
            dock.addView(t, tileParams())
        }
        if (dockItems.size < 6) {
            val add = tile("Agregar", vec(R.drawable.ic_plus))
            add.setOnClickListener {
                pickApp("Agregar acceso") { a -> dockItems.add(DockItem(a.label, a.pkg)); store.saveDock(dockItems); renderDock() }
            }
            dock.addView(add, tileParams())
        }
    }

    private fun tileParams(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(100, 92)
        lp.leftMargin = 4; lp.rightMargin = 4
        return lp
    }

    private fun tile(label: String, icon: Drawable?): LinearLayout {
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER
        t.background = C.card(16f)
        val iv = ImageView(this)
        iv.setImageDrawable(icon)
        t.addView(iv, LinearLayout.LayoutParams(40, 40))
        val l = tv(label, 15f, C.INK, gravity = Gravity.CENTER)
        l.maxLines = 1
        l.ellipsize = TextUtils.TruncateAt.END
        t.add(l, MP, WC, 8)
        return t
    }

    private fun vec(res: Int): Drawable? {
        val d = resources.getDrawable(res, theme)?.mutate()
        d?.setColorFilter(C.INK, PorterDuff.Mode.SRC_IN)
        return d
    }

    private fun intentFor(target: String): Intent? = when (target) {
        "@maps" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS)
        "@music" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
        "@dial" -> Intent(Intent.ACTION_DIAL)
        "@settings" -> Intent(Settings.ACTION_SETTINGS)
        else -> null
    }

    private fun radioPackage(): String? =
        listOf("com.syu.radio", "com.android.fmradio", "com.syu.ms")
            .firstOrNull { packageManager.getLaunchIntentForPackage(it) != null }

    private fun iconFor(item: DockItem): Drawable? {
        val pm = packageManager
        try {
            if (item.target == "@apps") return vec(R.drawable.ic_apps)
            if (item.target == "@radio") {
                val p = radioPackage() ?: return vec(R.drawable.ic_plus)
                return pm.getApplicationIcon(p)
            }
            val i = intentFor(item.target)
            if (i != null) return pm.resolveActivity(i, 0)?.loadIcon(pm) ?: vec(R.drawable.ic_plus)
            return pm.getApplicationIcon(item.target)
        } catch (e: Exception) {
            return vec(R.drawable.ic_plus)
        }
    }

    private fun startTarget(item: DockItem) {
        when (item.target) {
            "@apps" -> pickApp("Todas las apps") { a -> startPackage(a.pkg) }
            "@radio" -> {
                val p = radioPackage()
                if (p != null) startPackage(p)
                else pickApp("Elige tu app de radio") { a ->
                    item.target = a.pkg; item.label = "Radio"; store.saveDock(dockItems); renderDock(); startPackage(a.pkg)
                }
            }
            else -> {
                val i = intentFor(item.target)
                if (i != null) {
                    try { startActivity(i) } catch (e: ActivityNotFoundException) {
                        toastLong("No encontré una app para esto. Mantén pulsado para elegir otra.")
                    }
                } else startPackage(item.target)
            }
        }
    }

    private fun startPackage(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) toast("No se pudo abrir esa app") else startActivity(i)
    }

    private fun itemOptions(idx: Int) {
        val item = dockItems[idx]
        AlertDialog.Builder(this)
            .setTitle(item.label)
            .setItems(arrayOf("Elegir otra app", "Quitar")) { _, which ->
                if (which == 0) pickApp("Elegir app para ${item.label}") { a ->
                    item.target = a.pkg; store.saveDock(dockItems); renderDock()
                } else {
                    dockItems.removeAt(idx); store.saveDock(dockItems); renderDock()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun loadApps(): List<AppInfo> {
        val pm = packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .filter { it.pkg != packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    private fun pickApp(title: String, onPick: (AppInfo) -> Unit) {
        val apps = loadApps()
        val adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(position: Int) = apps[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = LinearLayout(this@MainActivity)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(24, 12, 24, 12)
                val iv = ImageView(this@MainActivity)
                iv.setImageDrawable(apps[position].icon)
                row.addView(iv, LinearLayout.LayoutParams(56, 56))
                val t = TextView(this@MainActivity)
                t.text = apps[position].label
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                t.setPadding(24, 0, 0, 0)
                row.addView(t)
                return row
            }
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setAdapter(adapter) { _, which -> onPick(apps[which]) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ================================================================ widgets elegidos

    private fun loadWidgetConfig() {
        val raw = store.widgetsJson ?: return
        try {
            val o = JSONObject(raw)
            val a = o.getString("a"); val b = o.getString("b"); val m1 = o.getString("m1"); val m2 = o.getString("m2")
            if (a in factory.cardKeys && b in factory.cardKeys && a != b &&
                m1 in factory.miniKeys && m2 in factory.miniKeys && m1 != m2) {
                wa = a; wb = b; wm1 = m1; wm2 = m2
            }
        } catch (e: Exception) { }
    }

    private fun saveWidgetConfig() {
        store.widgetsJson = JSONObject().put("a", wa).put("b", wb).put("m1", wm1).put("m2", wm2).toString()
    }

    private fun pickSlot() {
        val names = arrayOf(
            "Tarjeta superior: " + (factory.cardLabels[wa] ?: wa),
            "Tarjeta inferior: " + (factory.cardLabels[wb] ?: wb),
            "Mini izquierdo: " + (factory.miniLabels[wm1] ?: wm1),
            "Mini derecho: " + (factory.miniLabels[wm2] ?: wm2)
        )
        AlertDialog.Builder(this)
            .setTitle("Cambiar widget")
            .setItems(names) { _, i -> pickWidget(i) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun pickWidget(slot: Int) {
        val keys = if (slot < 2) factory.cardKeys else factory.miniKeys
        val labels = keys.map { (if (slot < 2) factory.cardLabels[it] else factory.miniLabels[it]) ?: it }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Elige el widget")
            .setItems(labels) { _, i -> setSlot(slot, keys[i]) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setSlot(slot: Int, key: String) {
        when (slot) {
            0 -> { if (wb == key) wb = wa; wa = key }
            1 -> { if (wa == key) wa = wb; wb = key }
            2 -> { if (wm2 == key) wm2 = wm1; wm1 = key }
            else -> { if (wm1 == key) wm1 = wm2; wm2 = key }
        }
        saveWidgetConfig()
        renderSlots()
        renderMinis(true)
    }

    // ================================================================ menú Personalizar y respaldo

    private fun customizeMenu() {
        AlertDialog.Builder(this)
            .setTitle("Personalizar")
            .setItems(arrayOf(
                "Cambiar widgets",
                "Tema día y noche",
                "Agregar servicio",
                "Ajustar kilometraje total",
                "Destinos Casa y Trabajo",
                "Activar acceso a la música",
                "Respaldo de datos",
                "Repetir animación de la camioneta",
                "Restablecer accesos"
            )) { _, which ->
                when (which) {
                    0 -> pickSlot()
                    1 -> themeMenu()
                    2 -> addServiceForm()
                    3 -> askOdometer()
                    4 -> editDestination("home")
                    5 -> openListenerSettings()
                    6 -> backupMenu()
                    7 -> playIntro()
                    else -> { dockItems = store.defaultDock(); store.saveDock(dockItems); renderDock() }
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun themeMenu() {
        AlertDialog.Builder(this)
            .setTitle("Tema")
            .setItems(arrayOf("Automático (según el sol)", "Día", "Noche")) { _, i ->
                store.themeMode = when (i) { 1 -> "day"; 2 -> "night"; else -> "auto" }
                C.apply(wantDay())
                rebuild()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun backupMenu() {
        AlertDialog.Builder(this)
            .setTitle("Respaldo de datos")
            .setItems(arrayOf("Exportar respaldo (Descargas y USB)", "Importar respaldo")) { _, i ->
                withStorage { if (i == 0) exportBackup() else importBackup() }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun withStorage(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingStorage = action
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 8)
        } else action()
    }

    private fun backupDirs(): List<File> {
        val dirs = mutableListOf<File>()
        dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        File("/storage").listFiles()?.forEach { f ->
            if (f.isDirectory && f.name != "emulated" && f.name != "self" && f.canWrite()) dirs.add(f)
        }
        return dirs
    }

    private fun exportBackup() {
        val json = store.exportAll()
        val ok = mutableListOf<String>()
        for (d in backupDirs()) {
            try { d.mkdirs(); File(d, BACKUP_NAME).writeText(json); ok.add(d.absolutePath) } catch (e: Exception) { }
        }
        toastLong(if (ok.isEmpty()) "No se pudo guardar el respaldo" else "Respaldo guardado en: " + ok.joinToString("  y  "))
    }

    private fun importBackup() {
        val f = backupDirs().map { File(it, BACKUP_NAME) }.filter { it.exists() }.maxByOrNull { it.lastModified() }
        if (f == null) { toastLong("No encontré $BACKUP_NAME en Descargas ni en una memoria USB"); return }
        AlertDialog.Builder(this)
            .setTitle("Importar respaldo")
            .setMessage("Esto reemplaza los datos actuales con los de:\n" + f.absolutePath)
            .setPositiveButton("Importar") { _, _ ->
                if (store.importAll(f.readText())) {
                    data = store.loadData(); services = store.loadServices(); dockItems = store.loadDock()
                    loadWidgetConfig()
                    C.apply(wantDay())
                    rebuild()
                    toast("Respaldo importado")
                } else toast("El archivo no es un respaldo válido")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    companion object {
        const val BACKUP_NAME = "outlander-respaldo.json"
    }
}
