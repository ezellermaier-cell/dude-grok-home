package com.dude.grokhome

/** Compatibility adapter: command interpretation is centralized in AssistantBrain. */
class LocalCommandRouter {
    data class Result(val reply: String, val domain: String? = null, val service: String? = null, val entityId: String? = null)
    private val brain = AssistantBrain()

    fun route(utterance: String, lightEntity: String, switchEntity: String): Result? =
        when (brain.understand(utterance).kind) {
            AssistantBrain.Kind.GREETING -> Result(brain.greeting())
            AssistantBrain.Kind.JOKE -> Result(brain.joke())
            AssistantBrain.Kind.LIGHT_ON -> device("Lights on. Let there be photons.", "light", "turn_on", lightEntity)
            AssistantBrain.Kind.LIGHT_OFF -> device("Lights off. Saving the planet, one switch at a time.", "light", "turn_off", lightEntity)
            AssistantBrain.Kind.PLUG_ON -> device("Plug switched on. Electricity, at your service.", "switch", "turn_on", switchEntity)
            AssistantBrain.Kind.PLUG_OFF -> device("Plug switched off. Power nap time.", "switch", "turn_off", switchEntity)
            else -> null
        }

    private fun device(reply: String, domain: String, service: String, entity: String): Result =
        if (entity.isBlank()) Result("I need your $domain device configured before I can do that.")
        else Result(reply, domain, service, entity)
}
