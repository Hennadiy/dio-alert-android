# Dio Alert

Android app that shows **Kyiv air raid alerts** from Mind (`mind-portal-service`), with a different vibration and sound per alert kind:

| Kind | Vibration | Sound |
|---|---|---|
| Drones | 2 short ×3 | 2 short buzzy beeps ×3 |
| Missiles (and alerts of unknown type) | 3 medium ×5 | 3 rising sirens ×5 |
| All clear | 1 long ×2 | 1 calm chime ×2 |

Web push can't do this: a website can't choose a notification's vibration or sound. Android takes both from the notification **channel**, so the app has one channel per kind (`AlertNotifier.kt`). Sounds use alarm audio, so they play at alarm volume even with the ringer on silent.

## How it works

- Mind's server polls alerts.in.ua and, on a change, sends a high-priority **FCM notification message** to every registered phone (`AirAlertNotifier` / `FcmService` in the service). It names the kind's channel (`alert_drones_v1` / `alert_missiles_v1` / `alert_end_v1`) and the texts as string resource names (`alert_<kind>_title`, `alert_<kind>_body`, `alert_end_duration_*`, `alert_test_body`), so **Android shows it itself**, with that channel's vibration and sound and in the phone's language, even when the app isn't running.
- While the app is open, Android passes the message to `AlertMessagingService` instead; it reads the data part `{kind, minutes?, test}` and shows the same notification (`AlertNotifier`).
- Channel ids and `alert_*` string names are a contract with the server: rename them on both sides.
- The phone connects with a Mind **device key** (header `X-Device-Key`): in Mind, Profile → Air raid alerts in Kyiv → Create connection key, then paste it into the app. The app calls `api/AirAlert/RegisterDevice` with its FCM token, and again on every start (token rotation, "last seen"). Revoking the key in Mind disconnects the phone.

## Changing sounds or vibration

- Sounds: edit and run `python tools/make_sounds.py` (needs numpy); it writes `app/src/main/res/raw/*.wav`. Any short wav/ogg/mp3 works there.
- Vibration: `pulses(...)` in `AlertNotifier.kt`.
- Android fixes a channel's sound and vibration when it is first created, so after a change **bump the channel ids** (`alert_drones_v1` → `_v2`) or installed phones keep the old ones.

## Build

CI builds everything (`.github/workflows/build.yml`); there is no Gradle wrapper in the repo, so locally use Android Studio or Gradle 8.11 with JDK 17.

One-time setup:
1. **Firebase:** create a project at console.firebase.google.com, add an Android app with package `com.dio.alert`, download `google-services.json` and either commit it to `app/` or store it as the repo secret `GOOGLE_SERVICES_JSON`. In Project settings → Service accounts, generate a private key and give that JSON to the Mind server (see the service's CLAUDE.md, `FirebaseServiceAccountPath`).
2. **Signing (recommended):** create a keystore once, e.g. `keytool -genkeypair -v -keystore release.jks -alias dioalert -keyalg RSA -keysize 2048 -validity 10000`, and add the secrets `KEYSTORE_BASE64` (`base64 -w0 release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Without them the APK is signed with a throwaway key and each update needs the old app uninstalled.

Release: `git tag v1.0.0 && git push --tags` builds the APK and publishes it as a GitHub release; Mind links to `releases/latest`. Every other push leaves the APK in the run's artifacts.

## Install on the phone

Download `dio-alert.apk`, allow installing from this source when Android asks, open Dio Alert, allow notifications, turn off battery optimisation (the app has buttons for both), paste the key and tap Connect. Use the test buttons to hear and feel each kind.
