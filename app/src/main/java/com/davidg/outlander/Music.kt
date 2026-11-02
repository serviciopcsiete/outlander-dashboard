package com.davidg.outlander

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService

/**
 * Servicio vacío. Su único propósito es que el usuario pueda darle "acceso a notificaciones",
 * lo que Android exige para leer qué canción se está reproduciendo.
 */
class MediaListener : NotificationListenerService()

/** Lee la canción activa de cualquier reproductor y controla la reproducción. */
class MusicController(private val ctx: Context, private val onChange: () -> Unit) {
    private val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val comp = ComponentName(ctx, MediaListener::class.java)
    private var controller: MediaController? = null

    var hasAccess = false
        private set
    var title = ""
        private set
    var artist = ""
        private set
    var playing = false
        private set
    private var duration = 0L

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> attach(list) }
    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { refresh() }
        override fun onMetadataChanged(metadata: MediaMetadata?) { refresh() }
    }

    private fun accessGranted(): Boolean =
        Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
            ?.contains(ctx.packageName) == true

    fun start() {
        hasAccess = accessGranted()
        if (!hasAccess) { onChange(); return }
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, comp)
            attach(msm.getActiveSessions(comp))
        } catch (e: SecurityException) {
            hasAccess = false
            onChange()
        }
    }

    fun stop() {
        try { msm.removeOnActiveSessionsChangedListener(sessionsListener) } catch (e: Exception) { }
        controller?.unregisterCallback(cb)
        controller = null
    }

    private fun attach(list: List<MediaController>?) {
        controller?.unregisterCallback(cb)
        controller = list?.firstOrNull()
        controller?.registerCallback(cb)
        refresh()
    }

    private fun refresh() {
        val c = controller
        if (c == null) {
            title = ""; artist = ""; playing = false; duration = 0L
        } else {
            val md = c.metadata
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        }
        onChange()
    }

    /** Progreso de 0 a 1, o -1 si no hay duración. */
    fun progress(): Float {
        val st = controller?.playbackState ?: return -1f
        if (duration <= 0L) return -1f
        var pos = st.position
        if (st.state == PlaybackState.STATE_PLAYING) {
            pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        return (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    fun hasSession(): Boolean = controller != null
    fun playPause() {
        val c = controller ?: return
        if (playing) c.transportControls.pause() else c.transportControls.play()
    }
    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
}
