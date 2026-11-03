package com.dude.grokhome

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** DUDE 2.0: completely rebuilt native dashboard and conversation layer. */
class MainActivity : Activity() {
    private val navy = Color.rgb(4, 10, 25)
    private val panel = Color.rgb(11, 30, 55)
    private val cyan = Color.rgb(70, 211, 255)
    private val mint = Color.rgb(87, 235, 190)
    private val muted = Color.rgb(155, 184, 211)
    private val io = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())
    private val brain = AssistantBrain()
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var weatherTitle: TextView
    private lateinit var weatherBody: TextView
    private lateinit var response: TextView
    private lateinit var status: TextView
    private lateinit var orb: TextView
    private lateinit var newsRow: LinearLayout
    private lateinit var ask: EditText
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var receiverRegistered = false
    private var weatherText = ""
    private var stories: List<DashboardData.NewsItem> = emptyList()

    private val voiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val command = intent?.getStringExtra(WakeWordService.EXTRA_COMMAND).orEmpty()
            when {
                command == WakeWordService.WAKE_ONLY -> speak("I'm listening. Let's hear what you've got.")
                command.startsWith("DUDE_ERROR:") -> speak(command.removePrefix("DUDE_ERROR:"))
                command.isNotBlank() -> handle(command)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        prefs = getSharedPreferences("dude_private", MODE_PRIVATE)
        tts = TextToSpeech(this) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) tts.language = Locale.US
        }
        buildUi()
        tick()
        loadDashboard()
        val filter = IntentFilter(WakeWordService.ACTION_WAKE_COMMAND)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(voiceReceiver, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(voiceReceiver, filter)
        receiverRegistered = true
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice()
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 502)
        main.post(clockRunnable)
    }

    private val clockRunnable = object : Runnable {
        override fun run() { if (!isFinishing) { tick(); main.postDelayed(this, 1000) } }
    }

    private fun shape(color: Int, stroke: Int = Color.TRANSPARENT, radius: Float = 22f) =
        GradientDrawable().apply { setColor(color); cornerRadius = radius; if (stroke != Color.TRANSPARENT) setStroke(2, stroke) }

    private fun grad(a: Int, b: Int, stroke: Int = Color.TRANSPARENT, radius: Float = 24f) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(a, b)).apply {
            cornerRadius = radius
            if (stroke != Color.TRANSPARENT) setStroke(2, stroke)
        }

    private fun txt(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color); includeFontPadding = true
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; textSize = 11f; isAllCaps = false; setTextColor(Color.WHITE)
        background = grad(Color.rgb(19, 112, 161), Color.rgb(12, 62, 111), cyan, 15f)
        setOnClickListener { action() }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 10, 20, 10)
            background = grad(navy, Color.rgb(11, 18, 47), Color.TRANSPARENT, 0f)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val logo = txt("D U D E", 25f, cyan, true)
        header.addView(logo, LinearLayout.LayoutParams(0, 48, 1f))
        status = txt("● ONLINE", 11f, mint, true)
        header.addView(status)
        header.addView(button("⚙") { settings() }, LinearLayout.LayoutParams(50, 42).apply { leftMargin = 10 })
        root.addView(header)

        val scroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val timeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(20, 12, 12, 12)
            background = grad(Color.rgb(16, 55, 98), Color.rgb(9, 31, 65), Color.rgb(39, 126, 185))
        }
        clock = txt("--:--", 54f, Color.WHITE, true).apply { setShadowLayer(20f, 0f, 0f, cyan) }
        date = txt("LOADING DATE", 13f, muted, true)
        timeCard.addView(clock); timeCard.addView(date)
        info.addView(timeCard, LinearLayout.LayoutParams(-1, 124).apply { bottomMargin = 10 })
        val wx = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(16, 12, 16, 12)
            background = grad(Color.rgb(8, 64, 81), Color.rgb(9, 38, 65), mint)
        }
        weatherTitle = txt("WEATHER / SET CITY", 11f, mint, true)
        weatherBody = txt("Your local forecast will appear here.", 17f, Color.WHITE, true)
        wx.addView(weatherTitle); wx.addView(weatherBody)
        info.addView(wx, LinearLayout.LayoutParams(-1, 105))
        top.addView(info, LinearLayout.LayoutParams(0, -1, 2.7f).apply { rightMargin = 12 })

        val assistant = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(10, 8, 10, 8)
            background = grad(Color.rgb(19, 44, 88), Color.rgb(10, 22, 52), Color.rgb(64, 127, 207))
        }
        orb = txt("◉", 72f, cyan, true).apply { gravity = Gravity.CENTER; setShadowLayer(28f, 0f, 0f, cyan) }
        assistant.addView(orb, LinearLayout.LayoutParams(-1, 100))
        assistant.addView(txt("HEY, I'M DUDE", 17f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        assistant.addView(txt("YOUR HOME'S SMART-MOUTH", 9f, mint, true).apply { gravity = Gravity.CENTER; setPadding(0, 5, 0, 10) })
        assistant.addView(button("TAP TO TALK") { startVoice(); speak("I'm listening. Try to make it interesting.") }, LinearLayout.LayoutParams(-1, 42))
        top.addView(assistant, LinearLayout.LayoutParams(0, -1, 2.0f))
        page.addView(top, LinearLayout.LayoutParams(-1, 238))

        val chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(16, 10, 16, 12)
            background = shape(panel, Color.rgb(53, 103, 158), 20f)
        }
        val chatHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        chatHeader.addView(txt("DUDE / CONVERSATION", 11f, cyan, true), LinearLayout.LayoutParams(0, 27, 1f))
        chatHeader.addView(txt("VOICE + TEXT", 9f, muted, true))
        chat.addView(chatHeader)
        response = txt("Hey! I'm DUDE. Ask me about the weather, news, time, or your smart home. I'll try not to be a show-off.", 15f)
        response.maxLines = 2
        chat.addView(response, LinearLayout.LayoutParams(-1, 48))
        val askRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        ask = EditText(this).apply {
            hint = "Ask DUDE…"; setHintTextColor(muted); setTextColor(Color.WHITE); textSize = 14f
            setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = shape(Color.rgb(5, 17, 35), Color.rgb(43, 93, 140), 14f); setPadding(12, 0, 12, 0)
        }
        askRow.addView(ask, LinearLayout.LayoutParams(0, 44, 1f).apply { rightMargin = 8 })
        askRow.addView(button("SEND") { val q = ask.text.toString().trim(); ask.setText(""); if (q.isNotBlank()) handle(q) }, LinearLayout.LayoutParams(82, 44))
        chat.addView(askRow)
        page.addView(chat, LinearLayout.LayoutParams(-1, 130).apply { topMargin = 11; bottomMargin = 11 })

        val newsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(14, 10, 14, 10)
            background = shape(panel, Color.rgb(43, 86, 135), 20f)
        }
        val newsHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        newsHeader.addView(txt("THE WORLD, BRIEFLY", 14f, Color.WHITE, true), LinearLayout.LayoutParams(0, 28, 1f))
        newsHeader.addView(txt("LIVE HEADLINES", 9f, mint, true))
        newsCard.addView(newsHeader)
        newsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        newsCard.addView(newsRow, LinearLayout.LayoutParams(-1, 125))
        page.addView(newsCard, LinearLayout.LayoutParams(-1, 164).apply { bottomMargin = 10 })

        val deviceHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        deviceHeader.addView(txt("YOUR HOME", 17f, Color.WHITE, true), LinearLayout.LayoutParams(0, 38, 1f))
        deviceHeader.addView(button("DISCOVER DEVICES  ›") { discoverDevices() }, LinearLayout.LayoutParams(150, 38))
        page.addView(deviceHeader)
        val tiles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("💡\nLIGHTS", "🌡\nSENSI", "🧺\nLG WASHER", "🤖\nSHARK").forEach { name ->
            tiles.addView(txt(name, 12f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER; background = shape(Color.rgb(13, 43, 69), Color.rgb(38, 92, 132), 16f)
                setPadding(4, 8, 4, 8); setOnClickListener { discoverDevices() }
            }, LinearLayout.LayoutParams(0, 70, 1f).apply { setMargins(4, 3, 4, 3) })
        }
        page.addView(tiles)
        page.addView(txt("VOICE RECOGNITION USES THE SPEECH SERVICE INSTALLED ON YOUR TABLET.", 9f, muted, true).apply {
            gravity = Gravity.CENTER; setPadding(0, 8, 0, 2)
        })
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun tick() {
        if (!::clock.isInitialized) return
        clock.text = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
        date.text = SimpleDateFormat("EEEE  •  MMMM d", Locale.getDefault()).format(Date()).uppercase(Locale.getDefault())
    }

    private fun setState(value: String) {
        status.text = "● $value"
        orb.text = if (value.contains("THINK")) "◌" else if (value.contains("LISTEN")) "◉" else "✦"
    }

    private fun speak(message: String) {
        response.text = message
        setState("READY")
        if (ttsReady) {
            @Suppress("DEPRECATION")
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    private fun handle(utterance: String) {
        setState("THINKING")
        when (brain.understand(utterance).kind) {
            AssistantBrain.Kind.GREETING -> speak(brain.greeting())
            AssistantBrain.Kind.WEATHER -> weatherAnswer()
            AssistantBrain.Kind.TIME -> speak(brain.time())
            AssistantBrain.Kind.DATE -> speak(brain.date())
            AssistantBrain.Kind.NEWS -> speak(if (stories.isEmpty()) "News hasn't arrived yet. The newsroom must be stuck in traffic." else "Latest headlines are on the screen. Pick a story and I'll read it out. Try not to panic at the state of the world.")
            AssistantBrain.Kind.JOKE -> speak(brain.joke())
            AssistantBrain.Kind.HELP -> speak("I'm DUDE: weather, time, news, jokes, and smart-home controls through Home Assistant. I'm clever, not clairvoyant. Yet.")
            AssistantBrain.Kind.LIGHT_ON -> control("light", "turn_on", "Lights on. Let there be photons.")
            AssistantBrain.Kind.LIGHT_OFF -> control("light", "turn_off", "Lights off. Saving the planet, one switch at a time.")
            AssistantBrain.Kind.PLUG_ON -> control("switch", "turn_on", "Plug switched on. Electricity, at your service.")
            AssistantBrain.Kind.PLUG_OFF -> control("switch", "turn_off", "Plug switched off. Power nap time.")
            AssistantBrain.Kind.UNKNOWN -> speak("I heard you say, $utterance. I don't understand every kind of conversation yet. Try asking about weather, time, news, or your devices.")
        }
    }

    private fun weatherAnswer() {
        val raw = weatherText
        if (raw.isBlank()) { speak("I need your city first. Open settings and tell me where to check. I'm good, but I'm not psychic."); return }
        if (raw.contains("unavailable", true) || raw.contains("not found", true)) { speak("The weather service is being dramatic. I can't get the forecast right now. Check your connection and city setting."); return }
        val bits = raw.split("\n").filter { it.isNotBlank() }
        val city = bits.firstOrNull().orEmpty()
        val details = bits.drop(1).joinToString(". ").replace("•", ",")
        val temp = Regex("(-?[0-9]+)°F").find(details)?.groupValues?.get(1)?.toIntOrNull()
        val extra = when { temp != null && temp <= 50 -> "It's chilly, so grab a jacket."; temp != null && temp >= 82 -> "It's warm out. Hydrate, superstar."; else -> "Looks like a decent day to head out." }
        speak("Here's the weather in $city. $details. $extra")
    }

    private fun loadDashboard() {
        io.execute {
            val city = prefs.getString("weather_city", "").orEmpty()
            val w = if (city.isBlank()) "" else DashboardData.weather(city)
            val n = DashboardData.newsItems()
            runOnUiThread {
                weatherText = w
                if (city.isBlank()) { weatherTitle.text = "WEATHER / SET CITY"; weatherBody.text = "Choose your city in settings." }
                else {
                    val parts = w.split("\n")
                    weatherTitle.text = "WEATHER / " + parts.firstOrNull().orEmpty().uppercase(Locale.getDefault())
                    weatherBody.text = parts.drop(1).joinToString("\n").ifBlank { w }
                }
                stories = n
                renderNews()
            }
        }
    }

    private fun renderNews() {
        newsRow.removeAllViews()
        if (stories.isEmpty()) { newsRow.addView(txt("Headlines are taking a coffee break. Check Wi-Fi.", 12f, muted)); return }
        stories.take(4).forEach { story ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(6, 6, 6, 6)
                background = shape(Color.rgb(5, 20, 40), Color.rgb(35, 80, 120), 13f)
                setOnClickListener { speak("Here's a headline: " + story.title) }
            }
            val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = shape(Color.rgb(20, 57, 86)) }
            tile.addView(image, LinearLayout.LayoutParams(-1, 62))
            tile.addView(txt(story.title, 10f, Color.WHITE, true).apply { maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END },
                LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 4 })
            tile.addView(txt(story.source.ifBlank { "GOOGLE NEWS" }.uppercase(Locale.getDefault()), 8f, mint, true))
            newsRow.addView(tile, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(3, 3, 3, 3) })
            if (story.imageUrl.isNotBlank()) io.execute {
                try {
                    val c = URL(story.imageUrl).openConnection() as HttpURLConnection
                    c.connectTimeout = 6000; c.readTimeout = 7000
                    val bitmap = c.inputStream.use { BitmapFactory.decodeStream(it) }; c.disconnect()
                    if (bitmap != null) runOnUiThread { image.setImageBitmap(bitmap) }
                } catch (_: Exception) { }
            }
        }
    }

    private fun startVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 502); return
        }
        try {
            val intent = Intent(this, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            setState("LISTENING FOR HEY DUDE")
        } catch (_: Exception) { speak("I couldn't start the microphone. Check the permission and try again.") }
    }

    private fun discoverDevices() {
        val base = prefs.getString("ha_url", "").orEmpty()
        val token = loadSecret("ha_token")
        if (base.isBlank() || token.isBlank()) { speak("Home Assistant isn't connected. Let's set that up."); settings(); return }
        response.text = "Looking around your smart home…"
        io.execute {
            try {
                val array = JSONArray(HomeAssistantClient(base, token).states())
                val ids = ArrayList<String>(); val labels = ArrayList<String>()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("entity_id")
                    if (id.substringBefore(".") !in setOf("light", "switch", "fan", "cover", "climate", "vacuum", "lock", "media_player")) continue
                    ids.add(id)
                    labels.add(item.optJSONObject("attributes")?.optString("friendly_name", id) + " • " + item.optString("state") + "\n" + id)
                }
                runOnUiThread {
                    if (labels.isEmpty()) { speak("No supported devices found. The house is playing hide and seek."); return@runOnUiThread }
                    AlertDialog.Builder(this).setTitle("YOUR CONNECTED DEVICES").setItems(labels.toTypedArray()) { _, which ->
                        deviceActions(ids[which], labels[which].substringBefore(" •"))
                    }.setNegativeButton("Close", null).show()
                }
            } catch (_: Exception) { runOnUiThread { speak("I can't reach Home Assistant. Check the URL, token, and Wi-Fi.") } }
        }
    }

    private fun control(domain: String, service: String, success: String) {
        val entity = prefs.getString(if (domain == "light") "ha_light_entity" else "ha_switch_entity", "").orEmpty()
        if (entity.isBlank()) { speak("I need the $domain entity set up first. Use Discover Devices or settings."); return }
        val base = prefs.getString("ha_url", "").orEmpty()
        val token = loadSecret("ha_token")
        if (base.isBlank() || token.isBlank()) { speak("Connect Home Assistant in settings first. No paid AI key needed."); return }
        response.text = "Sending command to your smart home…"
        io.execute {
            try {
                HomeAssistantClient(base, token).callService(domain, service, entity)
                runOnUiThread { speak(success) }
            } catch (_: Exception) { runOnUiThread { speak("That didn't reach the device. Check Home Assistant and its integration.") } }
        }
    }

    private fun deviceActions(entity: String, name: String) {
        val domain = entity.substringBefore(".")
        val options = when (domain) {
            "light", "switch", "fan" -> arrayOf("Turn on", "Turn off")
            "cover" -> arrayOf("Open", "Close")
            "vacuum" -> arrayOf("Start", "Pause", "Return to dock")
            "lock" -> arrayOf("Lock", "Unlock")
            else -> arrayOf("Refresh status")
        }
        AlertDialog.Builder(this).setTitle(name).setItems(options) { _, which ->
            val service = when (domain) {
                "light", "switch", "fan" -> if (which == 0) "turn_on" else "turn_off"
                "cover" -> if (which == 0) "open_cover" else "close_cover"
                "vacuum" -> arrayOf("start", "pause", "return_to_base")[which]
                "lock" -> if (which == 0) "lock" else "unlock"
                else -> null
            }
            if (service == null) speak("Status refresh is available from Home Assistant.")
            else {
                val base = prefs.getString("ha_url", "").orEmpty()
                val token = loadSecret("ha_token")
                io.execute {
                    try { HomeAssistantClient(base, token).callService(domain, service, entity); runOnUiThread { speak("$name. Command sent.") } }
                    catch (_: Exception) { runOnUiThread { speak("The device didn't respond. Check its integration.") } }
                }
            }
        }.setNegativeButton("Cancel", null).show()
    }

    private fun settings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 8, 18, 0) }
        fun field(title: String, value: String = "", type: Int = InputType.TYPE_CLASS_TEXT): EditText {
            box.addView(txt(title, 12f, cyan, true))
            return EditText(this).apply { setText(value); inputType = type; setSingleLine(true); box.addView(this) }
        }
        val city = field("Weather city", prefs.getString("weather_city", "").orEmpty())
        val url = field("Home Assistant URL", prefs.getString("ha_url", "").orEmpty())
        val token = field("Home Assistant long-lived access token", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val light = field("Main light entity ID", prefs.getString("ha_light_entity", "").orEmpty())
        val plug = field("Smart plug entity ID", prefs.getString("ha_switch_entity", "").orEmpty())
        box.addView(txt("Weather and headlines use free sources. Home Assistant is optional but required for device control. No paid AI API is configured.", 10f, muted))
        AlertDialog.Builder(this).setTitle("DUDE SETTINGS").setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("SAVE") { _, _ ->
                prefs.edit().putString("weather_city", city.text.toString().trim())
                    .putString("ha_url", url.text.toString().trim())
                    .putString("ha_light_entity", light.text.toString().trim())
                    .putString("ha_switch_entity", plug.text.toString().trim()).apply()
                if (token.text.isNotBlank()) saveSecret("ha_token", token.text.toString().trim())
                loadDashboard(); speak("Settings saved. Look at me, being configurable.")
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

    private fun loadSecret(name: String): String = try {
        val iv = prefs.getString(name + "_iv", null) ?: return ""
        val encrypted = prefs.getString(name + "_cipher", null) ?: return ""
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey(),
            javax.crypto.spec.GCMParameterSpec(128, android.util.Base64.decode(iv, android.util.Base64.NO_WRAP)))
        String(cipher.doFinal(android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (_: Exception) { "" }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 502 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoice()
        else if (requestCode == 502) speak("I need microphone permission to listen for Hey Dude. I'm not being nosy. Much.")
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(voiceReceiver)
        try { stopService(Intent(this, WakeWordService::class.java)) } catch (_: Exception) { }
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        io.shutdownNow(); main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
