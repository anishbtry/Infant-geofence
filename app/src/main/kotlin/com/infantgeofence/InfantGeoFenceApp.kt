package com.infantgeofence

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import org.osmdroid.config.Configuration

class InfantGeoFenceApp : Application() {

    override fun onCreate() {
        super.onCreate()

        Configuration.getInstance().apply {
            userAgentValue = packageName
            load(this@InfantGeoFenceApp,
                getSharedPreferences("osm_prefs", MODE_PRIVATE))
        }

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            // ── IMPORTANT: Android caches channel settings after first creation.
            // Delete the old silent channel so it gets recreated with the ringtone.
            nm.deleteNotificationChannel("geofence_call_alerts")

            // Ringtone URI — uses the device's default ringtone (not notification sound)
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            // AudioAttributes tell Android this sound is a call, not a notification
            val callAudioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            // 1. Call-style channel — HIGH importance + ringtone sound
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_CALL,
                    "Geofence Call Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description     = "Call-style alerts when child leaves the safe zone or triggers SOS"
                    setSound(ringtoneUri, callAudioAttrs)   // ← sets the ringtone
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
                    enableLights(true)
                    lightColor      = 0xFFFF4444.toInt()
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            )

            // 2. Info alerts — default notification sound
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_INFO,
                    "Info Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Low-battery warnings and safe-zone entry confirmations"
                }
            )

            // 3. Background service pin — silent
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SERVICE,
                    "Monitoring Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps geofence monitoring running in background"
                }
            )

            // Legacy alias kept for back-compat
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_GEOFENCE,
                    "Safety Alerts (legacy)",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    companion object {
        const val CHANNEL_CALL     = "geofence_call_alerts_v2"  // ← bumped to v2 to force fresh creation
        const val CHANNEL_GEOFENCE = "geofence_alerts"
        const val CHANNEL_INFO     = "info_alerts"
        const val CHANNEL_SERVICE  = "monitoring_service"
        const val DEVICE_ID        = "ESP32_001"
        const val BASE_URL         = "http://10.81.154.46/infant_geofence/api/"
    }
}
