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
    private val navy = Color.rgb(10, 18, 32)
    private val panel = Color.rgb(20, 32, 50)
    private val cyan = Color.rgb(74, 163, 255)
    private val mint = Color.rgb(69, 199, 176)
    private val muted = Color.rgb(157, 174, 195)
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
    private lateinit var mainScroll: ScrollView
    private lateinit var dashboardPage: LinearLayout
    private lateinit var settingsPage: LinearLayout
    private val dashboardSections = linkedMapOf<String, View>()
    private var dashboardFooter: View? = null
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
        text = value; textSize = 11f; isAllCaps = false; setTextColor(Color.WHITE); minHeight = 42; minimumHeight = 42; elevation = 0f; stateListAnimator = null; letterSpacing = 0.01f; setPadding(14, 0, 14, 0)
        background = grad(Color.rgb(35, 105, 177), Color.rgb(35, 105, 177), Color.rgb(62, 132, 198), 10f)
        setOnClickListener { action() }
    }

    private lateinit var navBar: LinearLayout
    private var activeSection = "HOME"

    private fun buildUi() {
        val wide = resources.configuration.screenWidthDp >= 600 &&
            resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(if (wide) 24 else 16, 12, if (wide) 24 else 16, 10)
            setBackgroundColor(Color.rgb(241, 245, 249))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(txt("DUDE", 26f, Color.rgb(24, 48, 76), true))
        brand.addView(txt("HOME INTELLIGENCE", 9f, Color.rgb(102, 122, 145), true).apply { letterSpacing = 0.12f })
        header.addView(brand, LinearLayout.LayoutParams(0, 52, 1f))
        status = txt("● SYSTEM READY", 10f, Color.rgb(28, 142, 115), true)
        header.addView(status)
        header.addView(button("⚙") { showSectionTab("SETTINGS") }, LinearLayout.LayoutParams(46, 42).apply { leftMargin = 10 })
        root.addView(header)

        mainScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            background = shape(Color.rgb(241, 245, 249), Color.TRANSPARENT, 0f)
        }
        dashboardPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 2, 0, 18)
        }
        settingsPage = buildSettingsPage()
        buildDashboard(wide)
        mainScroll.addView(dashboardPage)
        root.addView(mainScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 0)
        }
        root.addView(navBar, LinearLayout.LayoutParams(-1, 54))
        setContentView(root)
        renderNavigation()
        mainScroll.post { dashboardPage.minimumHeight = mainScroll.height }
        applySavedDashboardLayout(dashboardFooter)
    }

    private fun card(title: String, subtitle: String = ""): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
            background = shape(Color.WHITE, Color.rgb(222, 230, 239), 14f)
            elevation = 1.5f
        }
        box.addView(txt(title, 11f, Color.rgb(83, 105, 130), true).apply { letterSpacing = 0.07f })
        if (subtitle.isNotBlank()) box.addView(txt(subtitle, 10f, Color.rgb(133, 149, 169)).apply { setPadding(0, 3, 0, 0) })
        return box
    }

    private fun buildDashboard(wide: Boolean) {
        dashboardSections.clear()
        val intro = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2, 10, 2, 16)
        }
        intro.addView(txt("Good to see you.", 25f, Color.rgb(24, 43, 66), true))
        intro.addView(txt("Your home at a glance.", 13f, Color.rgb(105, 124, 146)))
        val overviewSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        overviewSection.addView(intro)

        val overview = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val timeCard = card("LOCAL TIME", "YOUR DAY")
        clock = txt("--:--", if (wide) 48f else 42f, Color.rgb(25, 49, 76), true)
        date = txt("LOADING", 11f, Color.rgb(104, 124, 148), true)
        timeCard.addView(clock)
        timeCard.addView(date.apply { setPadding(0, 2, 0, 8) })
        timeCard.addView(txt("DUDE IS ONLINE", 9f, Color.rgb(31, 153, 126), true))
        left.addView(timeCard, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = 10 })

        val wx = card("WEATHER", "LOCAL CONDITIONS")
        weatherTitle = txt("SET YOUR CITY", 18f, Color.rgb(30, 54, 81), true)
        weatherBody = txt("Choose your city in Settings to see the local forecast.", 12f, Color.rgb(105, 124, 146))
        wx.addView(weatherTitle)
        wx.addView(weatherBody.apply { setPadding(0, 4, 0, 0) })
        left.addView(wx, LinearLayout.LayoutParams(-1, 0, 1f))

        val assistant = card("DUDE ASSISTANT", "VOICE CONTROL • HEY DUDE")
        assistant.gravity = Gravity.CENTER_HORIZONTAL
        orb = txt("●", if (wide) 66f else 54f, Color.rgb(51, 130, 204), true).apply { gravity = Gravity.CENTER }
        assistant.addView(orb, LinearLayout.LayoutParams(-1, 0, 1f))
        assistant.addView(txt("Ready when you are.", 14f, Color.rgb(30, 54, 81), true))
        assistant.addView(txt("Hands-free voice and quick commands", 10f, Color.rgb(105, 124, 146)))
        assistant.addView(button("TALK TO DUDE") { startVoice(); speak("I'm listening. What's up?") },
            LinearLayout.LayoutParams(-1, 44).apply { topMargin = 12 })

        val news = card("TOP STORIES", "LATEST HEADLINES")
        newsRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        news.addView(newsRow, LinearLayout.LayoutParams(-1, 0, 1f))
        news.addView(button("OPEN NEWS") { showSectionTab("NEWS") }, LinearLayout.LayoutParams(-1, 40).apply { topMargin = 8 })

        if (wide) {
            overview.addView(left, LinearLayout.LayoutParams(0, 300, 1f).apply { rightMargin = 12 })
            overview.addView(assistant, LinearLayout.LayoutParams(0, 300, 1f).apply { rightMargin = 12 })
            overview.addView(news, LinearLayout.LayoutParams(0, 300, 1.15f))
        } else {
            overview.addView(left, LinearLayout.LayoutParams(-1, 280).apply { bottomMargin = 10 })
            overview.addView(assistant, LinearLayout.LayoutParams(-1, 250).apply { bottomMargin = 10 })
            overview.addView(news, LinearLayout.LayoutParams(-1, 260))
        }
        overviewSection.addView(overview, LinearLayout.LayoutParams(-1, if (wide) 300 else -2).apply { bottomMargin = 12 })
        dashboardPage.addView(overviewSection)
        dashboardSections["OVERVIEW"] = overviewSection

        val assistantChannel = card("ASSISTANT ACTIVITY", "VOICE + TEXT")
        response = txt("DUDE is ready. Ask about the weather, time, headlines, or connected devices.", 13f, Color.rgb(47, 67, 91))
        assistantChannel.addView(response)
        val askRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        ask = EditText(this).apply {
            hint = "Type a command…"
            setHintTextColor(Color.rgb(140, 155, 174))
            setTextColor(Color.rgb(29, 48, 70))
            textSize = 14f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = shape(Color.rgb(246, 249, 252), Color.rgb(218, 227, 237), 10f)
            setPadding(12, 0, 12, 0)
        }
        ask = dashboardAsk
        askRow.addView(dashboardAsk, LinearLayout.LayoutParams(0, 46, 1f).apply { rightMargin = 8 })
        askRow.addView(button("SEND") {
            val q = dashboardAsk.text.toString().trim()
            dashboardAsk.setText("")
            if (q.isNotBlank()) handle(q)
        }, LinearLayout.LayoutParams(90, 46))
        assistantChannel.addView(askRow, LinearLayout.LayoutParams(-1, 46).apply { topMargin = 10 })
        dashboardPage.addView(assistantChannel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        dashboardSections["DUDE CHANNEL"] = assistantChannel

        val home = card("CONNECTED HOME", "YOUR DEVICES")
        val homeHead = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        homeHead.addView(txt("Manage lights, climate, laundry and more.", 11f, Color.rgb(105, 124, 146)),
            LinearLayout.LayoutParams(0, 36, 1f))
        homeHead.addView(button("VIEW DEVICES") { showSectionTab("DEVICES") }, LinearLayout.LayoutParams(130, 40))
        home.addView(homeHead)
        val footer = txt("TECH IT WITH EZ  •  DUDE HOME INTELLIGENCE", 9f, Color.rgb(133, 149, 169), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 8)
        }
        dashboardFooter = footer
        dashboardPage.addView(home, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
        dashboardSections["SMART HOME"] = home
    }

    private fun renderNavigation() {
        if (!::navBar.isInitialized) return
        navBar.removeAllViews()
        val tabs = listOf("HOME", "DEVICES", "ROUTINES", "ASSISTANT", "NEWS", "WEATHER", "SETTINGS")
        tabs.forEach { tab ->
            val active = tab == activeSection
            val item = TextView(this).apply {
                text = when (tab) {
                    "HOME" -> "⌂\\nHome"
                    "DEVICES" -> "▦\\nDevices"
                    "ROUTINES" -> "↻\\nRoutines"
                    "ASSISTANT" -> "◉\\nAssistant"
                    "NEWS" -> "▤\\nNews"
                    "WEATHER" -> "☼\\nWeather"
                    else -> "⚙\\nSettings"
                }
                gravity = Gravity.CENTER
                textSize = 9f
                maxLines = 2
                setTextColor(if (active) Color.rgb(35, 112, 190) else Color.rgb(106, 124, 145))
                typeface = Typeface.create("sans-serif", if (active) Typeface.BOLD else Typeface.MEDIUM)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { showSectionTab(tab) }
            }
            navBar.addView(item, LinearLayout.LayoutParams(0, 52, 1f))
        }
    }

    private fun showSectionTab(section: String) {
        activeSection = section
        renderNavigation()
        mainScroll.removeAllViews()
        val page: View = when (section) {
            "HOME" -> {
                applySavedDashboardLayout(dashboardFooter)
                dashboardPage
            }
            "SETTINGS" -> settingsPage
            "DEVICES" -> buildDevicesPage()
            "ROUTINES" -> buildRoutinesPage()
            "ASSISTANT" -> buildAssistantPage()
            "NEWS" -> buildNewsPage()
            "WEATHER" -> buildWeatherPage()
            else -> dashboardPage
        }
        mainScroll.addView(page)
    }

    private fun pageShell(title: String, description: String): LinearLayout {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2, 16, 2, 22)
        }
        page.addView(txt(title, 25f, Color.rgb(24, 43, 66), true))
        page.addView(txt(description, 12f, Color.rgb(105, 124, 146)).apply { setPadding(0, 4, 0, 18) })
        return page
    }

    private fun buildDevicesPage(): LinearLayout {
        val page = pageShell("Devices", "Connected equipment and live availability.")
        val summary = card("HOME ASSISTANT", "DEVICE CONNECTION")
        val configured = prefs.getString("ha_url", "").orEmpty().isNotBlank() && loadSecret("ha_token").isNotBlank()
        summary.addView(txt(if (configured) "Connection details saved" else "Home Assistant is not configured",
            14f, if (configured) Color.rgb(31, 153, 126) else Color.rgb(183, 119, 39), true))
        summary.addView(button(if (configured) "REFRESH DEVICE LIST" else "CONFIGURE CONNECTION") {
            if (configured) loadDeviceList(page) else showSectionTab("SETTINGS")
        }, LinearLayout.LayoutParams(-1, 44).apply { topMargin = 12 })
        page.addView(summary, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list.tag = "device_list"
        page.addView(list)
        if (configured) loadDeviceList(page)
        return page
    }

    private fun loadDeviceList(page: LinearLayout) {
        val list = page.findViewWithTag<LinearLayout>("device_list") ?: return
        list.removeAllViews()
        list.addView(txt("Loading connected devices…", 12f, Color.rgb(105, 124, 146)))
        val base = prefs.getString("ha_url", "").orEmpty()
        val token = loadSecret("ha_token")
        io.execute {
            try {
                val states = JSONArray(HomeAssistantClient(base, token).states())
                runOnUiThread {
                    list.removeAllViews()
                    var count = 0
                    for (i in 0 until states.length()) {
                        val item = states.optJSONObject(i) ?: continue
                        val id = item.optString("entity_id")
                        val domain = id.substringBefore(".")
                        if (domain !in setOf("light", "switch", "fan", "cover", "climate", "vacuum", "lock", "media_player")) continue
                        count++
                        val name = item.optJSONObject("attributes")?.optString("friendly_name", id) ?: id
                        val stateText = item.optString("state", "unknown")
                        val row = card(name, id)
                        val statusLine = txt(stateText.uppercase(Locale.getDefault()),
                            11f, if (stateText in setOf("on", "home", "playing", "open", "heat", "cool")) Color.rgb(31, 153, 126) else Color.rgb(105, 124, 146), true)
                        row.addView(statusLine.apply { setPadding(0, 7, 0, 6) })
                        row.addView(button("MANAGE DEVICE") { deviceActions(id, name) }, LinearLayout.LayoutParams(-1, 40))
                        list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 9 })
                    }
                    if (count == 0) list.addView(txt("No supported devices were found.", 12f, Color.rgb(105, 124, 146)))
                }
            } catch (_: Exception) {
                runOnUiThread {
                    list.removeAllViews()
                    list.addView(txt("Could not reach Home Assistant. Check the server URL and access token in Settings.", 12f, Color.rgb(166, 83, 70)))
                }
            }
        }
    }

    private fun buildRoutinesPage(): LinearLayout {
        val page = pageShell("Routines", "Shortcuts and automations for your home.")
        val box = card("AUTOMATIONS", "ROUTINE MANAGEMENT")
        box.addView(txt("Routine creation is not connected yet. Your Home Assistant automations can be managed through its configured integrations; DUDE does not currently create or edit them.", 13f, Color.rgb(65, 84, 107)))
        box.addView(button("OPEN DEVICE CONNECTION SETTINGS") { showSectionTab("SETTINGS") },
            LinearLayout.LayoutParams(-1, 44).apply { topMargin = 12 })
        page.addView(box)
        return page
    }

    private fun buildAssistantPage(): LinearLayout {
        val page = pageShell("Assistant", "Talk to DUDE or send a quick command.")
        val box = card("DUDE", "VOICE ASSISTANT")
        val assistantOrb = txt("●", 72f, Color.rgb(51, 130, 204), true).apply { gravity = Gravity.CENTER }
        box.addView(assistantOrb, LinearLayout.LayoutParams(-1, 112))
        val assistantResponse = txt(response.text.toString(), 13f, Color.rgb(47, 67, 91))
        box.addView(assistantResponse, LinearLayout.LayoutParams(-1, -2))
        response = assistantResponse
        box.addView(button("START LISTENING") { startVoice(); speak("I'm listening. What's up?") },
            LinearLayout.LayoutParams(-1, 46).apply { topMargin = 12 })
        val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val assistantAsk = EditText(this).apply {
            hint = "Type a command…"
            setHintTextColor(Color.rgb(140, 155, 174))
            setTextColor(Color.rgb(29, 48, 70))
            textSize = 14f
            setSingleLine(true)
            background = shape(Color.rgb(246, 249, 252), Color.rgb(218, 227, 237), 10f)
            setPadding(12, 0, 12, 0)
        }
        ask = assistantAsk
        inputRow.addView(assistantAsk, LinearLayout.LayoutParams(0, 48, 1f).apply { rightMargin = 8 })
        inputRow.addView(button("SEND") {
            val q = assistantAsk.text.toString().trim()
            assistantAsk.setText("")
            if (q.isNotBlank()) handle(q)
        }, LinearLayout.LayoutParams(88, 48))
        box.addView(inputRow, LinearLayout.LayoutParams(-1, 48).apply { topMargin = 10 })
        page.addView(box)
        return page
    }

    private fun buildNewsPage(): LinearLayout {
        val page = pageShell("News", "Top headlines from your selected news feed.")
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (stories.isEmpty()) list.addView(txt("Headlines are loading. Check your connection and refresh.", 12f, Color.rgb(105, 124, 146)))
        stories.forEach { story ->
            val row = card(story.title, story.source.ifBlank { "NEWS" })
            row.addView(button("READ HEADLINE") { speak(story.title) }, LinearLayout.LayoutParams(-1, 40).apply { topMargin = 8 })
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 9 })
        }
        page.addView(list)
        page.addView(button("REFRESH HEADLINES") { loadDashboard(); showSectionTab("NEWS") },
            LinearLayout.LayoutParams(-1, 44).apply { topMargin = 10 })
        return page
    }

    private fun buildWeatherPage(): LinearLayout {
        val page = pageShell("Weather", "Current local conditions.")
        val box = card("LOCAL FORECAST", prefs.getString("weather_city", "CITY NOT SET").orEmpty())
        box.addView(txt(weatherText.ifBlank { "Set a city in Settings to load local weather." },
            16f, Color.rgb(37, 59, 84)))
        box.addView(button("REFRESH WEATHER") { loadDashboard(); showSectionTab("WEATHER") },
            LinearLayout.LayoutParams(-1, 44).apply { topMargin = 12 })
        page.addView(box)
        return page
    }

    private fun applySavedDashboardLayout(footer: View? = null) {
        if (!::dashboardPage.isInitialized) return
        val defaultOrder = listOf("OVERVIEW", "DUDE CHANNEL", "SMART HOME")
        val savedOrder = prefs.getString("dashboard_order", defaultOrder.joinToString("|")).orEmpty()
            .split("|").filter { it in dashboardSections.keys }
        val order = (savedOrder + defaultOrder.filter { it !in savedOrder }).distinct()
        val hidden = prefs.getStringSet("dashboard_hidden", emptySet()).orEmpty()
        dashboardPage.removeAllViews()
        order.filter { it !in hidden }.forEach { key ->
            val view = dashboardSections[key] ?: return@forEach
            val lp = view.layoutParams ?: LinearLayout.LayoutParams(-1, if (key == "OVERVIEW") 0 else 140)
            dashboardPage.addView(view, lp)
        }
        if (footer != null) dashboardPage.addView(footer)
    }

    private fun showLayoutEditor(parent: LinearLayout) {
        val keys = listOf("OVERVIEW", "DUDE CHANNEL", "SMART HOME")
        val order = prefs.getString("dashboard_order", keys.joinToString("|")).orEmpty()
            .split("|").filter { it in keys }.toMutableList()
        (keys - order.toSet()).forEach { order.add(it) }
        val hidden = prefs.getStringSet("dashboard_hidden", emptySet()).orEmpty().toMutableSet()
        val editor = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun redraw() {
            rows.removeAllViews()
            order.forEachIndexed { index, key ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    background = shape(Color.rgb(12, 31, 54), Color.rgb(39, 94, 140), 14f)
                    setPadding(8, 4, 8, 4)
                }
                val check = CheckBox(this).apply {
                    text = key; isChecked = key !in hidden; setTextColor(Color.WHITE); buttonTintList = android.content.res.ColorStateList.valueOf(cyan)
                    setOnCheckedChangeListener { _, checked -> if (checked) hidden.remove(key) else hidden.add(key) }
                }
                row.addView(check, LinearLayout.LayoutParams(0, 48, 1f))
                row.addView(button("↑") { if (index > 0) { val v=order.removeAt(index); order.add(index-1,v); redraw() } }, LinearLayout.LayoutParams(44,44))
                row.addView(button("↓") { if (index < order.lastIndex) { val v=order.removeAt(index); order.add(index+1,v); redraw() } }, LinearLayout.LayoutParams(44,44).apply { leftMargin=5 })
                rows.addView(row, LinearLayout.LayoutParams(-1, 54).apply { bottomMargin=6 })
            }
        }
        redraw()
        editor.addView(txt("DASHBOARD LAYOUT", 18f, Color.WHITE, true))
        editor.addView(txt("Choose what appears and use the arrows to change the order.", 11f, muted))
        editor.addView(rows)
        AlertDialog.Builder(this).setTitle("CUSTOMIZE DUDE")
            .setView(ScrollView(this).apply { addView(editor) })
            .setPositiveButton("SAVE LAYOUT") { _, _ ->
                prefs.edit().putString("dashboard_order", order.joinToString("|"))
                    .putStringSet("dashboard_hidden", hidden).apply()
                showDashboardTab()
            }.setNeutralButton("RESET") { _, _ ->
                prefs.edit().remove("dashboard_order").remove("dashboard_hidden").apply()
                showDashboardTab()
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun showDashboardTab() {
        applySavedDashboardLayout(dashboardFooter)
        mainScroll.removeAllViews()
        mainScroll.addView(dashboardPage)
        mainScroll.post { dashboardPage.minimumHeight = mainScroll.height }
    }

    private fun showSettingsTab() {
        mainScroll.removeAllViews()
        mainScroll.addView(settingsPage)
        mainScroll.post { settingsPage.minimumHeight = mainScroll.height }
    }

    private fun buildSettingsPage(): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 18, 18, 22) }
        fun section(title: String) { page.addView(txt(title, 13f, mint, true).apply { setPadding(0, 16, 0, 8) }) }
        fun field(title: String, value: String = "", type: Int = InputType.TYPE_CLASS_TEXT): EditText {
            page.addView(txt(title, 11f, cyan, true))
            return EditText(this).apply {
                setText(value); inputType = type; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(muted)
                background = shape(Color.rgb(3, 11, 27), Color.rgb(47, 118, 166), 12f); setPadding(12, 8, 12, 8)
                page.addView(this, LinearLayout.LayoutParams(-1, 48).apply { bottomMargin = 10 })
            }
        }
        page.addView(txt("DUDE SETTINGS", 24f, Color.WHITE, true))
        page.addView(txt("Tune your dashboard, voice, weather and connected home.", 12f, muted))
        section("WEATHER")
        val city = field("City or ZIP code", prefs.getString("weather_city", "").orEmpty())
        section("SMART HOME  •  HOME ASSISTANT")
        val url = field("Home Assistant URL", prefs.getString("ha_url", "").orEmpty())
        val token = field("Long-lived access token (leave blank to keep current)", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val light = field("Main light entity ID", prefs.getString("ha_light_entity", "").orEmpty())
        val plug = field("Smart plug entity ID", prefs.getString("ha_switch_entity", "").orEmpty())
        val vacuum = field("Vacuum entity ID", prefs.getString("ha_vacuum_entity", "").orEmpty())
        section("DASHBOARD & TABS")
        page.addView(button("MOVE TABS / CUSTOMIZE LAYOUT") { showLayoutEditor(page) }, LinearLayout.LayoutParams(-1, 48).apply { bottomMargin = 8 })
        page.addView(txt("Reorder dashboard sections, hide sections you do not use, and save your layout. Your choices stay on this tablet.", 10f, muted))
        section("VOICE & ASSISTANT")
        page.addView(button("CHOOSE DUDE'S VOICE") { chooseVoice() }, LinearLayout.LayoutParams(-1, 48).apply { bottomMargin = 8 })
        page.addView(button("START LISTENING FOR HEY DUDE") { startVoice(); speak("I'm back. What's up?") }, LinearLayout.LayoutParams(-1, 48).apply { bottomMargin = 8 })
        page.addView(txt("Voice quality depends on English voices installed on this tablet. Wake-word listening uses Android speech recognition.", 10f, muted))
        section("APP & DATA")
        page.addView(button("REFRESH WEATHER & NEWS") { loadDashboard(); speak("Refreshing the dashboard. One sec.") }, LinearLayout.LayoutParams(-1, 48).apply { bottomMargin = 8 })
        page.addView(txt("Weather and headlines use free online sources. Smart-home control requires a Home Assistant server and a valid token. No paid AI API is configured.", 10f, muted))
        page.addView(button("SAVE SETTINGS") {
            prefs.edit().putString("weather_city", city.text.toString().trim())
                .putString("ha_url", url.text.toString().trim())
                .putString("ha_light_entity", light.text.toString().trim())
                .putString("ha_switch_entity", plug.text.toString().trim())
                .putString("ha_vacuum_entity", vacuum.text.toString().trim()).apply()
            if (token.text.isNotBlank()) saveSecret("ha_token", token.text.toString().trim())
            loadDashboard(); showDashboardTab(); speak("Settings saved. All systems looking good.")
        }, LinearLayout.LayoutParams(-1, 52).apply { topMargin = 18 })
        return page
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

    private fun settings() { showSettingsTab() }

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
