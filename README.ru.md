# Shevery

<div align="center">

[![Stars](https://img.shields.io/github/stars/HmnDev-Tech/shevery?style=for-the-badge&color=yellow)](https://github.com/HmnDev-Tech/shevery/stargazers) [![Forks](https://img.shields.io/github/forks/HmnDev-Tech/shevery?style=for-the-badge&color=orange)](https://github.com/HmnDev-Tech/shevery/network/members) [![Downloads](https://img.shields.io/github/downloads/HmnDev-Tech/shevery/total?style=for-the-badge&color=green)](https://github.com/HmnDev-Tech/shevery/releases) [![Telegram](https://img.shields.io/badge/Telegram-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/hmndevtech)

</div>

[English](README.md) | [Русский](README.ru.md) | [Қазақша](README.kk.md) | [Qazaqşa (Latın)](README.kk-Latn.md) | [Português](README.pt.md) | [Español](README.es.md) | [العربية](README.ar.md) | [简体中文](README.zh-CN.md) | [日本語](README.ja.md)

## Статус форка

> [!IMPORTANT]
> **Требуется действие по миграции:** из-за смены идентификатора приложения (`moe.shizuku.privileged.api` -> `com.hamondev.shevery`) вы **ДОЛЖНЫ УДАЛИТЬ** старое официальное приложение Shizuku Manager с устройства перед установкой Shevery. Иначе они будут конфликтовать.
> Апстрим-проект: <https://github.com/RikkaApps/Shizuku>
>

## Нововведения форка

- Интерфейс менеджера на Jetpack Compose с компонентами Material 3 Expressive, анимациями, переключателями и скруглёнными иконками.
- **Управление Device Owner (Владелец устройства)**: полноценное управление правами Device Owner (делегирование полномочий приложениям, передача прав владельца, включение ADB через DPM) в стиле и совместимости с Dhizuku.
- **Биометрическая защита и безопасность**: главный переключатель запроса биометрии или PIN/пароля/графключа с настройкой тайм-аута сессии и выборочной защитой действий.
- **Экспериментальная поддержка Dhizuku**: специальная система bridging для Device Owner, доступная в разделе «Лаборатория».
- **Улучшенная функция «Comput»** на основе shell/adb с объяснениями от Gemini, макросами и созданием команд через Commandium AI.
- Работа над поддержкой Android 16/17 с актуальными SDK и инструментами сборки в этом форке.
- Экран ADB-модулей для установки ZIP-модулей и управления ими.
- Возможности модулей: `module.prop`, баннер, переключатель вкл/выкл, `action.sh`, контролируемый политиками `service.sh`, локальный WebUI, удаление, проверки путей, ограничения размера, ограничения вывода и журналы последних запусков.
- Настройки политики модулей: безопасный режим, полный доступ и управление фоновыми действиями.
- Каталог модулей с прямой установкой.

## Документация

- [Руководство по ADB-модулям](docs/adb-modules-guide.md)
- [Справочник API ADB-модулей](docs/adb-modules-api.md)
- [API коннекторов Shizuku](docs/shizuku-connectors.md)
- [Совместимость с Android 17](docs/android-17-compatibility.md)
- [Руководство по публикации ADB-модулей](docs/github-catalog.md)

## Предыстория

При разработке приложений, которым нужен root, самый распространённый способ — выполнять команды в su shell. Например, есть приложение, которое использует команду `pm enable/disable` для включения/отключения компонентов.

У этого способа очень большие недостатки:

1. **Крайне медленно** (создание множества процессов)
2. Нужно обрабатывать текст (**совершенно ненадёжно**)
3. Возможности ограничены доступными командами
4. Даже если у ADB достаточно прав, приложению всё равно нужен root для работы

Shizuku использует совершенно другой подход. Подробное описание — ниже.

## Руководство пользователя и загрузка

<https://shizuku.rikka.app/>

## Скриншоты

<details>
  <summary>Нажмите, чтобы открыть скриншоты</summary>
  <br/>
  <table>
    <tr>
      <td align="center"><img src="screenshots/main.png" width="300" /><br/><b>Главный экран</b></td>
      <td align="center"><img src="screenshots/comput.png" width="300" /><br/><b>Консоль Comput</b></td>
    </tr>
    <tr>
      <td align="center"><img src="screenshots/modules.png" width="300" /><br/><b>ADB-модули</b></td>
      <td align="center"><img src="screenshots/settings.png" width="300" /><br/><b>Настройки</b></td>
    </tr>
  </table>
</details>

## Как работает Shevery?

Сначала нужно поговорить о том, как приложения используют системные API. Например, если приложение хочет получить список установленных приложений, мы все знаем, что нужно использовать `PackageManager#getInstalledPackages()`. На самом деле это межпроцессное взаимодействие (IPC) процесса приложения и процесса системного сервера, просто Android-фреймворк берёт внутреннюю работу на себя.

Android использует `binder` для такого IPC. `Binder` позволяет серверной стороне узнать uid и pid клиентской стороны, чтобы системный сервер мог проверить, есть ли у приложения разрешение на выполнение операции.

Обычно, если у приложений есть «менеджер» (например, `PackageManager`), то в процессе системного сервера должен быть «сервис» (например, `PackageManagerService`). Можно упрощённо считать: если у приложения есть `binder` «сервиса», оно может communicating с «сервисом». При запуске процесс приложения получает binder'ы системных сервисов.

Shizuku предлагает пользователю сначала запустить процесс — сервер Shizuku — с правами root или через ADB. Когда приложение запускается, `binder` сервера Shizuku также передаётся приложению.

Самая важная возможность, которую предоставляет Shevery, — быть посредником: принимать запросы от приложения, отправлять их системному серверу и возвращать результаты. Подробности смотрите в методе `transactRemote` класса `rikka.shizuku.server.ShizukuService` и в классе `moe.shizuku.api.ShizukuBinderWrapper`.

Таким образом мы достигли цели — использовать системные API с повышенными правами. А для приложения это почти неотличимо от прямого использования системных API.

## Руководство разработчика

### API и пример

https://github.com/RikkaApps/Shizuku-API

### Миграция с версий до v11

> Существующие приложения, конечно же, продолжают работать.

https://github.com/RikkaApps/Shizuku-API#migration-guide-for-existing-applications-use-shizuku-pre-v11

### Обратите внимание

1. Права ADB ограничены

   Права ADB ограничены и различаются в разных версиях системы. Посмотреть права, выданные ADB, можно [здесь](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/Shell/AndroidManifest.xml).

   Перед вызовом API можно использовать `ShizukuService#getUid`, чтобы проверить, работает ли Shizuku от пользователя ADB, или `ShizukuService#checkPermission`, чтобы проверить, достаточно ли у сервера прав.

2. Ограничение скрытых API начиная с Android 9

   Начиная с Android 9 использование скрытых API обычными приложениями ограничено. Используйте другие методы (например, <https://github.com/LSPosed/AndroidHiddenApiBypass>).

3. Android 8.0 и ADB

   В настоящее время сервис Shizuku получает процесс приложения, комбинируя `IActivityManager#registerProcessObserver` и `IActivityManager#registerUidObserver` (26+), чтобы гарантировать отправку процесса при запуске приложения. Однако на API 26 у ADB нет прав на использование `registerUidObserver`, поэтому если нужно использовать Shizuku в процессе, который может быть запущен не через Activity, рекомендуется инициировать отправку binder запуском прозрачной activity.

4. На что обратить внимание при прямом использовании `transactRemote`

   * API может отличаться в разных версиях Android, обязательно проверяйте внимательно. Также `android.app.IActivityManager` существует в aidl-форме на API 26 и новее, а `android.app.IActivityManager$Stub` существует только на API 26.

   * `SystemServiceHelper.getTransactionCode` может получить неверный код транзакции, например `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages` не существует на API 25, и там есть `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages_47` (этот случай уже обработан, но не исключены другие подобные ситуации). При методе `ShizukuBinderWrapper` эта проблема не встречается.

## Разработка самого Shizuku

### Сборка

- Клонируйте с `git clone --recurse-submodules`
- Запустите задачу Gradle `:manager:assembleDebug` или `:manager:assembleRelease`

Задача `:manager:assembleDebug` генерирует отлаживаемый сервер. Можно подключить отладчик к `shizuku_server` для отладки сервера. Обратите внимание: в Android Studio в «Run/Debug configurations» должен быть отмечен пункт «Always install with package manager», чтобы сервер использовал актуальный код.

## Как работает «Запуск (через Dhizuku)»?

* Сначала нужно запустить Shevery с помощью ПК/OTG или беспроводной отладки.
* Затем запустите Dhizuku.
  - Не запускайте Shevery через Dhizuku первым делом.

## Лицензия

Все файлы кода в этом проекте лицензированы под Apache 2.0

## Благодарности

* [kerneldroid](https://github.com/kerneldroid) — за части интерфейса приложения, систему каталога модулей и поддержку Android 17.
* [iamr0s/Dhizuku](https://github.com/iamr0s/Dhizuku) — за проект Dhizuku, архитектуру и концепции Device Owner, интегрированные в Shevery.
* [RikkaApps/Shizuku](https://github.com/rikkaapps/Shizuku) — за Shizuku API и основные исходники.
* [Landon Moran](https://github.com/LandonMoran) — за Dhizuku, исправления TCP и др.
* [DP-Hridayan](https://github.com/DP-Hridayan/aShellYou) — за части интерфейса «Comput».
* [protonpony/Shizuku-Keeper](https://github.com/protonpony/Shizuku-Keeper) — за «Автозапуск при загрузке через беспроводную отладку».

## И ещё...

Теперь вы можете опубликовать свой ADB-модуль!
Выполните следующие шаги:
- Создайте свой модуль.
- Создайте GH-репозиторий для этого модуля.
- Добавьте топик «shevery-modules».
- Добавьте исходники и релиз с модулем.

> [!CAUTION]
> **Уведомление о бесплатности приложения**
>
> Shevery — **полностью бесплатное приложение**. Не платите сторонним файловым менеджерам или облачным сервисам за загрузку этого приложения.
