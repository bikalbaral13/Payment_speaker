package com.bikal.paymentspeaker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

// One-screen setup: grant notification access, allow background running, test the voice.
class MainActivity : Activity() {

    private lateinit var status: TextView
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { tts?.language = Locale.US }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 96, 48, 48)
        }
        layout.addView(TextView(this).apply {
            text = "Payment Speaker"; textSize = 26f; setPadding(0, 0, 0, 24)
        })
        status = TextView(this).apply { textSize = 18f; setPadding(0, 0, 0, 48) }
        layout.addView(status)
        layout.addView(button("1. Allow notification access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        layout.addView(button("2. Allow running in background") {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")))
        })
        layout.addView(button("3. Test voice") {
            tts?.speak("Received 250 rupees", TextToSpeech.QUEUE_FLUSH, null, "test")
        })
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        val access = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
        val battery = getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(packageName)
        status.text = "Notification access: ${if (access) "ON" else "OFF"}\n" +
            "Background allowed: ${if (battery) "YES" else "NO"}"
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
