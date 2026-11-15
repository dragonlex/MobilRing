# MobilRing — поиск потерянных вещей

Android-приложение для бюро находок: человек публикует объявление о пропаже или находке,
отмечает место и дату, а серверный скрипт сам находит совпадения и присылает их в раздел
«Уведомления». Контакты автора подставляются из профиля, из уведомления можно сразу позвонить.

Написан на Kotlin + Jetpack Compose, данные — Firebase Realtime Database, вход — анонимный.

## Экраны

| Экран | Файл | Что делает |
|---|---|---|
| Главный | `MainActivity` + `ui/screens/HomeScreen.kt` | кнопки «Сообщить о пропаже/находке», бейдж уведомлений, аватар |
| Все объявления | `AllAdsActivity` | лента потерь и находок, фильтры «Моё / ВСЕ / ПОТЕРИ / НАХОДКИ» и по категориям |
| Просмотр объявления | `AdDetailActivity` | полное описание, контакты автора, «ПОЗВОНИТЬ», «НАПИСАТЬ В ЧАТ»; у своего объявления — «ОТМЕТИТЬ КАК НАЙДЕНО» / «ВЕРНУТЬ В ПОИСК» и «УДАЛИТЬ ОБЪЯВЛЕНИЕ» |
| Сообщения | `ChatsActivity` | список переписок с последним сообщением и датой |
| Чат | `ChatActivity` | переписка по конкретному объявлению, сообщения обновляются в реальном времени |
| Уведомления | `NotificationsActivity` | карточки совпадений из узла `matches` |
| Профиль | `ProfileActivity` | ФИО, адрес, телефон, фото; без них публикация недоступна |
| Новая заявка | `ReportActivity` | форма с выбором даты потери/находки |
| Справка | `HelpActivity` | инструкция и правила безопасности |

## Как устроено

Три слоя, которые не смешиваются:

- `data/` — модели и работа с базой: `FirebaseRepo`, `DbFeed`, `AuthGate`, `ProfileStore`.
- `ui/` — внешний вид: экраны (`HomeScreen` и др.), компоненты (`DoodleButton`, `SimpleScreen`,
  `AvatarImage`), тема.
- `*Activity.kt` в корне — «клей»: берут данные из первого слоя, отдают во второй,
  а нажатия превращают в переходы между экранами.

Ключевой механизм — `DbFeed`. Он вешает слушателей на четыре узла базы и при любом изменении
пересобирает списки объявлений и уведомлений, поэтому лента обновляется сама, без pull-to-refresh.

### Схема базы

```
lost/{key}     id, name, location, info, date, eventDate, closed, userId, category
found/{key}    то же самое
users/{uid}    name, phone, address, about      — контакты автора
matches/{key}  lostId, findId, createdAt, score, confidence, method, reason
chats/{chatId} adId, adTitle, adType, createdAt, lastText, lastAt, participants
chats/{chatId}/messages/{pushId}  senderId, senderName, text, at
```

`chatId` — два uid в отсортированном порядке, соединённые `__`, плюс `__adId`,
поэтому у каждой пары «объявление + собеседник» ровно одна переписка.

`closed` — объявление закрыто: вещь нашлась или вернулась владельцу. Закрытые
объявления остаются в базе и в переписке, но помечаются бейджем «ВЕЩЬ НАЙДЕНА».

`date` — время публикации, `eventDate` — дата потери/находки (полночь по местному времени
отправителя). Ключи в базе строчные: при чтении в `matcher.py` они приводятся через `field()`.

### Кто ищет совпадения

Приложение ничего не сравнивает. Это делает отдельный сервис
[Server2](https://github.com/dragonlex/MobilRing-Server2): эвристика отсеивает кандидатов,
локальная языковая модель подтверждает пару, результат пишется в `matches`. Телефон только
читает готовые пары и показывает их участникам.

## Сборка

Нужны Android Studio (JDK 21) и подключение к Firebase.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

APK появится в `app/build/outputs/apk/debug/app-debug.apk`. Установка:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

`app/google-services.json` лежит в репозитории — без него Firebase не инициализируется.
Файл содержит клиентский ключ, который всё равно попадает в APK.

`local.properties` (путь к Android SDK) в репозиторий не входит и создаётся Android Studio
автоматически.

### Версии

| Что | Версия |
|---|---|
| Kotlin | 2.2.10 |
| AGP | 9.2.1 |
| Compose BOM | 2026.02.01 |
| Firebase BOM | 34.4.0 |
| minSdk / targetSdk | 24 / 36 |

## Права

- `INTERNET` — доступ к базе и вход в Firebase.
- `CALL_PHONE` — кнопка «ПОЗВОНИТЬ» набирает номер сразу. Разрешение спрашивается при первом
  нажатии; если пользователь отказал или в телефоне нет SIM, откроется обычная клавиатура
  набора с уже подставленным номером.

## Структура проекта

```
app/src/main/java/com/example/MobilRing24KRSK/
├── MainActivity.kt            главный экран
├── AllAdsActivity.kt          лента
├── AdDetailActivity.kt        просмотр объявления, звонок, статус, вход в чат
├── ChatsActivity.kt           список переписок
├── ChatActivity.kt            переписка по объявлению
├── NotificationsActivity.kt   совпадения
├── ProfileActivity.kt         профиль
├── ReportActivity.kt          форма заявки
├── HelpActivity.kt            справка
├── SystemBars.kt              светлые иконки системных панелей
├── data/
│   ├── AppData.kt             AdItem, NotificationItem, AdType
│   ├── FirebaseRepo.kt        вход, публикация, живая лента, статус
│   ├── ChatRepo.kt            переписки: заголовки чатов и сообщения
│   └── Profile.kt             Profile, ProfileStore
└── ui/
    ├── components/            DoodleButton, SimpleScreen, AvatarImage
    ├── screens/HomeScreen.kt  вёрстка главного экрана
    └── theme/                 цвета, типографика, тема
```

## Связанные репозитории

- [MobilRing-Server2](https://github.com/dragonlex/MobilRing-Server2) — матчер и локальная модель.
- [MobilRing-Project](https://github.com/dragonlex/MobilRing-Project) — общий обзор проекта.

## Статус

Рабочая версия 1.0: публикация, лента, совпадения, звонок автору объявления.
Добавлено в 1.1: категории вещей, статус «вещь найдена» и переписка внутри приложения
(список сообщений и чат по объявлению, сообщения приходят в реальном времени).
Проверено на Android 10, 14 и 16, а также на эмуляторе Android 14; чат проверен
на двух телефонах одновременно.
