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
import android.util.Log
import java.util.Locale

/** Foreground voice loop. Recreates SpeechRecognizer after each utterance/error
 * because Android providers can leave a reused recognizer busy on Android 10. */
class WakeWordService : Service() {
    companion object {
        const val ACTION_WAKE_COMMAND = "com.dude.grokhome.WAKE_COMMAND"
        const val EXTRA_COMMAND = "command"
        const val WAKE_ONLY = "__WAKE_ONLY__"
        private const val CHANNEL_ID = "dude_always_listening"
        private const val NOTIFICATION_ID = 741
        private const val TAG = "DudeWake"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var armedForCommand = false
    private var stopped = false
    private var restarting = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            sendCommand("DUDE_ERROR:No speech recognition service is installed.")
            stopSelf()
            return
        }
        startRecognition()
    }

    private fun processUtterance(raw: String) {
        val heard = raw.trim()
        if (heard.isBlank()) return
        if (armedForCommand) {
            armedForCommand = false
            sendCommand(heard)
            return
        }
        val match = Regex("\\bhey[, ]*dude\\b", RegexOption.IGNORE_CASE).find(heard) ?: return
        val command = heard.substring(match.range.last + 1).trim().trim(',', '.', ' ')
        if (command.isNotBlank()) sendCommand(command) else {
            armedForCommand = true
            sendCommand(WAKE_ONLY)
        }
    }

    private fun sendCommand(command: String) {
        sendBroadcast(Intent(ACTION_WAKE_COMMAND).setPackage(packageName).putExtra(EXTRA_COMMAND, command))
    }

    private fun restart(delay: Long = 500L) {
        if (stopped || restarting) return
        restarting = true
        handler.postDelayed({
            restarting = false
            startRecognition()
        }, delay)
    }

    private fun startRecognition() {
        if (stopped) return
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        Log.w(TAG, "Speech recognition error=$error")
                        recognizer?.destroy()
                        recognizer = null
                        restart(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500L else 700L)
                    }
                    override fun onResults(results: android.os.Bundle?) {
                        val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        processUtterance(heard)
                        recognizer?.destroy()
                        recognizer = null
                        restart(300L)
                    }
                    override fun onPartialResults(partialResults: android.os.Bundle?) {}
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                }
                sr.startListening(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start recognizer", e)
            recognizer?.destroy()
            recognizer = null
            restart(2000L)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "DUDE voice listener", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("DUDE voice is active")
            .setContentText("Listening for “Hey Dude” while DUDE is open")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
