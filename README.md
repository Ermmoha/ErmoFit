# ErmoFit

Android-приложение для домашних тренировок на Kotlin + Jetpack Compose.

## Что есть в проекте

- `app/src/main/java/com/ermofit/app/ui` — основной пользовательский поток приложения.
- `app/src/main/java/com/ermofit/app/newplan` — отдельный экспериментальный поток, который сейчас не подключён в `MainActivity`.
- `app/src/main/assets` — локальные seed-данные и медиа.
- `app/google-services.json` — локальная конфигурация Firebase, не относится к portable-части проекта.

## Текущий стек

- Kotlin
- Jetpack Compose + Material 3
- Hilt
- Room
- DataStore
- Firebase Auth
- Firebase Firestore
- Coil

## Как запустить

1. Откройте проект в Android Studio.
2. Установите JDK 17 и настройте `JAVA_HOME`.
3. Проверьте, что Android SDK доступен через `local.properties`.
4. При необходимости подложите актуальный `app/google-services.json`.
5. Запустите модуль `app`.

## Замечания по структуре

- В репозитории есть два параллельных сценария приложения: основной `ui/*` и экспериментальный `newplan/*`.
- Сейчас точка входа `MainActivity` использует `ErmoFitNavHost`, то есть рабочим остаётся основной сценарий.
- Если `newplan` должен стать основным приложением, его нужно отдельно довести и явно подключить вместо текущего nav host.
