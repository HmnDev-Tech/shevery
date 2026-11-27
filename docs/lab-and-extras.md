# Accessibility Manager, USB Debugging Hiding & Network Firewall

Shevery bundles three privileged system utilities accessible from **Settings → Extras** and **Lab Features** (`LabFeaturesActivity`):

1. **Accessibility Manager** (with Keep-Alive Pinning)
2. **USB Debugging Hiding** (Per-App Foreground Detection)
3. **Network Firewall** (Per-App Connectivity Chain3 Blocker)

Wiki mirror: https://github.com/HmnDev-Tech/shevery/wiki/Lab-and-Extras

---

## 1. Accessibility Manager

Accessible via **Settings → Extras → Accessibility manager** or directly via the public shortcut intent:

```bash
adb shell am start -a com.hamondev.shevery.action.ACCESSIBILITY_MANAGER
```

### Features

- **Live Enable / Disable**: Lists every installed `AccessibilityService` on the device and toggles services directly by writing `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` and `ACCESSIBILITY_ENABLED` through the Shizuku server (`WRITE_SECURE_SETTINGS`).
- **Keep-Alive Pinning (Lock Icon)**:
  - Tap the **Lock / Pin** icon next to any accessibility service to add it to `AccessibilityKeepAliveStore`.
  - When **Keep-alive service** (`AccessibilityDaemonService`) is enabled, Shevery monitors pinned accessibility services and automatically re-enables them if the OS or battery saver kills them.
  - Disabling a service manually in Accessibility Manager automatically unpins it so the daemon does not fight your toggle.
- **Auto-start on Boot**: When **Auto-start on boot** is enabled (`AccessibilityBootReceiver`), the keep-alive daemon resumes automatically after device reboot.

---

## 2. USB Debugging Hiding

Accessible via **Lab Features → Hide USB debugging** (`UsbDebuggingHideActivity`).

Many banking, payment, and DRM-protected apps inspect `Settings.Global.ADB_ENABLED` and refuse to launch if USB debugging is turned on—even when no cable is connected.

### How It Works

1. **Grant Usage Access**: Tap **Usage access** at the top of the screen to grant `android.permission.PACKAGE_USAGE_STATS` so Shevery can detect which app is currently in the foreground.
2. **Required Privilege**: Shevery needs `WRITE_SECURE_SETTINGS`, **Device Owner / Profile Owner**, or an active **Shizuku / Root** session (`AdbArm.canArm`) to toggle `Settings.Global.ADB_ENABLED`.
3. **Select Target Apps**: Enable the switch for each app from which USB debugging should be hidden.
4. **Automatic Hide & Restore**:
   - While at least one target app is selected, `UsbDebuggingHideService` polls the foreground app every second while the screen is unlocked.
   - When a target app enters the foreground, Shevery saves the original `ADB_ENABLED` value and sets `ADB_ENABLED = 0`.
   - As soon as you switch away from the target app or lock the screen, Shevery restores `ADB_ENABLED` to its previous state.
   - **Crash / Relaunch Recovery**: If Shevery is force-stopped while `ADB_ENABLED` is hidden, relaunching `HomeActivity` or restarting the service automatically recovers and restores the original `ADB_ENABLED` setting.

---

## 3. Network Firewall

Accessible via **Lab Features → Network firewall** (`NetworkFirewallActivity`).

### Requirements

- **Android with the connectivity firewall commands and rule verification** (Android 13+ on AOSP)
- **Shevery server running** (ADB or Root)

### How It Works

Unlike VPN-based firewalls that occupy Android's single VPN slot, Shevery's Network Firewall uses Android's built-in eBPF connectivity chain (`chain3`) through the privileged Shizuku shell:

1. Enables `chain3` if not already active:
   ```bash
   cmd connectivity set-chain3-enabled true
   ```
2. Blocks or unblocks networking for the selected package:
   ```bash
   # Block internet access for a package
   cmd connectivity set-package-networking-enabled false <package>

   # Restore internet access for a package
   cmd connectivity set-package-networking-enabled true <package>
   ```
3. Verifies the applied kernel state via `cmd connectivity get-package-networking-enabled <package>` (`:deny` vs `:allow`) and saves the list of packages configured in Shevery. The screen checks that the chain and its rules are active before showing packages as blocked.

---

## Other Extras & Maintenance Controls

In **Settings → Extras** (and **Lab Features**), you can also configure:

- **Notify on recovery**: Show a notification when the Watchdog automatically recovers the server.
- **Auto-refresh on resume**: Refresh server status automatically whenever Shevery returns to the foreground.
- **Verbose logging**: Output detailed diagnostic logs to `logcat` for troubleshooting.
- **AI explanations**: Show an **Explain with AI** button in the Comput console whenever a shell command exits with an error.
- **Restart Service Now**: Immediately trigger `WatchdogManager.attemptRestart()`.
- **Dismiss Update Banner**: Clear any cached pending app update banner.
- **Revoke Trusted Modules**: Clear all per-module **Full Trust** grants in one tap.

---

## Source Files

- `manager/src/main/java/moe/shizuku/manager/accessibility/AccessibilityManagerActivity.kt`
- `manager/src/main/java/moe/shizuku/manager/accessibility/AccessibilityDaemonService.kt`
- `manager/src/main/java/moe/shizuku/manager/settings/UsbDebuggingHideActivity.kt`
- `manager/src/main/java/moe/shizuku/manager/settings/UsbDebuggingHideManager.kt`
- `manager/src/main/java/moe/shizuku/manager/settings/UsbDebuggingHideService.kt`
- `manager/src/main/java/moe/shizuku/manager/settings/NetworkFirewallActivity.kt`
- `manager/src/main/java/moe/shizuku/manager/settings/NetworkFirewallManager.kt`
- `manager/src/main/java/moe/shizuku/manager/settings/LabFeaturesActivity.kt`
