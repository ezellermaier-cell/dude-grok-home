# DUDE project status

## Implemented in the current app
- Android tablet dashboard with screen kept awake
- Tap-to-talk speech input and spoken text-to-speech replies
- Optional xAI Grok API connection; no Grok/X subscription credits are used
- Encrypted on-device storage for xAI and Home Assistant tokens
- Home Assistant REST connection
- Device discovery from Home Assistant entity states
- Dashboard device picker with on/off controls for discovered lights, switches, fans and covers
- Status display for climate, vacuum, sensors, locks and media-player entities
- GitHub Actions debug APK build

## Still not implemented
- A reliable always-on "Hey Dude" wake-word engine and foreground microphone service
- Direct vendor cloud authorization without Home Assistant
- Dedicated, full-featured controls for LG ThinQ, Sensi and Shark (discovery/status depends on the entities exposed by the configured Home Assistant integrations)
- Built-in live weather and news panels
- Calendar/reminders and a complete Echo Show-style widget dashboard

## Important setup
The app runs on the Galaxy Tab S4. Home Assistant is an optional bridge that must run on an always-on computer, NAS or supported host; it is not embedded in the tablet app. Configure each supported vendor integration in Home Assistant, then enter its URL and a long-lived access token in DUDE Settings. The app can discover the entities Home Assistant exposes.

The current voice experience is tap-to-talk. Android background microphone rules and wake-word engine licensing/model requirements mean that "Hey Dude" cannot be honestly claimed as active until that engine and service are integrated and tested on the tablet.
