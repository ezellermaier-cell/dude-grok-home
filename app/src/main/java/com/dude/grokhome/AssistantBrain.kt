package com.dude.grokhome

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Local conversation and intent layer. Understands common ways people express
 * the same request. It is deterministic and key-free; it is not a generative LLM.
 */
class AssistantBrain {
    enum class Kind {
        GREETING, WEATHER, TIME, DATE, NEWS, JOKE, HELP,
        LIGHT_ON, LIGHT_OFF, PLUG_ON, PLUG_OFF,
        VACUUM_START, VACUUM_STOP, VACUUM_DOCK,
        THERMOSTAT, UNKNOWN
    }

    data class Intent(val kind: Kind, val original: String)

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace("what's", "what is")
        .replace("whats", "what is")
        .replace("it's", "it is")
        .replace("isn't", "is not")
        .replace(Regex("[^a-z0-9° ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun understand(utterance: String): Intent {
        val q = normalize(utterance)
        fun any(vararg phrases: String) = phrases.any { q.contains(it) }
        val kind = when {
            any("weather", "forecast", "temperature outside", "outside temperature",
                "how hot", "how cold", "is it raining", "will it rain", "going to rain",
                "do i need a jacket", "bring an umbrella", "need an umbrella",
                "is it sunny", "is it cloudy", "is it snowing", "outside like",
                "what should i wear", "weather like") -> Kind.WEATHER
            any("what time", "time is it", "current time", "tell me the time") -> Kind.TIME
            any("what is the date", "today date", "what day is it", "day of the week") -> Kind.DATE
            any("news", "headlines", "what is happening", "what is going on", "latest stories") -> Kind.NEWS
            any("joke", "make me laugh", "something funny", "cheer me up") -> Kind.JOKE
            any("what can you do", "help me", "your features", "how do i use you") -> Kind.HELP
            any("vacuum", "robot") && any("start", "clean", "vacuum the floor") -> Kind.VACUUM_START
            any("vacuum", "robot") && any("stop", "pause") -> Kind.VACUUM_STOP
            any("vacuum", "robot") && any("dock", "charge", "go home") -> Kind.VACUUM_DOCK
            any("light", "lights", "lamp", "lamps") && any("turn on", "switch on", "put on", "make brighter") -> Kind.LIGHT_ON
            any("light", "lights", "lamp", "lamps") && any("turn off", "switch off", "shut off", "kill the lights") -> Kind.LIGHT_OFF
            any("plug", "outlet") && any("turn on", "switch on", "power on") -> Kind.PLUG_ON
            any("plug", "outlet") && any("turn off", "switch off", "power off") -> Kind.PLUG_OFF
            any("hello", "hi dude", "hey dude", "good morning", "good afternoon", "good evening") -> Kind.GREETING
            else -> Kind.UNKNOWN
        }
        return Intent(kind, utterance)
    }

    fun greeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..11 -> "Morning"
            in 12..16 -> "Hey there"
            in 17..21 -> "Evening"
            else -> "You're up late"
        }
        return "$greeting. DUDE is online, looking sharp, and only mildly overconfident. What can I do for you?"
    }

    fun joke(): String = listOf(
        "I asked the router for relationship advice. It said we needed a stronger connection.",
        "I tried to organize hide and seek with the appliances. The washing machine is still spinning.",
        "I told the lights to brighten my day. They said, 'You first.' Honestly, rude.",
        "I would tell you a joke about electricity, but it might be shocking."
    ).random()

    fun time(): String =
        "It's " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) +
            ". You're welcome. I keep time so you don't have to."

    fun date(): String =
        "Today is " + SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()) +
            ". A perfectly good day to get things done. Or not. I'm not your boss."
}
