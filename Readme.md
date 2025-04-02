# Sms2Telegram

## English

**Sms2Telegram** is an Android application that monitors incoming SMS messages and calls and forwards them to a Telegram chat via a bot. It runs as a ForegroundService with a persistent notification and offers a settings screen to configure the Telegram Bot Token and Chat ID. All actions are logged to a file, and logs can be viewed directly within the app.

### Features
- Monitor incoming SMS and calls.
- Forward messages to Telegram using Telegram Bot API.
- Runs in the background with a persistent notification.
- Settings screen to enter Telegram Bot Token and Chat ID, and to send a test message.
- Logs all actions to a file.
- Displays logs in a dedicated UI tab with selectable text for easy copying.

### Requirements
- Android 15 (API 34) support.
- Permissions: `RECEIVE_SMS`, `READ_SMS`, `READ_PHONE_STATE`, `READ_CALL_LOG`, `INTERNET`, and `FOREGROUND_SERVICE`.

### Installation
1. Clone the repository.
2. Open the project in Android Studio.
3. Build and run the app on your device or emulator.

### Usage
- Configure your Telegram Bot Token and Chat ID in the settings tab.
- The app will forward incoming SMS messages and call notifications to your Telegram chat.
- Use the "Send Test" button to send a test message.

---

## Русский

**Sms2Telegram** — это Android-приложение, которое отслеживает входящие SMS и звонки, пересылая их в личный чат Telegram через бота. Приложение работает в фоне посредством ForegroundService с постоянным уведомлением и имеет экран настроек для ввода токена бота и chat_id. Все действия логируются в файл, а логи можно просматривать прямо в приложении.

### Особенности
- Отслеживание входящих SMS и звонков.
- Пересылка сообщений в Telegram через Telegram Bot API.
- Работа в фоне с постоянным уведомлением.
- Экран настроек для ввода Telegram Bot Token и chat_id, а также кнопка для отправки тестового сообщения.
- Логирование всех действий в файл.
- Отображение логов в отдельной вкладке с возможностью выделения текста для копирования.

### Требования
- Поддержка Android 15 (API 34).
- Разрешения: `RECEIVE_SMS`, `READ_SMS`, `READ_PHONE_STATE`, `READ_CALL_LOG`, `INTERNET` и `FOREGROUND_SERVICE`.

### Установка
1. Склонируйте репозиторий.
2. Откройте проект в Android Studio.
3. Соберите и запустите приложение на устройстве или эмуляторе.

### Использование
- Введите Telegram Bot Token и chat_id в настройках.
- Приложение будет пересылать входящие SMS и уведомления о звонках в указанный Telegram чат.
- Используйте кнопку "Send Test" для отправки тестового сообщения.