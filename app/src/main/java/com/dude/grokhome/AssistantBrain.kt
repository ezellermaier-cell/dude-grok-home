package com.dude.grokhome

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local intent understanding for DUDE.
 * It deliberately matches meaning and common conversational phrasing, not exact sentences.
 * This is deterministic/offline intent routing, not a claim of unrestricted generative AI.
 */
class AssistantBrain {
    enum class Kind { GREETING, WEATHER, TIME, DATE, NEWS, JOKE, HELP, LIGHT_ON, LIGHT_OFF, PLUG_ON, PLUG_OFF, UNKNOWN }
    data class Intent(val kind: Kind, val original: String)

    fun understand(utterance: String): Intent {
        val q = utterance.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9' ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        fun hasAny(vararg terms: String) = terms.any { q.contains(it) }

        val kind = when {
            hasAny("what's the weather", "what is the weather", "weather like", "weather today",
                "forecast", "temperature outside", "how hot", "how cold", "is it raining",
                "will it rain", "do i need a jacket", "should i bring an umbrella", "is it sunny",
                "is it cloudy", "is it snowing", "outside right now", "outside like") -> Kind.WEATHER
            hasAny("what time", "current time", "tell me the time", "time is it") -> Kind.TIME
            hasAny("what's the date", "what is the date", "today's date", "what day is it", "which day") -> Kind.DATE
            hasAny("news", "headlines", "what's happening", "what is happening", "latest stories") -> Kind.NEWS
            hasAny("tell me a joke", "make me laugh", "say something funny", "joke") -> Kind.JOKE
            hasAny("what can you do", "help me", "your features", "how do i use you") -> Kind.HELP
            hasAny("turn on", "switch on", "turn the", "switch the", "light on") &&
                hasAny("light", "lights", "lamp", "lamps") -> Kind.LIGHT_ON
            hasAny("turn off", "switch off", "shut off", "lights off", "light off") &&
                hasAny("light", "lights", "lamp", "lamps") -> Kind.LIGHT_OFF
            hasAny("turn on", "switch on", "plug on") && hasAny("plug", "outlet") -> Kind.PLUG_ON
            hasAny("turn off", "switch off", "plug off") && hasAny("plug", "outlet") -> Kind.PLUG_OFF
            hasAny("hello", "hi dude", "hey dude", "good morning", "good afternoon", "good evening") -> Kind.GREETING
            else -> Kind.UNKNOWN
        }
        return Intent(kind, utterance)
    }

    fun greeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val hello = when (hour) { in 5..11 -> "Morning"; in 12..16 -> "Hey there"; in 17..21 -> "Evening"; else -> "You're up late" }
        return "$hello. DUDE is online, looking handsome, and only mildly overconfident. What's up?"
    }

    fun joke(): String = listOf(
        "I tried to make friends with the router. It said we needed a stronger connection.",
        "The washing machine told me a joke. It was a spin-off. I'm still processing it.",
        "I asked the lights to brighten my day. They said, 'You first.' Rude.",
        "I would tell you a joke about electricity, but it might be shocking."
    ).random()

    fun time(): String = "It's " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) +
        ". Yes, I checked. I do have a clock-shaped brain."
    fun date(): String = "Today is " + SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()) +
        ". A fine day to be the most useful thing on this tablet."
}
