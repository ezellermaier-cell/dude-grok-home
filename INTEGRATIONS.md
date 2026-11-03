# DUDE integrations

## No Grok chatbot credits
DUDE does not sign in to or use your Grok/X chatbot subscription. It makes no xAI requests unless you add an xAI API key and ask a question that needs AI. xAI API usage is a separate paid service and is not included with a Grok subscription. Local commands and Home Assistant device actions do not call xAI.

## Smart-home compatibility
Alexa compatibility does not automatically grant another Android app permission to control a device. Each manufacturer has its own authorization and API rules. This project includes a Home Assistant REST bridge: Home Assistant can connect supported devices and expose them as entities. DUDE can call the Home Assistant service API using a URL, long-lived access token, and entity IDs.

This is a bridge integration, not a claim that all brands are already connected. Tuya/Smart Life, LG ThinQ, Sensi and Shark device support depends on whether the specific model/account is supported by the Home Assistant integration and whether it is configured there.

## Tablet-only limitation
The DUDE app and dashboard run on the Galaxy Tab S4. A Home Assistant server, if used as the device bridge, runs separately. Without a bridge, vendor cloud APIs and credentials must be integrated individually; some vendors do not offer public APIs for all functions.

## Wake word
A reliable always-on custom "Hey Dude" listener needs a licensed wake-word engine/model and an Android foreground microphone service. The current build is tap-to-talk; it does not secretly or continuously listen in the background.
