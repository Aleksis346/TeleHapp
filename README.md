# TeleHapp

**TeleHapp** — неофициальный Android-клиент с открытым исходным кодом на базе Telegram API со встроенным клиентом Xray (Happ VPN) для настройки собственных прокси-серверов.

**TeleHapp** is an unofficial open-source Android client based on the Telegram API with a built-in Xray client (Happ VPN) for configuring your own proxy servers.

## Скачать / Download

* **RU:** Готовый установочный файл (`TeleHapp-42.0.0.apk`) доступен в разделе Releases.
* **EN:** Ready-to-install APK (`TeleHapp-42.0.0.apk`) is available on the Releases page.

## Дисклеймер / Disclaimer

* **RU:** TeleHapp не связан с Telegram FZ-LLC и не является официальным приложением Telegram. Приложение использует официальный Telegram API и должно использоваться с соблюдением [правил использования Telegram API](https://telegram.org) и применимого законодательства. Название и оформление проекта не должны вводить пользователей в заблуждение относительно аффилированности с Telegram.
* **EN:** TeleHapp is not affiliated with Telegram FZ-LLC and is not an official Telegram application. This app uses the official Telegram API and must be used in compliance with the [Telegram API Terms of Service](https://telegram.org) and applicable laws. The project name and branding must not mislead users about affiliation with Telegram.

## Возможности / Features

* **RU:** Базовый функционал мессенджера на базе Telegram API, интеграция Xray (Happ VPN) для подключения собственных серверов и конфигураций, гостевой просмотр справки до входа в аккаунт, локальные настройки интерфейса TeleHapp.
* **EN:** Core messenger functionality based on the Telegram API, integrated Xray (Happ VPN) for connecting your own servers and configurations, guest preview before login, and local TeleHapp UI settings.

> **Примечание / Note:** Функциональность VPN зависит от вашей собственной конфигурации серверов и сетевого окружения. / VPN functionality depends on your own server configuration and network environment.

## Сборка / Build

### Требования / Requirements
* Android Studio (рекомендуется актуальная стабильная версия) / Recent stable version recommended
* Android SDK / NDK, установленные согласно конфигурации проекта / As required by the project
* JDK 17 (рекомендуется) / Recommended

### 1) Получите API-ключи Telegram / Obtain Telegram API credentials
1. Перейдите на [my.telegram.org](https://my.telegram.org) и войдите в аккаунт. / Go to [my.telegram.org](https://my.telegram.org) and sign in.
2. Откройте **API development tools** и создайте приложение, чтобы получить `api_id` и `api_hash`. / Open **API development tools** and create an application to obtain `api_id` and `api_hash`.

### 2) Настройте `local.properties` / Configure `local.properties`
В корне проекта создайте или отредактируйте файл `local.properties` (он добавлен в `.gitignore`, не коммитьте его):
In the project root, create or edit `local.properties` (it is gitignored; do not commit it):

```properties
sdk.dir=/path/to/Android/Sdk
TELEGRAM_APP_ID=123456
TELEGRAM_APP_HASH=your_api_hash_here
```

### 3) Сборка через Gradle / Build with Gradle

```bash
./gradlew :TMessagesProj_App:assembleAfatRelease
```
*(Для Windows / For Windows: `gradlew.bat :TMessagesProj_App:assembleAfatRelease`)*

Собранные APK находятся в папке `TMessagesProj_App/build/outputs/apk/`. Для релизной подписи используйте собственный keystore (`.jks`); не публикуйте keystore, `google-services.json` и другие секреты в публичный репозиторий.

## Лицензия / License

* **RU:** Этот проект распространяется на условиях **GNU General Public License v2.0 (GNU GPL v2)**. См. файл `LICENSE`. Проект основан на исходном коде [Telegram for Android](https://github.com) и содержит модификации TeleHapp.
* **EN:** This project is licensed under the **GNU General Public License v2.0 (GNU GPL v2)**. See the `LICENSE` file. This project is based on the [Telegram for Android](https://github.com) source code and includes TeleHapp modifications.

