# DUDE 2.0 project status

## Rewritten implementation
- Native Android landscape dashboard for Android 10+ tablets.
- Full-screen, keep-awake presentation.
- Live clock/date, weather panel, news story cards and assistant conversation panel.
- Android speech recognition foreground service and text-to-speech.
- Local intent engine with paraphrase matching and witty responses.
- Home Assistant REST client, entity discovery and service execution.
- Settings and Android Keystore encryption for the Home Assistant token.
- Public key-free Open-Meteo weather and Google News RSS feeds.

## Requirements
- Samsung Galaxy Tab S4 / Android 10 is the target.
- Weather requires internet and a configured city.
- Voice requires microphone permission and a working Android speech-recognition provider.
- Home Assistant controls require a separate Home Assistant host, compatible integrations, network access and a long-lived access token.
- Vendor device support depends on integration-exposed entities and services.

## Known limitations
- Wake phrase recognition uses Android's speech provider; there is no bundled offline wake-word model.
- Local intent matching is not unrestricted generative AI. No paid AI API is configured.
- News images are best-effort.
- Calendar/reminder sync and direct vendor cloud sign-in are not implemented in this build.

## Verification
A successful GitHub Actions build confirms compilation and APK packaging only. Runtime behavior on the physical Tab S4 and connectivity to a user's Home Assistant devices require on-device testing.
