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

/**
 * Foreground speech loop. Android's installed speech provider recognizes the
 * phrase "Hey Dude" and then captures a command. This is not a dedicated offline
 * acoustic wake-word model; availability and battery behavior depend on Android.
 */
class WakeWordService : Service() {
    companion object {
        const val ACTION_WAKE_COMMAND = "com.dude.grokhome.WAKE_COMMAND"
        const val EXTRA_COMMAND = "command"
        const val WAKE_ONLY = "__WAKE_ONLY__"
        private const val CHANNEL_ID = "dude_voice"
        private const val NOTIFICATION_ID = 741
        private const val TAG = "DudeVoice"
        private const val COMMAND_WINDOW_MS = 9000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var armed = false
    private var stopped = false
    private var restarting = false
    private val disarm = Runnable { armed = false }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            send("DUDE_ERROR:No speech recognition service is installed.")
            stopSelf()
        } else listen()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun process(raw: String) {
        val phrase = raw.trim()
        if (phrase.isBlank()) return
        if (armed) {
            armed = false
            handler.removeCallbacks(disarm)
            send(phrase)
            return
        }
        val match = Regex("\\bhey[ ,]*dude\\b", RegexOption.IGNORE_CASE).find(phrase) ?: return
        val remainder = phrase.substring(match.range.last + 1).trim().trim(',', '.', ' ')
        if (remainder.isNotBlank()) send(remainder)
        else {
            armed = true
            handler.removeCallbacks(disarm)
            handler.postDelayed(disarm, COMMAND_WINDOW_MS)
            send(WAKE_ONLY)
        }
    }

    private fun send(command: String) {
        sendBroadcast(Intent(ACTION_WAKE_COMMAND).setPackage(packageName).putExtra(EXTRA_COMMAND, command))
    }

    private fun scheduleRestart(delay: Long) {
        if (stopped || restarting) return
        restarting = true
        handler.postDelayed({ restarting = false; listen() }, delay)
    }

    private fun listen() {
        if (stopped) return
        try {
            recognizer?.destroy()
            val speech = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    Log.w(TAG, "Speech recognizer error=$error")
                    recognizer?.destroy()
                    recognizer = null
                    scheduleRestart(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1600L else 850L)
                }
                override fun onResults(results: android.os.Bundle?) {
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    process(heard)
                    recognizer?.destroy()
                    recognizer = null
                    scheduleRestart(350L)
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1100L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
            }
            speech.startListening(intent)
        } catch (error: Exception) {
            Log.e(TAG, "Could not start recognizer", error)
            recognizer?.destroy()
            recognizer = null
            scheduleRestart(2000L)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "DUDE voice listener", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("DUDE voice is active")
            .setContentText("Waiting for Hey Dude while the app is running")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
