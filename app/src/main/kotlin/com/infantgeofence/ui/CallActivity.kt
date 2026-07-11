package com.infantgeofence.ui

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.infantgeofence.R
import com.infantgeofence.databinding.ActivityCallBinding

/**
 * Full-screen call-style alert activity.
 * Shown when the user taps "View" on a call notification, or
 * when the full-screen intent fires on the lock screen.
 *
 * Receives extras:
 *   "child_name"  – String
 *   "alert_type"  – "outside" | "sos" | "low_battery" | "inside"
 *   "nav_to"      – "map" | "alerts"
 */
class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen without requiring unlock
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(KeyguardManager::class.java)
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val childName = intent.getStringExtra("child_name") ?: "Your child"
        val alertType = intent.getStringExtra("alert_type") ?: "outside"

        // Populate UI
        binding.tvCallName.text = childName
        binding.tvCallStatus.text = when (alertType) {
            "outside"     -> "⚠️ Has left the safe zone!"
            "sos"         -> "🆘 SOS Emergency Alert!"
            "low_battery" -> "🔋 Tracker battery is low"
            "inside"      -> "✅ Returned to safe zone"
            else          -> "Geofence Alert"
        }
        binding.ivCallIcon.setImageResource(
            when (alertType) {
                "outside"     -> R.drawable.ic_warning
                "sos"         -> R.drawable.ic_sos
                "low_battery" -> R.drawable.ic_battery_alert
                else          -> R.drawable.ic_check_circle
            }
        )

        // "View on Map" button → open MainActivity on Map tab
        binding.btnAccept.setOnClickListener {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("nav_to", "map") // FIX: was "alerts"
            }
            startActivity(mainIntent)
            finish()
        }

        // "Dismiss" button → just close
        binding.btnDecline.setOnClickListener { finish() }
    }
}