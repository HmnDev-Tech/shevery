# Intents

Reference of the public (and internal) intents declared by Shevery Manager. Package name: `com.hamondev.shevery`.

Wiki mirror: https://github.com/HmnDev-Tech/shevery/wiki/Intents

## Server control (internal)

Receiver: `moe.shizuku.manager.receiver.SheveryControlReceiver` (`exported=false`).

| Action | Effect |
|---|---|
| `moe.shizuku.manager.action.START_SERVER` | Clears the user-stop request; if last launch mode was ADB, enqueues `AdbStartWorker` and refreshes the notification; calls `WatchdogManager.attemptRestart()` |
| `moe.shizuku.manager.action.STOP_SERVER` | `goAsync()` + `WatchdogManager.stopServerAndWait(userInitiated=true)`, then refreshes the notification |

Because the receiver is **not exported**, only Shevery itself (notification buttons, `AdbStartWorker`, and the bundled [Tasker Plugin](tasker-plugin.md)) can send these. Third-party apps: use the Tasker plugin instead of crafting these broadcasts.

```kotlin
// inside Shevery (same UID) — explicit + package-scoped
val intent = Intent("moe.shizuku.manager.action.START_SERVER").apply {
    setPackage("com.hamondev.shevery")
    component = ComponentName(
        "com.hamondev.shevery",
        "moe.shizuku.manager.receiver.SheveryControlReceiver"
    )
}
context.sendBroadcast(intent)
```

> `adb shell am broadcast` with these internal actions will **not** work from the shell because `SheveryControlReceiver` is not exported. For shell/ADB or third-party automation, use the public broadcast actions below.

## Public automation control (Tasker, MacroDroid, ADB)

Receiver: `com.hamondev.shevery.tasker.PluginReceiver` (`exported=true`).

| Action | Effect |
|---|---|
| `com.hamondev.shevery.action.START_SERVER` | Starts Shevery server (clears stop flag, wakes watchdog) |
| `com.hamondev.shevery.action.STOP_SERVER` | Gracefully stops the server |
| `com.hamondev.shevery.action.RESTART_SERVER` | Restarts server with graceful binder death monitor |
| `com.hamondev.shevery.action.TOGGLE_SERVER` | Toggles server (stops if alive, starts if stopped) |

Example shell usage:
```bash
adb shell am broadcast -a com.hamondev.shevery.action.START_SERVER -p com.hamondev.shevery -e auth <token>
```

> **Requirement**: Third-party automation broadcasts and the Tasker plugin require **Shevery Connectors** to be enabled in **Settings → Automation** (`shizuku_connector_enabled`).
> 
> **Authentication Token**: Direct broadcasts require the secret auth token (`-e auth <token>`) configured in Shevery (accessible via **Settings → Automation** or the Home screen **View intents** sheet). If Connectors are disabled or the token is missing/invalid, broadcasts are rejected with `RESULT_CANCELED` and Shevery posts a high-importance alert notification that deep-links to **Settings → Automation**.

Detailed guide: [Tasker & MacroDroid Automation Guide](tasker-plugin.md).

## App update status (internal)

- Action: `moe.shizuku.manager.action.APP_UPDATE_INSTALL_STATUS`
- Sent by `PackageInstaller` to `AppUpdateInstaller` (`setPackage(context.packageName)`), consumed with an explicit `IntentFilter`. Not for external use.

## Public API for third-party apps

### Request permission (runtime dialog)

Launches `RequestPermissionActivity` (`exported=true`, category `android.intent.category.DEFAULT`):

- `com.hamondev.shevery.intent.action.REQUEST_PERMISSION`
- `moe.shizuku.privileged.api.intent.action.REQUEST_PERMISSION` (legacy alias)

Always add category `android.intent.category.DEFAULT`. This is the `ShizukuService#requestPermission()` flow — user taps Allow/Deny, decision is stored as `FLAG_ALLOWED` / `FLAG_DENIED`. Note: The permission `android.permission.INTERACT_ACROSS_USERS_FULL` is required by the internal binder transport provider `ShizukuManagerProvider`, not by this dialog activity.

### Legacy authorization

- `com.hamondev.shevery.intent.action.REQUEST_AUTHORIZATION`
- `moe.shizuku.privileged.api.intent.action.REQUEST_AUTHORIZATION`

→ `LegacyIsNotSupportedActivity` (guarded by `moe.shizuku.manager.permission.API`). Old Shizuku API clients only.

### Binder request (shell / legacy)

- `rikka.shizuku.intent.action.REQUEST_BINDER` → `ShellRequestHandlerActivity` and `ShizukuReceiver` (`exported=true`, `directBootAware=true`)
- Aliases also handled by `ShizukuReceiver`: `com.hamondev.shevery.intent.action.REQUEST_BINDER`, `moe.shizuku.privileged.api.intent.action.REQUEST_BINDER`

Used by the `rish` / shell path (`ShellBinderRequestHandler`, `ShizukuReceiverStarter`). See also [shizuku-connectors.md](shizuku-connectors.md).

### Binder delivery

- `moe.shizuku.api.action.BINDER_RECEIVED` (broadcast by `ShizukuProvider`, extra `moe.shizuku.privileged.api.intent.extra.BINDER`) — how API clients receive the binder. Native clients request `com.hamondev.shevery.permission.PRIVILEGED_API` (declared by Shevery, see [shevery-api](../shevery-api/README.md)); legacy Shizuku clients request `moe.shizuku.manager.permission.API_V23`, which is declared only by the Shizuku stub APK (`moe.shizuku.privileged.api`).

### Accessibility manager shortcut

- `com.hamondev.shevery.action.ACCESSIBILITY_MANAGER` (+ `DEFAULT` category) → `AccessibilityManagerActivity` (`exported=true`). Lists system accessibility services, enable/disable/pin via `WRITE_SECURE_SETTINGS`.

### External module ZIP intake

Activity: `moe.shizuku.manager.module.InstallZipModuleActivity` (`exported=true`).

Supports opening or sharing module ZIP files directly from file managers and web browsers into Shevery.

| Action | MIME Types | Data Type |
|---|---|---|
| `android.intent.action.VIEW` | `application/zip`, `application/x-zip-compressed` | Content URI (`Intent.getData()`) |
| `android.intent.action.SEND` | `application/zip`, `application/x-zip-compressed` | Stream URI (`Intent.EXTRA_STREAM`) |

The incoming stream is safely cached in `cacheDir/modules_zip/` before handing off the local file path to `HomeActivity` via `EXTRA_ZIP_PATH` to prevent content-provider permission revocation during asynchronous installation.

### Module WebUI (Activity)

Activity: `moe.shizuku.manager.module.ModuleWebViewActivity` (`exported=true`).

Allows opening an installed module's WebUI directly from shell commands (ADB, `rish`, Termux), automation apps (Tasker, MacroDroid), or home screen shortcuts.

> [!NOTE]
> `android.intent.category.BROWSABLE` is intentionally omitted to prevent untrusted web pages and browser iframes from invoking privileged WebUIs (`ModuleJsBridge`).
> If `APP_OPEN` protection is enabled in Security Settings, external launches are gated by biometric / device credential authentication before the WebUI is loaded.

| Action | Extras / Data | Description |
|---|---|---|
| `android.intent.action.VIEW` | URI `shevery://module/<moduleId>` or `shevery://<moduleId>` | Opens WebUI for `<moduleId>` via deep link |
| `com.hamondev.shevery.action.OPEN_WEBUI` / `moe.shizuku.manager.action.OPEN_WEBUI` | Extra `--es module_id <moduleId>` (or `--es id <moduleId>`) | Opens WebUI for `<moduleId>` via custom action |

Example shell usage:
```bash
# via deep-link URI (works in ADB, rish, Termux)
adb shell am start -a android.intent.action.VIEW -d "shevery://module/<moduleId>"

# via custom action (with --es module_id)
adb shell am start -a com.hamondev.shevery.action.OPEN_WEBUI -p com.hamondev.shevery --es module_id <moduleId>

# via explicit component
adb shell am start -n com.hamondev.shevery/moe.shizuku.manager.module.ModuleWebViewActivity --es module_id <moduleId>
```

## Quick examples

```sh
# open the accessibility manager
adb shell am start -a com.hamondev.shevery.action.ACCESSIBILITY_MANAGER

# legacy permission request (reproduces the API dialog path)
adb shell am start -a com.hamondev.shevery.intent.action.REQUEST_PERMISSION

# open module webui via deep link
adb shell am start -a android.intent.action.VIEW -d "shevery://module/<moduleId>"
```

Source of truth: `manager/src/main/AndroidManifest.xml`, `manager/.../receiver/SheveryControlReceiver.kt`, `tasker/.../PluginContract.kt`.
