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
            if (ttsReady) {
                val voices = tts.voices.orEmpty()
                val saved = prefs.getString("tts_voice", "").orEmpty()
                val selected = voices.firstOrNull { it.name == saved }
                    ?: voices.filter { it.locale.language == "en" }
                        .sortedWith(compareByDescending<android.speech.tts.Voice> { it.quality }.thenBy { it.isNetworkConnectionRequired })
                        .firstOrNull()
                if (selected != null) tts.voice = selected else tts.language = Locale.US
                tts.setSpeechRate(0.94f)
                tts.setPitch(0.93f)
            }
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
        // Responsive Echo Show-style dashboard. Wide displays use a three-column
        // command center; narrow displays reflow into a vertically scrolling layout.
        val wide = resources.configuration.screenWidthDp >= 800 &&
            resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(if (wide) 22 else 14, 10, if (wide) 22 else 14, 10)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(2, 7, 22), Color.rgb(5, 22, 46), Color.rgb(10, 9, 34)))
        }
        fun label(text: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
            txt(text, size, color, bold)
        fun panel(title: String, accent: Int = cyan): LinearLayout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14, 12, 14, 12)
                background = grad(Color.rgb(8, 25, 52), Color.rgb(4, 14, 34), accent, 22f)
                addView(label(title, 11f, accent, true).apply { letterSpacing = 0.08f })
            }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("DUDE", if (wide) 30f else 25f, cyan, true).apply {
            letterSpacing = 0.2f; setShadowLayer(16f, 0f, 0f, cyan)
        })
        brand.addView(label("SMART HOME  •  PERSONAL HOME INTELLIGENCE", 8f, muted, true).apply { letterSpacing = 0.12f })
        header.addView(brand, LinearLayout.LayoutParams(0, 54, 1f))
        status = label("● READY", 10f, mint, true)
        header.addView(status)
        header.addView(button("⚙") { settings() }, LinearLayout.LayoutParams(48, 42).apply { leftMargin = 10 })
        root.addView(header)

        val scroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val leftColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val timeCard = panel("LOCAL TIME  /  YOUR DAY", cyan)
        clock = label("--:--", if (wide) 62f else 58f, Color.WHITE, true).apply {
            setShadowLayer(22f, 0f, 0f, cyan)
        }
        date = label("LOADING", 12f, muted, true)
        timeCard.addView(clock)
        timeCard.addView(date)
        timeCard.addView(label("GOOD VIBRATIONS.  •  DUDE IS ONLINE", 8f, mint, true).apply { setPadding(0, 7, 0, 0) })
        leftColumn.addView(timeCard, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = 10 })

        val wx = panel("◈  WEATHER / ATMOSPHERE", mint)
        weatherTitle = label("SET YOUR CITY", 19f, Color.WHITE, true)
        weatherBody = label("Your local forecast will appear here.", 12f, muted)
        wx.addView(weatherTitle)
        wx.addView(weatherBody)
        leftColumn.addView(wx, LinearLayout.LayoutParams(-1, 0, 1f))

        val coreCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(14, 14, 14, 14)
            background = grad(Color.rgb(9, 39, 78), Color.rgb(8, 13, 38), Color.rgb(66, 186, 255), 28f)
        }
        coreCard.addView(label("HEY DUDE", 10f, mint, true).apply { letterSpacing = 0.22f })
        orb = label("◉", if (wide) 100f else 76f, cyan, true).apply {
            gravity = Gravity.CENTER; setShadowLayer(32f, 0f, 0f, cyan)
        }
        coreCard.addView(orb, LinearLayout.LayoutParams(-1, 0, 1f))
        coreCard.addView(label("DUDE CORE", 18f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        coreCard.addView(label("I'm listening. What's up?", 11f, muted).apply { gravity = Gravity.CENTER; setPadding(0, 4, 0, 12) })
        coreCard.addView(button("🎙  TALK TO DUDE") { startVoice(); speak("Yo. I'm listening. What's up?") },
            LinearLayout.LayoutParams(-1, 48))

        val news = panel("▤  GOOGLE NEWS  /  TOP STORIES", cyan)
        newsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        news.addView(newsRow, LinearLayout.LayoutParams(-1, 0, 1f))
        news.addView(button("VIEW NEWS  →") { handle("read the news") }, LinearLayout.LayoutParams(-1, 40))

        if (wide) {
            top.addView(leftColumn, LinearLayout.LayoutParams(0, -1, 1.05f).apply { rightMargin = 10 })
            top.addView(coreCard, LinearLayout.LayoutParams(0, -1, 1.05f).apply { rightMargin = 10 })
            top.addView(news, LinearLayout.LayoutParams(0, -1, 1.1f))
            page.addView(top, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = 12 })
        } else {
            leftColumn.layoutParams = LinearLayout.LayoutParams(-1, 300).apply { bottomMargin = 10 }
            top.addView(leftColumn)
            top.addView(coreCard, LinearLayout.LayoutParams(-1, 300).apply { bottomMargin = 10 })
            top.addView(news, LinearLayout.LayoutParams(-1, 300))
            page.addView(top)
        }

        val chat = panel("◉  DUDE CHANNEL  /  VOICE + TEXT", cyan)
        response = label("Yo. I'm DUDE. Weather, headlines, time, and your smart home. No awkward small talk required.", 13f)
        response.maxLines = 2
        chat.addView(response, LinearLayout.LayoutParams(-1, 44))
        val askRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        ask = EditText(this).apply {
            hint = "Ask DUDE anything…"; setHintTextColor(muted); setTextColor(Color.WHITE); textSize = 16f
            setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = shape(Color.rgb(3, 11, 27), Color.rgb(47, 118, 166), 14f); setPadding(13, 0, 13, 0)
        }
        askRow.addView(ask, LinearLayout.LayoutParams(0, 48, 1f).apply { rightMargin = 8 })
        askRow.addView(button("SEND  ↗") {
            val q = ask.text.toString().trim(); ask.setText(""); if (q.isNotBlank()) handle(q)
        }, LinearLayout.LayoutParams(94, 48))
        chat.addView(askRow)
        page.addView(chat, LinearLayout.LayoutParams(-1, if (wide) 126 else 150).apply { bottomMargin = 12 })

        val home = panel("⌂  SMART HOME  /  YOUR CONNECTED DEVICES", mint)
        val homeHead = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        homeHead.addView(label("CONTROL YOUR HOME", 9f, muted, true), LinearLayout.LayoutParams(0, 28, 1f))
        homeHead.addView(button("VIEW ALL DEVICES  →") { discoverDevices() }, LinearLayout.LayoutParams(154, 40))
        home.addView(homeHead)
        val tiles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("💡\nLIGHTS", "❄\nCLIMATE", "▤\nLAUNDRY", "◎\nVACUUM").forEachIndexed { index, name ->
            tiles.addView(label(name, 10f, if (index == 0) cyan else muted, true).apply {
                gravity = Gravity.CENTER
                background = grad(Color.rgb(13, 43, 75), Color.rgb(5, 18, 39), Color.rgb(37, 105, 157), 15f)
                setPadding(3, 8, 3, 8); setOnClickListener { discoverDevices() }
            }, LinearLayout.LayoutParams(0, if (wide) 66 else 72, 1f).apply { setMargins(3, 3, 3, 3) })
        }
        home.addView(tiles)
        page.addView(home, LinearLayout.LayoutParams(-1, if (wide) 132 else 142).apply { bottomMargin = 8 })
        page.addView(label("DUDE 2.0  •  YOUR HOME, AT A GLANCE", 8f, muted, true).apply {
            gravity = Gravity.CENTER; setPadding(0, 5, 0, 5); letterSpacing = 0.12f
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
            tts.setSpeechRate(0.94f)
            tts.setPitch(0.93f)
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
            AssistantBrain.Kind.VACUUM_START -> control("vacuum", "start", "Vacuum started. The floor's days are numbered.")
            AssistantBrain.Kind.VACUUM_STOP -> control("vacuum", "pause", "Vacuum paused.")
            AssistantBrain.Kind.VACUUM_DOCK -> control("vacuum", "return_to_base", "Sending the vacuum home.")
            AssistantBrain.Kind.THERMOSTAT -> speak("Open Discover Devices and select your thermostat to see its available controls.")
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
        val entity = prefs.getString(when (domain) { "light" -> "ha_light_entity"; "vacuum" -> "ha_vacuum_entity"; else -> "ha_switch_entity" }, "").orEmpty()
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
        val vacuum = field("Vacuum entity ID", prefs.getString("ha_vacuum_entity", "").orEmpty())
        box.addView(button("CHOOSE DUDE'S VOICE") { chooseVoice() }, LinearLayout.LayoutParams(-1, 44))
        box.addView(txt("Voice options depend on voices installed on this tablet. Install a higher-quality English voice in Android text-to-speech settings for the most natural sound.", 10f, muted))
        box.addView(txt("Weather and headlines use free sources. Home Assistant is optional but required for device control. No paid AI API is configured.", 10f, muted))
        AlertDialog.Builder(this).setTitle("DUDE SETTINGS").setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("SAVE") { _, _ ->
                prefs.edit().putString("weather_city", city.text.toString().trim())
                    .putString("ha_url", url.text.toString().trim())
                    .putString("ha_light_entity", light.text.toString().trim())
                    .putString("ha_switch_entity", plug.text.toString().trim())
                    .putString("ha_vacuum_entity", vacuum.text.toString().trim()).apply()
                if (token.text.isNotBlank()) saveSecret("ha_token", token.text.toString().trim())
                loadDashboard(); speak("Settings saved. Look at me, being configurable.")
            }.setNegativeButton("CANCEL", null).show()
    }


    private fun chooseVoice() {
        if (!ttsReady) { speak("My voice engine is still warming up."); return }
        val choices = tts.voices.orEmpty().filter { it.locale.language == "en" }
            .sortedWith(compareByDescending<android.speech.tts.Voice> { it.quality }.thenBy { it.isNetworkConnectionRequired })
        if (choices.isEmpty()) { speak("I couldn't find another English voice installed on this tablet."); return }
        val labels = choices.map {
            val quality = if (it.quality >= android.speech.tts.Voice.QUALITY_HIGH) "High quality" else "Standard"
            val network = if (it.isNetworkConnectionRequired) "Online" else "Offline"
            it.name + "  •  " + quality + "  •  " + network
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("DUDE'S VOICE")
            .setItems(labels) { _, which ->
                val selected = choices[which]
                tts.voice = selected
                prefs.edit().putString("tts_voice", selected.name).apply()
                speak("Nice. New voice installed. How do I sound?")
            }.setNegativeButton("Cancel", null).show()
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
