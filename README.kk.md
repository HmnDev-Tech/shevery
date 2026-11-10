# Shevery

<div align="center">

[![Stars](https://img.shields.io/github/stars/HmnDev-Tech/shevery?style=for-the-badge&color=yellow)](https://github.com/HmnDev-Tech/shevery/stargazers) [![Forks](https://img.shields.io/github/forks/HmnDev-Tech/shevery?style=for-the-badge&color=orange)](https://github.com/HmnDev-Tech/shevery/network/members) [![Downloads](https://img.shields.io/github/downloads/HmnDev-Tech/shevery/total?style=for-the-badge&color=green)](https://github.com/HmnDev-Tech/shevery/releases) [![Telegram](https://img.shields.io/badge/Telegram-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/hmndevtech)

</div>

[English](README.md) | [Русский](README.ru.md) | [Қазақша](README.kk.md) | [Qazaqşa (Latın)](README.kk-Latn.md) | [Português](README.pt.md) | [Español](README.es.md) | [العربية](README.ar.md) | [简体中文](README.zh-CN.md) | [日本語](README.ja.md)

## Форк мәртебесі

> [!IMPORTANT]
> **Көшу әрекеті қажет:** Қосымша таңбасының өзгеруіне байланысты (`moe.shizuku.privileged.api` -> `com.hamondev.shevery`) Shevery-ді орнатпас бұрын құралдағы ескі ресми Shizuku Manager қосымшасын **МІНДЕТТІ ТҮРДЕ ӨШІРІҢІЗ**. Өйтпесе, олар бір-бірімен қайшылыққа түсіп қалады.
> Жоғарғы жоба сілтемесі: <https://github.com/RikkaApps/Shizuku>
>

## Форктағы жаңа мүмкіндіктер

- Material 3 Expressive компоненттерімен, анимациялармен, ауыстырып-қосқыштармен және жұмыр белгішелермен Jetpack Compose басқару интерфейсі.
- **Жақсартылған «Comput»** функциясы — shell/adb негізінде, Gemini түсіндірмелері, Макростар және Commandium AI команда жасау мүмкіншілігімен.
- Бұл форкта қазіргі SDK/құрастыру құралдарын қолданып, Android 16/17-ге бейімдеу жұмыстары жүргізілуде.
- ZIP модульдерді орнату және басқаруға арналған ADB Modules экраны.
- Модуль мүмкіншіліктері: `module.prop`, баннер, қосу/өшіру ауыстырып-қосқышы, `action.sh`, саясатпен реттелетін `service.sh`, жергілікті WebUI, жою, жолдарды тексеру, өлшем шектеулері, шығыс шектеулері және соңғы іске қосу журналдары.
- Модуль саясаты баптаулары: Қауіпсіз режим, Толық қолдау және фондық әрекеттерді басқару.
- Тікелей орнатуға болатын Модульдер каталогы.

## Құжаттама

- [ADB Modules нұсқаулығы](docs/adb-modules-guide.md)
- [ADB Modules API анықтамасы](docs/adb-modules-api.md)
- [Shizuku Connectors API](docs/shizuku-connectors.md)
- [Android 17 үйлесімділігі](docs/android-17-compatibility.md)
- [ADB Modules жариялау нұсқаулығы](docs/github-catalog.md)

## Негіздеме

Root құқықтарды қажет ететін қосымшаларды жасау кезінде ең кең тараған әдіс — su shell-де кейбір командаларды орындау. Мысалы, компоненттерді қосу/өшіру үшін `pm enable/disable` командасын қолданатын қосымша бар.

Бұл әдістің өте үлкен кемшіліктері бар:

1. **Өте баяу** (көп процесс құру)
2. Мәтіндерді өңдеу қажет (**өте сенімсіз**)
3. Мүмкіндік қолжетімді командалармен шектеледі
4. ADB-де жеткілікті құқық болса да, қосымша жұмыс істеу үшін root құқықты қажет етеді

Shizuku мүлдем басқа жолды қолданады. Толық сипаттаманы төменнен қараңыз.

## Пайдаланушы нұсқаулығы және Жүктеу

<https://shizuku.rikka.app/>

## Скриншоттар

<details>
  <summary>Скриншоттарды ашу үшін басыңыз</summary>
  <br/>
  <table>
    <tr>
      <td align="center"><img src="screenshots/main.png" width="300" /><br/><b>Басты экран</b></td>
      <td align="center"><img src="screenshots/comput.png" width="300" /><br/><b>Comput консолі</b></td>
    </tr>
    <tr>
      <td align="center"><img src="screenshots/modules.png" width="300" /><br/><b>ADB Модульдер</b></td>
      <td align="center"><img src="screenshots/settings.png" width="300" /><br/><b>Баптаулар</b></td>
    </tr>
  </table>
</details>

## Shevery қалай жұмыс істейді?

Алдымен, қосымшалардың жүйелік API-ді қалай қолданатынын айтып өтейік. Мысалы, қосымша орнатылған қосымшаларды алғысы келсе, `PackageManager#getInstalledPackages()` қолдану керек екенін бәріміз білеміз. Негізінде бұл — қосымша процесі мен жүйелік сервер процесі арасындағы процесаралық байланыс (IPC), тек Android фреймворкі ішкі жұмысты біз үшін атқарады.

Android мұндай IPC үшін `binder`-ді қолданады. `Binder` сервер жаққа клиент жақтың uid мен pid-ін білуге мүмкіншілік береді, сондықтан жүйелік сервер қосымшаның осы әрекетті орындауға рұқсаты бар-жоғын тексере алады.

Әдетте, қосымшаларға арналған «басқарушы» (мысалы, `PackageManager`) болса, жүйелік сервер процесінде соған сәйкес «қызмет» (мысалы, `PackageManagerService`) болуы керек. Қарапайым түрде ойласақ: егер қосымшада «қызметтің» `binder`-і болса, ол «қызметпен» байланыса алады. Қосымша процесі іске қосылғанда жүйелік қызметтердің binderлерін алады.

Shizuku пайдаланушыға алдын ала бір процесті — Shizuku серверін — root немесе ADB арқылы іске қосуға нұсқайды. Қосымша іске қосылғанда Shizuku серверіне арналған `binder` де қосымшаға жіберіледі.

Shevery-дің ең маңызды мүмкіншілігі — делдал болу: қосымшадан сұраныларды қабылдап, оларды жүйелік серверге жіберіп, нәтижені қайтарады. Толығырақ `rikka.shizuku.server.ShizukuService` класындағы `transactRemote` әдісінен және `moe.shizuku.api.ShizukuBinderWrapper` класынан қараңыз.

Сөйтіп, біз жоғары құқықпен жүйелік API қолдану мақсатына жеттік. Ал қосымшаға бұл — жүйелік API-ді тікелей қолданғанмен бірдей.

## Әзірлеуші нұсқаулығы

### API және үлгі

https://github.com/RikkaApps/Shizuku-API

### v11-ге дейінгі нұсқалардан көшу

> Қолданыстағы қосымшалар әрине жұмысын жалғастырады.

https://github.com/RikkaApps/Shizuku-API#migration-guide-for-existing-applications-use-shizuku-pre-v11

### Назар аударыңыз

1. ADB рұқсаттары шектеулі

   ADB рұқсаттары шектеулі және әр түрлі жүйе нұсқаларында әрқалай. ADB-ге берілген рұқсаттарды [мұнда](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/Shell/AndroidManifest.xml) көруге болады.

   API-ды шақырмас бұрын Shizuku-дың ADB пайдаланушысы атынан жұмыс істейтінін тексеру үшін `ShizukuService#getUid` немесе серверде жеткілікті құқық бар-жоғын тексеру үшін `ShizukuService#checkPermission` қолдануға болады.

2. Android 9-дан бастап жасырын API шектеуі

   Android 9-дан бастап қарапайым қосымшалар үшін жасырын API қолдану шектеулі. Басқа әдістерді қолданыңыз (мысалы, <https://github.com/LSPosed/AndroidHiddenApiBypass>).

3. Android 8.0 және ADB

   Қазіргі уақытта Shizuku қызметі қосымша процесін алу үшін `IActivityManager#registerProcessObserver` пен `IActivityManager#registerUidObserver` (26+) қосындысын қолданады — бұл қосымша іске қосылғанда процестің міндетті түрде жіберілуін қамтамасыз етеді. Алайда API 26-да ADB-де `registerUidObserver` қолдануға рұқсат жоқ, сондықтан егер Shizuku-ды Activity арқылы іске қосылмауы мүмкін процесте қолдану қажет болса, мөлдір Activity іске қосып binder жіберуді түрткілеу ұсынылады.

4. `transactRemote`-ты тікелей қолдануға назар аударыңыз

   * API әр түрлі Android нұсқаларында өзгеруі мүмкін, міндетті түрде мұқият тексеріңіз. Сонымен қатар, `android.app.IActivityManager` API 26 және одан жоғарыда aidl түрінде болады, ал `android.app.IActivityManager$Stub` тек API 26-да ғана бар.

   * `SystemServiceHelper.getTransactionCode` дұрыс транзакция кодын ала алмауы мүмкін, мысалы API 25-те `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages` жоқ, оның орнына `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages_47` бар (бұл жағдай өңделген, бірақ басқа жағдайлар да болуы мүмкін). `ShizukuBinderWrapper` әдісінде бұл мәселе кездеспейді.

## Shevery-дің өзін әзірлеу

### Жинау

- `git clone --recurse-submodules` арқылы клондаңыз
- Gradle тапсырмасын орындаңыз: `:manager:assembleDebug` немесе `:manager:assembleRelease`

`:manager:assembleDebug` тапсырмасы жөндеуге болатын сервер жасайды. Серверді жөндеу үшін `shizuku_server`-ге жөндеушіні қосыңыз. Назарыңызда болсын: Android Studio-да «Run/Debug configurations» - «Always install with package manager» белгіленуі керек, сөйтіп сервер ең соңғы кодты қолданады.

## Лицензия

Бұл жобадағы барлық код файлдары Apache 2.0 бойынша лицензияланған

## Алғыс

* [kerneldroid](https://github.com/kerneldroid) — Қосымша UI бөліктері, Модульдер каталогы жүйесі және Android 17 қолдауы үшін.
* [RikkaApps/Shizuku](https://github.com/rikkaapps/Shizuku) — Shizuku API мен негізгі дереккөздер үшін.
* [Landon Moran](https://github.com/LandonMoran) — Dhizuku, TCP және т.б. түзетулер үшін.
* [DP-Hridayan](https://github.com/DP-Hridayan/aShellYou) — «Comput» UI бөліктері үшін.
* [protonpony/Shizuku-Keeper](https://github.com/protonpony/Shizuku-Keeper) — «Сымсыз жөндеу арқылы жүктелгенде автоқосу» үшін.

## Тағы...

Енді сіз өз ADB модуліңізді жариялай аласыз!
Мына қадамдарды орындаңыз:
- Өз модуліңізді жасаңыз.
- Бұл модульге арналған GH репозиторий жасаңыз.
- «shevery-modules» тақырыбын қосыңыз.
- Дереккөздер мен модулі бар релизді қосыңыз.

> [!CAUTION]
> **Тегін қосымша туралы хабарлама**
>
> Shevery — **толығымен тегін қосымша**. Бұл қосымшаны жүктеу үшін үшінші жақ файл менеджерлеріне немесе бұлттық қызметтерге ақша төлемеңіз.
