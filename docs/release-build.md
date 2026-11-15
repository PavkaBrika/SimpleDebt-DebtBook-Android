# Сборка и подпись SimpleDebt

Проверяемая база P0: `9be2dbe` (`develop_main`), рабочая ветка `codex/p0-r1a-reliability`. Исходная конфигурация — `com.breckneck.debtbook`, versionName `4.6.4`, versionCode `42`. Эти числа не доказывают совпадение с опубликованной версией и не являются автоматически свободным номером нового выпуска.

## Локальная проверка и CI

Нужны JDK 17, Android SDK Platform 36 и Build Tools 36.0.0. Gradle 9.4.1 поставляется через wrapper; версии AGP/Kotlin/Hilt закреплены в корневом `build.gradle`. [AGP 9.1 требует JDK 17 и Build Tools 36.0.0](https://developer.android.com/build/releases/agp-9-1-0-release-notes).

На macOS можно выбрать уже установленный JDK 17 для одной команды, не меняя системную Java:

```sh
JAVA_HOME="$(/usr/libexec/java_home -v 17)" bash ./gradlew :app:assembleDebug :domain:test :data:test :app:testDebugUnitTest :app:lintDebug --continue
```

HTML-отчеты — `<module>/build/reports/tests/`, XML — `<module>/build/test-results/`. APK находится в `app/build/outputs/apk/debug/`. Debug использует стандартную отладочную подпись и предназначен для проверки на тестовых устройствах. Установка поверх магазинного приложения требует совпадения сертификата; не удалять пользовательскую установленную версию ради debug APK.

CI выполняет ту же сборку, unit tests и lint на JDK 17. Push охватывает рабочие ветки; pull request не фильтруется по целевой ветке. [Правила GitHub Actions](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax) описывают различие этих фильтров. CI не подписывает и не публикует release.

## Подпись выпуска

Старый `release → signingConfigs.debug` удален. Release требует существующий ключ владельца; новый ключ не создается. Если используется Play App Signing, локальная сборка обычно подписывается зарегистрированным upload key, а распространяемый APK — app signing key Google Play. Для другого магазина необходимо сохранить ранее применявшийся сертификат. Эти ключи нельзя считать взаимозаменяемыми. См. [официальное руководство по подписи](https://developer.android.com/studio/publish/app-signing).

Задайте четыре значения через защищенную среду сборки:

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

`verifyReleaseSigning` проверяет наличие настроек/файла и запрещает стандартный alias `androiddebugkey`; реальную доступность ключа и пароля проверяет Android Gradle Plugin при подписи. Предварительная проверка сама по себе не доказывает совпадение сертификата с магазином. При отсутствии конфигурации сборка release останавливается до упаковки; debug по-прежнему доступен.

## Условия перед выпуском

1. В Play Console проверить максимальный versionCode во всех используемых треках и назначить свободный больший номер. Не угадывать его по локальной версии 42.
2. Найти существующий допустимый ключ и сверить SHA-256 сертификата с Console → App integrity (upload certificate для AAB) или сертификатом прежнего APK соответствующего магазина. Не заменять его новым ключом без отдельного процесса магазина.
3. Собрать подписанный AAB и проверить метаданные/сертификат. Сопоставить исходники с опубликованным артефактом либо явно записать, что сравнение отсутствует.
4. Завершить матрицу R1a из плана, включая обновление со старой базы, API 35/36, PIN, процесс, backup и UI. Локальные unit tests не заменяют ее.
5. Проверить актуальные ограничения Console и декларации, затем использовать тестовый трек в рамках поручения владельца.

На этапе настройки P0 релизный ключ и параметры подписи в проекте/доступной конфигурации не найдены. Подписанный магазинный артефакт, соответствие сертификату и опубликованной версии не подтверждены. Результаты остальных проверок фиксируются в [журнале плана](../.cursor/plans/simpledebt_recovery_and_growth.plan.md).
