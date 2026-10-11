package com.bikal.paymentspeaker

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.util.Locale

// Listens for Gmail notifications of Fonepay payment emails and speaks the amount.
class PaymentListener : NotificationListenerService(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pending = mutableListOf<String>()
    private val recent = LinkedHashMap<String, Long>() // dedupe key -> time spoken

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            ttsReady = true
            pending.forEach { say(it) }
            pending.clear()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in SOURCE_APPS) return
        val n = sbn.notification
        // Gmail bundles emails under a summary notification; read only the individual one
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val e = n.extras
        val text = listOf(
            e.getCharSequence(Notification.EXTRA_TITLE),
            e.getCharSequence(Notification.EXTRA_TEXT),
            e.getCharSequence(Notification.EXTRA_BIG_TEXT)
        ).joinToString(" ") { it?.toString() ?: "" }

        // SMS alerts only count when they come from the bank's sender ID, so a fake SMS can't trigger it
        if (sbn.packageName in SMS_APPS && SMS_SENDER !in text) return

        val amount = AMOUNT.find(text)?.groupValues?.get(1)?.replace(",", "")
            ?.removeSuffix(".00")?.removeSuffix(".0") ?: return
        val rrn = RRN.find(text)?.groupValues?.get(1)

        // Gmail may re-post the same notification; speak each payment once
        val key = rrn ?: "${sbn.key}|$amount"
        val now = System.currentTimeMillis()
        recent.entries.removeAll { now - it.value > 10 * 60_000 }
        if (recent.containsKey(key)) return
        recent[key] = now

        say("Received $amount rupees")
    }

    private fun say(msg: String) {
        if (!ttsReady) { pending += msg; return }
        tts?.speak(msg, TextToSpeech.QUEUE_ADD, null, msg + System.nanoTime())
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        private val SMS_APPS = setOf(
            "com.google.android.apps.messaging",   // Google Messages
            "com.samsung.android.messaging",       // Samsung Messages
            "com.android.mms"                      // Xiaomi / other stock SMS apps
        )
        private val SOURCE_APPS = SMS_APPS + setOf(
            "com.google.android.gm",      // Gmail
            "com.google.android.gm.lite"  // Gmail Go
        )
        private const val SMS_SENDER = "Prime_Alert"
        // Email: "Received Rs.250 from 985****734 for RRN 558645888219"
        // SMS:   "Transaction success for Rs.25.0 from 984****929 via Fonepay QR for RRN 17593094s1Y2."
        private val AMOUNT = Regex("""(?:Received|Transaction success for)\s+Rs\.?\s?([\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        private val RRN = Regex("""RRN\s*([0-9A-Za-z]+)""", RegexOption.IGNORE_CASE)
    }
}
