package com.dude.grokhome

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import android.text.InputType
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * DUDE dashboard rebuilt with platform Android views only (no extra UI dependencies).
 * All interaction and voice replies remain in this Activity.
 */
class MainActivity : Activity() {
    private val navy = Color.rgb(4, 10, 25)
    private val panel = Color.rgb(9, 27, 51)
    private val cyan = Color.rgb(44, 205, 255)
    private val mint = Color.rgb(73, 238, 181)
    private lateinit var prefs: SharedPreferences
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var weather: TextView
    private lateinit var reply: TextView
    private lateinit var voiceStatus: TextView
    private lateinit var newsList: LinearLayout
    private lateinit var deviceList: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var root: LinearLayout
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(3)
    private var receiverOn = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val command = intent?.getStringExtra(WakeWordService.EXTRA_COMMAND).orEmpty()
            when {
                command == WakeWordService.WAKE_ONLY -> speak("I'm listening. What's up?")
                command.startsWith("DUDE_ERROR:") -> setReply(command.removePrefix("DUDE_ERROR:"))
                command.isNotBlank() -> handleCommand(command)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        prefs = getSharedPreferences("dude_private", MODE_PRIVATE)
        buildDashboard()
        updateClock()
        loadDashboard()
        registerVoiceReceiver()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice()
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 502)
        main.post(object : Runnable {
            override fun run() { updateClock(); main.postDelayed(this, 1000) }
        })
        main.postDelayed(object : Runnable {
            override fun run() { loadDashboard(); main.postDelayed(this, 30 * 60 * 1000L) }
        }, 30 * 60 * 1000L)
    }

    private fun bg(color: Int, stroke: Int = cyan, radius: Float = 24f) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(color, Color.rgb(6, 16, 35))
    ).apply { cornerRadius = radius; setStroke(2, stroke) }

    private fun text(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun buildDashboard() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 12, 18, 10)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(2, 7, 20), Color.rgb(5, 22, 45), Color.rgb(10, 7, 32)))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        header.addView(text("✦  D U D E", 25f, cyan, true), LinearLayout.LayoutParams(0, 48, 1f))
        voiceStatus = text("● VOICE STARTING", 12f, mint, true)
        header.addView(voiceStatus)
        root.addView(header)

        scroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val hero = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val timeCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = bg(Color.rgb(13, 46, 91)); setPadding(10, 12, 10, 12) }
        clock = text("--:--", 58f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setShadowLayer(24f, 0f, 0f, cyan) }
        date = text("LOADING DATE", 14f, Color.rgb(193, 225, 255), true).apply { gravity = Gravity.CENTER }
        timeCard.addView(clock); timeCard.addView(date)
        left.addView(timeCard, LinearLayout.LayoutParams(-1, 142).apply { bottomMargin = 10 })
        weather = text("◉  WEATHER\nChoose a city in Settings", 18f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setPadding(12, 12, 12, 12); background = bg(Color.rgb(7, 48, 73), mint) }
        left.addView(weather, LinearLayout.LayoutParams(-1, 118))
        hero.addView(left, LinearLayout.LayoutParams(0, -1, 3f).apply { rightMargin = 10 })

        val assistant = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = bg(Color.rgb(10, 28, 64)); setPadding(8, 8, 8, 8) }
        val orb = text("◉", 64f, cyan, true).apply { gravity = Gravity.CENTER; setShadowLayer(28f, 0f, 0f, cyan) }
        assistant.addView(orb, LinearLayout.LayoutParams(-1, 90))
        assistant.addView(text("HEY DUDE", 23f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        assistant.addView(text("ALWAYS-ON VOICE", 12f, mint, true).apply { gravity = Gravity.CENTER; setPadding(0, 5, 0, 8) })
        assistant.addView(button("TAP TO SPEAK") { startVoice(); speak("I'm listening. Say your command.") })
        hero.addView(assistant, LinearLayout.LayoutParams(0, -1, 2.2f).apply { rightMargin = 10 })

        val news = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = bg(Color.rgb(7, 24, 48)); setPadding(12, 10, 12, 10) }
        news.addView(text("◈  LIVE GOOGLE NEWS", 17f, cyan, true), LinearLayout.LayoutParams(-1, 34))
        newsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        news.addView(newsList, LinearLayout.LayoutParams(-1, 0, 1f))
        hero.addView(news, LinearLayout.LayoutParams(0, -1, 4.8f))
        content.addView(hero, LinearLayout.LayoutParams(-1, 0, 300f))

        val replyCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = bg(Color.rgb(8, 23, 43), Color.rgb(105, 110, 255)); setPadding(14, 8, 14, 8) }
        replyCard.addView(text("DUDE  /  RESPONSE", 12f, cyan, true))
        reply = text("Hey! I'm ready. Say “Hey Dude” to get started.", 17f)
        reply.maxLines = 2
        replyCard.addView(reply)
        content.addView(replyCard, LinearLayout.LayoutParams(-1, 76).apply { topMargin = 10; bottomMargin = 10 })

        val deviceHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        deviceHeader.addView(text("⌂  SMART HOME", 20f, Color.WHITE, true), LinearLayout.LayoutParams(0, 42, 1f))
        deviceHeader.addView(button("ALL DEVICES  ›") { discoverDevices() })
        content.addView(deviceHeader)
        deviceList = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("💡\nLIGHTS", "🌡\nSENSI", "🧺\nLG WASHER", "🤖\nSHARK VACUUM").forEach { name ->
            val tile = text(name + "\nTap to connect", 13f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER; background = bg(Color.rgb(8, 36, 63)); setPadding(4, 8, 4, 8)
            }
            deviceList.addView(tile, LinearLayout.LayoutParams(0, 82, 1f).apply { setMargins(4, 3, 4, 3) })
        }
        content.addView(deviceList)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nav.addView(button("⌂ HOME") { scroll.smoothScrollTo(0, 0) }, LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(3, 3, 3, 3) })
        nav.addView(button("⌂ DEVICES") { discoverDevices() }, LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(3, 3, 3, 3) })
        nav.addView(button("⚙ SETTINGS") { settings() }, LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(3, 3, 3, 3) })
        root.addView(nav)
        setContentView(root)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 12f; isAllCaps = false; setTextColor(Color.WHITE)
        background = GradientDrawable().apply { setColor(Color.rgb(15, 100, 157)); cornerRadius = 16f; setStroke(1, cyan) }
        setOnClickListener { action() }
    }

    private fun updateClock() {
        if (!::clock.isInitialized) return
        clock.text = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
        date.text = SimpleDateFormat("EEEE  •  MMMM d", Locale.getDefault()).format(Date()).uppercase(Locale.getDefault())
    }

    private fun loadDashboard() {
        if (!::weather.isInitialized) return
        io.execute {
            val city = prefs.getString("weather_city", "").orEmpty()
            val w = if (city.isBlank()) "◉  WEATHER\nSet a city in Settings" else "◉  " + DashboardData.weather(city)
            val stories = DashboardData.newsItems()
            runOnUiThread {
                weather.text = w
                newsList.removeAllViews()
                if (stories.isEmpty()) newsList.addView(text("News feed unavailable. Check Wi-Fi.", 14f, Color.LTGRAY))
                stories.take(4).forEachIndexed { i, story ->
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(3, 4, 3, 4) }
                    val image = ImageView(this).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        background = GradientDrawable().apply { setColor(Color.rgb(22, 69, 105)); cornerRadius = 13f }
                        setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                    row.addView(image, LinearLayout.LayoutParams(108, 66).apply { rightMargin = 9 })
                    val title = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
                    title.addView(text(story.title, 13f, Color.WHITE, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
                    title.addView(text(if (story.source.isBlank()) "GOOGLE NEWS" else story.source.uppercase(Locale.getDefault()), 10f, cyan, true))
                    row.addView(title, LinearLayout.LayoutParams(0, -1, 1f))
                    row.setOnClickListener { speak(story.title) }
                    newsList.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
                    if (story.imageUrl.isNotBlank()) loadImage(story.imageUrl, image)
                    if (i < 3) newsList.addView(View(this).apply { setBackgroundColor(Color.rgb(27, 76, 116)) }, LinearLayout.LayoutParams(-1, 1))
                }
            }
        }
    }

    private fun loadImage(url: String, target: ImageView) {
        io.execute {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 7000; c.readTimeout = 7000
                c.setRequestProperty("User-Agent", "Mozilla/5.0")
                val bitmap = c.inputStream.use { BitmapFactory.decodeStream(it) }
                c.disconnect()
                if (bitmap != null) runOnUiThread { target.setImageBitmap(bitmap) }
            } catch (_: Exception) { }
        }
    }

    private fun registerVoiceReceiver() {
        if (receiverOn) return
        val filter = IntentFilter(WakeWordService.ACTION_WAKE_COMMAND)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
        receiverOn = true
    }

    private fun startVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 502); return
        }
        try {
            val intent = Intent(this, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            voiceStatus.text = "● MICROPHONE SERVICE ACTIVE"
        } catch (e: Exception) {
            voiceStatus.text = "● VOICE ERROR"
            setReply("Could not start microphone: " + (e.message ?: "permission required"))
        }
    }

    private fun speak(message: String) {
        setReply(message)
        try {
            val tts = android.speech.tts.TextToSpeech(this) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    ttsRef?.language = Locale.US
                    ttsRef?.speak(message, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "dude")
                }
            }
            ttsRef = tts
        } catch (_: Exception) { }
    }
    private var ttsRef: android.speech.tts.TextToSpeech? = null

    private fun setReply(message: String) {
        if (::reply.isInitialized) reply.text = message
    }

    private fun handleCommand(utterance: String) {
        val q = utterance.lowercase(Locale.ROOT)
        if (q.contains("what time")) { speak("It's " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())); return }
        if (q.contains("what's the date") || q.contains("what is the date")) { speak(date.text.toString()); return }
        if (q.contains("weather") || q.contains("whether") || q.contains("temperature outside") || q.contains("is it raining") || q.contains("how hot is it") || q.contains("how cold is it")) {
            speakWeather()
            return
        }
        if (q.contains("tell me a joke")) { speak("I asked the washing machine for a joke. It said it was on a spin cycle."); return }
        if (q.contains("what can you do")) { speak("I can show your dashboard, read headlines, check weather, and control devices connected to Home Assistant."); return }
        if (q.contains("news") || q.contains("headlines")) { speak(newsList.takeIf { it.childCount > 0 }?.let { "Here are the latest Google News headlines on your screen." } ?: "The news feed is not loaded yet."); return }

        val router = LocalCommandRouter()
        val result = router.route(utterance, prefs.getString("ha_light_entity", "").orEmpty(), prefs.getString("ha_switch_entity", "").orEmpty())
        if (result != null) {
            if (result.domain != null && result.service != null && result.entityId != null) {
                callHomeAssistant(result.domain, result.service, result.entityId, result.reply)
            } else speak(result.reply)
            return
        }
        when {
            q.contains("turn on") && (q.contains("light") || q.contains("lamp")) -> controlDomain("light", "turn_on", "Turning on the lights.")
            q.contains("turn off") && (q.contains("light") || q.contains("lamp")) -> controlDomain("light", "turn_off", "Turning off the lights.")
            q.contains("turn on") && q.contains("plug") -> controlDomain("switch", "turn_on", "Turning on the plug.")
            q.contains("turn off") && q.contains("plug") -> controlDomain("switch", "turn_off", "Turning off the plug.")
            else -> speak("I didn't catch a supported command. Try asking for the time, weather, news, or a smart-home action.")
        }
    }

    /**
     * Speak weather as a sentence, never read dashboard decoration aloud.
     * The old implementation passed the entire card to TTS, so Android
     * pronounced the leading ◉ icon as "circle".
     */
    private fun speakWeather() {
        val raw = weather.text.toString()
            .replace("◉", "")
            .replace("•", ",")
            .replace("\\n", " ")
            .replace(Regex("[^\\p{L}\\p{N}°%,.\\- ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (raw.isBlank() || raw.contains("set a city", ignoreCase = true)) {
            speak("I can check that. First, choose your city in settings.")
            return
        }
        if (raw.contains("unavailable", ignoreCase = true) || raw.contains("city not found", ignoreCase = true)) {
            speak("I can't get the weather right now. Check your internet connection and city setting.")
            return
        }
        val parts = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val place = parts.firstOrNull().orEmpty()
        val conditions = parts.drop(1).joinToString(". ")
        speak(if (conditions.isBlank()) "Here's the weather for $place." else "Here's the weather in $place. $conditions.")
    }

    private fun controlDomain(domain: String, service: String, replyText: String) {
        val entity = if (domain == "light") prefs.getString("ha_light_entity", "").orEmpty() else prefs.getString("ha_switch_entity", "").orEmpty()
        if (entity.isBlank()) { speak("Set the $domain entity in DUDE settings, or use View All Devices to choose a device."); return }
        callHomeAssistant(domain, service, entity, replyText)
    }

    private fun callHomeAssistant(domain: String, service: String, entity: String, replyText: String) {
        val base = prefs.getString("ha_url", "").orEmpty()
        val token = loadSecret("ha_token")
        if (base.isBlank() || token.isBlank()) { speak("Connect Home Assistant in DUDE settings first. No paid AI key is needed."); return }
        setReply("Sending command to your smart home…")
        io.execute {
            try {
                HomeAssistantClient(base, token).callService(domain, service, entity)
                runOnUiThread { speak(replyText) }
            } catch (e: Exception) { runOnUiThread { speak("Smart-home command failed: " + (e.message ?: "connection error")) } }
        }
    }

    private fun discoverDevices() {
        val base = prefs.getString("ha_url", "").orEmpty()
        val token = loadSecret("ha_token")
        if (base.isBlank() || token.isBlank()) { speak("Add your Home Assistant URL and token in DUDE settings first."); settings(); return }
        setReply("Discovering your connected devices…")
        io.execute {
            try {
                val arr = JSONArray(HomeAssistantClient(base, token).states())
                val entities = ArrayList<String>()
                val labels = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("entity_id")
                    if (id.substringBefore(".") !in setOf("light", "switch", "fan", "cover", "climate", "vacuum", "lock", "media_player")) continue
                    entities.add(id)
                    labels.add(item.optJSONObject("attributes")?.optString("friendly_name", id) + "  •  " + item.optString("state") + "\n" + id)
                }
                runOnUiThread {
                    if (labels.isEmpty()) { speak("No controllable entities found in Home Assistant."); return@runOnUiThread }
                    AlertDialog.Builder(this).setTitle("SMART HOME DEVICES")
                        .setItems(labels.toTypedArray()) { _, which -> showDeviceActions(entities[which], labels[which].substringBefore("  •")) }
                        .setNegativeButton("Close", null).show()
                }
            } catch (e: Exception) { runOnUiThread { speak("Can't reach Home Assistant. Check its address, token and Wi-Fi.") } }
        }
    }

    private fun showDeviceActions(entity: String, name: String) {
        val domain = entity.substringBefore(".")
        val choices = when (domain) {
            "light", "switch", "fan" -> arrayOf("Turn on", "Turn off")
            "cover" -> arrayOf("Open", "Close")
            "vacuum" -> arrayOf("Start cleaning", "Pause", "Return to dock")
            "lock" -> arrayOf("Lock", "Unlock")
            else -> arrayOf("Refresh status")
        }
        AlertDialog.Builder(this).setTitle(name).setItems(choices) { _, which ->
            val service = when (domain) {
                "light", "switch", "fan" -> if (which == 0) "turn_on" else "turn_off"
                "cover" -> if (which == 0) "open_cover" else "close_cover"
                "vacuum" -> arrayOf("start", "pause", "return_to_base")[which]
                "lock" -> if (which == 0) "lock" else "unlock"
                else -> null
            }
            if (service == null) speak("Status refresh is available from Home Assistant.") else callHomeAssistant(domain, service, entity, "$name: command sent.")
        }.setNegativeButton("Cancel", null).show()
    }

    private fun settings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 8, 20, 0) }
        fun input(label: String, value: String = "", type: Int = InputType.TYPE_CLASS_TEXT): EditText {
            box.addView(text(label, 13f, cyan, true))
            return EditText(this).apply { setText(value); inputType = type; setSingleLine(true); box.addView(this) }
        }
        val cityInput = input("Weather city", prefs.getString("weather_city", "").orEmpty())
        val urlInput = input("Home Assistant URL", prefs.getString("ha_url", "").orEmpty())
        val tokenInput = input("Home Assistant access token (stored encrypted)", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val lightInput = input("Light entity ID", prefs.getString("ha_light_entity", "").orEmpty())
        val switchInput = input("Smart plug entity ID", prefs.getString("ha_switch_entity", "").orEmpty())
        box.addView(text("No xAI/OpenAI API key is used. Home Assistant is optional and needs its own access token to control devices.", 12f, Color.LTGRAY))
        AlertDialog.Builder(this).setTitle("DUDE SETTINGS")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("SAVE") { _, _ ->
                prefs.edit().putString("weather_city", cityInput.text.toString().trim())
                    .putString("ha_url", urlInput.text.toString().trim())
                    .putString("ha_light_entity", lightInput.text.toString().trim())
                    .putString("ha_switch_entity", switchInput.text.toString().trim()).apply()
                if (tokenInput.text.isNotBlank()) saveSecret("ha_token", tokenInput.text.toString().trim())
                loadDashboard(); speak("Settings saved.")
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun secretKey(): javax.crypto.SecretKey {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "dude_local_secrets"
        (store.getKey(alias, null) as? javax.crypto.SecretKey)?.let { return it }
        val generator = javax.crypto.KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(alias,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }

    private fun saveSecret(name: String, value: String) {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey())
        val bytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name + "_iv", android.util.Base64.encodeToString(cipher.iv, android.util.Base64.NO_WRAP))
            .putString(name + "_cipher", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)).apply()
    }

    private fun loadSecret(name: String): String {
        return try {
            val iv = prefs.getString(name + "_iv", null) ?: return ""
            val encrypted = prefs.getString(name + "_cipher", null) ?: return ""
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey(),
                javax.crypto.spec.GCMParameterSpec(128, android.util.Base64.decode(iv, android.util.Base64.NO_WRAP)))
            String(cipher.doFinal(android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 502 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoice()
        else if (requestCode == 502) setReply("Microphone permission is required for voice control.")
    }

    override fun onDestroy() {
        if (receiverOn) { unregisterReceiver(receiver); receiverOn = false }
        try { stopService(Intent(this, WakeWordService::class.java)) } catch (_: Exception) {}
        ttsRef?.stop(); ttsRef?.shutdown(); ttsRef = null
        io.shutdownNow()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
