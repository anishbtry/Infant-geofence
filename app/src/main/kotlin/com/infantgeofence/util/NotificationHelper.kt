package com.infantgeofence.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.infantgeofence.InfantGeoFenceApp
import com.infantgeofence.R
import com.infantgeofence.data.model.GeoAlert
import com.infantgeofence.ui.CallActivity
import com.infantgeofence.ui.MainActivity

object NotificationHelper {

    private const val ID_OUTSIDE     = 2001
    private const val ID_INSIDE      = 2002
    private const val ID_SOS         = 2003
    private const val ID_LOW_BATTERY = 2004

    // ── Main entry point ──────────────────────────────────────
    fun sendAlertNotification(ctx: Context, alert: GeoAlert) {
        if (alert.isCritical) sendCallStyleNotification(ctx, alert)
        else                  sendInfoNotification(ctx, alert)
    }

    // ── Call-style notification (outside / SOS) ───────────────
    private fun sendCallStyleNotification(ctx: Context, alert: GeoAlert) {
        val (title, _, iconRes, notifId) = alertContent(alert)

        // Full-screen / tap → CallActivity
        val callIntent = Intent(ctx, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("child_name", alert.childName)
            putExtra("alert_type", alert.alertType)
        }
        val fullScreenPi = PendingIntent.getActivity(
            ctx, notifId, callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "View Alert" → MainActivity Alerts tab
        val viewIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("nav_to", "alerts")
        }
        val viewPi = PendingIntent.getActivity(
            ctx, notifId + 100, viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Dismiss" → broadcast receiver cancels the notification
        val dismissIntent = Intent(ctx, NotificationDismissReceiver::class.java).apply {
            putExtra("notif_id", notifId)
        }
        val dismissPi = PendingIntent.getBroadcast(
            ctx, notifId + 200, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // androidx.core.app.Person — works on all API levels
        val caller = Person.Builder()
            .setName(alert.childName)
            .setIcon(IconCompat.createWithResource(ctx, iconRes))
            .setImportant(true)
            .build()

        val builder = NotificationCompat.Builder(ctx, InfantGeoFenceApp.CHANNEL_CALL)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(alert.childName)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVibrate(longArrayOf(0, 500, 300, 500, 300, 500))
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)

        // CallStyle API available via NotificationCompat on API 21+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(caller, dismissPi, viewPi)
                    .setAnswerButtonColorHint(0xFF2ECC71.toInt())
                    .setDeclineButtonColorHint(0xFFE74C3C.toInt())
            )
        } else {
            // Fallback: manual action buttons for API < 31
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_check_circle, "View Alert", viewPi
                ).build()
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_call_end, "Dismiss", dismissPi
                ).build()
            )
        }

        notify(ctx, notifId, builder)
    }

    // ── Info notification (inside / low_battery) ──────────────
    private fun sendInfoNotification(ctx: Context, alert: GeoAlert) {
        val (title, body, iconRes, notifId) = alertContent(alert)

        val tapPi = PendingIntent.getActivity(
            ctx, notifId,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("nav_to", "alerts")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        notify(ctx, notifId,
            NotificationCompat.Builder(ctx, InfantGeoFenceApp.CHANNEL_INFO)
                .setSmallIcon(iconRes)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(tapPi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        )
    }

    // ── Foreground service pin ─────────────────────────────────
    fun buildServiceNotification(ctx: Context) =
        NotificationCompat.Builder(ctx, InfantGeoFenceApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_fence)
            .setContentTitle("Infant GeoFence Active")
            .setContentText("Monitoring child's location…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    // ── Legacy string overload (GeofenceMonitorService) ───────
    fun sendGeofenceAlert(ctx: Context, title: String, message: String) {
        val fakeAlert = GeoAlert(
            id        = System.currentTimeMillis().toInt(),
            deviceId  = "",
            childName = message.substringBefore(" has"),
            alertType = if (title.contains("left", ignoreCase = true)) "outside" else "inside",
            isRead    = 0,
            timestamp = ""
        )
        sendAlertNotification(ctx, fakeAlert)
    }

    // ── Helpers ───────────────────────────────────────────────
    private fun notify(ctx: Context, id: Int, builder: NotificationCompat.Builder) {
        try {
            NotificationManagerCompat.from(ctx).notify(id, builder.build())
        } catch (se: SecurityException) {
            // POST_NOTIFICATIONS not yet granted
        }
    }

    private data class Content(
        val title: String,
        val body: String,
        val iconRes: Int,
        val notifId: Int
    )

    private fun alertContent(alert: GeoAlert): Content = when (alert.alertType) {
        "outside" -> Content(
            title   = "⚠️ ${alert.childName} left the safe zone!",
            body    = "${alert.childName} has moved outside the geofence.",
            iconRes = R.drawable.ic_warning,
            notifId = ID_OUTSIDE
        )
        "sos" -> Content(
            title   = "🆘 SOS — ${alert.childName}!",
            body    = "${alert.childName} pressed the SOS button!",
            iconRes = R.drawable.ic_sos,
            notifId = ID_SOS
        )
        "low_battery" -> Content(
            title   = "🔋 Low battery — ${alert.childName}'s tracker",
            body    = "Charge the tracker soon to keep monitoring active.",
            iconRes = R.drawable.ic_battery_alert,
            notifId = ID_LOW_BATTERY
        )
        "inside" -> Content(
            title   = "✅ ${alert.childName} returned to safe zone",
            body    = "${alert.childName} is back inside the designated area.",
            iconRes = R.drawable.ic_check_circle,
            notifId = ID_INSIDE
        )
        else -> Content(
            title   = "Geofence Alert — ${alert.childName}",
            body    = alert.typeLabel,
            iconRes = R.drawable.ic_info,
            notifId = System.currentTimeMillis().toInt()
        )
    }
}
