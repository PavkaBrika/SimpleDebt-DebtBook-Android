# Finance Feature (app module)

## Назначение

Учёт доходов и расходов. Пользователь видит категории с процентным распределением, может переключаться между расходами и доходами, выбирать временной интервал (день/неделя/месяц/год), создавать категории и записи.

---

## Структура файлов

```
app/src/main/java/com/breckneck/debtbook/finance/
├── main/
│   ├── adapter/
│   │   └── UsedFinanceCategoryAdapter.kt    # Выбор категории при создании записи
│   ├── customview/
│   │   └── FinanceProgressBar.kt            # Кастомный прогресс-бар (% от общей суммы)
│   ├── FinanceFragment.kt                   # Фрагмент сводки
│   ├── FinanceScreen.kt                     # Сводка с двумя страницами
│   ├── FinanceOverview.kt                   # Доходы, расходы и итог за период
│   ├── FinancePeriodSheet.kt                # Выбор недели и диапазона дат
│   └── FinanceViewModel.kt                  # VM основного экрана
├── create/
│   ├── adapter/
│   │   └── FinanceCategoryAdapter.kt        # Категории в пикере при создании записи
│   ├── CreateFinanceFragment.kt             # Создание/редактирование записи
│   └── CreateFinanceViewModel.kt            # VM создания записи
├── create_category/
│   ├── model/
│   │   └── EmojiGroups.kt                   # Группы emoji для пикера (8 категорий × 10)
│   ├── CreateFinanceCategoryFragment.kt     # Фрагмент-хост для Compose экрана категории
│   ├── CreateFinanceCategoryScreen.kt       # Compose UI создания категории
│   └── CreateFinanceCategoryViewModel.kt    # VM создания категории (Orbit MVI)
├── details/
│   ├── screen/
│   │   ├── FinanceDetailsScreen.kt          # Compose entrypoint: VM binding + sheet orchestration
│   │   ├── FinanceDetailsContent.kt         # Compose scaffold/top bar + state switching
│   │   └── FinanceDetailsList.kt            # Compose список: badge, month headers, ledger rows
│   ├── FinanceDetailsActions.kt             # sealed interface интентов экрана деталей
│   ├── FinanceDetailsFragment.kt            # Фрагмент-хост для Compose экрана деталей
│   ├── FinanceDetailsState.kt               # State + BottomSheetState для Orbit контейнера
│   └── FinanceDetailsViewModel.kt           # VM деталей (Orbit MVI)
└── util/
    └── GetFinanceCategoryNameInLocalLanguage.kt  # Локализация имён категорий
```

### Layouts
- `fragment_create_finance.xml` — форма записи
- `fragment_create_finance_category.xml` — форма категории
- `fragment_finance_details.xml` — история по категории
- `item_finance.xml` — элемент финансовой записи
- `item_finance_category.xml` — элемент категории
- `item_finance_history.xml` — элемент истории
- `item_category_color.xml` — элемент палитры цветов
- `item_category_image.xml` — элемент палитры emoji
- `item_used_category.xml` / `item_used_category_shimmer.xml` — категория в пикере

---

## Сводка финансов

- Над вкладками видны доходы, расходы и их разница за выбранный период в выбранной валюте. Плюс выделен зелёным, минус — терракотовым, ноль — основным цветом текста.
- Слева находятся доходы, справа — расходы. При первом входе открываются доходы. Последнее выбранное направление восстанавливается при возврате. Страницы открываются нажатием на вкладку или горизонтальным свайпом. Карточки и кнопки расходов используют тёплый акцент, доходов — зелёный. Иллюстрации в карточках показывают движение денег от монет или к ним. У каждой вкладки своя позиция списка и своя кнопка добавления. Кнопка едет вместе со страницей и открывает форму соответствующего направления. При малой высоте она прокручивается под карточкой суммы. Смена вкладки сохраняет валюту и период.
- Кнопка с датой в карточке выделена контуром и открывает выбор периода. Неделя выбирается целиком из списка с датами с понедельника по воскресенье. Месяц и календарный год показывают итоговое название до применения. В «Диапазоне дат» пользователь задаёт начало и конец, обе даты входят в расчёт. Стрелки сдвигают диапазон на его длину, остальные интервалы — на день, неделю, месяц или год.
- «К текущему периоду» находится под датой в карточке. Кнопка занимает всю внутреннюю ширину карточки. При переходе к другому периоду и обратно её появление и скрытие плавно меняют высоту карточки. Для своего диапазона сохраняется его длина, текущий день становится последним. Новая операция в своём диапазоне получает сегодняшнюю дату, если она входит в диапазон, иначе дату начала.
- Валюта открывает общий экран с поиском. Выбор применяется к финансам и сохраняется в их настройке. Возврат без выбора сохраняет текущие фильтры.

### FinanceViewModel

`FinanceSummary` содержит валюту, период и суммы с категориями для обеих вкладок. `GetAllCategoriesWithFinances` перечитывает записи при открытии, возврате и повторе после ошибки. Смена периода или валюты пересчитывает обе сводки по последним полученным записям. Новый период, итог и список публикуются вместе. При повторном чтении предыдущая сводка остаётся доступной.

Суммы считаются в `BigDecimal`. Доли категорий округляются до целого процента, а категории сортируются по сумме. Для записей без сохранённого календарного дня применяется дата из прежнего формата. Направление и период восстанавливаются через `SavedStateHandle`.

---

## CreateFinanceViewModel

В форме операции поля и категории прокручиваются под закреплённой кнопкой сохранения. Отступы входят в прокрутку, последняя строка полностью видна перед кнопкой. При фокусе поле прокручивается в область между шапкой и кнопкой над клавиатурой.

### Зависимости
- `SetFinance` / `UpdateFinance` / `DeleteFinance` — CRUD записей
- `GetAllFinanceCategories` / `GetFinanceCategoriesByState` — список категорий
- `DeleteFinanceCategoryUseCase` / `DeleteAllFinancesByCategoryId` — удаление категории

### Ключевая логика
- Создание новой финансовой записи с привязкой к категории
- Выбор категории из списка (фильтрованного по state)
- Удаление категории с каскадным удалением записей

---

## CreateFinanceCategoryViewModel

Реализует `ContainerHost<CreateFinanceCategoryState, CreateFinanceCategorySideEffect>` (Orbit MVI).

### Зависимости
- `SetFinanceCategory` — создание категории
- `orbit-viewmodel` / `orbit-core` — контейнер состояния

### State (`CreateFinanceCategoryState`)

Дефолтное состояние: `CreateFinanceCategoryState.initial()`.

| Поле | Тип | Описание |
|------|-----|---------|
| `categoryName` | `String` | Текущее имя категории |
| `selectedImage` | `Int?` | Unicode codepoint выбранного emoji |
| `selectedColor` | `String?` | HEX-строка выбранного цвета |
| `financeCategoryState` | `FinanceCategoryState` | EXPENSE / INCOME |
| `isNameErrorVisible` | `Boolean` | Показывать ошибку валидации имени |
| `isImageErrorVisible` | `Boolean` | Показывать ошибку валидации иконки |
| `isColorErrorVisible` | `Boolean` | Показывать ошибку валидации цвета |

Строки ошибок резолвятся в UI через `stringResource` только когда флаг `true`.

### Side effects (`CreateFinanceCategorySideEffect`)
| Эффект | Когда |
|--------|-------|
| `CategorySaved` | После успешной записи в БД — инициирует навигацию назад |

### Intent-функции
| Метод | Что делает |
|-------|-----------|
| `onNameChange(value)` | Обновляет имя, сбрасывает `isNameErrorVisible` если непусто; ограничение 20 символов |
| `onImageSelected(image)` | Сохраняет выбор emoji (codepoint), сбрасывает `isImageErrorVisible` |
| `onColorSelected(color)` | Сохраняет выбор цвета (hex), сбрасывает `isColorErrorVisible` |
| `setFinanceCategoryState(state)` | Задаёт EXPENSE / INCOME |
| `onSaveClick()` | Валидация → `reduce` флагов ошибок → `withContext(IO) { setFinanceCategory.execute(...) }` → `postSideEffect(CategorySaved)` |

---

## FinanceDetailsViewModel

Реализует `ContainerHost<FinanceDetailsState, Nothing>` (Orbit MVI; side effects не используются).

### Зависимости
- `SavedStateHandle` — аргументы навигации (`categoryId`, `categoryState`, `categoryName`, `currency`) для первичной загрузки в `onCreate` контейнера
- `GetFinanceByCategoryId` — записи по категории
- `DeleteFinance` — удаление записи
- `orbit-viewmodel` / `orbit-core` — контейнер состояния

### State (`FinanceDetailsState`)

Дефолтное состояние: `FinanceDetailsState.initial()`.

| Поле | Тип | Описание |
|------|-----|---------|
| `financeList` | `List<Finance>` | Загруженные записи категории, отсортированные по дате (убыв.) |
| `financeListState` | `ListState` | LOADING / RECEIVED / EMPTY |
| `bottomSheet` | `FinanceDetailsBottomSheetState` | Состояние ExtraFunctions bottom sheet |
| `categoryId` | `Int?` | ID категории (заполняется из `SavedStateHandle` при создании контейнера) |
| `categoryName` | `String` | Имя категории (из аргументов навигации) |
| `currency` | `String` | Валюта для отображения (из аргументов навигации) |
| `isExpenses` | `Boolean` | true = расходы, false = доходы (из `categoryState` в аргументах) |

#### Bottom sheet state (`FinanceDetailsBottomSheetState`)
| Поле | Тип | Описание |
|------|-----|---------|
| `isOpened` | `Boolean` | Открыт ли bottom sheet |
| `finance` | `Finance?` | Выбранная запись |
| `title` | `String` | Заголовок sheet (дата + сумма + валюта) |

### Публичный API
- Единственный публичный метод: `onAction(FinanceDetailsActions)`.

### `container(..., onCreate = …)`
При первом обращении к контейнеру выполняется `onCreate`: чтение `categoryId` / `categoryState` / `categoryName` / `currency` из `SavedStateHandle`, `reduce` этих полей в state, затем загрузка списка (LOADING → RECEIVED / EMPTY). Фрагмент не читает `arguments` для UI — только `ViewModel` + `FragmentResult`.

### `FinanceDetailsActions` (sealed interface)
| Действие | Что делает |
|----------|------------|
| `OpenFinanceSheet(finance)` | Открыть bottom sheet и выбрать запись |
| `CloseFinanceSheet` | Закрыть bottom sheet |
| `DeleteFinance(finance)` | Удалить запись и перезагрузить список по `state.categoryId` |
| `EditFinanceClick` | Post side effect для навигации на редактирование выбранной записи |
| `RefreshListAfterEdit(wasModified)` | После `FragmentResult`: при `wasModified` и заданном `categoryId` — перезагрузка списка |

### Ключевая логика
- Отображение истории финансовых записей конкретной категории
- Удаление отдельных записей с перезагрузкой списка
- Загрузка списка выставляет `RECEIVED` / `EMPTY` после успеха

## FinanceDetailsScreen

Compose UI для экрана деталей категории (история записей). Состояние: `vm.collectAsState()` (включая заголовок, подзаголовок, валюту из state); события — только `vm.onAction(FinanceDetailsActions.…)` (без `LaunchedEffect` для первичной загрузки — она в `onCreate` контейнера).

| Компонент | Описание |
|-----------|---------|
| `LargeTopAppBar` | Большой заголовок категории с collapse поведением |
| `Crossfade` | Переключение между LOADING / EMPTY / RECEIVED с fade-анимацией |
| `ShimmerListPlaceholder` | Скелетон при состоянии LOADING |
| `EmptyListPlaceholder` | Заглушка при EMPTY; текст зависит от isExpenses |
| `FinanceDetailsList` | `LazyColumn` с type badge, sticky month headers, ledger rows |
| `FinanceRow` | Строка списка с суммой, валютой, датой, заметкой и affordance-стрелкой |
| `ExtraFunctionsBottomSheet` | Редактирование / удаление записи по клику на карточку |

---

## Custom Views

### FinanceProgressBar
Кастомный `View` для отображения процента категории от общей суммы. Отрисовывает цветную полосу пропорционально `categoryPercentage`. Атрибуты задаются через `finance_progress_bar_attrs.xml`.

---

## Особенности

- Дефолтные категории создаются при первом запуске (7 расходов + 4 дохода, определены в `ConstantsDomain.kt`)
- Имена категорий локализуются через `GetFinanceCategoryNameInLocalLanguage`
- Emoji отображаются как Unicode codepoints через `String(Character.toChars(codepoint))`
- Доступны **24 цвета** (19 пастельных Material 200 + 5 accent оттенков) и **80 эмодзи** (8 категорий × 10)
- Эмодзи-пикер группирует иконки по **8 категориям** (Food, Shopping, Fun, Health, Education, Finance, Home, Nature) с **10 эмодзи в каждой** (80 всего). Определены в `EmojiGroups.kt`. UI: `SecondaryScrollableTabRow` с текстовыми лейблами + `HorizontalPager` + фиксированная сетка 5 колонок (2 ряда)
- Цветовой пикер: `FlowRow` с круглыми `Surface(onClick)` элементами 40dp, анимация масштабирования при выборе

## R1a — форма записи

`CreateFinanceViewModel.SaveState`: IDLE/SAVING/SAVED/ERROR. `setFinance`/`editFinance` блокируют повторный запуск; навигация выполняется только после SAVED, ошибка показывает повтор. XML-форма использует `DecimalInput` для полного разбора точки/запятой и форматирования без группировки; никакой интеграции roadmap CreateFinance здесь нет. `CreateFinanceViewModelTest` проверяет отказ/повтор и отсутствие ранней навигации/дубля.

### R1b

R1b: денежные поля/агрегаты/ввод используют BigDecimal; доли категорий округляются отдельно HALF_EVEN только для отображения процентов.

R1b, схема 16: новая финансовая запись хранит currency/calendarDay. Выбор валюты фильтрует список/агрегаты/историю; старые суммы не переименовываются и валюты не складываются. При редактировании сохраняется валюта записи. Новые календарные дни используются для интервалов/группировки/сортировки; Date старых записей остается моментом.
