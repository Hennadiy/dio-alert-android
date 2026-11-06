# Dio Alert

Android app that shows **air raid alerts for the region the user picks** (any oblast, Kyiv or Sevastopol) from Mind (`mind-portal-service`), with a different vibration and sound per alert kind:

| Kind | Vibration | Sound |
|---|---|---|
| Drones | 2 short ×3 | 2 short buzzy beeps ×3 |
| Missiles (and alerts of unknown type) | 3 medium ×5 | 3 rising sirens ×5 |
| All clear | 1 long ×2 | 1 calm chime ×2 |

Web push can't do this: a website can't choose a notification's vibration or sound. The app plays both itself (`AlertSound`, `AlertVibration`) as an alarm, so they play at alarm volume even with the ringer on silent.

## How it works

- **Region:** the status card has a region picker (the list comes from `api/AirAlert/GetRegions`; default Kyiv city, uid 31). The phone registers with its `regionUid`; changing it re-registers, and the status is `GetStatus?regionUid=`.
- Mind's server polls alerts.in.ua and, on a change in a region, sends a high-priority **data-only FCM message** `{kind, region, regionEn, minutes?, test}` to the phones that follow it (`AirAlertNotifier` / `FcmService` in the service). Android wakes the app for it even when it is closed: `AlertMessagingService` shows the notification on the kind's channel (`alert_drones_v4` / `alert_missiles_v4` / `alert_end_v4`, silent and without vibration), `AlertVibration` vibrates the kind's pattern as an alarm, and `AlertSound` plays the kind's sound itself on the alarm stream (alarm volume, heard on silent), fading in over 3 s and waiting until it ends. Swiping the notification away or its **Stop sound** button stops both (`StopAlertReceiver`); tapping it stops them even on the lock screen (`AlertTapActivity`, shown when locked, then opens the app), and so does opening the app. A general alert uses the missile sound the user picked. The play icon in the title bar opens a dialog that plays a test missile, drone or all-clear alert on the phone. The **Alert sound and vibration** card switches each off (both on by default) sets the sound's loudness within the alarm volume (`MediaPlayer.setVolume`) and the sound per alert kind from `AlertSounds.kt` (17 built-in sounds, 6 of them soft; the chooser plays each one, `Prefs.sound(kind)` keeps the pick); turning sound or vibration off asks for confirmation. Channel sounds were silent on some phones (Motorola), which is why the app plays it.
- The texts are the app's strings (`alert_<kind>_title` with the region, Ukrainian `%1$s` / English `%2$s`; `alert_<kind>_body`, `alert_end_duration_*`, `alert_test_body`), in the app's language.
- Nothing about the display is in the message, so channel ids and string names are the app's own business.
- **Sign in** and **Create a Mind account** are the app's own forms. Instead of the web checkbox they use **reCAPTCHA Enterprise** for Android (`AppCaptcha.kt`, invisible: the SDK scores the request and Mind's server checks the score). Sign-in posts `api/User/AppSignIn`, and the server answers with a new **device key** for this phone; the app keeps only that key (header `X-Device-Key`). Registration posts `api/User/AppRegister`; an admin approves new accounts. **Forgot password?** opens Mind's page. **Sign out** unregisters the phone and revokes that key. Signed in, the connection card shows the account bubble and, only if the account's other phones get alerts too, which ones (`AirAlert/GetDevicesForApp`).
- The reCAPTCHA key (type Android, package `com.dio.alert`, not secret) comes from the repo **variable** `RECAPTCHA_SITE_KEY` at build time (`BuildConfig.RECAPTCHA_SITE_KEY`); a build without it says sign-in isn't set up. The server needs the same key, the Cloud project id and an API key (see the service's CLAUDE.md).
- The app calls `api/AirAlert/RegisterDevice` with its FCM token on sign-in and on every start (token rotation, "last seen"). Disconnecting the phone in Mind revokes its key and signs the app out.

## Alert map

Signed in, a card under the region shows the official alert map of Ukraine, map.ukrainealarm.com, in a WebView (`setUpAlertMap()`). The page updates itself; after loading, `MAP_ONLY_JS` accepts its intro panel and hides everything but the map (header, notices, buttons, promo banner, scale; the site's class names as of 2026-11-06). Links that leave it open in the browser, and dragging inside it moves the map rather than the page. It is someone else's site: if it changes or refuses to load, the card shows whatever the WebView gets.

## Phone setup checks

While signed in, the app checks everything on the phone that can stop an alert (`MainActivity.setupIssues()`): the notification permission and app switch, each alert kind's channel and whether it may override Do Not Disturb (one entry per kind, since only the user can switch that on; each button opens its channel), battery optimisation, restricted background use, Data Saver, alarm volume, Do Not Disturb silencing alarms, an app standby bucket of rare or restricted (Android rations its high-priority messages), and Adaptive battery being on. Maker settings no API can read (OnePlus/Oppo/Realme sleep standby optimisation, locking in recents, Xiaomi autostart, Samsung never-sleeping apps) are listed as steps; since they can't be read, their result is checked instead: they hide once an alert (or test) arrives within 30 s with the screen off (`Prefs.lastDelivery`), and come back if a later one is slower. The **Check your phone** card lists only what needs fixing, each with a button that fixes it or opens the right settings, and hides when nothing does; it re-checks on every return to the app. For battery it says what to pick for the phone it runs on (`batteryHintRes()`: Android 14+, 12–13, 8–11, Samsung, Xiaomi, OnePlus). The app records when each alert arrived and how long it took from Mind (the message's `sentAt`, with whether the screen was off); that drives the maker steps below. The bell and gear next to the title open the app's notification settings and app settings.

## Languages

English (`values/`, the default) and Ukrainian (`values-uk/`): a phone set to Ukrainian gets Ukrainian, any other language gets English. The **Language** button at the bottom picks one for the app only (AppCompat per-app languages; on Android 13+ also in Settings → Apps → Dio Alert → Language, from `res/xml/locales_config.xml`). Region names follow the app's language. The bundle turns off Play's language splits (`bundle.language.enableSplit = false` in `app/build.gradle.kts`), so both languages are installed whatever the phone's language; without that the in-app switch did nothing.

## Changing sounds or vibration

- Sounds: edit and run `python tools/make_sounds.py` (needs numpy); it writes `app/src/main/res/raw/*.wav` (the three defaults `alert_*` and the choices `sound_*`). Any short wav/ogg/mp3 works there; list a new one in `AlertSounds.kt` with a name in both languages. Keep ids stable: they are what the phone stores.
- Vibration: `pulses(...)` in `AlertVibration.kt`.
- Vibration patterns are in `AlertVibration.kt`, sounds in `res/raw`; neither is part of the channel, so changes take effect with the next build. If channel settings ever change, bump the ids (`_v4` → `_v5`) and add the old ones to `OBSOLETE_CHANNELS`.

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

Pushes only build the AAB (an artifact of the run). To publish, run the workflow by hand: **Actions → Build for Google Play → Run workflow**, tick **publish**, pick the track (`internal` or `alpha` = closed testing) and optionally write release notes. It is sent for review when the console has nothing else pending; otherwise Play only accepts it unsent, and it goes out from Play Console → **Publishing overview → Send changes for review** (the workflow tries both). Repo variable `PLAY_RELEASE_STATUS=draft` keeps it a draft. It needs the secret `PLAY_SERVICE_ACCOUNT_JSON`: a Google Cloud service account (project `dio-alert`) with a JSON key, invited in Play Console → Users and permissions with release rights for Dio Alert. From the command line: `gh workflow run build.yml -f publish=true -f track=alpha -f notes="..."`.

The `KEYSTORE_*` secrets are the **upload key**; Play re-signs the app with its own app signing key (Play App Signing).

Store material: `store/LISTING.md` (texts and Play Console answers), `store/icon-512.png` and `store/feature-graphic.png` (regenerate with `node tools/store-assets/make.js`), `PRIVACY.md` (the privacy policy URL).
