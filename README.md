# ErmoFit Home (Android, локальный дипломный проект)

`ErmoFit Home` — мобильное приложение для домашних тренировок по доступному оборудованию.
Проект работает полностью локально: без внешних API и без онлайн-переводов.

## Стек
- Kotlin
- Jetpack Compose + Material 3
- MVVM + Repository
- Coroutines + Flow
- Navigation Compose (single-activity)
- Hilt (DI)
- Room (локальная БД упражнений/тренировок/сессий/избранного)
- DataStore Preferences (настройки пользователя, история поиска, анти-повторы)

## Архитектура пакетов
- `app/src/main/java/com/ermofit/app/newplan/data`
  - `local/entity` — Room сущности:
    - `ExerciseEntity`
    - `TrainingEntity`
    - `TrainingExerciseEntity`
    - `WorkoutSessionEntity`
    - `FavoriteEntity`
  - `local/dao/NewPlanDao` — DAO с фильтрами, поиском, join и CRUD
  - `repository/SeedImportRepository` — импорт `assets/seed.json` в транзакции
  - `repository/NewPlanRepository` — единая точка доступа к данным
  - `datastore/NewPlanPreferencesStore` — DataStore настроек/истории/антиповторов
- `app/src/main/java/com/ermofit/app/newplan/domain`
  - `model/UserSettings`
  - `usecase/GenerateDailyTrainingUseCase`
- `app/src/main/java/com/ermofit/app/newplan/ui`
  - `bootstrap` — Splash/Bootstrap
  - `onboarding` — первичная настройка
  - `home` — тренировка на сегодня + рекомендации
  - `catalog` — тренировки/упражнения с фильтрами
  - `workoutdetails`
  - `exercisedetails`
  - `workoutplayer`
  - `search`
  - `stats`
  - `profile`
- `app/src/main/java/com/ermofit/app/newplan/navigation`
  - `NewPlanRoutes`
  - `NewPlanNavHost`

## Экранный поток
1. `Splash`:
- проверяет/импортирует seed в Room
- решает переход: `Onboarding` или `Home`
2. `Onboarding`:
- цель, уровень, длительность, оборудование, ограничения, отдых, уведомления
3. `Home`:
- мотивационная фраза
- тренировка на сегодня (генерация/перегенерация)
- рекомендованные тренировки
4. `Catalog`:
- вкладки: тренировки/упражнения
- фильтры + поиск по названию
5. `WorkoutDetails`
6. `ExerciseDetails` + избранное
7. `WorkoutPlayer` (таймеры, отдых, прогресс, сохранение сессии)
8. `Search` + история 5 последних запросов
9. `Stats` (7/30 дней, общее время, стрик)
10. `Profile` (редактирование настроек + очистка прогресса)

## Seed данные
Файл: `app/src/main/assets/seed.json`
- 60 упражнений
- 12 готовых тренировок
- 96 связей тренировка-упражнение

Медиа (локально):
- `app/src/main/res/drawable/ex_media_01.jpg ... ex_media_20.jpg`

## Генерация "Тренировка на сегодня"
UseCase: `GenerateDailyTrainingUseCase`
- фильтрация по уровню, оборудованию, ограничениям, цели
- баланс мышечных групп
- настройка под цель/длительность/отдых
- анти-повторы (последние 20 упражнений в DataStore)
- сохранение результата как `TrainingEntity(isGenerated=true)` в Room

## Как запустить
1. Откройте проект в Android Studio.
2. Установите JDK 17 и проверьте `JAVA_HOME`.
3. Синхронизируйте Gradle.
4. Запустите `app` на эмуляторе/устройстве (Android 8.0+, minSdk 26).

## Важно
- Все пользовательские данные и контент тренировок локальные.
- Контент тренировок в новой реализации русскоязычный (RU).
- При изменении схемы Room используется `fallbackToDestructiveMigration`.
