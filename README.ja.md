# Shevery

[English](README.md) | [Русский](README.ru.md) | [Қазақша](README.kk.md) | [Qazaqşa (Latın)](README.kk-Latn.md) | [Português](README.pt.md) | [Español](README.es.md) | [العربية](README.ar.md) | [简体中文](README.zh-CN.md) | [日本語](README.ja.md)

## フォークの状況

> [!IMPORTANT]
> **移行作業が必要です：** アプリ識別子が moe.shizuku.privileged.api から **com.hamondev.shevery** に変更されたため、Shevery をインストールする前に、端末にある旧バージョンの公式 Shizuku Manager アプリを**必ずアンインストールしてください**。アンインストールしない場合、両者が競合します。
上流プロジェクト：<https://github.com/RikkaApps/Shizuku>
>

## フォークで追加された機能

- Material 3 Expressive のコンポーネント、モーション、スイッチ、角を丸めたアイコンデザインを採用した Jetpack Compose 製マネージャー UI。
- Gemini による説明、マクロ、Commandium AI によるコマンド作成に対応した、shell/adb ベースの “Comput” 機能の改善。
- このフォークでは、現行の SDK とビルドツールを用いて Android 16/17 対応を進めています。
- ZIP モジュールをインストール・管理するための ADB Modules 画面。
- モジュール機能：`module.prop`、バナー、有効化/無効化スイッチ、`action.sh`、ポリシーで制御される `service.sh`、ローカル WebUI、削除、パス検査、サイズ制限、出力制限、直近の実行ログ。
- モジュールのポリシー設定：セーフモード、フルアクセス、バックグラウンド操作の制御。
- 直接インストールに対応したモジュールカタログ。

## ドキュメント


- [ADB Modules ガイド](docs/adb-modules-guide.md)
- [ADB Modules API リファレンス](docs/adb-modules-api.md)
- [Shizuku Connectors API](docs/shizuku-connectors.md)
- [Android 17 の互換性](docs/android-17-compatibility.md)
- [ADB Modules 公開ガイド](docs/github-catalog.md)

## 背景

root 権限を必要とするアプリを開発する場合、最も一般的な方法は su shell でコマンドを実行することです。たとえば、`pm enable/disable` コマンドを使ってコンポーネントを有効化または無効化するアプリがあります。

この方法には非常に大きな欠点があります。

1. **極めて遅い**（複数のプロセスを作成するため）
2. テキストを処理する必要がある（**非常に信頼性が低い**）
3. 利用可能なコマンドによって機能が制限される
4. ADB に十分な権限があっても、アプリの実行には root 権限が必要になる

Shizuku はまったく異なる方法を採用しています。詳しくは以下の説明を参照してください。

## ユーザーガイドとダウンロード

<https://shizuku.rikka.app/>

## スクリーンショット

<details>
  <summary>📸 クリックしてスクリーンショットギャラリーを開く</summary>
  <br/>
  <table>
    <tr>
      <td align="center"><img src="screenshots/main.png" width="300" /><br/><b>メイン画面</b></td>
      <td align="center"><img src="screenshots/comput.png" width="300" /><br/><b>Comput コンソール</b></td>
    </tr>
    <tr>
      <td align="center"><img src="screenshots/modules.png" width="300" /><br/><b>ADB Modules</b></td>
      <td align="center"><img src="screenshots/settings.png" width="300" /><br/><b>設定</b></td>
    </tr>
  </table>
</details>

## Shevery の仕組み

まず、アプリがシステム API をどのように利用するかを説明します。たとえば、アプリがインストール済みのアプリを取得したい場合、`PackageManager#getInstalledPackages()` を使用すべきことはよく知られています。これは実際には、アプリプロセスとシステムサーバープロセス間のプロセス間通信（IPC）であり、Android フレームワークが内部処理を代行しています。

Android はこの種の IPC に `binder` を使用します。`Binder` により、サーバー側はクライアント側の uid と pid を把握できるため、システムサーバーはそのアプリに操作を実行する権限があるか確認できます。

通常、アプリが使用する「マネージャー」（例：`PackageManager`）がある場合、システムサーバープロセスには対応する「サービス」（例：`PackageManagerService`）が存在します。アプリがその「サービス」の `binder` を保持していれば、「サービス」と通信できると簡単に考えることができます。アプリプロセスは起動時にシステムサービスの binder を受け取ります。

Shizuku は、ユーザーに root または ADB を使って Shizuku サーバーというプロセスを最初に実行するよう案内します。アプリの起動時には、Shizuku サーバーへの `binder` もアプリに送信されます。

Shevery が提供する最も重要な機能は、仲介役のように動作することです。アプリからリクエストを受け取り、それをシステムサーバーに送り、結果を返します。詳しくは、`rikka.shizuku.server.ShizukuService` クラスの `transactRemote` メソッドと、`moe.shizuku.api.ShizukuBinderWrapper` クラスを参照してください。

これにより、より高い権限でシステム API を使用するという目的を達成できます。アプリから見れば、システム API を直接使用する場合とほぼ同じです。

## 開発者ガイド

### API とサンプル

https://github.com/RikkaApps/Shizuku-API

### v11 より前のバージョンからの移行

> もちろん、既存のアプリケーションは引き続き動作します。

https://github.com/RikkaApps/Shizuku-API#migration-guide-for-existing-applications-use-shizuku-pre-v11

### 注意事項

1. ADB の権限には制限がある

   ADB の権限には制限があり、システムのバージョンによって異なります。ADB に付与される権限は[こちら](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/Shell/AndroidManifest.xml)で確認できます。

   API を呼び出す前に、`ShizukuService#getUid` を使って Shizuku が ADB ユーザーとして動作しているか確認したり、`ShizukuService#checkPermission` を使ってサーバーに十分な権限があるか確認したりできます。

2. Android 9 以降の隠し API 制限

   Android 9 以降、通常のアプリによる隠し API の使用は制限されています。別の方法（<https://github.com/LSPosed/AndroidHiddenApiBypass> など）を使用してください。

3. Android 8.0 と ADB

   現在、Shizuku サービスは `IActivityManager#registerProcessObserver` と `IActivityManager#registerUidObserver`（26 以降）を組み合わせてアプリプロセスを取得し、アプリの起動時にプロセスが確実に送信されるようにしています。ただし API 26 では、ADB に `registerUidObserver` を使用する権限がありません。そのため、Activity から起動されない可能性のあるプロセスで Shizuku を使用する場合は、透明な Activity を起動して binder の送信を発生させることを推奨します。

4. `transactRemote` を直接使用する場合の注意

   * API は Android のバージョンによって異なる可能性があるため、必ず注意深く確認してください。また、`android.app.IActivityManager` は API 26 以降では aidl 形式であり、`android.app.IActivityManager$Stub` は API 26 にのみ存在します。

   * `SystemServiceHelper.getTransactionCode` では正しいトランザクションコードを取得できない場合があります。たとえば、API 25 には `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages` が存在せず、代わりに `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages_47` が存在します（この状況には対処済みですが、ほかの状況が存在する可能性は否定できません）。`ShizukuBinderWrapper` を使用する方法では、この問題は発生しません。

## Shizuku 自体の開発

### ビルド

- `git clone --recurse-submodules` でクローンする
- Gradle タスク `:manager:assembleDebug` または `:manager:assembleRelease` を実行する

`:manager:assembleDebug` タスクはデバッグ可能なサーバーを生成します。`shizuku_server` にデバッガーを接続して、サーバーをデバッグできます。Android Studio では「Run/Debug configurations」-「Always install with package manager」にチェックを入れ、サーバーが最新のコードを使用するようにしてください。

## ライセンス

このプロジェクトのすべてのコードファイルは Apache 2.0 の下でライセンスされています。

Apache 2.0 の第 6 条に基づく具体的な制限：

* Shizuku 自体を表示する場合を除き、`manager/src/main/res/mipmap*/ic_launcher*.png` 画像ファイルの使用は**禁止**されています。

* アプリ名に `Shevery` を使用すること、アプリケーション ID に `moe.shizuku.privileged.api` または `com.hamondev.shevery` を使用すること、および `moe.shizuku.manager.permission.*` 権限を宣言することは**禁止**されています。

## クレジット

* [Nightzuku](https://github.com/kerneldroid/Nightzuku) - アプリ UI、モジュールカタログシステム、Android 17 対応の一部を提供しています。
* [Shizuku](https://github.com/rikkaapps/Shizuku) - Shizuku API と主要なソースコードを提供しています。
