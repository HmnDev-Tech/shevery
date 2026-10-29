# ErrorProtect

ErrorProtect is the built-in watchdog for the Shevery server process (`shizuku_server`). It monitors main-thread responsiveness and critical Android system service bindings, terminating the server when a fault occurs so the native starter daemon can restart it cleanly.

## How it works

1. **Main-thread heartbeat monitoring**:
   - When enabled, `ShizukuService` posts a heartbeat (`ErrorProtect.beat()`) on its main looper thread every 5 seconds.
   - `ErrorProtect.start()` records an initial baseline timestamp immediately so a freeze before the first scheduled beat is still caught.
   - A dedicated daemon `HandlerThread` (`errorprotect`) checks heartbeat freshness dynamically against the configured timeout (`60s`), scheduling follow-up checks at `min(pollInterval, remainingTimeout)` so stalls are detected promptly at the timeout boundary rather than up to `2x` timeout.
   - Before killing a stalled server process (`Process.killProcess(Process.myPid())`), ErrorProtect captures and logs the main looper thread's stack trace to aid debugging.

2. **System service death & recovery monitoring**:
   - ErrorProtect registers `IBinder.linkToDeath` recipients on four critical system services (`package`, `activity`, `user`, `appops`) and optional `permissionmgr`.
   - Each health-check cycle also performs a fallback `pingBinder()` check and properly unlinks stale death recipients before rebinding.
   - When a service dies or becomes unresponsive, ErrorProtect deduplicates pending rebinds per service and retries with exponential backoff (`5s`, `10s`, `20s`, `40s`, `60s`, up to 10 attempts), verifying that the replacement binder is alive (`pingBinder()`) before accepting it.
   - When a service recovers (`ErrorProtect.Listener.onServiceRecovered`), `ShizukuService` invalidates cached `Android17Compat` proxy handles (`package` / `permissionmgr`), re-registers `BinderSender` process/UID observers (if `activity` restarted), and re-dispatches binders to the manager and client apps.
   - If a critical service cannot be recovered after all 10 attempts, ErrorProtect terminates the server process so the starter daemon can respawn it and wait for system services cleanly.

## Enabling and disabling

ErrorProtect is controlled via **Settings -> ErrorProtect(Watchdog)** across Phone, Wear OS, and Android TV:

- `ErrorProtectClient` communicates with `ShizukuService` over Binder IPC (`BINDER_TRANSACTION_setErrorProtectEnabled` / `BINDER_TRANSACTION_getErrorProtectEnabled`).
- On the server, `ShizukuConfigManager` flushes `errorprotect_enabled` changes immediately to `/data/user_de/0/com.android.shell/shizuku.json`.
- If toggled in the manager while the server is offline, `ErrorProtectClient` caches the preference locally and synchronizes it automatically as soon as the server binder is received.

## Starter recovery behavior

When `shizuku_server` is launched or exits abnormally / is killed by ErrorProtect:

- The native `starter` (`manager/src/main/jni/starter.cpp`) ignores `SIGPIPE` and `SIGHUP` (`SIG_IGN`) so closing the launching ADB/root shell cannot kill the background supervisor or trigger broken-pipe terminations, while restoring `SIG_DFL` inside the spawned `shizuku_server` child before `execvp`.
- Before spawning a new supervisor, `starter` terminates both stale `shizuku_server` instances and any lingering previous `starter` supervisor processes (matched via `/proc/<pid>/exe`) so duplicate supervisors never race each other.
- `starter` forks a detached background supervisor (`setsid`, `/dev/null` stdio) and communicates the initial `shizuku_server` PID over a pipe so the foreground starter process immediately prints `info: shizuku_server pid is <pid>` and `info: shizuku_starter exit with 0` and exits cleanly without blocking `StarterActivity` or wireless ADB sessions.
- The detached supervisor waits on `shizuku_server` via `waitpid` (retrying on `EINTR`), logs lifecycle events to Android logcat (`Shizuku` tag), and waits 5 seconds before respawning `shizuku_server` on abnormal exit.
- Rapid crash loops are capped at 5 consecutive restarts (`max_restarts = 5`).
- If `shizuku_server` runs stably for at least 60 seconds (`STABLE_UPTIME_RESET_SECS`), the consecutive crash counter resets to `0`, allowing ErrorProtect to recover the server across multi-day uptimes without exhausting the retry budget.
- Clean exits (`0`) and manager-uninstalled exits (`50` / `MANAGER_APP_NOT_FOUND`) stop the starter without restarting.

## Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| Heartbeat interval | 5 s | Main thread heartbeat cadence |
| Heartbeat timeout | 60 s | Maximum allowed time without a heartbeat before process termination |
| Poll interval | 60 s | Maximum interval between fallback `pingBinder()` checks |
| Initial rebind delay | 2 s | Delay before first rebind attempt after service death |
| Backoff range | 5 s – 60 s | Exponential backoff delay between failed rebind attempts |
| Max rebind attempts | 10 | Maximum attempts before terminating server for starter recovery |
| Stable uptime reset | 60 s | Server uptime threshold that resets starter's consecutive crash counter |

## Files

- `errorprotect/src/main/java/rikka/shizuku/errorprotect/ErrorProtect.kt`
- `server/src/main/java/rikka/shizuku/server/ShizukuService.java`
- `server/src/main/java/rikka/shizuku/server/ShizukuConfigManager.java`
- `server/src/main/java/rikka/shizuku/server/BinderSender.kt`
- `manager/src/main/jni/starter.cpp`
- `starter/src/main/java/moe/shizuku/starter/ServiceStarter.java`
- `manager/src/main/java/moe/shizuku/manager/errorprotect/ErrorProtectClient.kt`
- `manager/src/main/java/moe/shizuku/manager/service/WatchdogManager.kt`
- `manager/src/main/java/moe/shizuku/manager/settings/SettingsScreen.kt`
