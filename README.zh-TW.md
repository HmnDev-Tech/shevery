# Shevery

[English](README.md) | [Русский](README.ru.md) | [Қазақша](README.kk.md) | [Qazaqşa (Latın)](README.kk-Latn.md) | [Português](README.pt.md) | [Español](README.es.md) | [العربية](README.ar.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md)

## 分支狀態

> [!IMPORTANT]
> **支援並存安裝：** 拜獨立的套件 ID（`com.hamondev.shevery`）與隔離的權限命名空間之賜，你**現在可以直接將 Shevery 與其他 Shizuku 實作並存安裝**，不需要先解除安裝它們。對於會明確檢查 `moe.shizuku.privileged.api` 的舊版應用程式，可以在移除原版 Shizuku 之後，選擇性啟用設定中的相容性橋接套件。
> 上游專案參考：<https://github.com/RikkaApps/Shizuku>
>

## 分支新增功能

- 以 Material 3 Expressive 元件、動效、開關與圓角圖示設計的 Jetpack Compose 管理介面。
- **裝置擁有者管理與整合**：完整的裝置擁有者管理（應用程式委派範圍、擁有權轉移、DPM 輔助 ADB），概念來自 Dhizuku 並與之相容。
- **生物辨識安全與保護**：可要求生物辨識或裝置 PIN／圖形／密碼驗證的主開關，支援可調整的逾時與逐項動作保護。
- 改良後基於 shell/adb 的「Comput」功能，支援 Gemini 解說、巨集與 Commandium AI 指令建立。
- 此分支採用目前 SDK／建置工具，進行 Android 16/17 目標版本適配。
- 用於安裝與管理 ZIP 模組的 ADB Modules 介面。
- 模組功能：`module.prop`、橫幅、啟用／停用開關、`action.sh`、受原則控制的 `service.sh`、本機 WebUI、刪除、路徑檢查、大小限制、輸出限制與最近執行記錄。
- 模組原則設定：安全模式、完整存取權限與背景動作控制。
- 支援直接安裝的模組目錄。

## 文件

- [Shevery Wiki](https://github.com/HmnDev-Tech/shevery/wiki)
- [Comput AI 代理指南](docs/comput-agent.md)
- [Device Owner 與 Profile Owner 指南](docs/device-owner.md)
- [隱蔽版本與相容性橋接套件 (Stubs)](docs/stealth-and-stubs.md)
- [無障礙管理、隱藏 USB 偵錯與網路防火牆](docs/lab-and-extras.md)
- [Tasker 與 MacroDroid 自動化指南](docs/tasker-plugin.md)
- [Intent 廣播動作參考](docs/intents.md)
- [ADB Modules 指南](docs/adb-modules-guide.md)
- [ADB Modules API 參考](docs/adb-modules-api.md)
- [ADB Modules 發佈指南](docs/github-catalog.md)
- [Shizuku Connectors API](docs/shizuku-connectors.md)
- [Android 17 相容性](docs/android-17-compatibility.md)
- [ErrorProtect 看門狗與啟動器復原](docs/errorprotect.md)

## 背景

開發需要 root 權限的應用程式時，最常見的方法是在 su shell 中執行一些指令。例如，某個應用程式會使用 `pm enable/disable` 指令啟用或停用元件。

這種方法有很大的缺點：

1. **極其緩慢**（需要建立多個行程）
2. 需要處理文字（**非常不可靠**）
3. 能力受限於可用指令
4. 即使 ADB 擁有足夠權限，應用程式仍需要 root 權限才能執行

Shizuku 採用了完全不同的方式。請參閱下方的詳細說明。

## 使用者指南與下載

<https://shizuku.rikka.app/>

> [!CAUTION]
> **官方免費應用程式聲明**
>
> Shevery 是一款**完全免費的應用程式 (App)**。請勿在任何第三方網路硬碟或付費檔案管理器中購買或付費下載本應用程式，切勿上當受騙！

## 截圖

<details>
  <summary>📸 點擊開啟截圖集</summary>
  <br/>
  <table>
    <tr>
      <td align="center"><img src="screenshots/main.png" width="300" /><br/><b>主畫面</b></td>
      <td align="center"><img src="screenshots/comput.png" width="300" /><br/><b>Comput 主控台</b></td>
    </tr>
    <tr>
      <td align="center"><img src="screenshots/modules.png" width="300" /><br/><b>ADB Modules</b></td>
      <td align="center"><img src="screenshots/settings.png" width="300" /><br/><b>設定</b></td>
    </tr>
  </table>
</details>

## Shevery 如何運作？

首先，我們需要了解應用程式如何使用系統 API。例如，如果應用程式需要取得已安裝的應用程式，我們都知道應該使用 `PackageManager#getInstalledPackages()`。這實際上是應用程式行程與系統伺服器行程之間的行程間通訊（IPC）過程，只是 Android 框架替我們完成了內部工作。

Android 使用 `binder` 執行這種 IPC。`Binder` 允許服務端得知用戶端的 uid 與 pid，讓系統伺服器能檢查應用程式是否有權執行相應操作。

通常，如果應用程式可以使用某個「管理器」（例如 `PackageManager`），系統伺服器行程中就應該有一個對應的「服務」（例如 `PackageManagerService`）。我們可以簡單理解為：如果應用程式持有該「服務」的 `binder`，就能與「服務」通訊。應用程式行程啟動時會收到各項系統服務的 binder。

Shizuku 會引導使用者先透過 root 或 ADB 執行一個行程，也就是 Shizuku 服務端。應用程式啟動時，Shizuku 服務端的 `binder` 也會被傳送給應用程式。

Shevery 提供最重要的功能類似於充當中間人：接收應用程式的請求，將請求傳送給系統伺服器，再把結果回傳。詳情可參閱 `rikka.shizuku.server.ShizukuService` 類別中的 `transactRemote` 方法，以及 `moe.shizuku.api.ShizukuBinderWrapper` 類別。

這樣我們就達成了以更高權限使用系統 API 的目標。對應用程式而言，這與直接使用系統 API 幾乎完全相同。

## 開發者指南

### API 與範例

https://github.com/RikkaApps/Shizuku-API

### 從 v11 之前的版本遷移

> 當然，現有應用程式仍然可以正常運作。

https://github.com/RikkaApps/Shizuku-API#migration-guide-for-existing-applications-use-shizuku-pre-v11

### 注意事項

1. ADB 權限有限

   ADB 的權限有限，並且會因系統版本而異。你可以在[這裡](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/Shell/AndroidManifest.xml)查看授予 ADB 的權限。

   呼叫 API 之前，可以使用 `ShizukuService#getUid` 檢查 Shizuku 是否以 ADB 使用者身分執行，或使用 `ShizukuService#checkPermission` 檢查服務端是否擁有足夠的權限。

2. Android 9 開始存在隱藏 API 限制

   從 Android 9 開始，一般應用程式對隱藏 API 的使用受到限制。請使用其他方法（例如 <https://github.com/LSPosed/AndroidHiddenApiBypass>）。

3. Android 8.0 與 ADB

   目前，Shizuku 服務將 `IActivityManager#registerProcessObserver` 與 `IActivityManager#registerUidObserver`（26+）結合使用來取得應用程式行程，以確保應用程式啟動時可以傳送該行程。然而在 API 26 上，ADB 無權使用 `registerUidObserver`。因此，如果需要在可能並非由 Activity 啟動的行程中使用 Shizuku，建議透過啟動一個透明 Activity 來觸發 binder 傳送。

4. 直接使用 `transactRemote` 時需要注意

   * 不同 Android 版本中的 API 可能有所不同，請務必仔細檢查。另外，`android.app.IActivityManager` 在 API 26 及以上版本採用 aidl 形式，而 `android.app.IActivityManager$Stub` 僅存在於 API 26。

   * `SystemServiceHelper.getTransactionCode` 可能無法取得正確的交易代碼。例如在 API 25 上並不存在 `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages`，而是存在 `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages_47`（這種情況已經處理，但不排除還存在其他情況）。使用 `ShizukuBinderWrapper` 方法時不會遇到這個問題。

## 開發 Shizuku 本身

### 建置

- 使用 `git clone --recurse-submodules` 複製
- 執行 Gradle 任務 `:manager:assembleDebug` 或 `:manager:assembleRelease`

`:manager:assembleDebug` 任務會產生可偵錯的服務端。你可以將偵錯器附加到 `shizuku_server` 以偵錯服務端。請注意，在 Android Studio 中應勾選「Run/Debug configurations」-「Always install with package manager」，以便服務端使用最新程式碼。

## 授權條款

本專案的所有程式碼檔案均採用 Apache 2.0 授權。

特別說明 Apache 2.0 第 6 節：

* **禁止**使用 `manager/src/main/res/mipmap*/ic_launcher*.png` 圖像檔案，除非用於展示 Shizuku 本身。

* **禁止**將 `Shevery` 用作應用程式名稱、將 `moe.shizuku.privileged.api` 或 `com.hamondev.shevery` 用作應用程式 ID，或宣告 `moe.shizuku.manager.permission.*` 權限。

## 致謝

* [Nightzuku](https://github.com/kerneldroid/Nightzuku) - 提供了部分應用程式介面、模組目錄系統與 Android 17 支援。
* [Shizuku](https://github.com/rikkaapps/Shizuku) - 提供了 Shizuku API 與主要原始碼。

### 不建議在第三方檔案管理器與網站上安裝 Shevery，最好到 GitHub 下載。
