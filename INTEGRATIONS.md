# DUDE integrations

## AI
DUDE calls the xAI API only when an AI answer is requested and an API key is configured. This is separately billed by xAI and does not use credits from a Grok/X subscription. Weather and news use public HTTPS feeds and do not require an xAI key.

## Dashboard
- Current time/date refreshes every second.
- Weather uses Open-Meteo geocoding and forecast services. Set the city in DUDE Settings.
- Headlines use the BBC News RSS feed.
- Dashboard data refreshes every 30 minutes while the app is running.

## Smart home
DUDE connects to Home Assistant using its REST API, URL and long-lived access token. Add supported vendor integrations in Home Assistant first, then use the Devices button in DUDE to discover exposed entities.

Supported generic entity actions currently include:
- Light, switch, fan and cover: toggle on/off.
- Vacuum: start, pause and return to dock.
- Climate: set target temperature.
- Lock: lock/unlock.
- Sensors and other entities: view reported state.

Smart Life/Tuya, LG ThinQ, Sensi and Shark support depends on the specific integration, device model and entity services exposed by Home Assistant. Alexa compatibility does not automatically grant third-party API access. The app does not yet implement separate direct cloud sign-in for each vendor.

Home Assistant is a separate service and must run on an always-on PC, NAS or supported host. The dashboard and DUDE app run on the Galaxy Tab S4.

## Voice and wake word
Current voice input is tap-to-talk using Android speech recognition, with Android text-to-speech replies. A genuine always-on "Hey Dude" wake word is not included yet. It requires an on-device wake-word engine, a licensed/configured keyword model and a foreground microphone service. Android 10 supports foreground microphone services, but background execution and battery optimization can still interrupt operation. DUDE must not claim to be continuously listening until this is integrated and tested on the tablet.
