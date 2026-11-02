package com.davidg.outlander

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder

fun hasLocationPermission(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 23 ||
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Estado en vivo compartido entre el servicio de GPS y la pantalla. */
object Live {
    @Volatile var speed = 0f
    @Volatile var lat = 0.0
    @Volatile var lon = 0.0
    @Volatile var bearing = 0f
    @Volatile var hasFix = false
    @Volatile var simulating = false
}

fun startTracking(ctx: Context) {
    if (!hasLocationPermission(ctx)) return
    val i = Intent(ctx, TrackingService::class.java)
    try {
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
    } catch (e: Exception) { }
}

/**
 * Corre aunque el launcher no esté al frente (por ejemplo mientras usas la navegación),
 * para que el kilometraje, el recorrido y el lugar de estacionamiento se sigan guardando.
 */
class TrackingService : Service(), LocationListener {
    private lateinit var store: Store
    private lateinit var lm: LocationManager
    private var prev: Location? = null
    private var lastT = 0L
    private var lastParkSave = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        goForeground()
        requestUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try { lm.removeUpdates(this) } catch (e: Exception) { }
        super.onDestroy()
    }

    private fun goForeground() {
        val ch = "tracking"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(ch, "Registro del recorrido", NotificationManager.IMPORTANCE_LOW))
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, ch) else Notification.Builder(this)
        val n = b.setContentTitle("Launcher Outlander")
            .setContentText("Registrando kilometraje y recorrido")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        startForeground(1, n)
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        if (!hasLocationPermission(this)) return
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this) } catch (e: Exception) { }
    }

    override fun onLocationChanged(loc: Location) {
        if (Live.simulating) return
        val now = System.currentTimeMillis()
        val kmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
        val v = if (kmh < 3f) 0f else kmh
        Live.speed = v
        Live.lat = loc.latitude
        Live.lon = loc.longitude
        if (loc.hasBearing()) Live.bearing = loc.bearing
        Live.hasFix = true

        val p = prev
        var dKm = 0.0
        if (p != null && v > 0f && loc.accuracy <= 50f) dKm = p.distanceTo(loc) / 1000.0
        val dt = if (lastT > 0L) minOf(now - lastT, 5000L) else 0L
        lastT = now
        store.recordMotion(v, dt, dKm)

        // El último punto donde se estaba moviendo es donde quedó estacionado el carro.
        if (v > 0f && now - lastParkSave > 5000L) {
            store.savePark(loc.latitude, loc.longitude, now)
            lastParkSave = now
        }
        prev = loc
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}

/** Arranca el registro del recorrido al encender la radio. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        startTracking(context)
    }
}
