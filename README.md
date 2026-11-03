# DUDE 2.0 — Android Smart Home Dashboard

DUDE is a full-screen landscape smart-display app designed for the Samsung Galaxy Tab S4 running Android 10.

## Included
- Large live clock and date.
- Weather dashboard: current temperature, conditions, feels-like, humidity, wind, daily high/low and rain chance.
- Visual Google News headline cards with publisher images when available.
- Android speech recognition and spoken Text-to-Speech responses.
- Foreground voice loop for "Hey Dude" followed by a command.
- Local intent understanding for weather, time, date, news, jokes, greetings, lights, plugs and supported vacuum phrases.
- Witty DUDE response style.
- Home Assistant entity discovery and service controls.
- Settings for city, Home Assistant URL/token and preferred light/plug entities.
- Home Assistant token encrypted with Android Keystore.
- GitHub Actions APK build.

## Important limitations
- The wake phrase uses the speech-recognition provider installed on Android. It is not a dedicated offline acoustic wake-word model; recognition may use network services, consume battery or be interrupted by Android.
- The no-paid-key build uses deterministic local intent matching. It is not unrestricted generative AI conversation. A generative model/API would be required for that; an X/Grok subscription is not automatically API access.
- Smart Life/Tuya, LG ThinQ, Sensi and Shark devices work only when compatible Home Assistant integrations expose the relevant entities and services. Alexa pairing alone does not grant DUDE access.
- Home Assistant runs on a separate always-on PC, NAS or supported host.
- News images are best-effort; some publishers do not expose usable preview images.

## Build
Open GitHub Actions, run Build Android APK, and download the dude-debug-apk artifact from a successful run.

## First setup
1. Open DUDE Settings and enter your city.
2. If using smart-home controls, enter the Home Assistant URL and long-lived access token.
3. Discover devices and set your preferred light and plug entity IDs.
4. Grant microphone permission and leave DUDE open for voice listening.
