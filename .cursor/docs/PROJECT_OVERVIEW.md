# DebtBook App — Project Overview

## Назначение

Android-приложение для учёта долгов, финансов и целей накопления. Позволяет вести список людей и их долгов, отслеживать расходы/доходы по категориям, ставить цели накопления с депозитами, создавать и восстанавливать резервные копии через Google Drive и защищать приложение PIN-кодом / биометрией.

---

## Архитектура и текущий UI

Три Gradle-модуля: `app → domain`, `app → data`, `data → domain`.

| Модуль | Содержимое |
| --- | --- |
| `:app` | Android UI, ViewModel, Hilt DI, навигация, авторизация, реклама и ручной backup Google Drive |
| `:domain` | Kotlin/JVM: модели, repository interfaces, use cases, форматирование и экспорт Excel |
| `:data` | Android library: Room, SharedPreferences, storage и реализации repository |

MVVM / LiveData сосуществуют с Compose / Orbit MVI. Полная миграция UI не является текущей целью; порядок работ — [план надежности и роста](../plans/simpledebt_recovery_and_growth.plan.md).

| Область | Реализация на текущей базе |
| --- | --- |
| Авторизация | `AuthorizationActivity` + `auth/screen/AuthorizationScreen.kt`, Compose |
| Долги | `debt/main`, `debt/create`, `debt/details`, XML + View Binding |
| Финансы | `finance/main`, `finance/create` — XML; `finance/details`, `finance/create_category` — Compose |
| Цели | `goal/main`, `goal/create` — Compose + Orbit; `goal/details` — XML |
| Настройки | `settings/main`, `settings/synchronization`, XML |
| Общие компоненты | `core/ui/components`, `core/ui/theme`, Compose |

`roadmap/phase2.2.3` содержит отдельную реализацию CreateFinance; она не включается автоматически в базу R1a.

## Сборка и зависимости

| Область | Фактическая конфигурация |
| --- | --- |
| Build | AGP 9.1.0 / Gradle wrapper 9.4.1 / JDK 17 |
| Kotlin | JVM / Compose plugins 2.3.20; Android Kotlin встроен в AGP 9 |
| SDK | `compileSdk 36`, `targetSdk 36`, `minSdk 23`; версия выпуска — `app/build.gradle` |
| Application ID | `com.breckneck.debtbook` |
| DI | Hilt 2.59.2, hilt-navigation-compose 1.2.0 |
| База | Room 2.8.4, schema version 16, KSP 2.3.6 |
| UI | Compose BOM 2026.03.00, Material3; Material Views 1.13.0 |
| Navigation / Lifecycle | 2.9.7 / 2.10.0 |
| Orbit | 10.0.0 |
| Изображения | Glide 4.16.0 / Coil 3.1.0 |
| Реклама | Yandex Mobile Ads 7.18.4 |
| Backup | Google Sign-In и Google Drive API, ручное сохранение/восстановление JSON |
| Экспорт / JSON | Apache POI 4.0.0 / Gson 2.10.1 |
| Тесты | domain — JUnit 5; app/data — JUnit 4; Mockito и coroutines-test; instrumented — Espresso/Compose |

`release` требует существующий release/upload key и не использует debug signing. Настройка и условия выпуска — [docs/release-build.md](../../docs/release-build.md). Наличие debug APK не подтверждает пригодность для магазина.

## Навигация

Launcher — `AuthorizationActivity`. После проверки доступа открывается `core/MainActivity`; она содержит `NavHostFragment` и четыре нижние вкладки. `nav_graph.xml` использует исторические ID `mainFragment` / `newDebtFragment`, но классы называются `DebtFragment` / `CreateDebtFragment`.

```
AuthorizationActivity
  └── MainActivity
        ├── DebtFragment → CreateDebtFragment / DebtDetailsFragment
        │                   DebtDetailsFragment → CreateDebtFragment
        ├── FinanceFragment → CreateFinanceFragment / FinanceDetailsFragment
        │                      CreateFinanceFragment → CreateFinanceCategoryFragment
        ├── GoalsFragment → CreateGoalsFragment / GoalDetailsFragment
        └── SettingsFragment → SynchronizationFragment
```

## Пакетная структура app

```
com.breckneck.debtbook
├── app/                     # Application
├── auth/                    # Activity, ViewModel, screen/, util/
├── common/                  # Compose, coroutine и Kotlin helpers
├── core/                    # MainActivity, MainViewModel, adapter/, customview/, ui/
├── debt/                    # main/, create/, details/; адаптеры внутри фич
├── finance/                 # main/, create/, create_category/, details/, util/
├── goal/                    # main/, create/, details/
├── settings/                # main/, synchronization/
└── di/                      # DataModule, DomainModule (Hilt)
```

В `domain` сохранен исторический пакет `com.breckneck.deptbook` (с `dept`, не `debt`). У `data` namespace `com.breckneck.data`; исходные пакеты `database`, `entity`, `repository`, `sharedprefs`, `storage`.

## Данные и интеграции

- Room v16: `Human`, `Debt`, `FinanceData`, `FinanceCategoryData`, `GoalData`, `GoalDepositData`. Полное восстановление выполняется в транзакции DAO.
- Суммы `BigDecimal` (Room TEXT), календарные даты долгов ISO с сохранением старой строки. Миграции 5→16 централизованы, destructive fallback отсутствует.
- Google Drive — резервное копирование/замена локальной базы, не автоматическая многопользовательская синхронизация.
- Ресурсы языков: default, RU, en-US, DE, FR; региональные ресурсы валют не означают отдельные переводы интерфейса.
- В исходном manifest удаляются location permissions SDK; используются READ_CONTACTS, VIBRATE и FileProvider. Итоговые разрешения проверяются в merged manifest.

## Проверки и CI

Из корня проекта с JDK 17 и установленными Android SDK Platform 36 / Build Tools 36.0.0:

```sh
bash ./gradlew :app:assembleDebug :domain:test :data:test :app:testDebugUnitTest :app:lintDebug --continue
```

`.github/workflows/tests.yml` запускается на push в `main`, `master`, `develop`, `develop_main`, `roadmap/**`, `codex/**`, на любой pull request и вручную. Workflow устанавливает JDK 17 и SDK packages, собирает debug APK, выполняет unit tests всех модулей, lintDebug и сохраняет отчеты. Удаленный запуск CI и instrumented-проверки учитываются отдельно от локальной сборки.

## Карта документации

| Файл | Область |
| --- | --- |
| `DOMAIN_MODULE.md` | Модели, repository interfaces, use cases, утилиты |
| `DATA_MODULE.md` | Room, entities, storage, SharedPreferences |
| `APP_CORE.md` | Application, MainActivity, DI, PIN/биометрия |
| `APP_DEBT_FEATURE.md` | Долги |
| `APP_FINANCE_FEATURE.md` | Финансы |
| `APP_GOAL_FEATURE.md` | Цели |
| `APP_SETTINGS_FEATURE.md` | Настройки и backup |
| `DESIGN_SYSTEM.md` | Читаемость, темы, состояния UI и доступность |

При структурных изменениях обновлять соответствующий документ по `.cursor/rules/update-docs.mdc`. Актуальные критерии приемки и незавершенные проверки вести в плане надежности и роста.

Восстановление PIN: внутренняя `auth/PinRecoveryActivity` (`exported=false`) вызывается из «Забыли PIN?»; новый PIN задаётся после системного подтверждения Android, без замены базы.

### R2.2 — календарный срок и уведомления

- Room 18, backup v4; чтение прежних v2/v3/legacy сохранено, старым долгам сроки не назначаются.
- App использует Room runtime 2.8.4 для observation расписания. Manifest добавляет POST_NOTIFICATIONS и RECEIVE_BOOT_COMPLETED; exact-alarm permissions/foreground service не требуются.
- Новая неэкспортируемая `debt.reminders.ReminderEntryActivity` → ActivityAuthGate → MainActivity → debtDetailsFragment (`openDebtId`). Неэкспортируемый receiver сверяет alarms после reboot/timezone/time/package replacement.
- Не добавлены analytics, архив, покупка или новые Compose-экраны.
