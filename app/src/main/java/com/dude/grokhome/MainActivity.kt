package com.dude.grokhome

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.Animator
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
    private val navy = Color.rgb(5, 12, 27)
    private val panel = Color.rgb(13, 26, 48)
    private val cyan = Color.rgb(67, 205, 255)
    private val mint = Color.rgb(79, 242, 202)
    private val muted = Color.rgb(151, 177, 207)
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
    private val responseViews = mutableListOf<TextView>()

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
        if (prefs.getBoolean("voice_enabled", true)) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice()
            else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 502)
        }
        main.post(clockRunnable)
    }

    private val clockRunnable = object : Runnable {
        override fun run() { if (!isFinishing) { tick(); main.postDelayed(this, 1000) } }
    }

    private fun shape(color: Int, stroke: Int = Color.TRANSPARENT, radius: Float = 22f) =
        GradientDrawable().apply { setColor(color); cornerRadius = radius; if (stroke != Color.TRANSPARENT) setStroke(1, stroke) }

    private fun grad(a: Int, b: Int, stroke: Int = Color.TRANSPARENT, radius: Float = 24f) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(a, b)).apply {
            cornerRadius = radius
            if (stroke != Color.TRANSPARENT) setStroke(1, stroke)
        }

    private fun animateIn(view: View, delay: Long = 0L) {
        view.alpha = 0f
        view.translationY = 18f
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(520)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    private fun pulse(view: View) {
        val scale = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.075f, 1f).apply {
            duration = 2400
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
        }
        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0.78f, 1f, 0.78f).apply {
            duration = 2400
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
        }
        scale.start()
        alpha.start()
    }

    private fun txt(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color); includeFontPadding = true
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; textSize = 11f; isAllCaps = false; setTextColor(Color.WHITE); minHeight = 42; minimumHeight = 42; elevation = 0f; stateListAnimator = null; letterSpacing = 0.01f; setPadding(14, 0, 14, 0)
        background = grad(Color.rgb(22, 126, 194), Color.rgb(23, 184, 185), Color.rgb(82, 206, 238), 14f)
        setOnClickListener { action() }
    }

    private lateinit var navBar: LinearLayout
    private lateinit var dashboardWeb: android.webkit.WebView
    private var activeSection = "HOME"
    private var articlePage: View? = null
    private var articleReturnSection = "NEWS"

    private inner class DudeBridge {
        @android.webkit.JavascriptInterface fun ask(message: String) { runOnUiThread { handle(message) } }
        @android.webkit.JavascriptInterface fun talk() { runOnUiThread { startVoice(); speak("I'm listening. What's up?") } }
        @android.webkit.JavascriptInterface fun navigate(section: String) { runOnUiThread { showSectionTab(section.uppercase(Locale.US)) } }
        @android.webkit.JavascriptInterface fun discover() { runOnUiThread { discoverDevices() } }
        @android.webkit.JavascriptInterface fun openArticle(link: String, title: String, source: String) {
            runOnUiThread { openNewsArticle(DashboardData.NewsItem(title, "", source, link)) }
        }
    }

    private fun buildUi() {
        val wide = resources.configuration.screenWidthDp >= 600 &&
            resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        dashboardPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 2, 0, 18) }
        settingsPage = buildSettingsPage()
        // Keep the existing native pages and service objects initialized; the home experience
        // is rendered by a responsive, purpose-built WebView interface.
        buildDashboard(wide)
        navBar = LinearLayout(this)
        mainScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Color.rgb(4, 8, 20))
        }
        dashboardWeb = android.webkit.WebView(this).apply {
            setBackgroundColor(Color.rgb(4, 8, 20))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            addJavascriptInterface(DudeBridge(), "Dude")
            webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, url: String?): Boolean = true
                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    syncWebDashboard()
                }
            }
            loadUrl("file:///android_asset/dude_dashboard.html")
        }
        mainScroll.addView(dashboardWeb)
        setContentView(mainScroll)
    }

    private fun card(title: String, subtitle: String = ""): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            background = grad(Color.rgb(17, 34, 61), Color.rgb(9, 21, 42), Color.rgb(36, 83, 123), 22f)
            elevation = 7f
        }
        box.addView(txt(title, 11f, Color.rgb(116, 211, 245), true).apply { letterSpacing = 0.12f })
        if (subtitle.isNotBlank()) box.addView(txt(subtitle, 10f, Color.rgb(143, 169, 199)).apply { setPadding(0, 4, 0, 10) })
        return box
    }

    private fun buildDashboard(wide: Boolean) {
        dashboardSections.clear()
        val intro = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 12, 4, 18)
        }
        intro.addView(txt("YOUR HOME. YOUR COMMAND.", 10f, Color.rgb(91, 210, 245), true).apply { letterSpacing = 0.18f })
        intro.addView(txt("Everything, in sync.", 28f, Color.WHITE, true).apply { setPadding(0, 4, 0, 2) })
        intro.addView(txt("A smarter home starts here.", 12f, Color.rgb(145, 169, 199)))

        val overviewSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        overviewSection.addView(intro)
        val overview = LinearLayout(this).apply { orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }

        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val timeCard = card("LOCAL TIME", "YOUR DAY AT A GLANCE")
        clock = txt("--:--", if (wide) 54f else 48f, Color.WHITE, true)
        date = txt("LOADING", 11f, Color.rgb(119, 207, 235), true)
        timeCard.addView(clock)
        timeCard.addView(date.apply { setPadding(0, 3, 0, 12) })
        val online = txt("✦  DUDE SYSTEM ONLINE", 9f, Color.rgb(91, 235, 199), true)
        timeCard.addView(online)
        left.addView(timeCard, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = 12 })

        val wx = card("WEATHER INTELLIGENCE", "LIVE LOCAL CONDITIONS")
        weatherTitle = txt("SET YOUR CITY", 20f, Color.WHITE, true)
        weatherBody = txt("Choose your city in Settings to see the forecast.", 12f, Color.rgb(160, 183, 210))
        wx.addView(weatherTitle)
        wx.addView(weatherBody.apply { setPadding(0, 6, 0, 0) })
        left.addView(wx, LinearLayout.LayoutParams(-1, 0, 1f))

        val assistant = card("DUDE AI CORE", "ALWAYS READY • HEY DUDE")
        assistant.gravity = Gravity.CENTER_HORIZONTAL
        assistant.background = grad(Color.rgb(14, 43, 75), Color.rgb(7, 18, 39), Color.rgb(45, 166, 220), 26f)
        val orbStage = FrameLayout(this).apply {
            setPadding(0, 8, 0, 8)
        }
        val halo = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(29, 151, 218), Color.rgb(72, 245, 216))).apply {
                shape = GradientDrawable.OVAL
                setStroke(2, Color.rgb(106, 221, 255))
            }
            alpha = 0.18f
        }
        orb = txt("✦", if (wide) 58f else 52f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(31, 153, 224), Color.rgb(39, 224, 202))).apply {
                shape = GradientDrawable.OVAL
                setStroke(3, Color.rgb(145, 237, 255))
            }
            elevation = 12f
        }
        orbStage.addView(halo, FrameLayout.LayoutParams(116, 116, Gravity.CENTER))
        orbStage.addView(orb, FrameLayout.LayoutParams(82, 82, Gravity.CENTER))
        assistant.addView(orbStage, LinearLayout.LayoutParams(-1, 142))
        pulse(orb)
        pulse(halo)
        assistant.addView(txt("I'm all ears.", 16f, Color.WHITE, true))
        assistant.addView(txt("Ask a question or control your home.", 10f, Color.rgb(154, 190, 216)))
        assistant.addView(button("✦  TALK TO DUDE") { startVoice(); speak("I'm listening. What's up?") },
            LinearLayout.LayoutParams(-1, 46).apply { topMargin = 14 })

        val news = card("THE DAILY SIGNAL", "HEADLINES THAT MATTER")
        newsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        news.addView(newsRow, LinearLayout.LayoutParams(-1, 0, 1f))
        news.addView(button("EXPLORE THE NEWS  →") { showSectionTab("NEWS") },
            LinearLayout.LayoutParams(-1, 42).apply { topMargin = 8 })

        if (wide) {
            overview.addView(left, LinearLayout.LayoutParams(0, 316, 1.02f).apply { rightMargin = 12 })
            overview.addView(assistant, LinearLayout.LayoutParams(0, 316, 0.95f).apply { rightMargin = 12 })
            overview.addView(news, LinearLayout.LayoutParams(0, 316, 1.15f))
        } else {
            overview.addView(left, LinearLayout.LayoutParams(-1, 288).apply { bottomMargin = 12 })
            overview.addView(assistant, LinearLayout.LayoutParams(-1, 252).apply { bottomMargin = 12 })
            overview.addView(news, LinearLayout.LayoutParams(-1, 260))
        }
        overviewSection.addView(overview, LinearLayout.LayoutParams(-1, if (wide) 316 else -2).apply { bottomMargin = 14 })
        dashboardPage.addView(overviewSection)
        dashboardSections["OVERVIEW"] = overviewSection

        val assistantChannel = card("CONVERSATION STREAM", "VOICE + TEXT • YOUR AI COMPANION")
        response = txt("DUDE is online. Ask me anything.", 13f, Color.rgb(222, 237, 250))
        responseViews.add(response)
        assistantChannel.addView(response)
        val askRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val dashboardAsk = EditText(this).apply {
            hint = "Message DUDE…"
            setHintTextColor(Color.rgb(112, 145, 176))
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = shape(Color.rgb(8, 20, 39), Color.rgb(43, 104, 145), 14f)
            setPadding(14, 0, 14, 0)
        }
        ask = dashboardAsk
        askRow.addView(dashboardAsk, LinearLayout.LayoutParams(0, 48, 1f).apply { rightMargin = 9 })
        askRow.addView(button("SEND  ↗") {
            val q = dashboardAsk.text.toString().trim()
            dashboardAsk.setText("")
            if (q.isNotBlank()) handle(q)
        }, LinearLayout.LayoutParams(100, 48))
        assistantChannel.addView(askRow, LinearLayout.LayoutParams(-1, 48).apply { topMargin = 14 })
        dashboardPage.addView(assistantChannel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        dashboardSections["DUDE CHANNEL"] = assistantChannel

        val home = card("SMART HOME", "YOUR CONNECTED SPACE")
        val homeHead = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        homeHead.addView(txt("Lights • climate • laundry • security", 11f, Color.rgb(161, 187, 213)),
            LinearLayout.LayoutParams(0, 42, 1f))
        homeHead.addView(button("OPEN DEVICES  →") { showSectionTab("DEVICES") }, LinearLayout.LayoutParams(144, 42))
        home.addView(homeHead)
        val footer = txt("DUDE HOME OS     •     TECH IT WITH EZ", 9f, Color.rgb(100, 142, 174), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, 22, 0, 10)
            letterSpacing = 0.14f
        }
        dashboardFooter = footer
        dashboardPage.addView(home, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
        dashboardSections["SMART HOME"] = home

        animateIn(intro, 0)
        animateIn(left, 70)
        animateIn(assistant, 150)
        animateIn(news, 230)
        animateIn(assistantChannel, 300)
        animateIn(home, 370)
    }

    private fun renderNavigation() {
        if (!::navBar.isInitialized) return
        navBar.removeAllViews()
        val tabs = listOf("HOME", "DEVICES", "ROUTINES", "ASSISTANT", "NEWS", "WEATHER", "SETTINGS")
        tabs.forEach { tab ->
            val active = tab == activeSection
            val item = TextView(this).apply {
                text = when (tab) {
                    "HOME" -> "⌂\nHome"
                    "DEVICES" -> "▦\nDevices"
                    "ROUTINES" -> "↻\nRoutines"
                    "ASSISTANT" -> "◉\nAssistant"
                    "NEWS" -> "▤\nNews"
                    "WEATHER" -> "☼\nWeather"
                    else -> "⚙\nSettings"
                }
                gravity = Gravity.CENTER
                textSize = 9f
                maxLines = 2
                setTextColor(if (active) Color.rgb(104, 226, 255) else Color.rgb(122, 151, 181))
                typeface = Typeface.create("sans-serif", if (active) Typeface.BOLD else Typeface.NORMAL)
                background = if (active) grad(Color.rgb(18, 62, 91), Color.rgb(12, 40, 66), Color.rgb(45, 153, 190), 13f)
                    else shape(Color.TRANSPARENT, Color.TRANSPARENT, 13f)
                setOnClickListener { showSectionTab(tab) }
            }
            navBar.addView(item, LinearLayout.LayoutParams(0, 50, 1f).apply { setMargins(2, 0, 2, 0) })
        }
    }

    private fun showSectionTab(section: String) {
        activeSection = section
        articlePage = null
        if (section == "HOME") {
            mainScroll.removeAllViews()
            mainScroll.addView(dashboardWeb)
            return
        }
        mainScroll.removeAllViews()
        val page: View = when (section) {
            "SETTINGS" -> settingsPage
            "DEVICES" -> buildDevicesPage()
            "ROUTINES" -> buildRoutinesPage()
            "ASSISTANT" -> buildAssistantPage()
            "NEWS" -> buildNewsPage()
            "WEATHER" -> buildWeatherPage()
            else -> dashboardWeb
        }
        mainScroll.addView(page, android.view.ViewGroup.LayoutParams(-1, -2))
        mainScroll.post { mainScroll.scrollTo(0, 0) }
    }

    private fun pageShell(title: String, description: String): LinearLayout {
        val horizontal = if (resources.configuration.screenWidthDp >= 720) 26 else 16
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(horizontal, 20, horizontal, 30)
            setBackgroundColor(Color.rgb(5, 12, 27))
            clipToPadding = false
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heading.addView(txt(title, 27f, Color.WHITE, true).apply { letterSpacing = 0.01f },
            LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(button("← BACK") { showSectionTab("HOME") }, LinearLayout.LayoutParams(94, 40))
        page.addView(heading)
        page.addView(txt(description, 12f, Color.rgb(151, 177, 207)).apply { setPadding(0, 6, 0, 20) })
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
        responseViews.add(assistantResponse)
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
        if (stories.isEmpty()) list.addView(txt("Headlines are loading. Check your connection and refresh.", 12f, Color.rgb(151, 177, 207)))
        stories.forEach { story ->
            val row = card(story.title, story.source.ifBlank { "TOP MEDIA" })
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = grad(Color.rgb(16, 45, 76), Color.rgb(23, 74, 105), Color.rgb(47, 130, 165), 14f)
                contentDescription = "News image"
            }
            row.addView(image, LinearLayout.LayoutParams(-1, if (resources.configuration.screenWidthDp >= 600) 190 else 150).apply { bottomMargin = 10 })
            if (story.imageUrl.isNotBlank()) io.execute {
                try {
                    val connection = URL(story.imageUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 7000; connection.readTimeout = 9000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 DUDEHome/2.0")
                    val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) }
                    connection.disconnect()
                    if (bitmap != null) runOnUiThread { image.setImageBitmap(bitmap) }
                } catch (_: Exception) { }
            }
            row.addView(button("READ ARTICLE  ↗") { openNewsArticle(story) }, LinearLayout.LayoutParams(-1, 44).apply { topMargin = 8 })
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        }
        page.addView(list)
        page.addView(button("REFRESH HEADLINES") { loadDashboard(); showSectionTab("NEWS") },
            LinearLayout.LayoutParams(-1, 44).apply { topMargin = 10 })
        return page
    }

    private fun openNewsArticle(story: DashboardData.NewsItem) {
        if (story.link.isBlank()) { speak("I don't have an article link for that headline."); return }
        articleReturnSection = activeSection
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(5, 12, 27)) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(14, 8, 14, 8) }
        bar.addView(button("← BACK TO NEWS") { showSectionTab(articleReturnSection) }, LinearLayout.LayoutParams(150, 44))
        bar.addView(txt(story.source, 10f, muted, true), LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(bar)
        page.addView(txt(story.title, 20f, Color.WHITE, true).apply { setPadding(18, 8, 18, 14) })
        val reader = android.webkit.WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean = false
            }
            loadUrl(story.link)
        }
        page.addView(reader, LinearLayout.LayoutParams(-1, 0, 1f))
        articlePage = page
        mainScroll.removeAllViews()
        mainScroll.addView(page, android.view.ViewGroup.LayoutParams(-1, -1))
        mainScroll.scrollTo(0, 0)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (articlePage != null && activeSection != "HOME") {
            articlePage = null
            showSectionTab(articleReturnSection)
        } else if (activeSection != "HOME") showSectionTab("HOME")
        else super.onBackPressed()
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
                row.addView(txt("☰", 20f, cyan, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(34, 48))
                row.addView(check, LinearLayout.LayoutParams(0, 48, 1f))
                row.contentDescription = key
                row.setOnLongClickListener {
                    val clip = android.content.ClipData.newPlainText("dashboard-section", key)
                    row.startDragAndDrop(clip, View.DragShadowBuilder(row), key, 0)
                    true
                }
                rows.addView(row, LinearLayout.LayoutParams(-1, 54).apply { bottomMargin=6 })
            }
        }
        redraw()
        rows.setOnDragListener { _, event ->
            when (event.action) {
                android.view.DragEvent.ACTION_DRAG_STARTED -> true
                android.view.DragEvent.ACTION_DROP -> {
                    val dragged = event.localState as? String
                    var target: String? = null
                    for (i in 0 until rows.childCount) {
                        val child = rows.getChildAt(i)
                        if (event.y >= child.top && event.y <= child.bottom) {
                            target = child.contentDescription?.toString()
                            break
                        }
                    }
                    if (dragged != null && target != null && dragged != target) {
                        val from = order.indexOf(dragged)
                        val to = order.indexOf(target)
                        if (from >= 0 && to >= 0) {
                            val item = order.removeAt(from)
                            order.add(to, item)
                            redraw()
                        }
                    }
                    true
                }
                else -> true
            }
        }
        editor.addView(txt("DASHBOARD LAYOUT", 18f, Color.WHITE, true))
        editor.addView(txt("Press and hold the drag handle, then move a section. You can also hide sections.", 11f, muted))
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

    private fun showDashboardTab() { showSectionTab("HOME") }

    private fun showSettingsTab() { showSectionTab("SETTINGS") }

    private fun buildSettingsPage(): LinearLayout {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val horizontal = if (resources.configuration.screenWidthDp >= 720) 26 else 16
            setPadding(horizontal, 20, horizontal, 30)
            setBackgroundColor(Color.rgb(5, 12, 27))
            clipToPadding = false
        }
        fun label(text: String, size: Float = 11f, color: Int = muted, bold: Boolean = false) =
            txt(text, size, color, bold)
        fun group(title: String, description: String): LinearLayout {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 17, 18, 18)
                background = grad(Color.rgb(17, 34, 59), Color.rgb(9, 20, 39), Color.rgb(39, 91, 128), 20f)
                elevation = 3f
            }
            box.addView(label(title, 14f, cyan, true))
            box.addView(label(description, 10f, muted).apply { setPadding(0, 4, 0, 14) })
            page.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 13 })
            return box
        }
        fun field(box: LinearLayout, title: String, hint: String, value: String = "",
                  type: Int = InputType.TYPE_CLASS_TEXT): EditText {
            box.addView(label(title, 11f, Color.WHITE, true).apply { setPadding(0, 5, 0, 5) })
            return EditText(this).apply {
                setText(value)
                this.hint = hint
                inputType = type
                setSingleLine(true)
                textSize = 13f
                setTextColor(Color.WHITE)
                setHintTextColor(Color.rgb(112, 139, 168))
                background = shape(Color.rgb(5, 15, 31), Color.rgb(48, 105, 145), 12f)
                setPadding(13, 8, 13, 8)
                box.addView(this, LinearLayout.LayoutParams(-1, 48).apply { bottomMargin = 8 })
            }
        }
        fun helper(box: LinearLayout, text: String) {
            box.addView(label(text, 10f, muted).apply { setPadding(0, 3, 0, 8) })
        }
        fun action(box: LinearLayout, title: String, subtitle: String? = null, run: () -> Unit) {
            box.addView(button(title, run), LinearLayout.LayoutParams(-1, 46).apply { topMargin = 7 })
            if (subtitle != null) helper(box, subtitle)
        }

        val settingsHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val settingsTitles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        settingsTitles.addView(label("PERSONALIZE YOUR DUDE", 10f, mint, true))
        settingsTitles.addView(label("Settings", 30f, Color.WHITE, true).apply { setPadding(0, 3, 0, 4) })
        settingsHeader.addView(settingsTitles, LinearLayout.LayoutParams(0, -2, 1f))
        settingsHeader.addView(button("←  HOME") { showDashboardTab() }, LinearLayout.LayoutParams(104, 42))
        page.addView(settingsHeader)
        page.addView(label("Everything is saved on this tablet. Choose a section below and follow the short hints.", 12f, muted)
            .apply { setPadding(0, 0, 0, 18) })

        val weather = group("☀  WEATHER & LOCATION", "Turn this on to show local weather on the dashboard.")
        val weatherToggle = Switch(this).apply {
            text = "Enable weather"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("weather_enabled", true)
        }
        weather.addView(weatherToggle)
        val city = field(weather, "Your city or ZIP code", "Example: Teaneck, NJ",
            prefs.getString("weather_city", "").orEmpty())
        helper(weather, "Use a city and state for the most accurate match.")
        city.visibility = if (weatherToggle.isChecked) View.VISIBLE else View.GONE
        weatherToggle.setOnCheckedChangeListener { _, enabled -> city.visibility = if (enabled) View.VISIBLE else View.GONE }

        val home = group("⌂  SMART HOME CONNECTION", "Connect Home Assistant once to control supported lights, plugs, climate and other devices.")
        val haToggle = Switch(this).apply {
            text = "Enable smart-home connection"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("ha_enabled", prefs.getString("ha_url", "").orEmpty().isNotBlank())
        }
        home.addView(haToggle)
        val url = field(home, "Home Assistant address", "http://homeassistant.local:8123",
            prefs.getString("ha_url", "").orEmpty())
        val token = field(home, "Access token", "Paste long-lived access token (hidden)",
            "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val light = field(home, "Main light entity (optional)", "light.living_room",
            prefs.getString("ha_light_entity", "").orEmpty())
        val plug = field(home, "Smart plug entity (optional)", "switch.smart_plug",
            prefs.getString("ha_switch_entity", "").orEmpty())
        val vacuum = field(home, "Vacuum entity (optional)", "vacuum.robot",
            prefs.getString("ha_vacuum_entity", "").orEmpty())
        action(home, "DISCOVER MY DEVICES", "Find supported entities automatically after connecting.") { discoverDevices() }
        val haFields = (3 until home.childCount).map { home.getChildAt(it) }
        fun showHaFields(enabled: Boolean) { haFields.forEach { it.visibility = if (enabled) View.VISIBLE else View.GONE } }
        showHaFields(haToggle.isChecked)
        haToggle.setOnCheckedChangeListener { _, enabled -> showHaFields(enabled) }

        val ai = group("✦  AI BRAIN", "Turn on Gemini only if you want DUDE to answer general questions with your API key.")
        val aiToggle = Switch(this).apply {
            text = "Enable Gemini AI"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("gemini_enabled", loadSecret("gemini_api_key").isNotBlank())
        }
        ai.addView(aiToggle)
        val geminiKey = field(ai, "Gemini API key", "Paste key here (leave blank to keep current)", "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val connected = loadSecret("gemini_api_key").isNotBlank()
        helper(ai, if (connected) "Status: Gemini key is saved on this tablet." else "Status: Not connected yet. Create a key in Google AI Studio.")
        action(ai, "CLEAR SAVED GEMINI KEY", "The key is encrypted locally using Android Keystore.") {
            prefs.edit().remove("gemini_api_key_iv").remove("gemini_api_key_cipher").apply()
            speak("Gemini key cleared from this tablet.")
            showSectionTab("SETTINGS")
        }
        val aiFields = (3 until ai.childCount).map { ai.getChildAt(it) }
        fun showAiFields(enabled: Boolean) { aiFields.forEach { it.visibility = if (enabled) View.VISIBLE else View.GONE } }
        showAiFields(aiToggle.isChecked)
        aiToggle.setOnCheckedChangeListener { _, enabled -> showAiFields(enabled) }

        val voice = group("◉  VOICE & LISTENING", "Turn on voice features to use Hey Dude and spoken responses.")
        val voiceToggle = Switch(this).apply {
            text = "Enable voice assistant"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("voice_enabled", true)
        }
        voice.addView(voiceToggle)
        action(voice, "CHOOSE DUDE'S VOICE", "Available voices depend on the voices installed on Android.") { chooseVoice() }
        action(voice, "START LISTENING FOR HEY DUDE", "Microphone permission is required. Android speech recognition powers wake listening.") {
            startVoice(); speak("I'm back. What's up?")
        }
        val voiceFields = (3 until voice.childCount).map { voice.getChildAt(it) }
        fun showVoiceFields(enabled: Boolean) { voiceFields.forEach { it.visibility = if (enabled) View.VISIBLE else View.GONE } }
        showVoiceFields(voiceToggle.isChecked)
        voiceToggle.setOnCheckedChangeListener { _, enabled -> showVoiceFields(enabled) }

        val layout = group("▦  DASHBOARD LAYOUT", "Make the home screen feel like yours.")
        action(layout, "CUSTOMIZE HOME SCREEN", "Reorder sections, hide what you don't use, and save your layout.") { showLayoutEditor(page) }

        val data = group("↻  DATA & APP", "Refresh your home-screen information whenever you want.")
        action(data, "REFRESH WEATHER & NEWS", "Headlines and weather require an internet connection.") {
            loadDashboard(); speak("Refreshing the dashboard. One sec.")
        }

        val save = button("SAVE ALL SETTINGS") {
            if (url.text.isNotBlank() && !(url.text.toString().startsWith("http://") || url.text.toString().startsWith("https://"))) {
                speak("The Home Assistant address should start with http or https.")
                return@button
            }
            prefs.edit().putBoolean("weather_enabled", weatherToggle.isChecked)
                .putBoolean("ha_enabled", haToggle.isChecked)
                .putBoolean("gemini_enabled", aiToggle.isChecked)
                .putBoolean("voice_enabled", voiceToggle.isChecked)
                .putString("weather_city", city.text.toString().trim())
                .putString("ha_url", if (haToggle.isChecked) url.text.toString().trim().trimEnd('/') else "")
                .putString("ha_light_entity", light.text.toString().trim())
                .putString("ha_switch_entity", plug.text.toString().trim())
                .putString("ha_vacuum_entity", vacuum.text.toString().trim()).apply()
            if (haToggle.isChecked && token.text.isNotBlank()) saveSecret("ha_token", token.text.toString().trim())
            if (aiToggle.isChecked && geminiKey.text.isNotBlank()) saveSecret("gemini_api_key", geminiKey.text.toString().trim())
            loadDashboard()
            showDashboardTab()
            speak("Settings saved. Your home screen is updating.")
        }
        save.text = "✓   SAVE ALL SETTINGS"
        page.addView(save, LinearLayout.LayoutParams(-1, 54).apply { topMargin = 5 })
        page.addView(label("DUDE HOME OS  •  TECH IT WITH EZ", 9f, Color.rgb(91, 130, 163), true)
            .apply { gravity = Gravity.CENTER; setPadding(0, 20, 0, 4) })
        return page
    }

    private fun tick() {
        if (!::clock.isInitialized) return
        val timeText = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
        val dateText = SimpleDateFormat("EEEE  •  MMMM d", Locale.getDefault()).format(Date()).uppercase(Locale.getDefault())
        clock.text = timeText
        date.text = dateText
        if (::dashboardWeb.isInitialized) dashboardWeb.evaluateJavascript("window.setClock && window.setClock(" + org.json.JSONObject.quote(timeText) + "," + org.json.JSONObject.quote(dateText) + ")", null)
    }

    private fun setState(value: String) {
        if (::status.isInitialized) status.text = "● $value"
        if (::orb.isInitialized) orb.text = if (value.contains("THINK")) "◌" else if (value.contains("LISTEN")) "◉" else "✦"
        if (::dashboardWeb.isInitialized) dashboardWeb.evaluateJavascript("window.setDudeState && window.setDudeState(" + org.json.JSONObject.quote(value) + ")", null)
    }

    private fun speak(message: String) {
        responseViews.forEach { it.text = message }
        response.text = message
        setState("READY")
        if (ttsReady) {
            tts.setSpeechRate(0.94f)
            tts.setPitch(0.93f)
            @Suppress("DEPRECATION")
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    private val conversation = mutableListOf<GeminiClient.Turn>()

    private fun askGemini(utterance: String) {
        val key = loadSecret("gemini_api_key")
        if (!prefs.getBoolean("gemini_enabled", key.isNotBlank()) || key.isBlank()) {
            speak("I can answer that once you connect Gemini. Open Settings and add your free-tier Gemini API key.")
            return
        }
        responseViews.forEach { it.text = "Thinking… let me ask my AI brain." }
        response.text = "Thinking… let me ask my AI brain."
        setState("THINKING")
        io.execute {
            try {
                val answer = GeminiClient(key).reply(utterance, conversation.toList())
                conversation.add(GeminiClient.Turn("user", utterance))
                conversation.add(GeminiClient.Turn("model", answer))
                while (conversation.size > 16) conversation.removeAt(0)
                runOnUiThread { speak(answer) }
            } catch (error: Exception) {
                val message = error.message?.takeIf { it.isNotBlank() } ?: "I couldn't reach Gemini. Check your internet connection and API key."
                runOnUiThread { speak(message) }
            }
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
            AssistantBrain.Kind.UNKNOWN -> askGemini(utterance)
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
            val w = if (!prefs.getBoolean("weather_enabled", true) || city.isBlank()) "" else DashboardData.weather(city)
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
                syncWebDashboard()
            }
        }
    }

    private fun syncWebDashboard() {
        if (!::dashboardWeb.isInitialized) return
        val parts = weatherText.split("\\n").filter { it.isNotBlank() }
        val cityName = parts.firstOrNull() ?: "SET YOUR CITY IN SETTINGS"
        val detail = parts.drop(1).joinToString(" · ").ifBlank { "Add a city in Settings for local conditions." }
        val temp = Regex("-?[0-9]+°F").find(detail)?.value ?: "--°"
        val icon = when {
            detail.contains("rain", true) -> "🌧"
            detail.contains("snow", true) -> "❄"
            detail.contains("cloud", true) -> "☁"
            else -> "☀"
        }
        val weatherArgs = listOf(cityName, temp, detail, icon).joinToString(",") { org.json.JSONObject.quote(it) }
        dashboardWeb.evaluateJavascript("window.setWeather && window.setWeather($weatherArgs)", null)
        val items = JSONArray()
        stories.take(8).forEach { story ->
            items.put(org.json.JSONObject().put("title", story.title).put("source", story.source).put("image", story.imageUrl).put("link", story.link))
        }
        dashboardWeb.evaluateJavascript("window.setStories && window.setStories($items)", null)
    }

    private fun renderNews() {
        newsRow.removeAllViews()
        if (stories.isEmpty()) { newsRow.addView(txt("Headlines are taking a coffee break. Check Wi-Fi.", 12f, muted)); return }
        stories.take(4).forEach { story ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(6, 6, 6, 6)
                background = shape(Color.rgb(5, 20, 40), Color.rgb(35, 80, 120), 13f)
                setOnClickListener { openNewsArticle(story) }
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
        if (requestCode == 502 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && prefs.getBoolean("voice_enabled", true)) startVoice()
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
