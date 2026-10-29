# Dio Alert: Privacy Policy

*Last updated: 29 October 2026*

**[Українською нижче](#політика-конфіденційності)**

Dio Alert shows air raid alerts for the region you pick (an oblast of Ukraine, or Kyiv) on your phone. It is a companion app of the Mind service (mind.dio-quiz.com.ua).

## What the app stores and sends

- **Your Mind account.** You sign in with your Mind email and password in the app's own form. They are sent once, over an encrypted connection, to Mind's server and are not stored on the phone. The server then gives the app a *connection key* for this phone; the app keeps only that key, plus your email address to show whom you are signed in as. Creating an account in the app sends your name, email, phone number and password to Mind's server the same way.
- **Your region.** The region you pick in the app (an oblast or city, not your GPS location) is sent to Mind's server, so it sends you that region's alerts.
- **Push token and phone model.** To deliver alerts, the app sends Mind's server its Firebase Cloud Messaging token and the phone's model name (for example "Samsung SM-A546B"), so you can recognise the phone in Mind's list of connected phones. The server also records when the phone last checked in.
- **Nothing else.** The app has no ads, no analytics and no tracking. It does not read the phone's location (GPS or network), contacts, files or any other data on the phone.

## Who receives the data

- **Mind's server** stores the connection key (as a one-way hash), push token, phone model, chosen region and last check-in time, only to send you alerts.
- **Google Firebase Cloud Messaging** delivers the alerts to the phone and processes the push token for that purpose ([Firebase privacy](https://firebase.google.com/support/privacy)).
- **Google reCAPTCHA** protects signing in and creating an account from bots: when you tap the button, the reCAPTCHA SDK sends Google information about the phone and how the app is used, and Google returns a score that Mind's server checks ([Google privacy policy](https://policies.google.com/privacy), [terms](https://policies.google.com/terms)).
- Alert data comes from alerts.in.ua; the app sends it nothing about you.

The data is not sold or shared with anyone else.

## Deleting your data

- **Sign out** in the app: the phone is removed from Mind and its connection key is deleted.
- In Mind, **Profile → Air raid alerts → Connected phones → Disconnect** removes a phone without the app.
- Uninstalling the app stops delivery; the server removes the phone's push token the next time delivery to it fails.
- To delete your Mind account and everything in it, follow [Deleting your account](DELETE-ACCOUNT.md).

---

# Політика конфіденційності

*Оновлено: 29 жовтня 2026*

Dio Alert показує на телефоні повітряні тривоги для вибраного регіону (області України чи Києва). Це супутній застосунок сервісу Mind (mind.dio-quiz.com.ua).

## Що застосунок зберігає й надсилає

- **Ваш акаунт Mind.** Ви входите з email і паролем Mind у формі самого застосунку. Вони один раз надсилаються захищеним з’єднанням на сервер Mind і не зберігаються на телефоні. Сервер видає застосунку *ключ підключення* для цього телефона; застосунок зберігає лише цей ключ і вашу email-адресу, щоб показати, під ким ви увійшли. Створення акаунта в застосунку так само надсилає серверу Mind ваше ім’я, email, телефон і пароль.
- **Ваш регіон.** Регіон, вибраний у застосунку (область чи місто, а не ваше GPS-місцезнаходження), надсилається серверу Mind, щоб той надсилав вам тривоги цього регіону.
- **Push-токен і модель телефона.** Щоб доставляти тривоги, застосунок надсилає серверу Mind токен Firebase Cloud Messaging і назву моделі телефона (наприклад, «Samsung SM-A546B»), щоб ви впізнали телефон у списку підключених у Mind. Сервер також запам'ятовує, коли телефон востаннє виходив на зв'язок.
- **Більше нічого.** У застосунку немає реклами, аналітики й відстеження. Він не читає місцезнаходження телефона (GPS чи мережу), контакти, файли чи інші дані на телефоні.

## Хто отримує дані

- **Сервер Mind** зберігає ключ підключення (як односторонній хеш), push-токен, модель телефона, вибраний регіон і час останнього зв'язку — лише для того, щоб надсилати вам тривоги.
- **Google Firebase Cloud Messaging** доставляє тривоги на телефон і для цього обробляє push-токен ([конфіденційність Firebase](https://firebase.google.com/support/privacy)).
- **Google reCAPTCHA** захищає вхід і створення акаунта від ботів: коли ви натискаєте кнопку, SDK reCAPTCHA надсилає Google відомості про телефон і користування застосунком, а Google повертає оцінку, яку перевіряє сервер Mind ([політика конфіденційності Google](https://policies.google.com/privacy), [умови](https://policies.google.com/terms)).
- Дані про тривоги надходять з alerts.in.ua; застосунок нічого про вас туди не надсилає.

Дані не продаються й нікому більше не передаються.

## Видалення даних

- **Вийти** в застосунку: телефон видаляється з Mind, а його ключ підключення — знищується.
- У Mind: **Профіль → Повітряна тривога → Підключені телефони → Відключити** видаляє телефон і без застосунку.
- Після видалення застосунку доставка припиняється; сервер видаляє push-токен телефона після першої невдалої спроби доставки.
- Щоб видалити акаунт Mind з усіма даними, див. [Видалення акаунта](DELETE-ACCOUNT.md#dio-alert-видалення-акаунта-й-даних).
