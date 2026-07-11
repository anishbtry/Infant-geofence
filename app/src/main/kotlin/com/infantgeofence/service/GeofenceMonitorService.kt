package com.infantgeofence.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.infantgeofence.InfantGeoFenceApp
import com.infantgeofence.data.model.FenceStatus
import com.infantgeofence.data.model.GeoAlert
import com.infantgeofence.data.repository.GeofenceEngine
import com.infantgeofence.data.repository.LocationRepository
import com.infantgeofence.util.GeofencePrefs
import com.infantgeofence.util.NotificationHelper
import kotlinx.coroutines.*
import org.osmdroid.util.GeoPoint

/**
 * Foreground service that polls the tracker every 5 s.
 * Runs even when the app is in background or killed.
 * Fires status-bar notifications via NotificationHelper on zone transitions.
 */
class GeofenceMonitorService : Service() {

    private val scope      = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repo       = LocationRepository()
    private var lastStatus = FenceStatus.UNKNOWN

    // Debounce: don't re-fire the same transition within 30 s
    private var lastNotifTime = 0L
    private val DEBOUNCE_MS   = 30_000L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, NotificationHelper.buildServiceNotification(this))
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        scope.launch {
            while (true) {
                try { poll() } catch (e: Exception) { e.printStackTrace() }
                delay(2_000)
            }
        }
    }

    private suspend fun poll() {
        val loc = repo.getLocation(InfantGeoFenceApp.DEVICE_ID) ?: return
        if (!loc.success) return

        val polygon = GeofencePrefs.loadPolygon(this, InfantGeoFenceApp.DEVICE_ID)
        if (polygon.size < 3) return

        val pt     = GeoPoint(loc.latitude, loc.longitude)
        val inside = GeofenceEngine.isInside(pt, polygon)
        val status = GeofenceEngine.toFenceStatus(inside)

        val now = System.currentTimeMillis()
        val isTransition = status != lastStatus && lastStatus != FenceStatus.UNKNOWN
        val pastDebounce = (now - lastNotifTime) > DEBOUNCE_MS

        if (isTransition && pastDebounce) {
            val alertType = if (status == FenceStatus.OUTSIDE) "outside" else "inside"

            // Build a synthetic GeoAlert so NotificationHelper can produce rich content
            val syntheticAlert = GeoAlert(
                id        = now.toInt(),
                deviceId  = InfantGeoFenceApp.DEVICE_ID,
                childName = loc.childName,
                alertType = alertType,
                latitude  = loc.latitude,
                longitude = loc.longitude,
                isRead    = 0,
                timestamp = ""
            )
            NotificationHelper.sendAlertNotification(this, syntheticAlert)
            lastNotifTime = now
        }

        lastStatus = status
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
