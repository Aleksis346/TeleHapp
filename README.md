# TeleHapp

**TeleHapp** — неофициальный Android-клиент с открытым исходным кодом на базе Telegram API со встроенным клиентом Xray (Happ VPN) для настройки собственных прокси-серверов.

**TeleHapp** is an unofficial open-source Android client based on the Telegram API with a built-in Xray client (Happ VPN) for configuring your own proxy servers.

---

## Дисклеймер / Disclaimer

**RU:** TeleHapp не связан с Telegram FZ-LLC и не является официальным приложением Telegram. Приложение использует официальный Telegram API и должно использоваться с соблюдением [правил использования Telegram API](https://core.telegram.org/api/terms) и применимого законодательства. Название и оформление проекта не должны вводить пользователей в заблуждение относительно аффилированности с Telegram.

**EN:** TeleHapp is not affiliated with Telegram FZ-LLC and is not an official Telegram application. This app uses the official Telegram API and must be used in compliance with the [Telegram API Terms of Service](https://core.telegram.org/api/terms) and applicable laws. The project name and branding must not mislead users about affiliation with Telegram.

---

## Возможности / Features

- **RU:** Базовый функционал мессенджера на базе Telegram API, интеграция Xray (Happ VPN) для подключения собственных серверов/конфигураций, локальные настройки интерфейса TeleHapp.
- **EN:** Core messenger functionality based on the Telegram API, integrated Xray (Happ VPN) for connecting your own servers/configurations, and local TeleHapp UI settings.

> Примечание / Note: функциональность VPN зависит от вашей конфигурации и окружения. / VPN functionality depends on your configuration and environment.

## Сборка / Build

### Требования / Requirements
- Android Studio (рекомендуется актуальная стабильная версия) / Android Studio (recent stable version recommended)
- Android SDK / NDK, установленные согласно конфигурации проекта / Android SDK/NDK as required by the project
- JDK 17 (рекомендуется) / JDK 17 (recommended)

### 1) Получите API-ключи Telegram / Obtain Telegram API credentials
1. Перейдите на [https://my.telegram.org](https://my.telegram.org) и войдите в аккаунт.  
   Go to [https://my.telegram.org](https://my.telegram.org) and sign in.
2. Откройте **API development tools** и создайте приложение, чтобы получить `api_id` и `api_hash`.  
   Open **API development tools** and create an application to obtain `api_id` and `api_hash`.

### 2) Настройте `local.properties` / Configure `local.properties`
В корне репозитория Telegram (`Telegram/`) создайте или отредактируйте файл `local.properties` (он должен быть в `.gitignore`, не коммитьте его):  
In the Telegram repository root (`Telegram/`), create or edit `local.properties` (it must be gitignored; do not commit it):

```properties
sdk.dir=/path/to/Android/Sdk
TELEGRAM_APP_ID=123456
TELEGRAM_APP_HASH=your_api_hash_here
# Optional / Опционально:
# SAFETYNET_KEY=
```

> `sdk.dir` укажите путь к вашему Android SDK. / Set `sdk.dir` to your local Android SDK path.

### 3) Сборка через Gradle / Build with Gradle

**Release (AFAT):**
```bash
./gradlew :TMessagesProj_App:assembleAfatRelease
```
(Windows: `gradlew.bat :TMessagesProj_App:assembleAfatRelease`)

Собранные APK обычно находятся в `TMessagesProj_App/build/outputs/apk/`.  
Built APKs are typically located in `TMessagesProj_App/build/outputs/apk/`.

> Для релизной подписи используйте собственный keystore и настройки подписи Gradle/CI; не публикуйте keystore, `google-services.json` и другие секреты в публичный репозиторий.  
> For release signing use your own keystore and Gradle/CI signing configuration; do not publish keystores, `google-services.json`, or other secrets to a public repository.

## Лицензия / License

Этот проект распространяется на условиях **GNU General Public License v2.0 (GNU GPL v2)**. См. файл [`LICENSE`](LICENSE).  
This project is licensed under the **GNU General Public License v2.0 (GNU GPL v2)**. See the [`LICENSE`](LICENSE) file.

Проект основан на исходном коде Telegram for Android и содержит модификации TeleHapp.  
This project is based on Telegram for Android source code and includes TeleHapp modifications.
"# TeleHapp" 
