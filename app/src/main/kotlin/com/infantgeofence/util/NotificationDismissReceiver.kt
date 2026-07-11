package com.infantgeofence.util

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the "Dismiss" action from a call-style notification
 * and cancels it by its ID.
 */
class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra("notif_id", -1)
        if (notifId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notifId)
        }
    }
}
