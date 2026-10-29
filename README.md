# Dio Alert

Android app that shows **air raid alerts for the region the user picks** (any oblast, Kyiv or Sevastopol) from Mind (`mind-portal-service`), with a different vibration and sound per alert kind:

| Kind | Vibration | Sound |
|---|---|---|
| Drones | 2 short ×3 | 2 short buzzy beeps ×3 |
| Missiles (and alerts of unknown type) | 3 medium ×5 | 3 rising sirens ×5 |
| All clear | 1 long ×2 | 1 calm chime ×2 |

Web push can't do this: a website can't choose a notification's vibration or sound. Android takes both from the notification **channel**, so the app has one channel per kind (`AlertNotifier.kt`). Sounds use alarm audio, so they play at alarm volume even with the ringer on silent.

## How it works

- **Region:** the status card has a region picker (the list comes from `api/AirAlert/GetRegions`; default Kyiv city, uid 31). The phone registers with its `regionUid`; changing it re-registers, and the status is `GetStatus?regionUid=`.
- Mind's server polls alerts.in.ua and, on a change in a region, sends a high-priority **FCM notification message** to the phones that follow it (`AirAlertNotifier` / `FcmService` in the service). It names the kind's channel (`alert_drones_v2` / `alert_missiles_v2` / `alert_end_v2`) and the texts as string resource names (`alert_<kind>_title`, `alert_<kind>_body`, `alert_end_duration_*`, `alert_test_body`; titles take the region name as two args, Ukrainian strings use `%1$s`, English `%2$s`), so **Android shows it itself**, with that channel's vibration and sound and in the phone's language, even when the app isn't running.
- While the app is open, Android passes the message to `AlertMessagingService` instead; it reads the data part `{kind, region, regionEn, minutes?, test}` and shows the same notification (`AlertNotifier`).
- Channel ids and `alert_*` string names are a contract with the server: rename them on both sides.
- **Sign in** and **Create a Mind account** are the app's own forms. Instead of the web checkbox they use **reCAPTCHA Enterprise** for Android (`AppCaptcha.kt`, invisible: the SDK scores the request and Mind's server checks the score). Sign-in posts `api/User/AppSignIn`, and the server answers with a new **device key** for this phone; the app keeps only that key (header `X-Device-Key`). Registration posts `api/User/AppRegister`; an admin approves new accounts. **Forgot password?** opens Mind's page. **Sign out** unregisters the phone and revokes that key.
- The reCAPTCHA key (type Android, package `com.dio.alert`, not secret) comes from the repo **variable** `RECAPTCHA_SITE_KEY` at build time (`BuildConfig.RECAPTCHA_SITE_KEY`); a build without it says sign-in isn't set up. The server needs the same key, the Cloud project id and an API key (see the service's CLAUDE.md).
- The app calls `api/AirAlert/RegisterDevice` with its FCM token on sign-in and on every start (token rotation, "last seen"). Disconnecting the phone in Mind revokes its key and signs the app out.

## Phone setup help

Above the battery button the app says what to pick on the settings page it opens, for the phone it runs on (`batteryHintRes()`: Android 14+, 12–13, 8–11, Samsung, Xiaomi). Mind's guide (`/guides?tab=dioAlert`) has the same per phone, plus a no-sound checklist.

## Languages

English (`values/`, the default) and Ukrainian (`values-uk/`): a phone set to Ukrainian gets Ukrainian, any other language gets English. The **Language** button at the bottom picks one for the app only (AppCompat per-app languages; on Android 13+ also in Settings → Apps → Dio Alert → Language, from `res/xml/locales_config.xml`). Region names follow the app's language.

## Changing sounds or vibration

- Sounds: edit and run `python tools/make_sounds.py` (needs numpy); it writes `app/src/main/res/raw/*.wav`. Any short wav/ogg/mp3 works there.
- Vibration: `pulses(...)` in `AlertNotifier.kt`.
- Android fixes a channel's sound and vibration when it is first created, so after a change **bump the channel ids** (`alert_drones_v2` → `_v3`, here and in the server) or installed phones keep the old ones; add the old ids to `OBSOLETE_CHANNELS`. Sounds are referenced by name (`android.resource://…/raw/<name>`): a numeric resource id changes between builds and silently breaks the channel (that is why v1 was replaced).

## Build

CI builds everything (`.github/workflows/build.yml`); there is no Gradle wrapper in the repo, so locally use Android Studio or Gradle 8.13 with JDK 17.

One-time setup:
1. **Firebase:** create a project at console.firebase.google.com, add an Android app with package `com.dio.alert`, download `google-services.json` and either commit it to `app/` or store it as the repo secret `GOOGLE_SERVICES_JSON`. In Project settings → Service accounts, generate a private key and give that JSON to the Mind server (see the service's CLAUDE.md, `FirebaseServiceAccountPath`).
2. **Upload key:** the secrets `KEYSTORE_BASE64` (`base64 -w0 release.p12`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (set; the key itself is kept outside the repo). Losing it means asking Google Play support to reset the upload key.

Release: every push to `main` builds the AAB and, once Play is connected, uploads it to Google Play (see below).

## Install on the phone

Install **Dio Alert** from Google Play (during internal testing: accept the tester invitation link first), open it and sign in with your Mind email and password (until then the app shows only the sign-in card). Then allow notifications, set Battery to "Unrestricted" (the button opens Dio Alert's settings page) and pick the region. To hear and feel each kind, an admin sends test alerts from the user's page in Mind (or use the test buttons on your own Mind profile).

## Google Play

The app is distributed only through Google Play; Play installs and updates it (Mind's profile card links to the store page). Each build is an AAB with version name `1.0.<run>` and version code = the CI run number.

CI uploads the AAB to the **internal testing** track on every push to `main` once the secret `PLAY_SERVICE_ACCOUNT_JSON` is set (a Google Cloud service account with release permission in Play Console). Before the app's first review Play only takes draft releases; afterwards set the repo variable `PLAY_RELEASE_STATUS=completed`. The very first AAB has to be uploaded by hand in Play Console (download it from the run's artifacts).

The `KEYSTORE_*` secrets are the **upload key**; Play re-signs the app with its own app signing key (Play App Signing).

Store material: `store/LISTING.md` (texts and Play Console answers), `store/icon-512.png` and `store/feature-graphic.png` (regenerate with `node tools/store-assets/make.js`), `PRIVACY.md` (the privacy policy URL).
