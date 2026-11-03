package com.dude.grokhome

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var tts: TextToSpeech
    private val worker = Executors.newSingleThreadExecutor()
    private val bg = Color.rgb(10, 18, 32)
    private val panel = Color.rgb(23, 37, 58)
    private val blue = Color.rgb(87, 190, 255)
    private val requestVoice = 501
    private val requestMic = 502

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences("dude_private", MODE_PRIVATE)
        tts = TextToSpeech(this, this)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 18, 28, 18)
            setBackgroundColor(bg)
        }
        val header = TextView(this).apply {
            text = "DUDE   •   YOUR HOME, YOUR RULES"
            textSize = 18f; setTextColor(blue); gravity = Gravity.CENTER
        }
        root.addView(header, LinearLayout.LayoutParams(-1, 48))
        val clock = TextView(this).apply {
            text = "HEY THERE 👋"
            textSize = 34f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        root.addView(clock, LinearLayout.LayoutParams(-1, 78))
        status = TextView(this).apply {
            text = "Ready when you are. Try a command or ask me something."
            textSize = 16f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(-1, 52))
        transcript = TextView(this).apply {
            text = "“I’m Dude. Your lights obey me. Your snacks remain your responsibility.”"
            textSize = 19f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(20, 20, 20, 20); setBackgroundColor(panel)
        }
        root.addView(transcript, LinearLayout.LayoutParams(-1, 0, 1f).apply { setMargins(0, 16, 0, 16) })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val talk = makeButton("🎙  TALK TO DUDE") { startListening() }
        val settings = makeButton("⚙  SETTINGS") { settingsDialog() }
        buttons.addView(talk, LinearLayout.LayoutParams(0, 64, 1f).apply { setMargins(4, 4, 8, 4) })
        buttons.addView(settings, LinearLayout.LayoutParams(0, 64, 1f).apply { setMargins(8, 4, 4, 4) })
        root.addView(buttons)
        val note = TextView(this).apply {
            text = "FIRST BUILD • Tap to talk • Local quick replies • Grok AI when configured"
            textSize = 11f; setTextColor(Color.GRAY); gravity = Gravity.CENTER
        }
        root.addView(note, LinearLayout.LayoutParams(-1, 32))
        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 14f; setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(30, 105, 160)); setOnClickListener { action() }
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestMic); return
        }
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Dude")
            }
            startActivityForResult(intent, requestVoice)
        } catch (_: Exception) {
            say("I can't find a speech recognition service on this tablet. Check Google voice services.")
        }
    }

    @Deprecated("Deprecated Android callback retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestVoice && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) handleText(spoken)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == requestMic && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
        else if (requestCode == requestMic) say("No microphone permission. I promise I wasn't eavesdropping anyway.")
    }

    private fun handleText(raw: String) {
        val text = raw.trim()
        transcript.text = "You: $text"
        val lower = text.lowercase(Locale.ROOT)
        val local = when {
            lower.contains("hello") || lower.contains("hey dude") ->
                "Well hello there. I was just standing here looking decorative."
            lower.contains("your name") -> "I'm Dude. Like Alexa, but with better banter and fewer weird shopping suggestions."
            lower.contains("joke") -> "I told my smart lights a joke. They were delighted. It was a very bright audience."
            lower.contains("time") -> "I can tell time, but I refuse to judge how late you're staying up."
            lower.contains("turn on") || lower.contains("turn off") || lower.contains("lights") ->
                "I heard a smart-home command. Device control isn't connected yet, so nobody's lights changed. I'm enthusiastic, not magical."
            else -> null
        }
        if (local != null) { say(local); return }
        val key = prefs.getString("xai_key", "").orEmpty()
        if (key.isBlank()) {
            say("Good question. Add your xAI API key in Settings and I'll put my giant brain to work.")
            return
        }
        status.text = "Dude is thinking…"
        worker.execute {
            val answer = askGrok(key, text)
            runOnUiThread { say(answer) }
        }
    }

    private fun askGrok(key: String, prompt: String): String {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("https://api.x.ai/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 20000; readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $key")
            }
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content",
                    "You are Dude, a funny, quirky, warm smart-home assistant. Be witty but not mean. Keep spoken answers concise. Never claim to have controlled a device unless a real integration confirms it."))
                .put(JSONObject().put("role", "user").put("content", prompt))
            val body = JSONObject().put("model", "grok-3-mini").put("messages", messages).put("temperature", 0.8)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            if (code !in 200..299) "Grok returned an error: ${json.optString("error", response).take(240)}"
            else json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } catch (e: Exception) {
            "I couldn't reach Grok. Check the internet and API key. ${e.message ?: ""}"
        } finally { connection?.disconnect() }
    }

    private fun say(message: String) {
        status.text = "DUDE SAYS"
        transcript.text = "Dude: $message"
        if (::tts.isInitialized) tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "dude_reply")
    }

    private fun settingsDialog() {
        val input = EditText(this).apply {
            hint = "Paste xAI API key"; setText(prefs.getString("xai_key", ""))
            inputType = 129
        }
        AlertDialog.Builder(this).setTitle("Dude settings")
            .setMessage("Your API key is stored privately on this tablet. Never share it or commit it to GitHub.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("xai_key", input.text.toString().trim()).apply()
                say("Settings saved. My brain cells are now on standby.")
            }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) tts.language = Locale.US
    }

    override fun onDestroy() {
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        worker.shutdownNow()
        super.onDestroy()
    }
}
