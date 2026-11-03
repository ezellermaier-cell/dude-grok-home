# DUDE project status

## Implemented
- Android 10+ landscape tablet dashboard; screen stays awake
- Live clock and date, refreshed every second
- Current weather by city using Open-Meteo, refreshed every 30 minutes
- Current BBC News RSS headlines, refreshed every 30 minutes
- Tap-to-talk speech recognition and spoken replies
- Optional xAI Grok chat; xAI API billing is separate from an X/Grok subscription
- Encrypted on-device storage for xAI and Home Assistant tokens
- Home Assistant device discovery for lights, switches, fans, covers, climate, vacuum, sensors, locks and media players
- On/off controls for lights, switches, fans and covers
- Start, pause and dock commands for supported Home Assistant vacuum entities
- Set-temperature command for supported Home Assistant climate entities
- Lock/unlock commands for supported lock entities
- GitHub Actions debug APK build

## Still not implemented
- Always-on "Hey Dude" wake word. This requires a wake-word engine, its access/configuration key and a foreground microphone service; current voice mode is tap-to-talk.
- Direct vendor sign-in/API implementations for Smart Life/Tuya, LG ThinQ, Sensi and Shark. These brands must first be added in Home Assistant, and the exact functions depend on the devices and integration support.
- Full calendar/reminders and the full Echo Show feature set.

## Setup
Set a city in DUDE Settings for weather. Connect Home Assistant URL and long-lived token in Settings; add the supported vendor integrations and devices in Home Assistant first. Home Assistant runs on a separate always-on PC, NAS or supported host; the DUDE app and dashboard run on the Galaxy Tab S4. The tablet alone cannot automatically gain access to private vendor cloud APIs or Alexa-linked devices.
