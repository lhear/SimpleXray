[English](README.md) I [Русский](README_RU.md)

# SimpleXray

<img src="https://raw.githubusercontent.com/lhear/SimpleXray/main/metadata/en-US/images/icon.png" alt="icon" width="150">

[![GitHub Release](https://img.shields.io/github/v/release/lhear/SimpleXray)](https://github.com/lhear/SimpleXray/releases)
[![FDroid Release](https://img.shields.io/f-droid/v/com.simplexray.an.svg)](https://f-droid.org/packages/com.simplexray.an)
[![GitHub License](https://img.shields.io/github/license/lhear/SimpleXray)](LICENSE)
[![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/lhear/SimpleXray/.github%2Fworkflows%2Fbuild.yml)](https://github.com/lhear/SimpleXray/actions)
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/lhear/SimpleXray/total)

SimpleXray — это высокопроизводительный прокси-клиент для Android, **основанный на надёжном ядре Xray-core ([@XTLS/Xray-core](https://github.com/XTLS/Xray-core))**.

Он использует **инновационный подход**: **прямое выполнение официального бинарного файла Xray-core**, в отличие от традиционных методов JNI. Этот метод изолирует основную логику от слоя приложения, повышая стабильность и максимально используя производительность Xray-core. SimpleXray стремится обеспечить стабильную и эффективную работу в сети.

## Ключевые особенности

*   **Повышенная стабильность**: Запуская Xray-core как независимый дочерний процесс, SimpleXray избегает сложностей JNI, потенциальных проблем с памятью и сбоев приложений, связанных с ошибками основной библиотеки. Такая изоляция значительно повышает надёжность.
*   **Высокая производительность**: Использует нативную скорость Xray-core и интегрирует [@heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) для эффективного Tun2socks, обеспечивая низкую задержку и высокую пропускную способность.
*   **Удобство для пользователя**: Предлагает чистый, интуитивно понятный интерфейс и упрощённую настройку, облегчая конфигурацию и управление подключениями.

## Уникальный технический подход

Большинство Android-клиентов Xray-core используют JNI для вызова скомпилированной библиотеки .so. Хотя это просто в интеграции, такой подход может привести к проблемам со стабильностью, таким как нагрузка на производительность, сложности межъязыкового взаимодействия и сбои приложения при сбоях основной библиотеки.

**Ключевое отличие SimpleXray — способ запуска и управления прокси:**

При установке или обновлении встроенный бинарный файл Xray-core (как `libxray.so`) извлекается. При подключении приложение использует стандартные API Android для **запуска этого бинарного файла как отдельного дочернего процесса**, а не через вызовы JNI. Общение происходит через определённый межпроцессный протокол (IPC).

Такой дизайн сохраняет стабильность и производительность оригинального бинарного файла Xray-core, физически изолируя основной процесс от главного приложения, что повышает надёжность и безопасность.

## Файлы данных (`geoip.dat` / `geosite.dat`)

Проект **включает упрощённую версию** с базовыми правилами (`"geoip:private"`, `"geoip:cn"`, `"geosite:gfw"`) из [@lhear/v2ray-rules-dat](https://github.com/lhear/v2ray-rules-dat).

## Быстрый старт

1.  **Требования**: Android 10 и выше.
2.  **Получение приложения**: Скачайте APK с [страницы релизов](https://github.com/lhear/SimpleXray/releases) или из [F-Droid](https://f-droid.org/packages/com.simplexray.an).
3.  **Установка**: Установите APK на ваше устройство.
4.  **Настройка**: Запустите приложение, импортируйте или вручную добавьте данные сервера.
5.  **Подключение**: Выберите конфигурацию и нажмите подключиться.

## Руководство по сборке (для разработчиков)

1.  **Среда**: Установите [Android Studio](https://developer.android.com/studio) и настройте Android SDK.
2.  **Получение кода**: Клонируйте репозиторий с подмодулями:
    ```
    git clone --recursive https://github.com/lhear/SimpleXray
    ```
3.  **Импорт проекта**: Откройте проект в Android Studio.
4.  **Интеграция ядра**: Поместите бинарный файл Xray-core (`libxray.so`) для вашей архитектуры в `app/src/main/jniLibs/[папка архитектуры]`. Например, `app/src/main/jniLibs/arm64-v8a/libxray.so`.
5.  **Добавление файлов данных**: Поместите файлы `geoip.dat` и `geosite.dat` в каталог `app/src/main/assets/`. Эти файлы нужны для маршрутизации.
6.  **Сборка**: Синхронизируйте Gradle и выполните сборку.

## Вклад в проект

Приветствуются ваши вклады! Вы можете помочь, отправив:
*   Отчёты об ошибках (Issues)
*   Предложения по новым функциям
*   Код (Pull Requests)
*   Улучшение документации

## Лицензия

Проект лицензирован под **[Mozilla Public License Version 2.0](LICENSE)**.
