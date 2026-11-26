# Сборка и подпись SimpleDebt

Пакет — `com.breckneck.debtbook`. Google Play и RuStore используют один release-вариант. AppMetrica подключена с `4.6.5 (44)`, события использования долгов добавлены в `4.6.6 (45)`. [Воронка использования долгов](appmetrica-debt-usage.md). [Подготовка обновления с магазинной версии](r2.2-play-internal-2026-11-20.md).

## Локальная проверка и CI

Нужны JDK 17, Android SDK Platform 36 и Build Tools 36.0.0. Gradle 9.4.1 поставляется через wrapper; версии AGP/Kotlin/Hilt закреплены в корневом `build.gradle`. [AGP 9.1 требует JDK 17 и Build Tools 36.0.0](https://developer.android.com/build/releases/agp-9-1-0-release-notes).

На macOS можно выбрать уже установленный JDK 17 для одной команды, не меняя системную Java:

```sh
JAVA_HOME="$(/usr/libexec/java_home -v 17)" bash ./gradlew :app:assembleDebug :domain:test :data:test :app:testDebugUnitTest :app:lintDebug --continue
```

HTML-отчеты — `<module>/build/reports/tests/`, XML — `<module>/build/test-results/`. APK находится в `app/build/outputs/apk/debug/`. Debug использует стандартную отладочную подпись и предназначен для проверки на тестовых устройствах. Установка поверх магазинного приложения требует совпадения сертификата; не удалять пользовательскую установленную версию ради debug APK.

CI выполняет ту же сборку, unit tests и lint на JDK 17. Push охватывает рабочие ветки; pull request не фильтруется по целевой ветке. [Правила GitHub Actions](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax) описывают различие этих фильтров. CI не подписывает и не публикует release.

## Проверка рекламного слота

Обычная debug-сборка запускается с выключенной рекламой. Для проверки баннера на выделенном эмуляторе соберите debug APK с демонстрационными ID Яндекса.

```sh
JAVA_HOME="$(/usr/libexec/java_home -v 17)" bash ./gradlew -Pandroid.builder.sdkDownload=false -PdemoAds=true :app:assembleDebug
```

Запустите приложение с иконки лаунчера. Дождитесь записи `Banner loaded` в Logcat, смените вкладку и поверните экран. Долгое нажатие на фильтр долгов переключает рекламу в этой сборке. Проверьте, что при отключении баннера список и нижняя панель занимают освободившееся место. Демонстрационная сборка использует `demo-banner-yandex` и `demo-interstitial-yandex`. [Документация Яндекса](https://ads.yandex.com/helpcenter/ru/dev/android/compose/adaptive-sticky-banner).

## AppMetrica в release и debug

Каждый вариант берёт свой **API key (для использования в SDK)**. Переменная среды имеет приоритет над значением в локальном `local.properties`.

| Вариант | Поле в `local.properties` | Переменная среды | Проект AppMetrica |
| --- | --- | --- | --- |
| `release` | `appMetricaApiKey` | `SIMPLEDEBT_APPMETRICA_API_KEY` | [Debt Book](https://appmetrica.yandex.ru/application/edit?type=edit&appId=6392758) |
| `debug` | `appMetricaTestApiKey` | `SIMPLEDEBT_APPMETRICA_TEST_API_KEY` | [Debt Book — тест аналитики](https://appmetrica.yandex.ru/application/edit?type=edit&appId=6394279) |

Для `release` требуется ключ. Для `debug` пустое значение отключает AppMetrica. Gradle проверяет формат заданных ключей и отклоняет `debug`, если его ключ совпадает с ключом `release`. События первого долга в `debug` проверяйте на чистой установке с тестовым ключом. [Тестовая воронка](appmetrica-debt-usage.md).

## Подпись выпуска

Для Google Play подпишите AAB существующим ключом загрузки. Распространяемый APK SimpleDebt подписывает Google Play своим ключом приложения. Для другого магазина необходимо сохранить ранее применявшийся сертификат. См. [официальное руководство по подписи](https://developer.android.com/studio/publish/app-signing).

В Android Studio выберите **Build → Generate Signed Bundle / APK → Android App Bundle**, модуль `app`, затем **Choose existing** и существующий keystore, его пароль, alias и пароль ключа. На следующем экране выберите `release` и каталог назначения. Мастер передаёт ключ в Android Gradle Plugin, а проверка релизной подписи использует те же параметры. Обязательные для командной строки переменные и `keystore.properties` при этом можно оставить незаданными.

Для сборки из командной строки задайте четыре значения через защищенную среду сборки.

| Переменная окружения | Поле в `keystore.properties` |
| --- | --- |
| `SIMPLEDEBT_KEYSTORE_FILE` | `storeFile` |
| `SIMPLEDEBT_KEYSTORE_PASSWORD` | `storePassword` |
| `SIMPLEDEBT_KEY_ALIAS` | `keyAlias` |
| `SIMPLEDEBT_KEY_PASSWORD` | `keyPassword` |

Альтернатива — локальный `keystore.properties` в корне репозитория, не отслеживаемый Git. `storeFile` — абсолютный путь либо путь относительно корня проекта. Переменные окружения имеют приоритет перед файлом. Файлы `*.jks`, `*.keystore`, `keystore.properties` исключены из Git. Пароли не передавать в командной строке и не добавлять в журнал проверки.

```sh
bash ./gradlew :app:verifyReleaseSigning
bash ./gradlew :app:bundleRelease
```

`bundleRelease` создаёт `app/build/outputs/bundle/release/SimpleDebtBook-v4.6.6(45)-release.aab`, а `assembleRelease` создаёт одноимённый APK в `app/build/outputs/apk/release/`. Версия и код в имени берутся из `app/build.gradle`. Мастер Android Studio использует те же имена в выбранном каталоге.

`verifyReleaseSigning` проверяет наличие полного набора параметров и файла и блокирует `androiddebugkey`. Доступность ключа и корректность пароля проверяет Android Gradle Plugin при подписи. В каталоге с AAB проверьте подпись командой `jarsigner -verify 'SimpleDebtBook-v4.6.6(45)-release.aab'`, затем сравните SHA-256 из `keytool -printcert -jarfile 'SimpleDebtBook-v4.6.6(45)-release.aab'` с сертификатом **ключа загрузки** в Play Console. При отсутствии конфигурации сборка release останавливается до упаковки.

## Условия перед выпуском

1. В Google Play Console и RuStore Консоли проверить максимальный загруженный versionCode и использовать свободный больший номер.
2. Найти существующий допустимый ключ и сверить SHA-256 сертификата с Console → App integrity (upload certificate для AAB) или сертификатом прежнего APK соответствующего магазина. Не заменять его новым ключом без отдельного процесса магазина.
3. Собрать подписанный AAB и проверить метаданные/сертификат. Сопоставить исходники с опубликованным артефактом либо явно записать, что сравнение отсутствует.
4. Завершить матрицу R1a из плана, включая обновление со старой базы, API 35/36, PIN, процесс, backup и UI. Локальные unit tests не заменяют ее.
5. Проверить актуальные ограничения Console и декларации, затем использовать тестовый трек в рамках поручения владельца.

История предыдущих этапов находится в [журнале плана](../.cursor/plans/simpledebt_recovery_and_growth.plan.md).
