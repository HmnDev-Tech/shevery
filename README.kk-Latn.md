# Shevery

<div align="center">

[![Stars](https://img.shields.io/github/stars/HmnDev-Tech/shevery?style=for-the-badge&color=yellow)](https://github.com/HmnDev-Tech/shevery/stargazers) [![Forks](https://img.shields.io/github/forks/HmnDev-Tech/shevery?style=for-the-badge&color=orange)](https://github.com/HmnDev-Tech/shevery/network/members) [![Downloads](https://img.shields.io/github/downloads/HmnDev-Tech/shevery/total?style=for-the-badge&color=green)](https://github.com/HmnDev-Tech/shevery/releases) [![Telegram](https://img.shields.io/badge/Telegram-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/hmndevtech)

</div>

[English](README.md) | [Русский](README.ru.md) | [Қазақша](README.kk.md) | [Qazaqşa (Latın)](README.kk-Latn.md) | [Português](README.pt.md) | [Español](README.es.md) | [العربية](README.ar.md) | [简体中文](README.zh-CN.md) | [日本語](README.ja.md)

## Fork märtebesi

> [!IMPORTANT]
> **Köşu äreketi qajet:** Qosımşa tañbasınıñ özgéruine baylanıstı (`moe.shizuku.privileged.api` -> `com.hamondev.shevery`) Shevery-di ornatpas burın quraldağı eski resmi Shizuku Manager qosımşasın **MİNDETTİ TÜRDE ÖŞİRİÑİZ**. Öytpese, olar bir-birimen qayşılıqqa tüsep qaladı.
> Joğarğı joba siltemesi: <https://github.com/RikkaApps/Shizuku>
>

## Fork-tağı jaña mümkinşilikter

- Material 3 Expressive komponentterimen, animaciyalarmen, auıstırıp-qosqıştarmen jäne jumır belgilerimen Jetpack Compose basqaru interfeysi.
- **Jaqsartılğan «Comput»** funksiäsı — shell/adb negizinde, Gemini tüsindirmeleri, Makrostar jäne Commandium AI komanda jasau mümkinşiligimen.
- Bul fork-ta qazirgi SDK/qūrastıru qūraldarın qoldanıp, Android 16/17-ge beyimdeu jumāstarı jürgizilude.
- ZIP moduldärdi ornatu jäne basqaruğa arnalğan ADB Modules ekranı.
- Modul mümkinşilikteri: `module.prop`, banner, qosu/öşiru auıstırıp-qosqışı, `action.sh`, sayasatpen retteletin `service.sh`, jergilikti WebUI, joyu, joldardı teqseru, ölşem şekteuleri, şığıs şekteuleri jäne soñğı iske qosu jurnaldarı.
- Modul sayasatı baptauları: Qauipsiz rejim, Tolıq qoldau jäne fondıq äreketterdi basqaru.
- Tikeley ornatuğa bolatın Modulder katalogı.

## Qūjattama

- [ADB Modules nusqaulığı](docs/adb-modules-guide.md)
- [ADB Modules API anıqtaması](docs/adb-modules-api.md)
- [Shizuku Connectors API](docs/shizuku-connectors.md)
- [Android 17 üylesimdiligі](docs/android-17-compatibility.md)
- [ADB Modules jariälau nusqaulığı](docs/github-catalog.md)

## Negizdeme

Root qūqıqtardı qajet etetin qosımşalardı jasau kezinde eñ keñ tarağan ädis — su shell-de keybir komandalardı orındau. Mısalı, komponentterdi qosu/öşiru üşin `pm enable/disable` komandasın qoldanatın qosımşa bar.

Bul ädistiñ öte ülken kemşilikteri bar:

1. **Öte bayau** (köp proces qwru)
2. Mätinderdi öñdeu qajet (**öte senimsiz**)
3. Mümkindik qoljetimdi komandalармен şekteledi
4. ADB-de jetkilikti qūqıq bolsa da, qosımşa jūmıs isteu üşin root qūqıqtı qajet etedi

Shizuku müldem basqa joldı qoldanadı. Tolıq sipattamanı tömenden qarañız.

## Paydalanuşı nusqaulığı jäne Jükteu

<https://shizuku.rikka.app/>

## Skrinşottar

<details>
  <summary>Skriņşottardı aşu üşin basıñız</summary>
  <br/>
  <table>
    <tr>
      <td align="center"><img src="screenshots/main.png" width="300" /><br/><b>Bastı ekran</b></td>
      <td align="center"><img src="screenshots/comput.png" width="300" /><br/><b>Comput konsoli</b></td>
    </tr>
    <tr>
      <td align="center"><img src="screenshots/modules.png" width="300" /><br/><b>ADB Moduldär</b></td>
      <td align="center"><img src="screenshots/settings.png" width="300" /><br/><b>Baptaular</b></td>
    </tr>
  </table>
</details>

## Shevery qalayı jūmıs isteydi?

Aldımen, qosımşalardıñ jüyelik API-di qalayı qoldanatıñın aytıp öteyik. Mısalı, qosımşa ornаtılğan qosımşalardı alğıısı kelse, `PackageManager#getInstalledPackages()` qoldanu kerek ekenin bärimiz bilemiz. Negizinde bul — qosımşa procesi men jüyelik server procesi arasındağı procesaralıq baylanıs (IPC), tek Android freymvorki işki jūmıstı biz üşin atqaradı.

Android bunday IPC üşin `binder`-di qoldanadı. `Binder` server jaqqa klient jaqtıñ uid men pid-in biluge mümkinşilik beredi, sondıqtan jüyelik server qosımşanıñ osı äreketti orındauğa rūqsatı bar-joğın teqsere aladı.

Ädette, qosımşalarğa arnalğan «basqaruşı» (mısalı, `PackageManager`) bolsa, jüyelik server procesinde sоğan säykes «qızmet» (mısalı, `PackageManagerService`) boluı kerek. Qarapayım türde oylasaq: eger qosımşada «qızmettiñ» `binder`-i bolsa, ol «qızmetpen» baylanısa aladı. Qosımşa procesi iske qosılğanda jüyelik qızmetterdiñ binderlerin aladı.

Shizuku paydalanuşığa aldın ala bir procesti — Shizuku serverin — root nemese ADB arqılı iske qosuğa nusqaydı. Qosımşa iske qosılğanda Shizuku serverine arnalğan `binder` de qosımşağa jiberiledi.

Shevery-diñ eñ mağızdı mümkinşiligі — deldal bolu: qosımşadan sўranılardı qabıldap, olardı jüyelik serverge jiberip, nätijeni qaytaradı. Tolığıraq `rikka.shizuku.server.ShizukuService` klasındağı `transactRemote` ädisinen jäne `moe.shizuku.api.ShizukuBinderWrapper` klasınan qarañız.

Söytil, biz joğarı qūqıqpen jüyelik API qoldanu mağsatına jettik. Al qosımşağa bul — jüyelik API-di tikeley qoldanğanmen birdey.

## Äzirleuşi nusqaulığı

### API jäne ülgi

https://github.com/RikkaApps/Shizuku-API

### v11-ge deyіngi nusqalardan köşu

> Qoldanıstağı qosımşalar ärine jūmısın jalğastıradı.

https://github.com/RikkaApps/Shizuku-API#migration-guide-for-existing-applications-use-shizuku-pre-v11

### Nazar audarñız

1. ADB rūqsattarı şekteuli

   ADB rūqsattarı şekteuli jäne är türli jüye nusqalarında ärqalaй. ADB-ğe berilgen rūqsattardı [munda](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/Shell/AndroidManifest.xml) köruğe boladı.

   API-dı şaqırmas burın Shizuku-dıñ ADB paydalanuşısı atınan jūmıs isteytinin teqseru üşin `ShizukuService#getUid` nemese serverde jetkilikti qūqıq bar-joğın teqseru üşin `ShizukuService#checkPermission` qoldanuğa boladı.

2. Android 9-dan bastap jasırın API şekteui

   Android 9-dan bastap qarapayım qosımşalar üşin jasırın API qoldanu şekteulі. Basqa ädisterdi qoldanıñız (mısalı, <https://github.com/LSPosed/AndroidHiddenApiBypass>).

3. Android 8.0 jäne ADB

   Qazirgi uaqıta Shizuku qızmeti qosımşa procesin alu üşin `IActivityManager#registerProcessObserver` pen `IActivityManager#registerUidObserver` (26+) qosındısın qoldanadı — bul qosımşa iske qosılğanda procestiñ mindetti türde jiberiluin qamtamasız etedi. Alayda API 26-da ADB-de `registerUidObserver` qoldanuğa rūqsat joq, sondıqtan eger Shizuku-dı Activity arqılı iske qosılmauı mümkin proceste qoldanu qajet bolsa, möldir Activity iske qosıp binder jiberudi tikeleu ūsınıladı.

4. `transactRemote`-tı tikeley qoldanuğa nazar audarñız

   * API är türli Android nusqalarında özgeşelenuі mümkin, mindetti türde mūqıyat teqserñiz. Sonımen qatar, `android.app.IActivityManager` API 26 jäne odan joğarıda aidl türinde boladı, al `android.app.IActivityManager$Stub` tek API 26-da ğana bar.

   * `SystemServiceHelper.getTransactionCode` dūrıs tranzaksiya kodın ala almauı mümkin, mısalı API 25-te `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages` joq, onıñ ornına `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages_47` bar (bul jağday öñdelgen, biraq basqa jağdaylar da boluı mümkin). `ShizukuBinderWrapper` ädisinde bul mäsele kezdespeydi.

## Shevery-diñ özin äzirleu

### Jinau

- `git clone --recurse-submodules` arqılı klondañız
- Gradle tapsırmasın orındañız: `:manager:assembleDebug` nemese `:manager:assembleRelease`

`:manager:assembleDebug` tapsırması jöndeuge bolatın server jasaydı. Serverdi jöndeu üşin `shizuku_server`-ge jöndeuşini qosıñız. Nazarıñızda bolsın: Android Studio-da «Run/Debug configurations» - «Always install with package manager» belgilenuі kerek, söytip server eñ soñğı kodtı qoldanadı.

## Lisenziya

Bul jobadağı barlıq kod fayldarı Apache 2.0 boyınşa lisenziyalanğan

## Alğıs

* [kerneldroid](https://github.com/kerneldroid) — Qosımşa UI bölikteri, Modulder katalogı jüyesi jäne Android 17 qoldauı üşin.
* [RikkaApps/Shizuku](https://github.com/rikkaapps/Shizuku) — Shizuku API men negizgi derekközder üşin.
* [Landon Moran](https://github.com/LandonMoran) — Dhizuku, TCP jäne t.b. tüzetuler üşin.
* [DP-Hridayan](https://github.com/DP-Hridayan/aShellYou) — «Comput» UI bölikteri üşin.
* [protonpony/Shizuku-Keeper](https://github.com/protonpony/Shizuku-Keeper) — «Sımsız jöndeu arqılı jüktegende avtoqosu» üşin.

## Tаğı...

Endi siz öz ADB modiliñizdi jariälay alasız!
Mına qadamdardı orındañız:
- Öz modiliñizdi jasañız.
- Bul modülge arnalğan GH repozitoriy jasañız.
- «shevery-modules» taqırıbın qosıñız.
- Dерекközder men modülü bar relizdi qosıñız.

> [!CAUTION]
> **Tegin qosımşa turalı xabarlama**
>
> Shevery — **toluğımen tegin qosımşa**. Bul qosımşanı jükteu üşin üşinşi jaq fayl menedjerlerine nemese bulttıq qızmetterge aqşa tölemeñiz.
