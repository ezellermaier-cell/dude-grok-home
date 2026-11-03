package com.dude.grokhome

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Continuous, foreground speech recognizer. It listens for "Hey Dude", then
 * forwards the next utterance to the app. Uses Android's installed speech
 * recognition provider; no DUDE/Grok API key is required.
 */
class WakeWordService : Service() {
    companion object {
        const val ACTION_WAKE_COMMAND = "com.dude.grokhome.WAKE_COMMAND"
        const val EXTRA_COMMAND = "command"
        const val WAKE_ONLY = "__WAKE_ONLY__"
        private const val CHANNEL_ID = "dude_always_listening"
        private const val NOTIFICATION_ID = 741
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var armedForCommand = false
    private var listening = false
    private var restarting = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf()
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) { listening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { listening = false }
                override fun onError(error: Int) { listening = false; scheduleRestart(900) }
                override fun onResults(results: android.os.Bundle?) {
                    listening = false
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    processUtterance(heard)
                    scheduleRestart(350)
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
        }
        startRecognition()
    }

    private fun processUtterance(raw: String) {
        val heard = raw.trim()
        if (heard.isBlank()) return
        val normalized = heard.lowercase(Locale.ROOT)
        if (armedForCommand) {
            armedForCommand = false
            sendCommand(heard)
            return
        }
        val wake = Regex("\\bhey[, ]*dude\\b", RegexOption.IGNORE_CASE)
        val match = wake.find(normalized) ?: return
        val command = heard.substring(match.range.last + 1).trim().trim(',', '.', ' ')
        if (command.isNotBlank()) sendCommand(command)
        else {
            armedForCommand = true
            sendCommand(WAKE_ONLY)
        }
    }

    private fun sendCommand(command: String) {
        sendBroadcast(Intent(ACTION_WAKE_COMMAND).setPackage(packageName).putExtra(EXTRA_COMMAND, command))
    }

    private fun scheduleRestart(delay: Long) {
        if (restarting) return
        restarting = true
        handler.postDelayed({
            restarting = false
            startRecognition()
        }, delay)
    }

    private fun startRecognition() {
        val sr = recognizer ?: return
        if (listening) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            sr.startListening(intent)
        } catch (_: Exception) {
            scheduleRestart(1500)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "DUDE always listening", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("DUDE is listening")
            .setContentText("Say “Hey Dude” to give a voice command")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
