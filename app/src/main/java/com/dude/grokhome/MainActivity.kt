package com.dude.grokhome

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
    private lateinit var clockView: TextView
    private lateinit var weatherView: TextView
    private lateinit var newsView: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
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
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences("dude_private", MODE_PRIVATE)
        tts = TextToSpeech(this, this)
        buildUi()
        updateClock()
        refreshDashboard()
        mainHandler.post(object : Runnable { override fun run() { updateClock(); mainHandler.postDelayed(this, 1000) } })
        mainHandler.postDelayed(object : Runnable { override fun run() { refreshDashboard(); mainHandler.postDelayed(this, 30 * 60 * 1000L) } }, 30 * 60 * 1000L)
        if (loadApiKey().isBlank()) settingsDialog()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(26, 14, 26, 14)
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(5, 12, 28), Color.rgb(10, 27, 48), Color.rgb(7, 15, 32))
            )
        }
        fun panelBackground(): android.graphics.drawable.GradientDrawable =
            android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(14, 31, 52))
                cornerRadius = 22f
                setStroke(2, Color.rgb(35, 112, 158))
            }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = TextView(this).apply {
            text = "D U D E  /  SMART HOME"
            textSize = 17f; setTextColor(Color.rgb(91, 220, 255))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        }
        top.addView(brand, LinearLayout.LayoutParams(0, 46, 1f))
        val live = TextView(this).apply {
            text = "● SYSTEM ONLINE"
            textSize = 11f; setTextColor(Color.rgb(91, 235, 174)); gravity = Gravity.CENTER
        }
        top.addView(live, LinearLayout.LayoutParams(-2, 46))
        root.addView(top)

        clockView = TextView(this).apply {
            text = "12:00 PM"
            textSize = 38f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            setShadowLayer(18f, 0f, 0f, Color.rgb(0, 155, 255))
        }
        root.addView(clockView, LinearLayout.LayoutParams(-1, 62))

        val cards = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        weatherView = TextView(this).apply {
            text = "◉  LIVE WEATHER\nConnecting to the atmosphere…"
            textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(18, 14, 18, 14); background = panelBackground()
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }
        newsView = TextView(this).apply {
            text = "GLOBAL FEED  /  GOOGLE NEWS\nFetching latest headlines…"
            textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
            setPadding(22, 14, 18, 14); background = panelBackground()
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }
        cards.addView(weatherView, LinearLayout.LayoutParams(0, 176, 1f).apply { setMargins(4, 8, 8, 8) })
        cards.addView(newsView, LinearLayout.LayoutParams(0, 176, 2.2f).apply { setMargins(8, 8, 4, 8) })
        root.addView(cards)

        status = TextView(this).apply {
            text = "READY  •  DUDE IS LISTENING"
            textSize = 12f; setTextColor(Color.rgb(91, 220, 255)); gravity = Gravity.CENTER
            letterSpacing = 0.08f
        }
        root.addView(status, LinearLayout.LayoutParams(-1, 34))

        transcript = TextView(this).apply {
            text = "“Welcome back. Your home is looking sharp. What are we doing?”"
            textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(20, 10, 20, 10); background = panelBackground()
        }
        root.addView(transcript, LinearLayout.LayoutParams(-1, 0, 1f).apply { setMargins(4, 4, 4, 10) })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val talk = makeButton("🎙  TALK") { startListening() }
        val devices = makeButton("⌂  SMART HOME") { discoverDevices() }
        val settings = makeButton("⚙  SETTINGS") { settingsDialog() }
        buttons.addView(talk, LinearLayout.LayoutParams(0, 58, 1f).apply { setMargins(4, 4, 4, 4) })
        buttons.addView(devices, LinearLayout.LayoutParams(0, 58, 1.3f).apply { setMargins(4, 4, 4, 4) })
        buttons.addView(settings, LinearLayout.LayoutParams(0, 58, 1f).apply { setMargins(4, 4, 4, 4) })
        root.addView(buttons)
        val note = TextView(this).apply {
            text = "DUDE OS  •  LIVE GOOGLE NEWS  •  VOICE CONTROL"
            textSize = 10f; setTextColor(Color.rgb(100, 145, 174)); gravity = Gravity.CENTER
            letterSpacing = 0.12f
        }
        root.addView(note, LinearLayout.LayoutParams(-1, 24))
        setContentView(root)
    }

    private fun updateClock() {
        if (::clockView.isInitialized) clockView.text = SimpleDateFormat("h:mm a  •  EEEE, MMM d", Locale.getDefault()).format(Date())
    }

    private fun refreshDashboard() {
        if (!::weatherView.isInitialized || !::newsView.isInitialized) return
        worker.execute {
            val city = prefs.getString("weather_city", "").orEmpty()
            val weather = if (city.isBlank()) "◉  LIVE WEATHER\\nSet a city in Settings" else "◉  LIVE WEATHER\\n" + DashboardData.weather(city)
            val news = DashboardData.headlines()
            runOnUiThread { weatherView.text = weather; newsView.text = news }
        }
    }
    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 14f; setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(30, 105, 160)); setOnClickListener { action() }
    }

    private fun discoverDevices() {
        val haUrl = prefs.getString("ha_url", "").orEmpty()
        val haToken = loadSecret("ha_token")
        if (haUrl.isBlank() || haToken.isBlank()) {
            say("Connect Home Assistant in Settings first. Add your Smart Life, LG, Sensi and Shark integrations there, then I can discover the entities they expose.")
            return
        }
        status.text = "Looking for your connected home devices…"
        worker.execute {
            try {
                val states = JSONArray(HomeAssistantClient(haUrl, haToken).states())
                val rows = ArrayList<String>()
                val ids = ArrayList<String>()
                val names = ArrayList<String>()
                val statesById = HashMap<String, String>()
                val supported = setOf("light", "switch", "fan", "cover", "climate", "vacuum", "sensor", "binary_sensor", "lock", "media_player")
                for (i in 0 until states.length()) {
                    val item = states.optJSONObject(i) ?: continue
                    val entity = item.optString("entity_id")
                    if (entity.substringBefore(".") !in supported) continue
                    val attrs = item.optJSONObject("attributes") ?: JSONObject()
                    val name = attrs.optString("friendly_name", entity)
                    val state = item.optString("state", "unknown")
                    ids.add(entity); names.add(name); statesById[entity] = state
                    rows.add("$name  •  $state\n$entity")
                }
                runOnUiThread {
                    if (rows.isEmpty()) { say("Home Assistant connected, but it returned no supported device entities. Check your integrations."); return@runOnUiThread }
                    status.text = "Found ${rows.size} Home Assistant entities"
                    AlertDialog.Builder(this).setTitle("Your connected devices")
                        .setItems(rows.toTypedArray()) { _, which ->
                            val entity = ids[which]
                            val domain = entity.substringBefore(".")
                            if (domain in setOf("light", "switch", "fan", "cover")) {
                                val current = statesById[entity].orEmpty()
                                val service = if (current == "on" || current == "open") "turn_off" else "turn_on"
                                worker.execute {
                                    try {
                                        HomeAssistantClient(haUrl, haToken).callService(domain, service, entity)
                                        runOnUiThread { say("${if (service == "turn_on") "Turning on" else "Turning off"} ${names[which]}.") }
                                    } catch (e: Exception) { runOnUiThread { say("Could not control ${names[which]}: ${e.message ?: "connection error"}") } }
                                }
                            } else if (domain == "vacuum") {
                                AlertDialog.Builder(this).setTitle(names[which])
                                    .setItems(arrayOf("Start cleaning", "Pause", "Return to dock")) { _, action ->
                                        val service = when (action) { 0 -> "start"; 1 -> "pause"; else -> "return_to_base" }
                                        worker.execute {
                                            try { HomeAssistantClient(haUrl, haToken).callService("vacuum", service, entity); runOnUiThread { say("${names[which]}: $service command sent.") } }
                                            catch (e: Exception) { runOnUiThread { say("Vacuum command failed: ${e.message ?: "error"}") } }
                                        }
                                    }.setNegativeButton("Close", null).show()
                            } else if (domain == "climate") {
                                val temp = EditText(this).apply { hint = "Target temperature °F"; inputType = 8194 }
                                AlertDialog.Builder(this).setTitle("Set ${names[which]} temperature").setView(temp)
                                    .setPositiveButton("Set") { _, _ ->
                                        val value = temp.text.toString().toFloatOrNull()
                                        if (value == null) say("Enter a valid temperature.") else worker.execute {
                                            try { HomeAssistantClient(haUrl, haToken).callServiceWithData("climate", "set_temperature", entity, JSONObject().put("temperature", value)); runOnUiThread { say("Thermostat target set to ${value.toInt()} degrees.") } }
                                            catch (e: Exception) { runOnUiThread { say("Thermostat command failed: ${e.message ?: "error"}") } }
                                        }
                                    }.setNegativeButton("Cancel", null).show()
                            } else if (domain == "lock") {
                                val service = if (statesById[entity] == "locked") "unlock" else "lock"
                                worker.execute {
                                    try { HomeAssistantClient(haUrl, haToken).callService("lock", service, entity); runOnUiThread { say("${names[which]}: $service command sent.") } }
                                    catch (e: Exception) { runOnUiThread { say("Lock command failed: ${e.message ?: "error"}") } }
                                }
                            } else say("${names[which]} is reporting ${statesById[entity]}.")
                        }.setNegativeButton("Close", null).show()
                }
            } catch (e: Exception) { runOnUiThread { say("I could not discover devices. Check the Home Assistant address, token and Wi-Fi. ${e.message ?: ""}") } }
        }
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
        val local = LocalCommandRouter().route(text,
            prefs.getString("ha_light_entity", "").orEmpty(),
            prefs.getString("ha_switch_entity", "").orEmpty())
        if (local != null) {
            if (local.domain != null && local.service != null && local.entityId != null) {
                val haUrl = prefs.getString("ha_url", "").orEmpty()
                val haToken = loadSecret("ha_token")
                if (haUrl.isBlank() || haToken.isBlank()) {
                    say("Add your Home Assistant URL and token in Settings to control devices.")
                    return
                }
                status.text = "Connecting to your smart home…"
                worker.execute {
                    try {
                        HomeAssistantClient(haUrl, haToken).callService(local.domain, local.service, local.entityId)
                        runOnUiThread { say(local.reply + " Done.") }
                    } catch (e: Exception) {
                        runOnUiThread { say("Home Assistant error: " + (e.message ?: "connection error")) }
                    }
                }
            } else say(local.reply)
            return
        }
        val key = loadApiKey()
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
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0) }
        fun field(label: String, value: String = "", secret: Boolean = false): EditText {
            layout.addView(TextView(this).apply { text = label; textSize = 13f })
            return EditText(this).apply {
                hint = label
                setText(value)
                if (secret) inputType = 129
                layout.addView(this)
            }
        }
        val api = field("Optional xAI API key (separate API billing)", secret = true)
        val city = field("Weather city (example: Newark, NJ)", prefs.getString("weather_city", "").orEmpty())
        val url = field("Home Assistant URL (example: http://192.168.1.20:8123)", prefs.getString("ha_url", "").orEmpty())
        val token = field("Home Assistant long-lived access token", secret = true)
        val light = field("Light entity ID (example: light.living_room)", prefs.getString("ha_light_entity", "").orEmpty())
        val plug = field("Smart plug entity ID (example: switch.plug)", prefs.getString("ha_switch_entity", "").orEmpty())
        layout.addView(TextView(this).apply { text = "Home Assistant is free/open-source software and must run on an always-on computer or supported server. Add your brands inside Home Assistant first. Secrets are encrypted on this tablet."; textSize = 12f })
        AlertDialog.Builder(this).setTitle("DUDE connections")
            .setView(ScrollView(this).apply { addView(layout) })
            .setPositiveButton("Save") { _, _ ->
                if (api.text.toString().isNotBlank()) saveSecret("xai", api.text.toString().trim())
                prefs.edit().putString("weather_city", city.text.toString().trim())
                    .putString("ha_url", url.text.toString().trim())
                    .putString("ha_light_entity", light.text.toString().trim())
                    .putString("ha_switch_entity", plug.text.toString().trim()).apply()
                if (token.text.toString().isNotBlank()) saveSecret("ha_token", token.text.toString().trim())
                say("Connections saved. Updating your dashboard.")
                refreshDashboard()
            }
            .setNeutralButton("Remove AI key") { _, _ ->
                prefs.edit().remove("xai_cipher").remove("xai_iv").apply()
                say("AI key removed. Local commands can still work.")
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "dude_local_secrets"
        val existing = store.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    private fun saveSecret(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name + "_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(name + "_cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun loadSecret(name: String): String {
        return try {
            val iv = prefs.getString(name + "_iv", null) ?: return ""
            val encrypted = prefs.getString(name + "_cipher", null) ?: return ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }

    private fun saveApiKey(value: String) = saveSecret("xai", value)
    private fun loadApiKey(): String = loadSecret("xai")
    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) tts.language = Locale.US
    }

    override fun onDestroy() {
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        worker.shutdownNow()
        super.onDestroy()
    }
}
