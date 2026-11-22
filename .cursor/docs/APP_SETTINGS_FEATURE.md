# Settings & Synchronization Feature (app module)

## Назначение

Настройки приложения и синхронизация данных через Google Drive. Позволяет менять валюты, тему, настройки шаринга, управлять безопасностью и выполнять бэкап/восстановление.

---

## Структура файлов

```
app/src/main/java/com/breckneck/debtbook/settings/
├── main/
│   ├── SettingsFragment.kt            # Экран настроек
│   └── SettingsViewModel.kt           # VM настроек
└── synchronization/
    ├── util/
    │   └── DriveServiceHelper.kt      # Хелпер Google Drive API
    ├── BackupScreen.kt                # Compose-экран резервного копирования
    ├── BackupTransfer.kt              # Свежий snapshot перед upload; проверка JSON до restore
    ├── SynchronizationFragment.kt     # Compose-хост и системные действия
    └── SynchronizationViewModel.kt    # VM синхронизации
```

> `SettingsAdapter` (radio-style пикер) перемещён в `core/adapter/SettingsAdapter.kt` — переиспользуется несколькими фичами.

### Layouts
- `fragment_settings.xml` — экран настроек
- `dialog_setting.xml` — диалог выбора значения настройки
- `item_setting.xml` — элемент настройки (radio button)
- `dialog_pincode_lock.xml` — PIN, смена PIN, задержка блокировки и отпечаток

---

## SettingsViewModel

VM экрана настроек.

### Зависимости
- `Get/SetFirstMainCurrency` — основная валюта 1
- `Get/SetSecondMainCurrency` — основная валюта 2
- `Get/SetDefaultCurrency` — валюта по умолчанию для новых долгов
- `Get/SetAddSumInShareText` — включать ли сумму в текст при шаринге
- `Get/SetAppTheme` — тема (светлая/тёмная/системная)
- `GetIsAuthorized` — авторизован ли для Google Drive
- `GetUserData` — данные пользователя (имя, email)
- `Get/SetIsFingerprintAuthEnabled` — включение биометрии
- `Get/SetPinLockDelay` — интервал повторного запроса PIN после ухода в фон

### State
| Поле | Тип | Описание |
|------|-----|---------|
| `firstMainCurrency` | `LiveData<String>` | Валюта 1 |
| `secondMainCurrency` | `LiveData<String>` | Валюта 2 |
| `defaultCurrency` | `LiveData<String>` | Валюта по умолчанию |
| `addSumInShareText` | `LiveData<Boolean>` | Сумма в тексте шаринга |
| `appTheme` | `LiveData<String>` | Текущая тема |
| `isAuthorized` | `LiveData<Boolean>` | Авторизация Google |
| `userName` / `emailAddress` | `LiveData<String>` | Данные пользователя |
| `isFingerprintAuthEnabled` | `LiveData<Boolean>` | Биометрия |
| `pinLockDelay` | `LiveData<PinLockDelay>` | Сразу / 30 секунд / 1 минута / 5 минут |
| `isSynchronizationAvailable` | `LiveData<Boolean>` | Доступна ли синхронизация |

### Ключевая логика
- Все настройки загружаются при init
- Изменение настройки мгновенно сохраняется через соответствующий use case
- Диалог настроек: универсальный `dialog_setting.xml` с `SettingsAdapter` (radio-кнопки)
- Тема применяется через `AppCompatDelegate.setDefaultNightMode()` в Fragment
- В диалоге PIN настройка «Блокировка после ухода из приложения» появляется при включённом PIN. Выбор сохраняется сразу, по умолчанию — сразу. После завершения процесса вход требуется независимо от выбранной задержки.
- Если отпечаток ещё не зарегистрирован, открытие диалога PIN не запускает системные настройки. Регистрация открывается только при явном нажатии на отпечаток (API30+).

---

## SynchronizationViewModel

VM экрана резервного копирования в файл и Google Drive.

### Зависимости
- `GetIsAuthorized` / `SetIsAuthorized` — статус авторизации
- `GetAllAppData` — актуальный snapshot всех таблиц в транзакции чтения
- `ReplaceAllAppData` — полное восстановление через существующую Room-транзакцию
- `SetUserData` — сохранение данных пользователя
- `SetDateUseCase` — форматирование даты
- `Set/GetLastSyncDate` — дата последней синхронизации

### Ключевая логика
- `uploadBackup()` и `exportFile()` получают актуальный снимок `AppDataLists` перед записью JSON.
- `loadDriveBackups()` и `restoreFile()` проверяют копии для предпросмотра. `confirmRestore()` применяет выбранную копию через `BackupTransfer` и `ReplaceAllAppData`.
- `savingTo` указывает место текущего сохранения, Drive или файл. Сохранение и восстановление взаимно исключают друг друга. Состояние операции хранится во ViewModel и сбрасывается в `finally`. После `operationError` действие можно повторить.
- `BackupTransfer` не меняет формат backup и Room schema. Полная совместимость старых форматов/версионирование и хранение предыдущей копии относятся к R1b.
- **Авторизация**: Google Sign-In + Google Drive scope
- **DriveServiceHelper**: обёртка над Google Drive API v3 (upload/download файлов)

---

## DriveServiceHelper

Утилитный класс для работы с Google Drive API.

### Основные операции
- Создание/поиск файла на Drive
- Upload содержимого (JSON backup)
- Download содержимого (JSON restore)
- Использует legacy Google HTTP Client + Gson

---

## Настройки приложения (полный список)

| Настройка | Описание | Тип |
|-----------|---------|-----|
| First Main Currency | Валюта 1 для итогов на главном экране | String |
| Second Main Currency | Валюта 2 для итогов | String |
| Default Currency | Валюта по умолчанию для новых долгов | String |
| Finance Currency | Валюта для финансов | String |
| App Theme | Светлая / Тёмная / Системная | String |
| Debt Order | Сортировка долгов | Pair<DebtOrderAttribute, Boolean> |
| Human Order | Сортировка людей | Pair<HumanOrderAttribute, Boolean> |
| Add Sum In Share Text | Включать сумму в шаринг | Boolean |
| PIN Code Enabled | PIN-замок включён | Boolean |
| PIN Code | Зашифрованный PIN-код | String |
| PIN Lock Delay | Задержка блокировки при уходе в фон; по умолчанию 0 | Long, миллисекунды |
| Fingerprint Auth Enabled | Биометрия включена | Boolean |
| Is Authorized | Авторизован в Google | Boolean |
| User Data | Имя + email | User (JSON) |
| Last Sync Date | Timestamp последней синхронизации | Long |
| Debt Quantity For Rate Dialog | Порог для показа диалога оценки | Int |

---

## Связанные документы

- Auth (PIN/биометрия): см. `APP_CORE.md` → раздел «Auth»
- Полная карта навигации: см. `PROJECT_OVERVIEW.md` → «Экраны и переходы»
- Настройки domain-модели: см. `DOMAIN_MODULE.md` → `SettingsRepository`

### R1b — резервная копия / восстановление

В настройках строка «Резервная копия» подписана «Drive / Файл». На вложенном экране Google Drive расположен над блоком «Копия в файл». Горизонтальные разделители разграничивают действия в обоих блоках и версии в списке копий Drive. Во время сохранения индикатор показывается внутри строки «Сохранить в Google Drive» или «Сохранить файл» и исчезает по завершении операции.

Перед входом политика конфиденциальности открывается по нажатию в любом месте подписи под кнопкой входа. Название политики подчёркнуто. Лист выхода объясняет, что для сохранения и восстановления через Drive потребуется снова войти в аккаунт. Записи на телефоне и существующие копии в Drive сохраняются.

Раздел всегда доступен, включая устройства без Google Play Services. `SynchronizationViewModel` координирует SAF-файлы и Drive через `BackupTransfer`: предварительная проверка и preview (дата/число записей/фото) → явное подтверждение → атомарная замена. `BackupFiles` хранит страховочную копию в AtomicFile и переносит фото байтами. После восстановления можно вернуть предыдущие локальные данные; перед перезаписью Drive сохраняется предыдущая проверенная версия отдельным файлом. Предпросмотр открывается в прокручиваемом нижнем листе Compose. Формат/ограничения — `docs/r1b-data-contract.md`.

### Оценка приложения

«Настройки → Оценить приложение» и автоматическое приглашение открывают один и тот же нижний лист Bubble. На обоих шагах основная кнопка называется «Продолжить». При 1–3 звёздах она открывает форму отзыва, а из формы передаёт черновик с оценкой и введённым текстом в почту. Тема содержит SimpleDebt и версию установленной сборки. При ошибке запуска почты лист остаётся открытым с введённым текстом. При 4–5 звёздах «Продолжить» открывает Google Play.

Системная кнопка или жест «Назад» закрывают лист. Нажатие на затемнённую область и смахивание вниз оставляют его открытым. Кнопка закрытия в заголовке убрана на обоих шагах.

Оценка и текст хранятся в состоянии `MainViewModel`, поэтому переживают пересоздание Activity. Порог следующего автоматического приглашения сдвигается один раз при завершении сценария. Вызов из настроек на порог не влияет.
