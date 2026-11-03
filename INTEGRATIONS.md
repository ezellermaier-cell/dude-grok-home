# DUDE 2.0 integrations

## Weather
Uses Open-Meteo geocoding and forecast endpoints. No API key is required. Configure a city in Settings. Current conditions include temperature, apparent temperature, humidity, wind, daily high/low and precipitation probability.

## News
Uses the Google News US RSS feed. Cards attempt to load image URLs from RSS media metadata or publisher preview metadata. Some publishers do not provide images or block image requests, so previews are best-effort.

## Voice and personality
DUDE uses Android SpeechRecognizer and TextToSpeech. A foreground service repeatedly listens for "Hey Dude", then captures the next phrase. This is not a dedicated offline wake-word model. Recognition availability, network requirements, battery use and background execution depend on the tablet's speech provider and Android settings.

The local assistant recognizes common paraphrases for weather, time, date, news, jokes, greetings and a limited set of smart-home commands. Its witty personality is built into local responses. Without a generative model/API it cannot answer arbitrary questions like ChatGPT or Grok.

## Smart home
DUDE connects to Home Assistant using its REST API. Home Assistant must run on an always-on PC, NAS or supported host. Add compatible vendor integrations in Home Assistant, then configure its URL and long-lived access token in DUDE Settings.

Supported entity families include lights, switches, fans, covers, climate, vacuums, locks, media players and sensors. Exact controls depend on the services exposed by each integration. Smart Life/Tuya, LG ThinQ, Sensi and Shark support depends on device model, region, firmware and integration availability.

Alexa-linked devices are not automatically accessible to DUDE. Alexa pairing does not grant this app access to Alexa's private device API. Direct vendor cloud sign-in is not implemented.

## Credentials
The Home Assistant token is encrypted locally with Android Keystore. Never commit credentials or API keys to source control.
