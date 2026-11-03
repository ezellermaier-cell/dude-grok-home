package com.dude.grokhome

/**
 * Routes simple, deterministic commands locally. These do not consume xAI API usage.
 * For real device actions, provide an exact Home Assistant entity ID in settings.
 */
class LocalCommandRouter {
    data class Result(val reply: String, val domain: String? = null, val service: String? = null, val entityId: String? = null)

    fun route(utterance: String, lightEntity: String, switchEntity: String): Result? {
        val q = utterance.lowercase().trim()
        if (q.contains("hello") || q.contains("hey dude")) return Result("Hey! Your favorite household algorithm has arrived.")
        if (q.contains("tell me a joke")) return Result("I tried to organize a hide and seek tournament with your appliances. The washing machine is still spinning.")
        if (q.contains("turn on") && q.contains("light")) {
            if (lightEntity.isBlank()) return Result("I can do that once you set your light entity in settings.")
            return Result("Turning on your light.", "light", "turn_on", lightEntity)
        }
        if (q.contains("turn off") && q.contains("light")) {
            if (lightEntity.isBlank()) return Result("Set your light entity in settings and I'll switch it off.")
            return Result("Turning off your light.", "light", "turn_off", lightEntity)
        }
        if (q.contains("turn on") && q.contains("plug")) {
            if (switchEntity.isBlank()) return Result("Set your smart plug entity in settings first.")
            return Result("Turning on your plug.", "switch", "turn_on", switchEntity)
        }
        if (q.contains("turn off") && q.contains("plug")) {
            if (switchEntity.isBlank()) return Result("Set your smart plug entity in settings first.")
            return Result("Turning off your plug.", "switch", "turn_off", switchEntity)
        }
        if (q.contains("what can you do")) return Result("I can answer locally, tell jokes, and control configured Home Assistant lights and plugs. Grok AI is optional.")
        return null
    }
}
