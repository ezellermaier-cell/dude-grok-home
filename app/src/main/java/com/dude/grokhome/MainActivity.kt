package com.dude.grokhome

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
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
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var clockView: TextView
    private lateinit var weatherView: TextView
    private lateinit var newsView: TextView
    private lateinit var dateView: TextView
    private lateinit var newsContainer: LinearLayout
    private lateinit var clockCard: LinearLayout
    private lateinit var weatherCard: TextView
    private lateinit var voiceOrb: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var tts: TextToSpeech
    private val worker = Executors.newSingleThreadExecutor()
    private val bg = Color.rgb(10, 18, 32)
    private val panel = Color.rgb(23, 37, 58)
    private val blue = Color.rgb(87, 190, 255)
    private val requestMic = 502
    private var wakeReceiverRegistered = false
    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val phrase = intent?.getStringExtra(WakeWordService.EXTRA_COMMAND).orEmpty()
            if (phrase == WakeWordService.WAKE_ONLY) say("I'm listening. What's up?") else if (phrase.isNotBlank()) handleText(phrase)
        }
    }

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
        registerWakeReceiver()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startWakeWordService()
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestMic)
        mainHandler.post(object : Runnable { override fun run() { updateClock(); mainHandler.postDelayed(this, 1000) } })
        mainHandler.postDelayed(object : Runnable { override fun run() { refreshDashboard(); mainHandler.postDelayed(this, 30 * 60 * 1000L) } }, 30 * 60 * 1000L)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 10, 18, 10)
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(3, 9, 24), Color.rgb(5, 22, 43), Color.rgb(2, 8, 22))
            )
        }
        fun card(color: Int = Color.rgb(8, 27, 52)): android.graphics.drawable.GradientDrawable =
            android.graphics.drawable.GradientDrawable().apply {
                setColor(color); cornerRadius = 24f; setStroke(2, Color.rgb(0, 165, 245))
            }
        fun label(text: String, size: Float = 15f) = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("✦  DUDE", 25f), LinearLayout.LayoutParams(0, 48, 1f))
        status = label("● LISTENING FOR  “HEY DUDE”", 12f).apply { setTextColor(Color.rgb(70, 245, 174)) }
        header.addView(status)
        root.addView(header)

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; weightSum = 10f }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clockCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(12, 8, 12, 8)
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(11, 58, 112), Color.rgb(28, 31, 84), Color.rgb(8, 26, 54))
            ).apply { cornerRadius = 26f; setStroke(2, Color.rgb(0, 185, 255)) }
        }
        clockView = label("12:00", 56f).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setShadowLayer(20f, 0f, 0f, Color.rgb(0, 160, 255))
        }
        dateView = label("MONDAY  •  NOVEMBER 3", 15f).apply { gravity = Gravity.CENTER; setTextColor(Color.rgb(190, 225, 255)) }
        clockCard.addView(clockView)
        clockCard.addView(dateView)
        left.addView(clockCard, LinearLayout.LayoutParams(-1, 160).apply { setMargins(4, 4, 4, 8) })
        weatherCard = label("☀   LIVE WEATHER\nSet your city in Settings", 19f).apply {
            gravity = Gravity.CENTER; setPadding(14, 8, 14, 8); background = card(Color.rgb(8, 40, 72))
        }
        left.addView(weatherCard, LinearLayout.LayoutParams(-1, 0, 1f).apply { setMargins(4, 4, 4, 4) })
        top.addView(left, LinearLayout.LayoutParams(0, 0, 3.2f).apply { setMargins(0, 4, 8, 4) })

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = card(Color.rgb(4, 18, 42)); setPadding(8, 10, 8, 10)
        }
        voiceOrb = label("🎙", 58f).apply {
            gravity = Gravity.CENTER; setTextColor(Color.rgb(70, 225, 255))
            setShadowLayer(22f, 0f, 0f, Color.rgb(0, 130, 255))
        }
        center.addView(voiceOrb, LinearLayout.LayoutParams(-1, 100))
        center.addView(label("HEY DUDE", 24f).apply { gravity = Gravity.CENTER; setTextColor(Color.WHITE) })
        center.addView(label("I'm listening…", 14f).apply { gravity = Gravity.CENTER; setTextColor(Color.rgb(90, 230, 190)) })
        center.addView(label("Say a command naturally", 12f).apply { gravity = Gravity.CENTER; setTextColor(Color.LTGRAY) })
        top.addView(center, LinearLayout.LayoutParams(0, -1, 2.2f).apply { setMargins(4, 4, 8, 4) })

        val newsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(12, 10, 12, 8); background = card(Color.rgb(5, 25, 49))
        }
        newsPanel.addView(label("🌐  GOOGLE NEWS  /  TOP STORIES", 17f), LinearLayout.LayoutParams(-1, 34))
        newsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        newsPanel.addView(newsContainer, LinearLayout.LayoutParams(-1, 0, 1f))
        top.addView(newsPanel, LinearLayout.LayoutParams(0, -1, 4.6f).apply { setMargins(4, 4, 0, 4) })
        root.addView(top, LinearLayout.LayoutParams(-1, 0, 1f))

        val homeHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        homeHeader.addView(label("⌂  SMART HOME", 20f), LinearLayout.LayoutParams(0, 42, 1f))
        homeHeader.addView(makeButton("VIEW ALL DEVICES  ›") { discoverDevices() })
        root.addView(homeHeader)
        val deviceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val deviceLabels = listOf("💡  LIGHTS", "🌡  THERMOSTAT", "🧺  WASHER", "🤖  SHARK VACUUM")
        deviceLabels.forEach { title ->
            val tile = label(title + "\nConnect Home Assistant to show status", 13f).apply {
                gravity = Gravity.CENTER; setPadding(6, 8, 6, 8); background = card(Color.rgb(10, 36, 66))
            }
            deviceRow.addView(tile, LinearLayout.LayoutParams(0, 76, 1f).apply { setMargins(4, 3, 4, 3) })
        }
        root.addView(deviceRow)

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        nav.addView(makeButton("⌂  HOME") { refreshDashboard() }, LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(3, 3, 3, 3) })
        nav.addView(makeButton("▦  DEVICES") { discoverDevices() }, LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(3, 3, 3, 3) })
        nav.addView(makeButton("◉  ASSISTANT") { launchGoogleAssistant() }, LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(3, 3, 3, 3) })
        nav.addView(makeButton("⚙  SETTINGS") { settingsDialog() }, LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(3, 3, 3, 3) })
        root.addView(nav)
        setContentView(root)
    }

    private fun registerWakeReceiver() {
        if (wakeReceiverRegistered) return
        val filter = IntentFilter(WakeWordService.ACTION_WAKE_COMMAND)
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(wakeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(wakeReceiver, filter)
        wakeReceiverRegistered = true
    }

    private fun startWakeWordService() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestMic)
            return
        }
        try {
            val intent = Intent(this, WakeWordService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            status.text = "● ALWAYS LISTENING  •  SAY HEY DUDE"
        } catch (e: Exception) {
            say("I couldn't start the microphone service: " + (e.message ?: "permission required"))
        }
    }

    private fun updateClock() {
        if (::clockView.isInitialized) {
            clockView.text = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
            dateView.text = SimpleDateFormat("EEEE  •  MMMM d, yyyy", Locale.getDefault()).format(Date()).uppercase(Locale.getDefault())
        }
    }

    private fun refreshDashboard() {
        if (!::weatherCard.isInitialized || !::newsContainer.isInitialized) return
        worker.execute {
            val city = prefs.getString("weather_city", "").orEmpty()
            val weather = if (city.isBlank()) "☀   LIVE WEATHER\\nSet your city in Settings" else "☀   " + DashboardData.weather(city)
            val stories = DashboardData.newsItems()
            runOnUiThread {
                weatherCard.text = weather
                newsContainer.removeAllViews()
                if (stories.isEmpty()) {
                    newsContainer.addView(TextView(this).apply {
                        text = "News headlines are temporarily unavailable."
                        setTextColor(Color.LTGRAY); textSize = 14f; gravity = Gravity.CENTER
                    })
                } else stories.take(4).forEachIndexed { index, story ->
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(4, 5, 4, 5) }
                    val thumb = ImageView(this).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(18, 66, 104)); cornerRadius = 14f }
                        setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                    row.addView(thumb, LinearLayout.LayoutParams(104, 70).apply { setMargins(0, 0, 10, 0) })
                    val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
                    textCol.addView(TextView(this).apply {
                        text = story.title; textSize = 13f; setTextColor(Color.WHITE); maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                    })
                    textCol.addView(TextView(this).apply {
                        text = if (story.source.isBlank()) "GOOGLE NEWS  •  TOP STORY" else story.source.uppercase(Locale.getDefault())
                        textSize = 10f; setTextColor(Color.rgb(90, 205, 255))
                    })
                    row.addView(textCol, LinearLayout.LayoutParams(0, -1, 1f))
                    newsContainer.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
                    if (story.imageUrl.isNotBlank()) loadNewsImage(story.imageUrl, thumb)
                    row.setOnClickListener {
                        try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://news.google.com/search?q=" + android.net.Uri.encode(story.title)))) } catch (_: Exception) {}
                    }
                    if (index < minOf(stories.size, 4) - 1) newsContainer.addView(View(this).apply { setBackgroundColor(Color.rgb(25, 86, 130)) }, LinearLayout.LayoutParams(-1, 1))
                }
            }
        }
    }

    private fun loadNewsImage(imageUrl: String, target: ImageView) {
        worker.execute {
            try {
                val connection = java.net.URL(imageUrl).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 7000; connection.readTimeout = 7000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 DUDE")
                val bitmap = connection.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
                connection.disconnect()
                if (bitmap != null) runOnUiThread { target.setImageBitmap(bitmap) }
            } catch (_: Exception) { }
        }
    }

    private fun launchGoogleAssistant() {
        try {
            startActivity(Intent(Intent.ACTION_ASSIST))
        } catch (_: Exception) {
            say("The Google assistant app is not available as the default assistant on this tablet. Set Google or Gemini as your default digital assistant in Android settings.")
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
                                val service = when (domain) {
                                    "cover" -> if (current == "open") "close_cover" else "open_cover"
                                    else -> if (current == "on") "turn_off" else "turn_on"
                                }
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
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == requestMic && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startWakeWordService()
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
        say("I can handle smart-home commands and basic local requests right now. Cloud AI is disabled; no API key is used.")
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
        val city = field("Weather city (example: Newark, NJ)", prefs.getString("weather_city", "").orEmpty())
        val url = field("Home Assistant URL (example: http://192.168.1.20:8123)", prefs.getString("ha_url", "").orEmpty())
        val token = field("Home Assistant long-lived access token", secret = true)
        val light = field("Light entity ID (example: light.living_room)", prefs.getString("ha_light_entity", "").orEmpty())
        val plug = field("Smart plug entity ID (example: switch.plug)", prefs.getString("ha_switch_entity", "").orEmpty())
        layout.addView(TextView(this).apply { text = "Home Assistant is free/open-source software and must run on an always-on computer or supported server. Add your brands inside Home Assistant first. Secrets are encrypted on this tablet."; textSize = 12f })
        AlertDialog.Builder(this).setTitle("DUDE connections")
            .setView(ScrollView(this).apply { addView(layout) })
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("weather_city", city.text.toString().trim())
                    .putString("ha_url", url.text.toString().trim())
                    .putString("ha_light_entity", light.text.toString().trim())
                    .putString("ha_switch_entity", plug.text.toString().trim()).apply()
                if (token.text.toString().isNotBlank()) saveSecret("ha_token", token.text.toString().trim())
                say("Connections saved. Updating your dashboard.")
                refreshDashboard()
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

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) tts.language = Locale.US
    }

    override fun onDestroy() {
        if (wakeReceiverRegistered) { unregisterReceiver(wakeReceiver); wakeReceiverRegistered = false }
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        worker.shutdownNow()
        super.onDestroy()
    }
}
