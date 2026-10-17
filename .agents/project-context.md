---
context_version: 1.0.0
synced_commit: "a4c51e3427181395914fa6443cfa13e00fc92ee0"
synced_at: "2026-10-17T11:52:17Z"
---

# Shevery Project Context

## 1. Quick Specs & Project Cockpit

| Property | Details / Specification |
| :--- | :--- |
| **Identifier** | Package ID: `com.hamondev.shevery` | Namespace: `moe.shizuku.manager` |
| **Runtime / Platform** | Kotlin 2.4.20-Beta2, Java 21, C++17 (CMake 3.22.1, NDK 29.0.14206865, libc++) |
| **SDK Targets** | `compileSdk = 37`, `targetSdk = 37`, `minSdk = 24`, `buildToolsVersion = "37.0.0"` |
| **Version Status** | Version Name: `14.3.0` | Version Code: `43` | Stub Version: `2000000000` |
| **Core Frameworks** | Jetpack Compose (BOM 2026.06.01, M3 Expressive `1.5.0-alpha25`), Coroutines `1.11.0`, WorkManager `2.10.1` |
| **Key Dependencies** | `libsu:core:6.0.0`, `Dhizuku-API:2.5.4`, `hiddenapibypass:6.1`, `bcpkix-jdk18on:1.85`, `okhttp:4.12.0`, `coil-compose:2.7.0`, `security-crypto:1.1.0-beta01`, `biometric:1.2.0-alpha05` |
| **Security & Auth** | `moe.shizuku.manager.permission.API_V23`, `INTERACT_ACROSS_USERS_FULL`, BiometricPrompt / Strong Credential, TokenStore (AES-GCM) |
| **Primary Commands** | Build: `fast-gradlew :manager:assembleRelease` | CI/CD: Automated on push to GitHub | Test: `fast-gradlew :manager:testDebugUnitTest` | Logs: `pidcat moe.shizuku.manager` |

---

## 2. Component Ownership & Architecture Map

### Elevated Server & Starter Runtime (`:server`, `:starter`, `:shell`, `:manager` JNI)
* `server/src/main/java/rikka/shizuku/server/ShizukuService.java` → `ShizukuService`, `IShizukuService.Stub`; Central elevated daemon process (UID 0/2000) hosting RPC Binder transactions, permission dispatch, and multi-user client management.
* `server/src/main/java/rikka/shizuku/server/ShizukuUserServiceManager.java` → `ShizukuUserServiceManager`; Subclass managing client-spawned privileged background service processes.
* `server/src/main/java/rikka/shizuku/server/ShizukuClientManager.java` → `ShizukuClientManager`; Tracks active client app binder connections and lifecycle death recipients.
* `server/src/main/java/rikka/shizuku/server/ShizukuConfigManager.java` → `ShizukuConfigManager`; Manages file persistence of authorized client packages on elevated daemon.
* `server/src/main/java/rikka/shizuku/server/ShizukuConfig.java` → `ShizukuConfig`; Data model for server configuration, authorized packages, and permission flags.
* `server/src/main/java/rikka/shizuku/server/ServerConstants.java` → `ServerConstants`; Daemon build, version, and process name constants for elevated execution.
* `server/src/main/java/rikka/shizuku/server/ApkChangedObservers.kt` → `ApkChangedObservers`; Monitors package additions, updates, and removals to invalidate cached server tokens.
* `server/src/main/java/rikka/shizuku/server/BinderSender.kt` → `BinderSender`; Dispatches server IBinder transactions to registered client listeners.
* `server/src/main/java/rikka/shizuku/server/api/IContentProviderUtils.java` → `IContentProviderUtils`; Reflection helpers invoking hidden ActivityManager ContentProvider APIs.
* `server/src/main/java/rikka/shizuku/server/ktx/Handler.kt` → `Handler.postDelayed()`; Extension utilities for coroutine and main-thread handler execution.
* `server/src/main/java/rikka/shizuku/server/util/Android17Compat.kt` → `Android17Compat`; Compatibility layer resolving package installation and hidden system server IPC calls on Android 17 (API 37).
* `starter/src/main/java/moe/shizuku/starter/ServiceStarter.java` → `ServiceStarter`; Elevated Java launcher bootstrapping ShizukuService within `app_process`.
* `starter/src/main/java/moe/shizuku/starter/util/IContentProviderCompat.java` → `IContentProviderCompat`; Reflection wrapper resolving ContentProviders across diverse Android versions.
* `shell/src/main/java/rikka/shizuku/shell/ShizukuShellLoader.java` → `ShizukuShellLoader`; Classloader bootstrapping elevated shell runtime environment.
* `manager/src/main/jni/starter.cpp` → `starter_main()`; Native executable bootstrapping `app_process`, establishing supervisor auto-respawn, SIGHUP/SIGPIPE immunity, and server classpath handoff.
* `manager/src/main/jni/selinux.cpp` → `se::setcon()`; Native SELinux domain switching utilities for elevated execution context.
* `manager/src/main/jni/cgroup.cpp` → `cgroup::freeze()`; Native cgroup v1/v2 process freeze and unfreeze controller.
* `manager/src/main/jni/misc.cpp` → `misc::trim()`; Native string and file descriptor manipulation utilities.
* `manager/src/main/jni/android.cpp` → `android::syscall()`; JNI bindings for platform reflection and direct system calls.
* `manager/src/main/jni/helper.cpp` → `setSELinuxContext()`; JNI helper registering native methods and SELinux context handlers.
* `manager/src/main/jni/adb_pairing.cpp` → `adb_pairing()`; Native SPAKE2 dynamic TLS pairing protocol crypto implementation.

### NightDog Heartbeat & Watchdog Subsystem (`:nightdog`, `:manager`)
* `nightdog/src/main/java/rikka/shizuku/nightdog/NightDog.kt` → `NightDog`; In-server background watchdog monitoring system service health (`package`, `activity`, `user`, `appops`), detecting hangs, and executing automatic rebinds.
* `manager/src/main/java/moe/shizuku/manager/nightdog/NightDogClient.kt` → `NightDogClient`; Synchronizes NightDog watchdog state with elevated shizuku_server over Binder IPC.
* `manager/src/main/java/moe/shizuku/manager/service/WatchdogManager.kt` → `WatchdogManager`; Coordinates client-side watchdog scheduling, auto-recovery grace intervals, and starter race suppression (`isStarterActive`, `expectingDeath`).

### Native Interactive Shell CLI (`:rish`, `api/rish/src/main/cpp`)
* `api/rish/src/main/cpp/main.cpp` → `main()`; Native command-line client entry point bridging interactive Termux / POSIX shells to the active server binder.
* `api/rish/src/main/cpp/pts.cpp` → `pts_open()`; Pseudoterminal (PTY) allocation and terminal window size setup for interactive shell sessions.
* `api/rish/src/main/cpp/rikka_rish_RishHost.cpp` → `RishHost_native`; JNI implementation bridging RishHost binder calls to native POSIX streams.
* `api/rish/src/main/cpp/rikka_rish_RishTerminal.cpp` → `RishTerminal_native`; JNI implementation handling terminal window resize signals (SIGWINCH) and PTY stream forwarding.
* `api/rish/src/main/java/rikka/rish/Rish.java` → `Rish`; Java entry point establishing binder link for command-line rish executions.
* `api/rish/src/main/java/rikka/rish/RishHost.java` → `RishHost`; Host service bridging local shell sessions to the elevated Shizuku binder.
* `api/rish/src/main/java/rikka/rish/RishService.java` → `RishService`; Elevated background service executing shell commands on behalf of rish clients.
* `api/rish/src/main/java/rikka/rish/RishTerminal.java` → `RishTerminal`; Coordinates terminal window size changes (SIGWINCH) and raw stream piping.
* `api/rish/src/main/java/rikka/rish/RishConfig.java` → `RishConfig`; Configuration store managing rish execution parameters and token caching.
* `api/rish/src/main/java/rikka/rish/RishConstants.java` → `RishConstants`; Environment variable keys and IPC transaction codes for rish integration.
* `api/rish/src/main/java/rikka/rish/FileDescriptors.java` → `FileDescriptors`; Native file descriptor transfer helpers for interactive shell sessions.

### Public Client API, AIDL & ContentProvider Transport (`:api`, `:provider`, `:aidl`, `:shared`, `:server-shared`)
* `api/aidl/src/main/aidl/moe/shizuku/server/IShizukuService.aidl` → `IShizukuService`; AIDL IPC contract defining remote Binder transactions and permission verification.
* `api/aidl/src/main/aidl/moe/shizuku/server/IShizukuApplication.aidl` → `IShizukuApplication`; AIDL callback contract delivering server binder handles to client applications.
* `api/aidl/src/main/aidl/moe/shizuku/server/IShizukuServiceConnection.aidl` → `IShizukuServiceConnection`; AIDL callback interface for binding standalone privileged user services.
* `api/aidl/src/main/aidl/moe/shizuku/server/IRemoteProcess.aidl` → `IRemoteProcess`; AIDL interface controlling remote child processes spawned by server daemon.
* `api/api/src/main/java/rikka/shizuku/Shizuku.java` → `Shizuku`; Primary public entry point for third-party client apps to bind, request permissions, and transact with daemon.
* `api/api/src/main/java/rikka/shizuku/ShizukuBinderWrapper.java` → `ShizukuBinderWrapper`; IBinder proxy delegating transaction calls through elevated daemon to system server.
* `api/api/src/main/java/rikka/shizuku/ShizukuRemoteProcess.java` → `ShizukuRemoteProcess`; Remote java.lang.Process implementation wrapping daemon-spawned processes.
* `api/api/src/main/java/rikka/shizuku/ShizukuServiceConnection.java` → `ShizukuServiceConnection`; ServiceConnection callback interface for binding user services.
* `api/api/src/main/java/rikka/shizuku/ShizukuServiceConnections.java` → `ShizukuServiceConnections`; Registry caching and dispatching active user service connection callbacks.
* `api/api/src/main/java/rikka/shizuku/ShizukuSystemProperties.java` → `ShizukuSystemProperties`; Client-side helper querying system properties via Shizuku binder.
* `api/api/src/main/java/rikka/shizuku/SystemServiceHelper.java` → `SystemServiceHelper`; Resolves hidden Android system service binders and transaction codes.
* `api/api/src/main/java/rikka/sui/Sui.java` → `Sui`; Compatibility layer providing Magisk / Sui root bridge integration.
* `api/provider/src/main/java/moe/shizuku/api/BinderContainer.java` → `BinderContainer`; Parcelable container transferring IBinder instances across ContentProviders.
* `api/provider/src/main/java/rikka/shizuku/ShizukuProvider.java` → `ShizukuProvider`; Public client ContentProvider dispatching binder tokens to requesting client apps.
* `api/shared/src/main/java/rikka/shizuku/ShizukuApiConstants.java` → `ShizukuApiConstants`; Version codes, action names, and IPC method constants shared across API modules.
* `api/server-shared/src/main/java/rikka/shizuku/server/ClientManager.java` → `ClientManager`; Abstract coordinator tracking registered client application process records.
* `api/server-shared/src/main/java/rikka/shizuku/server/ClientRecord.java` → `ClientRecord`; Encapsulates client UID, PID, package name, and binder death recipient.
* `api/server-shared/src/main/java/rikka/shizuku/server/ConfigManager.java` → `ConfigManager`; Abstract base managing package authorization configuration persistence.
* `api/server-shared/src/main/java/rikka/shizuku/server/ConfigPackageEntry.java` → `ConfigPackageEntry`; Serialized entity storing permission flags per authorized package.
* `api/server-shared/src/main/java/rikka/shizuku/server/Service.java` → `Service`; Base lifecycle contract for standalone elevated Shizuku services.
* `api/server-shared/src/main/java/rikka/shizuku/server/UserService.java` → `UserService`; Base implementation for client-spawned privileged background service processes.
* `api/server-shared/src/main/java/rikka/shizuku/server/UserServiceManager.java` → `UserServiceManager`; Coordinates standalone app-provided privileged background user services (`UserServiceRecord`).
* `api/server-shared/src/main/java/rikka/shizuku/server/UserServiceRecord.java` → `UserServiceRecord`; Tracks token, component, and binder link for active user services.
* `api/server-shared/src/main/java/rikka/shizuku/server/api/RemoteProcessHolder.java` → `RemoteProcessHolder`; Manages IPC handles to child processes spawned by server.
* `api/server-shared/src/main/java/rikka/shizuku/server/util/AbiUtil.java` → `AbiUtil`; Inspects system architecture, 32/64-bit ABI support, and library paths.
* `api/server-shared/src/main/java/rikka/shizuku/server/util/HandlerUtil.java` → `HandlerUtil`; Threading helper dispatching synchronous tasks to background Handlers.
* `api/server-shared/src/main/java/rikka/shizuku/server/util/Logger.java` → `Logger`; Server-side logger outputting diagnostic traces to elevated logcat / file.
* `api/server-shared/src/main/java/rikka/shizuku/server/util/OsUtils.java` → `OsUtils`; Native OS utilities querying process UIDs, GIDs, and process names.
* `api/server-shared/src/main/java/rikka/shizuku/server/util/ParcelFileDescriptorUtil.java` → `ParcelFileDescriptorUtil`; Pipes and transfers file descriptors across binder boundaries.
* `api/server-shared/src/main/java/rikka/shizuku/server/util/UserHandleCompat.java` → `UserHandleCompat`; Multi-user utility querying Android user IDs from process UIDs.

### ADB Engine, Wireless Pairing & WorkManager Workers (`:manager`)
* `manager/src/main/java/moe/shizuku/manager/adb/AdbArm.kt` → `AdbArm`; Arms and disarms adbd and wireless debugging flags via WRITE_SECURE_SETTINGS or Device Owner.
* `manager/src/main/java/moe/shizuku/manager/adb/AdbClient.kt` → `AdbClient`; Pure-Kotlin TLS and plain socket ADB client with bounded 5s connect / 15s read timeouts.
* `manager/src/main/java/moe/shizuku/manager/adb/AdbStarter.kt` → `AdbStarter.start()`; Dispatches ADB shell invocation over localhost/TCP or dynamic TLS to launch server daemon with idempotency checks.
* `manager/src/main/java/moe/shizuku/manager/adb/AdbPairingClient.kt` → `AdbPairingClient`; Performs Android 11+ dynamic TLS pairing using SPAKE2 encryption and 6-digit codes.
* `manager/src/main/java/moe/shizuku/manager/adb/AdbPairingService.kt` → `AdbPairingService`; Foreground service hosting background TLS pairing notifications and progress callbacks.
* `manager/src/main/java/moe/shizuku/manager/adb/AdbPairingTutorialActivity.kt` → `AdbPairingTutorialActivity`; Step-by-step visual tutorial and split-screen guide for pairing Wireless Debugging.
* `manager/src/main/java/moe/shizuku/manager/adb/AdbKey.kt` → `AdbKey`; Generates, loads, and manages RSA/TLS keypairs and self-signed X.509 certificates.
* `manager/src/main/java/moe/shizuku/manager/adb/AdbMdns.kt` → `AdbMdns`; NsdManager wrapper discovering dynamic `_adb-tls-connect._tcp` and `_adb-tls-pairing._tcp` ports.
* `manager/src/main/java/moe/shizuku/manager/adb/AdbMessage.kt` → `AdbMessage`; Binary protocol packet framing and parser for ADB commands (CNXN, OPEN, OKAY, CLSE, WRTE).
* `manager/src/main/java/moe/shizuku/manager/adb/AdbProtocol.kt` → `AdbProtocol`; Protocol constants, authentication tokens, and stream headers for ADB protocol framing.
* `manager/src/main/java/moe/shizuku/manager/adb/AdbException.kt` → `AdbException`, `AdbKeyException`; Domain exceptions for ADB TLS handshakes, socket errors, and pairing failures.
* `manager/src/main/java/moe/shizuku/manager/worker/AdbStartWorker.kt` → `AdbStartWorker`; WorkManager worker managing automated Wi-Fi reconnect with idempotency and alive binder pre-checks.
* `manager/src/main/java/moe/shizuku/manager/worker/AdbNetworkObserver.kt` → `AdbNetworkObserver`; ConnectivityManager network callback monitoring active Wi-Fi changes for ADB auto-reconnect.
* `manager/src/main/java/moe/shizuku/manager/worker/WifiDebugReassert.kt` → `WifiDebugReassert`; Periodically validates and re-enables wireless debugging settings after network transitions.

### Device Owner, Dhizuku Bridge & Compatibility Stubs (`:manager`, `:stub`)
* `manager/src/main/aidl/moe/shizuku/manager/dhizuku/IDhizukuService.aidl` → `IDhizukuService`; AIDL contract for Dhizuku service binding and elevated administration.
* `manager/src/main/java/moe/shizuku/manager/deviceowner/DeviceOwnerManager.kt` → `DeviceOwnerManager`; Coordinates Device Owner / Profile Owner states, ADB setup commands, 1-tap activation, app delegation, and DPM policy execution.
* `manager/src/main/java/moe/shizuku/manager/dhizuku/DhizukuAuthManager.kt` → `DhizukuAuthManager`; Manages session-only and persistent UID authorization for Dhizuku IPC callers.
* `manager/src/main/java/moe/shizuku/manager/dhizuku/DhizukuProvider.kt` → `DhizukuProvider`; ContentProvider hosting `IDhizuku.Stub` binder for third-party apps requesting Device Owner delegated scopes.
* `manager/src/main/java/moe/shizuku/manager/dhizuku/DhizukuService.kt` → `DhizukuService`; Device / Profile Owner bridge interfacing with Dhizuku service to spawn server or bind TCP 5555.
* `manager/src/main/java/moe/shizuku/manager/compat/StubManager.kt` → `StubManager`; Manages extraction, installation, and verification of Shizuku and Dhizuku compatibility stub APKs.
* `manager/src/main/java/moe/shizuku/manager/settings/CompatStubsScreen.kt` → `CompatStubsScreen`; Compose screen managing installation and removal of Shizuku and Dhizuku compatibility stubs.
* `manager/src/main/java/moe/shizuku/manager/settings/DeviceOwnerScreen.kt` → `DeviceOwnerScreen`; Compose screen displaying Device Owner status, ADB activation commands, and DPM policy controls.
* `manager/src/main/java/moe/shizuku/manager/settings/DeviceOwnerDelegationScreen.kt` → `DeviceOwnerDelegationScreen`; Compose screen configuring app-level delegation scopes for Device Owner.
* `manager/src/main/java/moe/shizuku/manager/settings/DeviceOwnerTransferScreen.kt` → `DeviceOwnerTransferScreen`; Compose screen facilitating ownership transfer to other device admin components.
* `stub/src/dhizuku/java/com/rosan/dhizuku/ForwardDhizukuActivity.java` → `ForwardDhizukuActivity`; Dhizuku compatibility stub activity redirecting callers to Shevery manager.
* `stub/src/dhizuku/java/com/rosan/dhizuku/ForwardDhizukuProvider.java` → `ForwardDhizukuProvider`; Dhizuku compatibility stub ContentProvider exposing Shevery Dhizuku binder.
* `stub/src/shizuku/java/moe/shizuku/privileged/api/ForwardActivity.java` → `ForwardActivity`; Legacy Shizuku compatibility stub activity redirecting callers to Shevery.
* `stub/src/shizuku/java/moe/shizuku/privileged/api/ForwardReceiver.java` → `ForwardReceiver`; Legacy Shizuku broadcast receiver stub forwarding authorization broadcasts.

### Security, Biometric Auth, Firewall, USB Debug Hide & Stealth (`:manager`)
* `manager/src/main/java/moe/shizuku/manager/security/AuthManager.kt` → `AuthManager`; BiometricPrompt and Device Credential session coordinator protecting sensitive manager operations.
* `manager/src/main/java/moe/shizuku/manager/security/SecuritySettings.kt` → `SecuritySettings`; SharedPreferences store for authentication policies, protected actions, and session timeouts.
* `manager/src/main/java/moe/shizuku/manager/settings/SecuritySettingsScreen.kt` → `SecuritySettingsScreen`; Compose screen configuring biometric auth, USB debugging hiding, firewall, and stealth APK options.
* `manager/src/main/java/moe/shizuku/manager/settings/NetworkFirewallActivity.kt` → `NetworkFirewallActivity`; Compose screen configuring per-package network firewall rules.
* `manager/src/main/java/moe/shizuku/manager/settings/NetworkFirewallManager.kt` → `NetworkFirewallManager`; Controls Android connectivity chain rules (`cmd connectivity set-package-networking-enabled`) via Shizuku binder.
* `manager/src/main/java/moe/shizuku/manager/settings/UsbDebuggingHideActivity.kt` → `UsbDebuggingHideActivity`; Compose screen configuring per-app USB debugging hiding rules.
* `manager/src/main/java/moe/shizuku/manager/settings/UsbDebuggingHideManager.kt` → `UsbDebuggingHideManager`; Manages automated toggling of global ADB debugging flags based on foreground app usage.
* `manager/src/main/java/moe/shizuku/manager/settings/UsbDebuggingHideService.kt` → `UsbDebuggingHideService`; Foreground service monitoring foreground app usage stats to dynamically mask ADB status.
* `manager/src/main/java/moe/shizuku/manager/stealth/StealthApkGenerator.kt` → `StealthApkGenerator`; In-memory APK cloner rewriting package IDs and launcher labels, regenerating Adler32/SHA-1 checksums, and applying APK Signature Scheme v2.

### Comput Terminal, Autonomous Agent & Multi-Provider AI (`:manager`)
* `manager/src/main/java/moe/shizuku/manager/logs/ComputScreen.kt` → `ComputScreen`; Interactive terminal console executing privileged shell commands, macros, and output explanations.
* `manager/src/main/java/moe/shizuku/manager/logs/ComputAgentSheet.kt` → `ComputAgentSheet`; Compose bottom sheet interface for autonomous multi-step Comput AI agent planning and execution.
* `manager/src/main/java/moe/shizuku/manager/logs/CommandiumSheet.kt` → `CommandiumSheet`; Compose sheet translating natural language queries into executable shell commands via AI.
* `manager/src/main/java/moe/shizuku/manager/comput/agent/ComputAgent.kt` → `ComputAgent`; Autonomous AI agent orchestrating multi-step shell and module actions with safety mode gating (Secure/Turbo/Custom).
* `manager/src/main/java/moe/shizuku/manager/comput/agent/AgentChats.kt` → `AgentChats`; JSON persistence store managing multi-turn conversation logs and action histories for Comput agent.
* `manager/src/main/java/moe/shizuku/manager/settings/AiManagerScreen.kt` → `AiManagerScreen`; Compose screen for configuring custom AI providers, API keys, and endpoint connectivity tests.
* `manager/src/main/java/moe/shizuku/manager/settings/AiProviderDialog.kt` → `AiProviderDialog`; Compose dialog for adding and editing custom AI provider URLs and credentials.
* `manager/src/main/java/moe/shizuku/manager/commandium/AiProviderRepository.kt` → `AiProviderRepository`; Manages encrypted AI provider configs and model list caching with 24h TTL.
* `manager/src/main/java/moe/shizuku/manager/commandium/AiProviderPresets.kt` → `AiProviderPresets`; Factory presets for Gemini, OpenAI, Claude, OpenRouter, Groq, DeepSeek, and Requesty.
* `manager/src/main/java/moe/shizuku/manager/commandium/AiProvider.kt` → `AiProvider`; Data model representing custom or preset AI provider endpoints and API credentials.
* `manager/src/main/java/moe/shizuku/manager/commandium/AiModelPicker.kt` → `AiModelPicker`; Compose bottom sheet with model search, recommendations, and provider selection.
* `manager/src/main/java/moe/shizuku/manager/utils/AiClient.kt` → `AiClient`; HTTP client executing streaming and non-streaming prompts against configured AI endpoints.
* `manager/src/main/java/moe/shizuku/manager/utils/AiExplainUtil.kt` → `AiExplainUtil`; Helper structuring terminal output explanation prompts for Comput console.

### Accessibility Service Subsystem (`:manager`)
* `manager/src/main/java/moe/shizuku/manager/accessibility/AccessibilityManager.kt` → `AccessibilityManager`; Core coordinator for querying, binding, and toggling privileged accessibility services.
* `manager/src/main/java/moe/shizuku/manager/accessibility/AccessibilityManagerActivity.kt` → `AccessibilityManagerActivity`; Compose UI for managing privileged accessibility bridge services.
* `manager/src/main/java/moe/shizuku/manager/accessibility/AccessibilityDaemonService.kt` → `AccessibilityDaemonService`; Elevated accessibility service bridging privileged input injection and window inspections.
* `manager/src/main/java/moe/shizuku/manager/accessibility/AccessibilityBootReceiver.kt` → `AccessibilityBootReceiver`; Listens for device boot to auto-restore accessibility keep-alive daemon.
* `manager/src/main/java/moe/shizuku/manager/accessibility/AccessibilityKeepAliveStore.kt` → `AccessibilityKeepAliveStore`; SharedPreferences persistence tracking enabled accessibility service states.

### Modules Subsystem, Catalog & Auto-Update (`:manager`)
* `manager/src/main/java/moe/shizuku/manager/module/AdbModule.kt` → `AdbModule`; Data model representing local ADB module properties, flags, banners, and execution states.
* `manager/src/main/java/moe/shizuku/manager/module/AdbModuleManager.kt` → `AdbModuleManager`; Manages local ADB ZIP module installation, verification, policy gating, and removal.
* `manager/src/main/java/moe/shizuku/manager/module/ModulesScreen.kt` → `ModulesScreen`; Compose screen managing installed local modules with action triggers and toggles.
* `manager/src/main/java/moe/shizuku/manager/module/ModuleSettings.kt` → `ModuleSettings`; SharedPreferences store for module execution policies, safe mode, and update caches.
* `manager/src/main/java/moe/shizuku/manager/module/ModuleWebViewActivity.kt` → `ModuleWebViewActivity`; Container activity rendering local module HTML/JS WebUI dashboards.
* `manager/src/main/java/moe/shizuku/manager/module/ModuleJsBridge.kt` → `ModuleJsBridge`; JavaScript bridge providing secure IPC between local module WebUIs and Shizuku APIs.
* `manager/src/main/java/moe/shizuku/manager/module/ModuleCommandReview.kt` → `ModuleCommandReview`; Security review dialog inspecting module commands prior to execution.
* `manager/src/main/java/moe/shizuku/manager/module/catalog/CatalogScreen.kt` → `CatalogScreen`; Compose UI exploring, inspecting, and installing community modules from GitHub.
* `manager/src/main/java/moe/shizuku/manager/module/catalog/TokenStore.kt` → `TokenStore`; Encrypted token storage for GitHub API authorization tokens during catalog discovery.
* `manager/src/main/java/moe/shizuku/manager/module/discovery/DiscoveredModule.kt` → `DiscoveredModule`; Data model representing a catalog module repository found via GitHub search.
* `manager/src/main/java/moe/shizuku/manager/module/discovery/DiscoveryCache.kt` → `DiscoveryCache`; Local disk cache caching catalog module discovery responses to respect rate limits.
* `manager/src/main/java/moe/shizuku/manager/module/discovery/GitHubModels.kt` → `GitHubRepo`, `SearchResponse`; Serialization data classes for GitHub REST API discovery endpoints.
* `manager/src/main/java/moe/shizuku/manager/module/discovery/ModuleDiscoveryManager.kt` → `ModuleDiscoveryManager`; Queries GitHub repository search API for topic `shevery-modules`.
* `manager/src/main/java/moe/shizuku/manager/module/discovery/ModuleValidator.kt` → `ModuleValidator`; Validates module repository structures, `module.prop` integrity, and safety.
* `manager/src/main/java/moe/shizuku/manager/module/discovery/RateLimitTracker.kt` → `RateLimitTracker`; Parses and tracks GitHub HTTP rate limit headers to prevent request lockouts.
* `manager/src/main/java/moe/shizuku/manager/module/update/AppUpdateDownloader.kt` → `AppUpdateDownloader`; Manages background downloading of newer Shevery APK releases.
* `manager/src/main/java/moe/shizuku/manager/module/update/AppUpdateInstaller.kt` → `AppUpdateInstaller`; Initiates package installer sessions to seamlessly update Shevery on device.
* `manager/src/main/java/moe/shizuku/manager/module/update/AppUpdateSettingsGroup.kt` → `AppUpdateSettingsGroup`; Compose settings card displaying pending updates, release notes, and install CTA.
* `manager/src/main/java/moe/shizuku/manager/module/update/GitHubReleaseModels.kt` → `GitHubRelease`, `GitHubAsset`; Data models deserializing GitHub release tags, assets, and changelogs.
* `manager/src/main/java/moe/shizuku/manager/module/update/ModuleInstaller.kt` → `ModuleInstaller`; Downloads, unpacks, validates, and installs module ZIPs from remote release URLs.
* `manager/src/main/java/moe/shizuku/manager/module/update/SheveryAutoUpdateWorker.kt` → `SheveryAutoUpdateWorker`; Periodic WorkManager background worker checking and downloading updates.
* `manager/src/main/java/moe/shizuku/manager/module/update/SheveryUpdateCheckWorker.kt` → `SheveryUpdateCheckWorker`; One-shot worker checking GitHub releases for new versions.
* `manager/src/main/java/moe/shizuku/manager/module/update/SheveryUpdateChecker.kt` → `SheveryUpdateChecker`; Queries GitHub API, sorts releases by published date, and detects updates.
* `manager/src/main/java/moe/shizuku/manager/module/update/SourceZipBuilder.kt` → `SourceZipBuilder`; Packages local module directories into standardized ZIP bundles.
* `manager/src/main/java/moe/shizuku/manager/module/update/UpdateChecker.kt` → `UpdateChecker`; Semantic version comparison utilities for modules and core app releases.
* `manager/src/main/java/moe/shizuku/manager/module/update/UpdateResult.kt` → `UpdateResult`; Sealed class representing update check states (Success, UpToDate, Error).
* `manager/src/main/java/moe/shizuku/manager/module/update/UpdateSettingsScreen.kt` → `UpdateSettingsScreen`; Compose screen managing auto-update intervals, channel selection, and Wi-Fi gating.

### Automation Connectors, Tasker & Control Receivers (`:tasker`, `:manager`)
* `tasker/src/main/java/com/hamondev/shevery/tasker/EditActivity.kt` → `EditActivity`; Pure Compose configuration activity for Tasker / MacroDroid plugin actions.
* `tasker/src/main/java/com/hamondev/shevery/tasker/PluginContract.kt` → `PluginContract`; Action bundles, extra keys (`EXTRA_AUTH`), and command definitions for Tasker plugin automation.
* `tasker/src/main/java/com/hamondev/shevery/tasker/PluginReceiver.kt` → `PluginReceiver`; Tasker / MacroDroid broadcast receiver with auth token gating, active restart guards, and alert notifications.
* `manager/src/main/java/moe/shizuku/manager/receiver/SheveryControlReceiver.kt` → `SheveryControlReceiver`; Broadcast receiver for external automation commands (`START_SERVICE`, `STOP_SERVER`) with state idempotency and worker cancellation.
* `manager/src/main/java/moe/shizuku/manager/receiver/BootCompleteReceiver.kt` → `BootCompleteReceiver`; Handles `BOOT_COMPLETED` to trigger automatic background server activation.
* `manager/src/main/java/moe/shizuku/manager/receiver/ShizukuReceiver.kt` → `ShizukuReceiver`; Internal broadcast receiver coordinating inter-process signals across manager components.
* `manager/src/main/java/moe/shizuku/manager/receiver/ShizukuReceiverStarter.kt` → `ShizukuReceiverStarter`; Helper dispatching broadcast intents to launch elevated server binaries.
* `manager/src/main/java/moe/shizuku/manager/receiver/AutoDisableUsbDebuggingReceiver.kt` → `AutoDisableUsbDebuggingReceiver`; Broadcast receiver disabling wireless debugging upon successful server start.
* `manager/src/main/java/moe/shizuku/manager/receiver/SheveryDeviceAdminReceiver.kt` → `SheveryDeviceAdminReceiver`; DeviceAdminReceiver responding to Device Owner, Profile Owner, and administration lifecycle events.
* `manager/src/main/java/moe/shizuku/manager/receiver/NotifAttemptReceiver.kt` → `NotifAttemptReceiver`; Handles notification action clicks to retry server startup.
* `manager/src/main/java/moe/shizuku/manager/receiver/NotifCancelReceiver.kt` → `NotifCancelReceiver`; Handles notification action clicks to dismiss ongoing connection attempts.
* `manager/src/main/java/moe/shizuku/manager/receiver/NotifRestoreReceiver.kt` → `NotifRestoreReceiver`; Handles notification action clicks to re-enable watchdog keep-alive.
* `manager/src/main/java/moe/shizuku/manager/connector/ShizukuConnectorProvider.kt` → `ShizukuConnectorProvider`; ContentProvider dispatching gated connector events to external automators.

### Manager Core UI, Expressive Components & Settings Hub (`:manager`)
* `manager/src/main/java/moe/shizuku/manager/MainActivity.kt` → `MainActivity`; Splash / routing activity directing callers to HomeActivity or permission setup.
* `manager/src/main/java/moe/shizuku/manager/home/HomeActivity.kt` → `HomeActivity`; Main container activity hosting floating expressive navigation bar, tab switching, and Home automation card.
* `manager/src/main/java/moe/shizuku/manager/home/HomeViewModel.kt` → `HomeViewModel`; Exposes `serverState: StateFlow<State>` and triggers service start, stop, and restart actions.
* `manager/src/main/java/moe/shizuku/manager/home/AdbDialogFragment.kt` → `AdbDiscoveryDialog`; Compose dialog providing one-click TCP 5555 activation, port probes, and help.
* `manager/src/main/java/moe/shizuku/manager/home/AdbPairDialogFragment.kt` → `AdbPairDialog`; Compose dialog for 6-digit wireless debugging code entry and progress feedback.
* `manager/src/main/java/moe/shizuku/manager/settings/SettingsScreen.kt` → `SettingsScreen`; Segmented Settings Hub dividing options into General, Service, Automation, Security, Backups, and Extras.
* `manager/src/main/java/moe/shizuku/manager/about/AboutActivity.kt` → `AboutActivity`; Compose screen presenting app version, system info, update checks, and contributor credits.
* `manager/src/main/java/moe/shizuku/manager/about/ShizukuApps.kt` → `ShizukuApps`; Provides daily curated ecosystem app recommendations with online GitHub stars and offline fallback.
* `manager/src/main/java/moe/shizuku/manager/ui/compose/ShizukuExpressive.kt` → `ShizukuLazyScaffold`, `SettingsGroup`, `SettingsRow`; Canonical Material 3 Expressive UI components with squircle geometry and tokenized layouts.
* `manager/src/main/java/moe/shizuku/manager/ui/compose/MotionBlur.kt` → `Modifier.scrollMotionBlur()`; Directional scroll motion blur utilizing AGSL runtime shader (API 33+) with velocity-based decay.
* `manager/src/main/java/moe/shizuku/manager/app/AppActivity.kt` → `AppActivity`; Base activity configuring window insets, theme styling, and edge-to-edge layout.
* `manager/src/main/java/moe/shizuku/manager/app/ThemeHelper.java` → `ThemeHelper`; Manages dynamic system colors, night modes, and theme attribute resolution.
* `manager/src/main/java/moe/shizuku/manager/service/SheveryNotificationManager.kt` → `SheveryNotificationManager`; Channels, builds, and posts foreground notifications for active daemon status.
* `manager/src/main/java/moe/shizuku/manager/service/StartupNotificationManager.kt` → `StartupNotificationManager`; Displays transient progress and error notifications during server startup.
* `manager/src/main/java/moe/shizuku/manager/starter/Starter.kt` → `Starter.writeFiles()`, `Starter.command`; Generates starter shell scripts and extracts native starter binaries.
* `manager/src/main/java/moe/shizuku/manager/starter/StarterActivity.kt` → `StarterActivity`; UI orchestrator for Root/Wireless starter runs with provider pre-warming and timeout watchdog.
* `manager/src/main/java/moe/shizuku/manager/shell/Shell.java` → `Shell`; Utility wrapper executing synchronous local shell processes.
* `manager/src/main/java/moe/shizuku/manager/shell/ShellBinderRequestHandler.kt` → `ShellBinderRequestHandler`; Intercepts and delegates external shell binder IPC requests.
* `manager/src/main/java/moe/shizuku/manager/shell/ShellTutorialActivity.kt` → `ShellTutorialActivity`; Step-by-step documentation and export screen for `rish` CLI setup.
* `manager/src/main/java/moe/shizuku/manager/legacy/LegacyIsNotSupportedActivity.kt` → `LegacyIsNotSupportedActivity`; Compose dialog informing legacy Shizuku v3 callers that the old API is unsupported.
* `manager/src/main/java/moe/shizuku/manager/legacy/ShellRequestHandlerActivity.kt` → `ShellRequestHandlerActivity`; Handles legacy intent-based shell execution requests from third-party apps.
* `manager/src/debug/java/moe/shizuku/manager/TestHostActivity.kt` → `TestHostActivity`; Debug-only test harness activity hosting Compose component previews and instrumented tests.
* `manager/src/debug/java/moe/shizuku/manager/receiver/TestKeysReceiver.kt` → `TestKeysReceiver`; Debug-only broadcast receiver simulating key events and mock binder connections.

### Application Management & Permission Authorization (`:manager`)
* `manager/src/main/java/moe/shizuku/manager/management/ApplicationManagementActivity.kt` → `ApplicationManagementActivity`; Compose UI displaying app list with instant search and permission filter chips.
* `manager/src/main/java/moe/shizuku/manager/management/AppDetailActivity.kt` → `AppDetailActivity`; Compose activity inspecting detailed app authorization permissions and token state.
* `manager/src/main/java/moe/shizuku/manager/management/AppsViewModel.kt` → `AppsViewModel`; Loads installed apps, filters privileged callers, and mutates package permission states.
* `manager/src/main/java/moe/shizuku/manager/authorization/AuthorizationManager.kt` → `AuthorizationManager`; Grants, revokes, and persists package-level client permission authorizations.
* `manager/src/main/java/moe/shizuku/manager/authorization/RequestPermissionActivity.kt` → `RequestPermissionActivity`; Non-blocking Compose prompt dialog requesting privileged access for client apps.

### System Utilities, State Machine & Common Interop (`:manager`, `:common`)
* `manager/src/main/java/moe/shizuku/manager/ShizukuApplication.kt` → `ShizukuApplication`; Application entry point initializing logging, theme, night mode, and state observation.
* `manager/src/main/java/moe/shizuku/manager/ShizukuManagerProvider.kt` → `ShizukuManagerProvider`; ContentProvider receiving elevated binder hand-off tokens via `METHOD_SEND_BINDER`.
* `manager/src/main/java/moe/shizuku/manager/ShizukuSettings.java` → `ShizukuSettings`; SharedPreferences store for TCP mode, automation connectors, auth token, watchdog, and theme flags.
* `manager/src/main/java/moe/shizuku/manager/utils/ShizukuStateMachine.kt` → `ShizukuStateMachine`; Reactive state machine tracking `STARTING`, `RUNNING`, `STOPPING`, `STOPPED`, `CRASHED`.
* `manager/src/main/java/moe/shizuku/manager/utils/EnvironmentUtils.kt` → `EnvironmentUtils`; Probes live TCP ports, detects Watch/TV form factors, and inspects SELinux status.
* `manager/src/main/java/moe/shizuku/manager/utils/BackupRestoreUtil.kt` → `BackupRestoreUtil`; Exports and imports app settings, AI configs, and module presets to JSON backups.
* `manager/src/main/java/moe/shizuku/manager/utils/CommandSanitizer.kt` → `CommandSanitizer`; Validates and cleans shell strings to prevent shell injection vulnerabilities.
* `manager/src/main/java/moe/shizuku/manager/utils/CustomTabsHelper.java` → `CustomTabsHelper`; Launches in-app Chrome Custom Tabs with copy-to-clipboard fallback.
* `manager/src/main/java/moe/shizuku/manager/utils/EmptySharedPreferencesImpl.java` → `EmptySharedPreferencesImpl`; No-op SharedPreferences implementation for mock / safe fallbacks.
* `manager/src/main/java/moe/shizuku/manager/utils/Logger.java` → `Logger`; Tagged Android logcat wrapper utility for manager components.
* `manager/src/main/java/moe/shizuku/manager/utils/MultiLocaleEntity.java` → `MultiLocaleEntity`; Data model supporting localized display names across multiple languages.
* `manager/src/main/java/moe/shizuku/manager/utils/ShizukuSystemApis.kt` → `ShizukuSystemApis`; Reflection wrappers invoking hidden platform APIs (multi-user, activity manager).
* `manager/src/main/java/moe/shizuku/manager/utils/UserHandleCompat.java` → `UserHandleCompat`; Compatibility helper resolving multi-user profiles on Android 7+.
* `manager/src/main/java/moe/shizuku/manager/utils/UserInfoCompat.java` → `UserInfoCompat`; Wrapper exposing hidden UserInfo properties (userId, name, flags).
* `manager/src/main/java/moe/shizuku/manager/model/ServiceStatus.kt` → `ServiceStatus`; Data class holding active server status, UID, PID, API version, and SEPolicy context.
* `manager/src/main/java/moe/shizuku/manager/AppConstants.java` → `AppConstants`; Global manager constants, notification channels, and intent extra keys.
* `manager/src/main/java/moe/shizuku/manager/Helps.java` → `Helps`; Central repository of external documentation URLs and guide endpoints.
* `manager/src/main/java/moe/shizuku/manager/Manifest.java` → `Manifest.permission`; Declares manager-specific permission strings in Java.
* `manager/src/main/java/moe/shizuku/manager/ktx/Context.kt` → `Context.hasPermission()`; Extension helpers for checking permissions, resources, and system services.
* `manager/src/main/java/moe/shizuku/manager/ktx/Log.kt` → `Log.d()`, `Log.e()`; Structured logging helpers formatting messages under canonical tags.
* `manager/src/main/java/moe/shizuku/manager/ktx/PackageManager.kt` → `PackageManager.getApplicationInfoCompat()`; Compatibility wrapper querying package metadata safely.
* `manager/src/main/java/moe/shizuku/manager/ktx/String.kt` → `String.htmlCompat()`; Text formatting helpers sanitizing strings and parsing HTML styling.
* `manager/src/main/java/rikka/shizuku/manager/ShizukuLocales.java` → `ShizukuLocales`; Locale definitions and language tags supported by Shevery.
* `common/src/main/java/moe/shizuku/common/util/BuildUtils.java` → `BuildUtils`; Device hardware and Android API version detection utilities.
* `common/src/main/java/moe/shizuku/common/util/InstalledPackagesCompat.java` → `InstalledPackagesCompat`; Safeguards package querying against TransactionTooLargeException on Android 11+.
* `common/src/main/java/moe/shizuku/common/util/OsUtils.java` → `OsUtils`; Common OS reflection utilities for UID/PID inspection.

---

## 3. Domain & Technical Specifications

### Protocols & IPC Contracts
* **Server Binder Dispatch:** `ShizukuService` broadcasts its IBinder instance to `ShizukuManagerProvider` via `call(content://${applicationId}.shizuku, METHOD_SEND_BINDER, null, bundleWithBinder)`.
* **Client App Binder Interface:** `moe.shizuku.server.IShizukuService` exposes:
  - `transactRemote(Parcel data, Parcel reply, int flags)`: Proxies raw binder calls directly to `system_server`.
  - `checkPermission(String permission)`: Validates client calling UID privileges against elevated system server.
* **Dhizuku Device Owner Bridge:** `DhizukuProvider` implements `IDhizuku.Stub`, delegating Device Owner / Profile Owner APIs and scopes.
* **Network Discovery:**
  - mDNS TLS Connect: Service type `_adb-tls-connect._tcp.local.`, resolves dynamic port for TLS socket communication.
  - mDNS TLS Pairing: Service type `_adb-tls-pairing._tcp.local.`, resolves dynamic port for SPAKE2 handshake.
  - Loopback TCP 5555: `127.0.0.1:5555` queried via non-blocking socket probe `EnvironmentUtils.isAdbPortLive(5555)`.

### Persistence & Storage Schemas
* **`ShizukuSettings`:** Backed by default `SharedPreferences` (`moe.shizuku.manager_preferences`). Stores `tcp_mode`, `connectors_enabled`, `night_mode`, and prompt suppression bits.
* **`SecuritySettings`:** SharedPreferences store managing biometric authentication flags, session timeout seconds, and protected actions.
* **`TokenStore`:** AES-GCM encrypted token persistence for client app authorization tokens and GitHub API access tokens.
* **`AiProviderRepository`:** JSON serialization of AI provider endpoints, encrypted API keys, and model cache lists with 24h TTL.
* **`StealthApkGenerator`:** In-memory APK clone and AXML rewriter producing randomized package IDs signed with APK Signature Scheme v2.
* **Module Storage:** Modules unpack into `/data/local/tmp/modules/<id>/` or app private internal module sandbox with `module.prop`, `action.sh`, and `service.sh`.

---

## 4. Architectural Invariants & Guardrails

1. **Reactive State Ownership:**
   * `ShizukuStateMachine` is the single source of truth for daemon operational states (`STOPPED`, `STARTING`, `RUNNING`, `STOPPING`, `CRASHED`).
   * Polling loops (`delay(250)`, `delay(500)`) are strictly forbidden. Use `awaitRunning(timeout)` or `asFlow()`.
2. **Automation & Starter Idempotency:**
   * Automation triggers (`SheveryControlReceiver`, `PluginReceiver`) and background workers (`AdbStartWorker`) must be strictly idempotent.
   * Redundant `START` broadcasts while `RUNNING` or `STARTING` are immediate no-ops.
   * `STOP_SERVER` must explicitly cancel active/queued WorkManager workers (`cancelUniqueWork(UNIQUE_WORK_NAME)`).
   * Multi-user manager dispatch checks package installation via `Android17Compat` before provider resolution to prevent blocking client app delivery.
3. **NightDog & Dual-Tier Watchdog:**
   * In-process daemon health check (`NightDog`) watches critical system services (`package`, `activity`, `user`, `appops`) and executes rebinds on failure.
   * Client-side `WatchdogManager` coordinates with `NightDogClient` over Binder IPC. Native starter supervisor grace period allows respawn before triggering fallback restarts.
   * `WatchdogManager.expectingDeath` and `WatchdogManager.isStarterActive` suppress false-positive watchdog race conditions.
4. **Main Thread Non-Blocking:**
   * All network operations (`AdbClient`, socket probes, GitHub update checks, AI queries) must execute on `Dispatchers.IO`.
   * Main thread Binder calls during UI rendering must be memoized or offloaded via `produceState`.
5. **Pure Compose UI Standard:**
   * Zero XML layout inflation (`findViewById`, `MaterialAlertDialogBuilder`, or `supportFragmentManager`) in `:manager` UI.
   * All dialogs must use Compose `AlertDialog` styled with `MaterialTheme.shapes.extraLarge` (28dp) and `surfaceContainerHigh`.
6. **Android 17 (API 37) Platform Readiness:**
   * `ACCESS_LOCAL_NETWORK` permission checks are strictly gated to `Build.VERSION.SDK_INT >= 37`. Do not invoke on API <= 36.
   * Minimum touch target standard is 48×48dp across all interactive controls (WCAG 2.2).

### Known Landmines & Anti-Hallucination Guardrails
* `TRAP: Using polling delay loops (delay(250), delay(500)) for daemon state observation` → `RISK: CPU spin and race conditions` → `FIX: Use reactive state observation via ShizukuStateMachine.awaitRunning(timeout) or asFlow()`
* `TRAP: Triggering redundant START_SERVER broadcasts while server is RUNNING or STARTING` → `RISK: Worker collision and starter race condition` → `FIX: Idempotent pre-check via Shizuku.pingBinder() or ShizukuStateMachine.isRunning()`
* `TRAP: Dispatching binder to non-system user (UID != 0) without checking package installation` → `RISK: Fatal IPC block or silent drop on Android 17` → `FIX: Android17Compat.getApplicationInfo pre-check before binder delivery`
* `TRAP: Using XML layout inflation (findViewById) or legacy CircularProgressIndicator in :manager` → `RISK: Theme regression and WCAG failure` → `FIX: Pure Compose UI with Squircle geometry and Material 3 Expressive morphing LoadingIndicator`
* `TRAP: Invoking ACCESS_LOCAL_NETWORK permission checks on Android 16 or lower` → `RISK: NoSuchMethodError / runtime exception on legacy Android` → `FIX: Strictly gate with Build.VERSION.SDK_INT >= 37`

---

## 5. End-to-End Data & State Flow Pipelines

### 1. Server Activation (Wireless ADB / TCP 5555 / Root)
`User/Worker Trigger` → `AdbStarter.start()` → `Check Live Port 5555` → `AdbClient TLS/TCP Socket Connect` → `Send 'sh /data/local/tmp/starter'` → `starter.cpp execvp app_process` → `ShizukuService.main()` → `NightDog.start()` → `Multi-User Install Pre-check` → `sendBinderToManager()` → `ShizukuManagerProvider.call()` → `ShizukuStateMachine: RUNNING` → `HomeViewModel UI State Emission`

### 2. Client Authorization & Permission Request
`Client App` → `Shizuku.requestPermission()` → `RequestPermissionActivity (UID checks)` → `Biometric Auth Check (if configured)` → `Compose AlertDialog Prompt` → `User Confirms` → `AuthorizationManager.grant(packageName, uid)` → `TokenStore.persist()` → `Notify Client Binder Listener`

### 3. Server Watchdog & Self-Healing Pipeline
`System Service Death / Hang` → `NightDog In-Server Monitor` → `Rebind Service` → `[If Process Dies]` → `starter.cpp Supervisor Auto-Respawn` → `[If Supervisor Fails >8s]` → `WatchdogManager Fallback Restart (AdbStartWorker / Root)` → `Await ShizukuStateMachine.awaitRunning(30s)`

### 4. Automation & External Connectors
`Tasker / MacroDroid / Shell Broadcast` → `PluginReceiver` / `SheveryControlReceiver` → `Verify ShizukuSettings.isConnectorsEnabled()` → `Verify Auth Token (EXTRA_AUTH)` → `State Idempotency Guard (RUNNING/STARTING Check)` → `AdbStartWorker.enqueueIfIdle()` → `Server Activation`

### 5. Comput AI Agent Execution
`User Goal Query` → `ComputAgent.plan()` → `AiClient Prompt to Selected Provider` → `Parse AgentPlan Steps` → `Safety Mode Check (Secure/Turbo/Custom)` → `Step Approval Dialog` → `Execute Shell Command / Module Service` → `Persist Multi-turn History in AgentChats`

---

## 6. UI/UX Design System & Tokens

* **Color & Surface Hierarchy:**
  * Background: `MaterialTheme.colorScheme.background`
  * Dialog Containers: `MaterialTheme.colorScheme.surfaceContainerHigh`
  * Card Surfaces: `MaterialTheme.colorScheme.surfaceContainerLow`
  * Prominent Headers / Badges: `MaterialTheme.colorScheme.primaryContainer`
* **Geometry & Shapes:**
  * Dialogs: Extra-Large Squircle `RoundedCornerShape(28.dp)`
  * Cards: Large / Medium Squircle `RoundedCornerShape(16.dp)` to `RoundedCornerShape(24.dp)`
  * App & Module Avatars: Squircle `RoundedCornerShape(12.dp)` to `RoundedCornerShape(14.dp)`
* **Motion & Effects:**
  * 100% parity on Material 3 Expressive morphing `LoadingIndicator`. Legacy `CircularProgressIndicator` is prohibited in `:manager`.
  * Directional scroll motion blur implemented via AGSL runtime shader (API 33+) with fallback in `MotionBlur.kt`.
* **Accessibility Standards:**
  * All interactive buttons, chips, and icon toggles enforce `Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)`.
  * Rows support TalkBack semantics via `Modifier.semantics(mergeDescendants = true)`.

---

## 7. Build, Verification & Diagnostic Recipes

### Local Build (Termux / Linux)
```bash
# Build Release APK
fast-gradlew :manager:assembleRelease

# Build Debug APK
fast-gradlew :manager:assembleDebug
```

### GitHub CI / CD Workflow Dispatch
```bash
# Trigger remote GitHub Action build (builds automatically on git push to dev/main)
gh workflow run apk-build.yml -f build_type=debug -f publish_artifact=true
```

### Verification & Test Suite
```bash
# Run local unit tests
fast-gradlew :manager:testDebugUnitTest
```

### Runtime Diagnostics & Logging
```bash
# Stream clean, filtered application logs
pidcat moe.shizuku.manager

# Check active service dump
adb shell dumpsys activity service com.hamondev.shevery

# Verify privileged shell interop via rish
rish -c "id"
```
