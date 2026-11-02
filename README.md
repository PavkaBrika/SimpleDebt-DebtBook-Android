# Simple Debt Book 💸

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" alt="Simple Debt Book app icon" width="144" />
</p>

<p align="center"><strong>Keep track of debts, manage everyday finances, and reach your savings goals.</strong></p>

An Android app for tracking personal debts, income and expenses, and savings goals. Keep a history of who owes whom, understand spending by category, and follow your progress toward a financial goal.

Financial records are stored locally with Room. Google Drive backup and restore are optional; everyday record keeping works without signing in.

📱 **Android 6.0+ · Kotlin · Clean Architecture · Hilt · Room · Jetpack Compose + XML Views**

## ✨ What the app does

### 🤝 Debts

- 💸 Track money you lend and borrow, grouped by person.
- 📝 Record amounts, dates, notes, and a currency for each person's account.
- 👥 Pick a name from device contacts or enter it manually.
- 🔎 Search by name, filter by debt direction, and sort by name, amount, or date.
- 📊 View totals in two selected currencies, share debt details, and export a person's debt history to Excel (`.xls`).

### 💰 Income and expenses

- 🧾 Record income and expenses with dates, notes, and categories.
- 🎨 Create categories with custom names, emoji, and colors.
- 🗓️ Browse daily, weekly, monthly, and yearly summaries.
- 📈 See each category's share of the total and review its transaction history.

### 🎯 Savings goals

- 🏁 Set a target amount, currency, optional deadline, and photo.
- 📊 Track the saved amount and progress toward each goal.
- 🪙 Add contributions from the goal list and review the deposit history.

### ⚙️ Settings and data

- 🌓 Light, dark, and system themes.
- 🔐 PIN lock and biometric authentication.
- ☁️ Optional manual backup and restore through Google Drive.
- 🌍 Russian, English, German, and French string resources.

The app includes Yandex Mobile Ads. See the repository's [privacy policy](docs/privacy_policy.html) for details about data handling and third-party services.

## 🏗️ Architecture

The project uses three Gradle modules with Clean Architecture boundaries:

```text
app ──────► domain
 │            ▲
 └──► data ───┘
```

| Module | Responsibility |
| --- | --- |
| [`app`](app) | Screens, ViewModels, navigation, Hilt bindings, authentication, and Google Drive integration. Organized by feature: `debt`, `finance`, `goal`, and `settings`, with shared `auth`, `core`, and `di` packages. |
| [`domain`](domain) | Kotlin/JVM business logic, models, repository interfaces, and use cases. Does not depend on the Android SDK. |
| [`data`](data) | Room entities and DAOs, repository implementations, and SharedPreferences storage. |

Presentation is being migrated from **MVVM with LiveData and XML Views** to **Jetpack Compose with Orbit MVI**. Coroutines and Flow handle asynchronous work. Existing Fragments host migrated Compose screens while `MainActivity` retains the Navigation Component XML graph and bottom navigation.

In `master` and `develop_main`, Compose is used for authentication, finance category creation, finance details, the goal list, and goal creation/editing. The remaining screens still use Views and View Binding. The local `roadmap/phase2.2.3` branch also migrates finance record creation/editing to Compose and Orbit MVI; see [Branches](#branches).

Shared Compose components and theme tokens live in `app/src/main/java/com/breckneck/debtbook/core/ui/`. The design direction and conventions are documented in the [design system](.cursor/docs/DESIGN_SYSTEM.md).

## 🧰 Built with

Versions below reflect the checked-in build configuration in `develop_main`.

| Area | Technology |
| --- | --- |
| Language and JVM target | Kotlin 2.3.20, Java 17 |
| Build | Android Gradle Plugin 9.1.0, Gradle Wrapper 9.4.1, KSP 2.3.6 |
| Android SDK | Minimum 23, compile 36, target 35 |
| UI | Jetpack Compose BOM 2026.03.00, Material 3, XML Views, View Binding |
| Presentation | ViewModel, LiveData, Coroutines/Flow, Orbit MVI 10.0.0 |
| Navigation | AndroidX Navigation 2.9.7 |
| Dependency injection | Hilt 2.59.2 |
| Persistence | Room 2.8.4, SharedPreferences |
| Images | Coil 3.1.0 for Compose, Glide 4.16.0 for Views |
| Backup | Google Sign-In, Google Drive API v3, Gson |
| Export | Apache POI for Excel `.xls` files |
| Authentication | AndroidX Biometric, Android Keystore for PIN encryption |
| Advertising | Yandex Mobile Ads 7.18.4 |
| Tests | JUnit 5 in `domain`, JUnit 4-style app/data tests, Mockito, Coroutines Test, Orbit Test |

## 🚀 Getting started

### 📋 Requirements

- JDK 17.
- Android Studio with support for the project's Android Gradle Plugin, or Android SDK command-line tools.
- Android SDK Platform 36 and the build tools required by Gradle.
- An Android 6.0+ device or emulator to run the app.

### 🔨 Build and run

```bash
git clone https://github.com/PavkaBrika/debtbookapp.git
cd debtbookapp
git switch develop_main
```

Open the project in Android Studio, select JDK 17 as the Gradle JDK, and let Gradle sync. Android Studio can create `local.properties` with your SDK location. For a command-line setup, create it yourself:

```properties
sdk.dir=/absolute/path/to/android-sdk
```

Build the debug APK:

```bash
bash ./gradlew :app:assembleDebug
```

The current build configuration uses version **4.6.4 (42)** and names the APK `SimpleDebtBook-v4.6.4(42).apk`. Debug output is written to `app/build/outputs/apk/debug/`.

Run the `app` configuration in Android Studio, or install onto a connected device:

```bash
bash ./gradlew :app:installDebug
```

The application ID is `com.breckneck.debtbook`. Commands use `bash ./gradlew` because the wrapper script is currently tracked without executable permission. On Windows, use `gradlew.bat` instead.

Google Drive backup requires Google sign-in. For your own build, configure Android OAuth access for its package name and signing certificate in a Google Cloud project with the Drive API enabled. The integration uses the `DRIVE_APPDATA` scope and stores a JSON snapshot in Drive's app data folder. Restoring a backup replaces the local records.

The checked-in `release` build type currently uses the debug signing configuration. Configure your own release signing before publishing a build.

## 🧪 Tests and CI

Run local unit tests:

```bash
bash ./gradlew :domain:test
bash ./gradlew :app:testDebugUnitTest
bash ./gradlew :data:testDebugUnitTest
```

Domain tests cover use cases and sorting/filtering logic. App tests cover selected ViewModels and goal form validation. Instrumentation test sources are also present under `app/src/androidTest` and `data/src/androidTest`.

HTML unit test reports are generated under each module's `build/reports/tests/` directory.

The [Unit Tests workflow](.github/workflows/tests.yml) runs domain, app, and data unit test tasks with JDK 17 and uploads their reports. Its current push and pull request filters are `main`, `master`, and `develop`; `develop_main` and `roadmap/*` are not included.

<a name="branches"></a>

## 🌿 Branches

| Branch | Role |
| --- | --- |
| `master` | GitHub default branch; contains the version 4.6.4 (42) configuration. |
| `develop_main` | Contains four fixes after `master`: debt totals after deletion, recovery of debt details after process death, large-font layout, and authentication/navigation behavior. |
| `roadmap/phase2.2.3` | Continues the Compose/Orbit migration for finance record creation/editing. The local branch contains work beyond the GitHub branch. |
| `roadmap/phase2.2` | Earlier stage of the Compose migration. |
| `masterRuStore`, `master2` | Historical distribution and release branches. |
| `DI_Dagger`, `DI_Hilt`, `DI_Koin`, `MVVM`, `UnitTests`, `NewFeature`, `NewFeatures` | Earlier architecture experiments and feature development. |

At the time of this documentation update, local `roadmap/phase2.2.3` is **14 commits ahead of `origin/roadmap/phase2.2.3`**. It has 18 commits after its common ancestor with `develop_main`, but does not include the four subsequent fixes from `develop_main`. The finance form migration should therefore be read as branch-specific work rather than a feature already merged into `master`.

## 📚 Documentation and roadmap

Detailed engineering notes are kept in [`.cursor/docs`](.cursor/docs), mostly in Russian:

| Document | Contents |
| --- | --- |
| [Project overview](.cursor/docs/PROJECT_OVERVIEW.md) | Project structure, navigation, and development patterns. |
| [Domain module](.cursor/docs/DOMAIN_MODULE.md) | Models, repository contracts, use cases, and utilities. |
| [Data module](.cursor/docs/DATA_MODULE.md) | Room database, entities, storage, and repository implementations. |
| [App core](.cursor/docs/APP_CORE.md) | Application setup, dependency injection, authentication, and shared UI. |
| [Debts](.cursor/docs/APP_DEBT_FEATURE.md) | Debt screens, search, sorting, and sharing. |
| [Finances](.cursor/docs/APP_FINANCE_FEATURE.md) | Income/expenses, categories, and transaction history. |
| [Goals](.cursor/docs/APP_GOAL_FEATURE.md) | Savings goals, contributions, and form state. |
| [Settings and backup](.cursor/docs/APP_SETTINGS_FEATURE.md) | Preferences and Google Drive integration. |
| [Design system](.cursor/docs/DESIGN_SYSTEM.md) | Theme tokens, shared components, and UI conventions. |

The [long-term roadmap](.cursor/plans/debt_book_roadmap_21faec44.plan.md) and [Compose migration plan](.cursor/plans/compose_migration_plan_d99600c6.plan.md) describe the next stages: completing the Compose and Orbit migration, moving to Compose Navigation, and extending the shared design system. Reminders, widgets, and premium features are roadmap items.

Some engineering notes and plan checkboxes describe earlier revisions. Use the build files and the code in your selected branch as the source of truth for dependency versions and migration status.

---

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.breckneck.debtbook">
    <img src="assets/google-play-badge.png" alt="Get it on Google Play" height="80" />
  </a>
</p>
