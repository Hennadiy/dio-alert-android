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
- Mind's server polls alerts.in.ua and, on a change in a region, sends a high-priority **data-only FCM message** `{kind, region, regionEn, minutes?, test}` to the phones that follow it (`AirAlertNotifier` / `FcmService` in the service). Android wakes the app for it even when it is closed: `AlertMessagingService` shows the notification on the kind's channel (`alert_drones_v3` / `alert_missiles_v3` / `alert_end_v3`, which carry the vibration and no sound) and `AlertSound` plays the kind's sound itself on the alarm stream (alarm volume, heard on silent), waiting until it ends. Channel sounds were silent on some phones (Motorola), which is why the app plays it.
- The texts are the app's strings (`alert_<kind>_title` with the region, Ukrainian `%1$s` / English `%2$s`; `alert_<kind>_body`, `alert_end_duration_*`, `alert_test_body`), in the app's language.
- Nothing about the display is in the message, so channel ids and string names are the app's own business.
- **Sign in** and **Create a Mind account** are the app's own forms. Instead of the web checkbox they use **reCAPTCHA Enterprise** for Android (`AppCaptcha.kt`, invisible: the SDK scores the request and Mind's server checks the score). Sign-in posts `api/User/AppSignIn`, and the server answers with a new **device key** for this phone; the app keeps only that key (header `X-Device-Key`). Registration posts `api/User/AppRegister`; an admin approves new accounts. **Forgot password?** opens Mind's page. **Sign out** unregisters the phone and revokes that key.
- The reCAPTCHA key (type Android, package `com.dio.alert`, not secret) comes from the repo **variable** `RECAPTCHA_SITE_KEY` at build time (`BuildConfig.RECAPTCHA_SITE_KEY`); a build without it says sign-in isn't set up. The server needs the same key, the Cloud project id and an API key (see the service's CLAUDE.md).
- The app calls `api/AirAlert/RegisterDevice` with its FCM token on sign-in and on every start (token rotation, "last seen"). Disconnecting the phone in Mind revokes its key and signs the app out.

## Phone setup checks

While signed in, the app checks everything on the phone that can stop an alert (`MainActivity.setupIssues()`): the notification permission and app switch, each alert kind's channel and whether it may override Do Not Disturb (only the user can switch that on; the button opens the channel), battery optimisation, restricted background use, Data Saver, alarm volume, Do Not Disturb silencing alarms, and an app standby bucket of rare or restricted (Android rations its high-priority messages). The **Check your phone** card lists only what needs fixing, each with a button that fixes it or opens the right settings, and hides when nothing does; it re-checks on every return to the app. For battery it says what to pick for the phone it runs on (`batteryHintRes()`: Android 14+, 12–13, 8–11, Samsung, Xiaomi, OnePlus). Under the region status the app shows when the last alert arrived and how long it took from Mind (the message's `sentAt`), red above a minute: a phone that holds alerts back shows there. The bell and gear next to the title open the app's notification settings and app settings.

## Languages

English (`values/`, the default) and Ukrainian (`values-uk/`): a phone set to Ukrainian gets Ukrainian, any other language gets English. The **Language** button at the bottom picks one for the app only (AppCompat per-app languages; on Android 13+ also in Settings → Apps → Dio Alert → Language, from `res/xml/locales_config.xml`). Region names follow the app's language.

## Changing sounds or vibration

- Sounds: edit and run `python tools/make_sounds.py` (needs numpy); it writes `app/src/main/res/raw/*.wav`. Any short wav/ogg/mp3 works there.
- Vibration: `pulses(...)` in `AlertNotifier.kt`.
- Android fixes a channel's vibration when it is first created, so after a change **bump the channel ids** (`alert_drones_v3` → `_v4`) and add the old ids to `OBSOLETE_CHANNELS`. The sound is not in the channel: change the wav and it plays from the next build.

## Build

CI builds everything (`.github/workflows/build.yml`); there is no Gradle wrapper in the repo, so locally use Android Studio or Gradle 8.13 with JDK 17.

One-time setup:
1. **Firebase:** create a project at console.firebase.google.com, add an Android app with package `com.dio.alert`, download `google-services.json` and either commit it to `app/` or store it as the repo secret `GOOGLE_SERVICES_JSON`. In Project settings → Service accounts, generate a private key and give that JSON to the Mind server (see the service's CLAUDE.md, `FirebaseServiceAccountPath`).
2. **Upload key:** the secrets `KEYSTORE_BASE64` (`base64 -w0 release.p12`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (set; the key itself is kept outside the repo). Losing it means asking Google Play support to reset the upload key.

Release: every push to `main` builds the AAB; publishing to Google Play is a manual run (see below).

## Install on the phone

Install **Dio Alert** from Google Play (during internal testing: accept the tester invitation link first), open it and sign in with your Mind email and password (until then the app shows only the sign-in card). Then allow notifications, set Battery to "Unrestricted" (the button opens Dio Alert's settings page) and pick the region. To hear and feel each kind, an admin sends test alerts from the user's page in Mind (or use the test buttons on your own Mind profile).

## Google Play

The app is distributed only through Google Play; Play installs and updates it (Mind's profile card links to the store page). Each build is an AAB with version name `1.0.<run>` and version code = the CI run number.

Pushes only build the AAB (an artifact of the run). To publish, run the workflow by hand: **Actions → Build for Google Play → Run workflow**, tick **publish** and optionally write release notes; that build goes to the **internal testing** track and rolls out to testers (repo variable `PLAY_RELEASE_STATUS=draft` makes it a draft instead). It needs the secret `PLAY_SERVICE_ACCOUNT_JSON`: a Google Cloud service account (project `dio-alert`) with a JSON key, invited in Play Console → Users and permissions with release rights for Dio Alert. From the command line: `gh workflow run build.yml -f publish=true -f notes="..."`.

The `KEYSTORE_*` secrets are the **upload key**; Play re-signs the app with its own app signing key (Play App Signing).

Store material: `store/LISTING.md` (texts and Play Console answers), `store/icon-512.png` and `store/feature-graphic.png` (regenerate with `node tools/store-assets/make.js`), `PRIVACY.md` (the privacy policy URL).
