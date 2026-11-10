# Shevery

<div align="center">

[![Stars](https://img.shields.io/github/stars/HmnDev-Tech/shevery?style=for-the-badge&color=yellow)](https://github.com/HmnDev-Tech/shevery/stargazers) [![Forks](https://img.shields.io/github/forks/HmnDev-Tech/shevery?style=for-the-badge&color=orange)](https://github.com/HmnDev-Tech/shevery/network/members) [![Downloads](https://img.shields.io/github/downloads/HmnDev-Tech/shevery/total?style=for-the-badge&color=green)](https://github.com/HmnDev-Tech/shevery/releases) [![Telegram](https://img.shields.io/badge/Telegram-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/hmndevtech)

</div>

[English](README.md) | [Русский](README.ru.md) | [Қазақша](README.kk.md) | [Qazaqşa (Latın)](README.kk-Latn.md) | [Português](README.pt.md) | [Español](README.es.md) | [العربية](README.ar.md) | [简体中文](README.zh-CN.md) | [日本語](README.ja.md)

## حالة الفرع (Fork)

> [!IMPORTANT]
> **يلزم إجراء الترحيل:** بسبب تغيير معرّف التطبيق (`moe.shizuku.privileged.api` -> `com.hamondev.shevery`)، يجب عليك **إلغاء تثبيت** أي نسخة رسمية قديمة من تطبيق Shizuku Manager من جهازك قبل تثبيت Shevery. وإلا سيحدث تعارض بينهما.
> مرجع المشروع الأصلي: <https://github.com/RikkaApps/Shizuku>
>

## إضافات الفرع

- واجهة مدير مبنية بـ Jetpack Compose مع مكونات Material 3 Expressive والحركات والمفاتيح وأيقونات دائرية.
- ميزة **«Comput» المحسّنة** المبنية على shell/adb مع شرح Gemini ووحدات الماكرو وإنشاء الأوامر عبر Commandium AI.
- العمل على دعم Android 16/17 باستخدام أحدث SDK وأدوات البناء في هذا الفرع.
- شاشة وحدات ADB لتثبيت وإدارة وحدات ZIP.
- مزايا الوحدات: `module.prop`، والبانر، ومفتاح التفعيل/التعطيل، و`action.sh`، و`service.sh` الخاضع للسياسات، وواجهة WebUI المحلية، والحذف، وفحص المسارات، وحدود الحجم، وحدود المخرجات، وسجلات آخر عمليات التشغيل.
- إعدادات سياسة الوحدات: الوضع الآمن، والوصول الكامل، والتحكم في إجراءات الخلفية.
- كتالوج الوحدات مع التثبيت المباشر.

## التوثيق

- [دليل وحدات ADB](docs/adb-modules-guide.md)
- [مرجع API لوحدات ADB](docs/adb-modules-api.md)
- [API موصلات Shizuku](docs/shizuku-connectors.md)
- [التوافق مع Android 17](docs/android-17-compatibility.md)
- [دليل نشر وحدات ADB](docs/github-catalog.md)

## خلفية

عند تطوير تطبيقات تتطلب صلاحيات الجذر (root)، فإن الطريقة الأكثر شيوعًا هي تشغيل بعض الأوامر في غلاف su. على سبيل المثال، هناك تطبيق يستخدم الأمر `pm enable/disable` لتفعيل/تعطيل المكونات.

هذه الطريقة لها عيوب كبيرة جدًا:

1. **بطيئة للغاية** (إنشاء عمليات متعددة)
2. تحتاج إلى معالجة النصوص (**غير موثوقة إطلاقًا**)
3. الإمكانيات محدودة بالأوامر المتاحة
4. حتى لو كانت لدى ADB صلاحيات كافية، فإن التطبيق يحتاج إلى صلاحيات الجذر ليعمل

يستخدم Shizuku طريقة مختلفة تمامًا. انظر الوصف التفصيلي أدناه.

## دليل المستخدم والتنزيل

<https://shizuku.rikka.app/>

## لقطات الشاشة

<details>
  <summary>انقر لفتح لقطات الشاشة</summary>
  <br/>
  <table>
    <tr>
      <td align="center"><img src="screenshots/main.png" width="300" /><br/><b>الشاشة الرئيسية</b></td>
      <td align="center"><img src="screenshots/comput.png" width="300" /><br/><b>طرفية Comput</b></td>
    </tr>
    <tr>
      <td align="center"><img src="screenshots/modules.png" width="300" /><br/><b>وحدات ADB</b></td>
      <td align="center"><img src="screenshots/settings.png" width="300" /><br/><b>الإعدادات</b></td>
    </tr>
  </table>
</details>

## كيف يعمل Shevery؟

أولًا، نحتاج إلى الحديث عن كيفية استخدام التطبيقات لواجهات النظام. على سبيل المثال، إذا أراد التطبيق الحصول على التطبيقات المثبتة، فنحن جميعًا نعلم أنه يجب استخدام `PackageManager#getInstalledPackages()`. وهذا في الواقع عملية اتصال بين العمليات (IPC) بين عملية التطبيق وعملية خادم النظام، فقط إطار عمل Android يتولى الأعمال الداخلية نيابة عنا.

يستخدم Android الوسيط `binder` لهذا النوع من IPC. يتيح `Binder` لجهة الخادم معرفة uid وpid جهة العميل، حتى يتمكن خادم النظام من التحقق مما إذا كان لدى التطبيق إذن لتنفيذ العملية.

عادةً، إذا كان هناك «مدير» (مثل `PackageManager`) لتستخدمه التطبيقات، فيجب أن تكون هناك «خدمة» (مثل `PackageManagerService`) في عملية خادم النظام. يمكننا ببساطة أن نفترض أنه إذا كان التطبيق يحمل `binder` الخاص بـ «الخدمة»، فيمكنه التواصل مع «الخدمة». وستستقبل عملية التطبيق روابط (binders) خدمات النظام عند بدء التشغيل.

يرشد Shizuku المستخدمين إلى تشغيل عملية أولًا، وهي خادم Shizuku، بصلاحيات الجذر أو عبر ADB. وعندما يبدأ التطبيق، سيتم أيضًا إرسال `binder` الخاص بخادم Shizuku إلى التطبيق.

أهم ميزة يقدمها Shevery هي العمل كوسيط يستقبل الطلبات من التطبيق ويرسلها إلى خادم النظام ثم يعيد النتائج. يمكنك الاطلاع على الطريقة `transactRemote` في الفئة `rikka.shizuku.server.ShizukuService`، والفئة `moe.shizuku.api.ShizukuBinderWrapper` للتفاصيل.

وهكذا حققنا هدفنا، وهو استخدام واجهات النظام بصلاحيات أعلى. وبالنسبة للتطبيق، فالأمر مطابق تقريبًا لاستخدام واجهات النظام مباشرة.

## دليل المطور

### API والمثال

https://github.com/RikkaApps/Shizuku-API

### الترحيل من إصدارات ما قبل v11

> التطبيقات الحالية ما زالت تعمل بالطبع.

https://github.com/RikkaApps/Shizuku-API#migration-guide-for-existing-applications-use-shizuku-pre-v11

### تنبيه

1. صلاحيات ADB محدودة

   صلاحيات ADB محدودة وتختلف باختلاف إصدارات النظام. يمكنك الاطلاع على الصلاحيات الممنوحة لـ ADB [هنا](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/Shell/AndroidManifest.xml).

   قبل استدعاء API، يمكنك استخدام `ShizukuService#getUid` للتحقق مما إذا كان Shizuku يعمل بمستخدم ADB، أو استخدام `ShizukuService#checkPermission` للتحقق مما إذا كانت لدى الخادم صلاحيات كافية.

2. قيود الواجهات المخفية اعتبارًا من Android 9

   اعتبارًا من Android 9، أصبح استخدام الواجهات المخفية محدودًا للتطبيقات العادية. يرجى استخدام طرق أخرى (مثل <https://github.com/LSPosed/AndroidHiddenApiBypass>).

3. Android 8.0 وADB

   في الوقت الحالي، يحصل خادم Shizuku على عملية التطبيق عبر الجمع بين `IActivityManager#registerProcessObserver` و`IActivityManager#registerUidObserver` (26+) لضمان إرسال عملية التطبيق عند بدء تشغيله. ومع ذلك، في API 26 تفتقر ADB إلى صلاحيات استخدام `registerUidObserver`، لذا إذا كنت بحاجة إلى استخدام Shizuku في عملية قد لا تبدأ عبر Activity، يُنصح بتحفيز إرسال الـ binder عبر بدء activity شفاف.

4. الاستخدام المباشر لـ `transactRemote` يتطلب الانتباه

   * قد تختلف الواجهة باختلاف إصدارات Android، لذا تأكد من التحقق بعناية. كما أن `android.app.IActivityManager` بصيغة aidl في API 26 والإصدارات الأحدث، بينما `android.app.IActivityManager$Stub` موجود فقط في API 26.

   * قد لا يحصل `SystemServiceHelper.getTransactionCode` على رمز المعاملة الصحيح، فمثلًا `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages` غير موجود في API 25 ويوجد بدلًا منه `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages_47` (تم التعامل مع هذه الحالة، لكن لا يُستبعد وجود حالات أخرى). ولا تواجه هذه المشكلة مع طريقة `ShizukuBinderWrapper`.

## تطوير Shizuku نفسه

### البناء

- انسخ المستودع مع `git clone --recurse-submodules`
- شغّل مهمة Gradle `:manager:assembleDebug` أو `:manager:assembleRelease`

تُنتج المهمة `:manager:assembleDebug` خادمًا قابلًا لتصحيح الأخطاء. يمكنك إرفاق المصحح بـ `shizuku_server` لتصحيح الخادم. انتبه إلى أنه في Android Studio يجب تفعيل «Run/Debug configurations» - «Always install with package manager» حتى يستخدم الخادم أحدث الشيفرة.

## الترخيص

جميع ملفات الشيفرة في هذا المشروع مرخصة بموجب Apache 2.0

## شكر وتقدير

* [kerneldroid](https://github.com/kerneldroid) - لأجزاء من واجهة التطبيق ونظام كتالوج الوحدات ودعم Android 17.
* [RikkaApps/Shizuku](https://github.com/rikkaapps/Shizuku) - لواجهة Shizuku API والمصادر الرئيسية.
* [Landon Moran](https://github.com/LandonMoran) - لإصلاحات Dhizuku وTCP وغيرها.
* [DP-Hridayan](https://github.com/DP-Hridayan/aShellYou) - لأجزاء من واجهة «Comput».
* [protonpony/Shizuku-Keeper](https://github.com/protonpony/Shizuku-Keeper) - لـ «التشغيل التلقائي عند الإقلاع عبر التصحيح اللاسلكي».

## وأيضًا...

الآن يمكنك نشر وحدة ADB الخاصة بك!
اتبع هذه الخطوات:
- أنشئ وحدتك.
- أنشئ مستودع GH لهذه الوحدة.
- أضف الموضوع «shevery-modules».
- أضف المصادر والإصدار مع الوحدة.

> [!CAUTION]
> **إشعار التطبيق المجاني**
>
> Shevery **تطبيق مجاني تمامًا**. لا تدفع لأي مدير ملفات خارجي أو خدمات سحابية لتنزيل هذا التطبيق.
