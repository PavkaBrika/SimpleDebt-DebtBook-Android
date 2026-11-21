# Debt Feature (app module)

## Назначение

Основная фича приложения — учёт долгов. Позволяет вести список людей (должников / кредиторов), добавлять/редактировать/удалять записи долгов, фильтровать, сортировать, искать, экспортировать и делиться.

---

## Структура файлов

```
app/src/main/java/com/breckneck/debtbook/debt/
├── main/
│   ├── adapter/
│   │   └── HumanAdapter.kt        # Адаптер списка людей (главный экран)
│   ├── DebtFragment.kt            # Главный экран — список людей с долгами
│   └── DebtViewModel.kt           # VM главного экрана
├── create/
│   ├── adapter/
│   │   └── ContactsAdapter.kt     # Адаптер выбора контакта из телефонной книги
│   ├── CreateDebtFragment.kt      # Создание/редактирование долга
│   └── CreateDebtViewModel.kt     # VM создания долга
└── details/
    ├── adapter/
    │   └── DebtDetailsAdapter.kt  # Адаптер списка долгов (детали человека)
    ├── DebtDetailsFragment.kt     # Детали долгов конкретного человека
    └── DebtDetailsViewModel.kt    # VM деталей
```

### Layouts
- `fragment_main.xml` — главный экран (RecyclerView + итоговые суммы)
- `fragment_debt_details.xml` — детали долгов человека
- `fragment_create_human_debt.xml` — форма создания/редактирования
- `item_human.xml` / `item_human_shimmer.xml` — элемент списка человека
- `item_debt.xml` / `item_debt_shimmer.xml` — элемент списка долга
- `item_contact.xml` — элемент контакта

### Диалоги
- `dialog_sort.xml` — сортировка и фильтрация
- `dialog_contacts.xml` — выбор контакта
- `dialog_change_debt_name.xml` — переименование
- `dialog_share.xml` — поделиться
- `dialog_extra_functions.xml` — доп. функции для записи долга
- `dialog_are_you_sure.xml` — подтверждение удаления

---

## DebtViewModel

Главный ViewModel экрана долгов.

### Зависимости (use cases)
- `GetAllHumansUseCase` и `GetAllDebts` — люди и их операции, прочитанные в общей транзакции с итогами
- `GetAllDebtsSumUseCase` — суммы по двум основным валютам
- `GetFirstMainCurrency` / `GetSecondMainCurrency` — валюты для отображения итогов
- `GetHumanOrder` / `SetHumanOrder` — сохранение/загрузка порядка сортировки
- `UpdateHuman` — обновление имени (переименование)

### State
| Поле | Тип | Описание |
|------|-----|---------|
| `screenState` | `LiveData<ScreenState>` | LOADING / EMPTY / SUCCESS |
| `resultedHumanList` | `LiveData<List<HumanDomain>>` | Итоговый (фильтрованный + отсортированный) список |
| `mainSums` | `LiveData<Pair<String, String>>` | Суммы долгов по двум валютам |
| `humanFilter` | `LiveData<Filter>` | ALL / POSITIVE / NEGATIVE |
| `humanOrder` | `LiveData<Pair<HumanOrderAttribute, Boolean>>` | Сортировка (атрибут + ASC/DESC) |
| `isSearching` | `LiveData<Boolean>` | Режим поиска |

### Ключевая логика
- **Фильтрация людей.** `PersonDebtHistory.matches(Filter)` проверяет наличие открытого займа нужного направления. Встречные займы могут включить человека сразу в обе выборки. `ALL` включает каждого человека, в том числе с пустой историей или только с погашенными долгами.
- **Сортировка**: по имени, сумме, дате; ASC/DESC — через `sortHumans()` из domain
- **Поиск**: debounce 500ms через `StateFlow`, фильтр по имени (case-insensitive)
- **Async**: `viewModelScope.launch` + `withContext(Dispatchers.IO/Default)` для всех IO-операций; `StateFlow` для state machine и поиска

### Действия
| Метод | Что делает |
|-------|-----------|
| `setListStateLoading()` | Перезагрузка данных |
| `onSetHumanSort(filter, order)` | Применить фильтр + сортировку |
| `saveHumanOrder(order)` | Сохранить порядок в настройки |
| `updateHuman(human)` | Обновить человека (например, имя) |
| `onStartSearch()` / `onStopSearch()` | Управление поиском |

---

## DebtDetailsViewModel

ViewModel деталей долгов одного человека.

### Зависимости (use cases)
- `GetAllDebtsByIdUseCase` — долги по humanId
- `GetLastHumanIdUseCase` — ID нового человека (при создании)
- `GetHumanSumDebtUseCase` — общая сумма
- `DeleteHumanUseCase` / `DeleteDebtsByHumanIdUseCase` — удаление человека + долгов
- `DeleteDebtUseCase` — удаление конкретного долга
- `AddSumUseCase` — корректировка суммы при удалении долга
- `GetDebtOrder` / `SetDebtOrder` — сортировка долгов
- `SavedStateHandle` — аргументы навигации (humanId, name, currency, newHuman)

### State
| Поле | Тип | Описание |
|------|-----|---------|
| `loans` | `LiveData<PersonDebtHistory>` | Исходные займы, их остатки и связанные возвраты |
| `showSettledDebts` | `LiveData<Boolean>` | Выбранный раздел, хранится в SavedStateHandle |
| `visibleDebtRows` | `LiveData<List<DebtDomain>>` | Отсортированные исходные записи выбранного раздела с историей под каждой записью |
| `resultedDebtList` | `LiveData<List<DebtDomain>>` | История для текстового экспорта и XLS с учётом сортировки и фильтра направления |
| `overallSum` | `LiveData<BigDecimal>` | Сальдо человека с начальной поправкой |
| `debtOrder` | `LiveData<Pair<DebtOrderAttribute, Boolean>>` | Сортировка |
| `debtFilter` | `LiveData<Filter>` | Фильтр |
| `humanName` | `LiveData<String>` | Имя человека |
| `screenState` | `LiveData<DebtDetailsState>` | LOADING / SUCCESS / EMPTY / ERROR; ошибка отличается от пустой истории |
| `isDeleting` / `humanDeleted` / `operationFailed` | `LiveData<Boolean>` | Защита от повторного удаления, подтверждённое завершение, видимая ошибка |

### Ключевая логика
- `retry()` загружает историю и сумму вместе; ошибка прекращает shimmer, экран предлагает повтор и смену сортировки.
- При удалении долга: вычитание суммы из human через `AddSumUseCase`; переход после удаления человека — только по `humanDeleted`.
- `SavedStateHandle`: аргументы навигации, разрешённый ID нового человека, фильтр, атрибут и направление сортировки. Переданный ID имеет приоритет над legacy-поиском последнего человека.
- Legacy-даты не мигрируются в R1a: если сортировка по локализованной дате не удалась, можно выбрать порядок создания; исходные записи сохраняются.
- Диалоги: удаление человека, настройки долга, сортировка, шаринг

---

## CreateDebtViewModel

ViewModel создания/редактирования долга.

### Зависимости
- `GetDefaultCurrency` — валюта по умолчанию
- `GetCurrentDateUseCase` — текущая дата
- `SetDateUseCase` — установка произвольной даты

### State
| Поле | Тип | Описание |
|------|-----|---------|
| `currency` | `LiveData<String>` | Выбранная валюта |
| `date` | `LiveData<String>` | Дата долга |
| `isCurrencyDialogOpened` | `LiveData<Boolean>` | Состояние диалога валют |

### Примечание
Сохранение долга происходит в `CreateDebtFragment` / `DebtDetailsFragment` напрямую через use cases.

---

## Adapters

### HumanAdapter
- RecyclerView adapter для списка людей
- Поддержка drag & reorder (для изменения порядка)
- Отображает: имя, суммарный долг, валюту
- Callbacks: клик (переход в детали), long press (доп. функции)
- Shimmer layout для загрузки

### DebtDetailsAdapter
- RecyclerView adapter для записей долгов
- Отображает: сумму, дату, описание
- Callbacks: клик (настройки долга — edit / delete)
- Shimmer layout

### ContactsAdapter
- RecyclerView adapter для выбора контакта из телефонной книги
- Используется в диалоге при создании долга
- Требует `READ_CONTACTS` permission

---

## Навигация

См. полную карту в `PROJECT_OVERVIEW.md` → «Экраны и переходы». Навигационные аргументы:
- `DebtFragment → CreateDebtFragment`: idHuman, idDebt, currency, sum, date, info, name
- `DebtFragment → DebtDetailsFragment`: humanId, newHuman
- `DebtDetailsFragment → CreateDebtFragment`: добавление нового долга к человеку

## R1a — сохранение формы

- `CreateDebtViewModel` теперь владеет записью, `isSaving`, `saveError`, `savedHumanId`; UI переходит к истории после завершения записи и передает конкретный ID. Повторное нажатие блокируется, ошибка оставляет форму для повтора.
- `DataTransaction` оборачивает связанные записи; при изменении исходная сумма читается из DB внутри транзакции, при удалении повторная операция не вычитает баланс снова.
- Bundle `sum` остается `Double` на всем пути; float-аргумент удален из navigation XML. Редактирование показывает модуль суммы и устанавливает направление переключателя.
- `DecimalInput` обеспечивает одинаковое чтение дробной точки/запятой; ошибки ввода видимы. Полный переход от Double и миграция дат — R1b.

### R1b

R1b: суммы и аргументы редактирования точные (BigDecimal/десятичная строка), календарная дата отделена от локализованного отображения; суммы обновляются транзакционными командами DAO. Неизвестные старые даты не ломают историю.

### R2.2 — срок и личное напоминание

| Файл | Назначение |
| --- | --- |
| `reminders/DebtDueDialogFragment.kt` | DialogFragment: дата, время, однократное напоминание, контекстный POST_NOTIFICATIONS, отказ/настройки, сохранение черновика при recreation |
| `reminders/DebtReminders.kt` | Сверка Room → AlarmManager, один PendingIntent/notification на долг, проверка token/остатка, постоянная отметка доставки и локальный журнал token против повторов старого backup |
| `reminders/DebtReminderReceiver.kt` | Доставка и системное перепланирование через goAsync |
| `reminders/ReminderEntryActivity.kt` | PIN до возврата в MainActivity |

`DebtDetailsFragment` открывает настройку срока отдельно от защищённого редактирования суммы при возвратах. Adapter показывает срок/просрочку по остатку и состояние напоминания; день/зона обновляются при возвращении и системной смене даты. Создание долга остаётся прежним: срок назначается выбранной исходной записи в истории. `dialog_debt_due.xml`, `r2_due_dates.xml` (EN/RU/DE/FR) добавляют форму и тексты. Полное погашение выключает напоминание; отмена возврата не включает его автоматически. Контракт: `docs/r2-due-dates-reminders.md`.

### R2.3 — архив и перенос карточки на Bubble

В карточке человека переключатель «Открытые долги / Погашенные долги» вызывает `DebtDetailsViewModel.selectDebtSection(settled)`. Кнопка добавления доступна в открытых долгах. Для Compose этапа «Долги» используйте `loans`, `showSettledDebts` и `visibleDebtRows` этой же ViewModel. По ID исходной записи доступен `loans.value?.loan(id)`. Выбранное направление и сортировка применяются к исходным записям. Возвраты идут под своим займом по дате и ID.

[PersonDebtHistory](../../domain/src/main/java/com/breckneck/deptbook/domain/model/PersonDebtHistory.kt) строится из истории одного человека. Каждый `LoanHistory` содержит `original`, `remaining` и `repayments`, включая отменённые. Валюту предоставляет карточка человека. Старые нулевые записи доступны через `zeroAmountEntries`. Для дробных остатков старых записей используйте `MoneyDisplay.formatExact`. Действия возврата и отмены вызывают существующие команды ViewModel. Полное погашение выключает напоминание в транзакции Room, отмена сохраняет его выключенным.
