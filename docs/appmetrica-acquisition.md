# Проверка установок Simple Debt Book в AppMetrica

Google Play и RuStore используют один проект Debt Book (App ID `6392758`) и один SDK API key для пакета `com.breckneck.debtbook`. Ключ для release настраивается по инструкции сборки. [Сборка и подпись](release-build.md).

| Магазин | Ссылка для ручной проверки | Настройки трекера |
| --- | --- | --- |
| Google Play | [Открыть тестовую ссылку Google Play](https://redirect.appmetrica.yandex.com/serve/1038929808093879441?appmetrica_js_redirect=0) | [Debt Book — Яндекс Директ — Google Play](https://appmetrica.yandex.ru/campaign?campaignId=1038929808093879441&appId=6392758) |
| RuStore | [Открыть тестовую ссылку RuStore](https://redirect.appmetrica.yandex.com/serve/606584242574466277?appmetrica_js_redirect=0) | [Debt Book — Яндекс Директ — RuStore](https://appmetrica.yandex.ru/campaign?campaignId=606584242574466277&appId=6392758) |

В кампанию Директа вставляйте полный URL из поля **Трекинг-ссылка** соответствующего трекера. В нём есть макросы `click_id={LOGID}` и `campaign_id={campaign_id}`. Оба трекера относятся к партнёру `Yandex.Direct`, используют платформу Android и ведут в свой магазин. [Настройка ссылок в Директе](https://yandex.ru/support/direct/ru/products-mobile-apps-ads/tracking-systems).

## После выхода обновления

1. Для каждого магазина используйте отдельное тестовое устройство без предыдущих установок Simple Debt Book. На устройстве для RuStore должен быть установлен клиент RuStore.
2. Откройте ссылку из таблицы на самом устройстве, перейдите в соответствующий магазин, установите опубликованную сборку с AppMetrica и один раз запустите её с подключением к интернету.
3. За дату первого запуска проверьте метрику **Установки** в двух отчётах User Acquisition. В **Debt Book — установки по магазинам** группировка **App Installer** показывает фактический установщик. Ожидаются Google Play (`com.android.vending`) и RuStore (`ru.vk.store`). В режиме совместимости RuStore Android может указать системный установщик. [Отчёт по магазинам](https://appmetrica.yandex.ru/statistic?appId=6392758&reportId=4b512c5f-b207-49f9-b46c-4373247dc869). [Как RuStore определяет установщик](https://www.rustore.ru/help/sdk/payments/kotlin-java/faq).
4. В **Debt Book — установки по источникам** разверните партнёра **Yandex.Direct** и проверьте одну установку у каждого трекера. Для просмотра органики добавьте **Тип атрибуции** и смотрите строку **Органика**. Если у одного трекера несколько кампаний Директа, добавьте группировку **Параметр** со значением `campaign_id`. [Отчёт по источникам](https://appmetrica.yandex.ru/statistic?appId=6392758&reportId=f14129b2-c038-4879-8c01-b6da2c8a8843). [Группировки и метрики User Acquisition](https://appmetrica.yandex.com/docs/ru/mobile-reports/user-acquisition-report).

После интеграции подтверждения оплаты ЮKassa передавайте цену и валюту один раз на подтверждённый платёж. Для оценки результата добавьте **In-App Revenue → Платящие пользователи** в отчёт по источникам. [Метрики User Acquisition](https://appmetrica.yandex.com/docs/ru/mobile-reports/user-acquisition-report#metrics).
