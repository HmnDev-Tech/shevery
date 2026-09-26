# Tasker & MacroDroid Automation Guide

Shevery provides native automation integration for **Tasker**, **MacroDroid**, **Automate**, and **ADB / Termux shell scripts**.

Wiki mirror: https://github.com/HmnDev-Tech/shevery/wiki/Tasker-Plugin

> **Important**: To allow Tasker, MacroDroid, or external broadcasts to control Shevery, ensure **Shevery Connectors** is enabled under **Settings → Automation** (`Allow plugins to activate Shevery`). If disabled, control actions will be safely rejected.

---

## Method 1: Built-in Locale / Tasker Plugin (Recommended for Tasker)

Shevery ships with a standard Tasker / Locale plugin interface (`:tasker` module) with 4 actions and 1 state condition.

### 1. Task Actions (Task → Plugin → Shevery Tasker)

| Action | Description |
|---|---|
| **Start server** | Clears user-stop flag, triggers wireless ADB auto-start if in ADB mode, and resumes watchdog service |
| **Stop server** | Stops the Shizuku server and sets user-initiated flag to prevent automatic watchdog restarts |
| **Restart server** | Gracefully stops the server, monitors binder death (up to 10 seconds), and restarts it cleanly |
| **Toggle server** | Checks if server binder is alive: stops if running, starts if stopped |

### 2. State Condition (Profiles → State → Plugin → Shevery Tasker)

- **Condition**: `Server is running`
- Returns `RESULT_CONDITION_SATISFIED` when Shizuku server binder responds to ping, otherwise `RESULT_CONDITION_UNSATISFIED`.

---

## Method 2: Direct Broadcast Intents (Recommended for MacroDroid)

For tools like **MacroDroid**, **Automate**, or **Termux**, you can send standard explicit broadcast intents without configuring nested JSON bundles.

### Intent Parameters

- **Target**: Broadcast
- **Package**: `com.hamondev.shevery`
- **Class / Receiver** (optional, recommended): `com.hamondev.shevery.tasker.PluginReceiver`

### Actions

| Intent Action | Action Effect |
|---|---|
| `com.hamondev.shevery.action.START_SERVER` | Start Shevery server |
| `com.hamondev.shevery.action.STOP_SERVER` | Stop Shevery server |
| `com.hamondev.shevery.action.RESTART_SERVER` | Gracefully restart Shevery server |
| `com.hamondev.shevery.action.TOGGLE_SERVER` | Toggle Shevery server state |

### MacroDroid Setup Guide

1. In MacroDroid, add action: **Connectivity** → **Send Intent** (or search "Send Intent").
2. Set **Action**: `com.hamondev.shevery.action.START_SERVER` (or `STOP_SERVER`, `RESTART_SERVER`, `TOGGLE_SERVER`).
3. Set **Package**: `com.hamondev.shevery`.
4. Set **Target**: `Broadcast`.
5. Under **Extra 1**, set Parameter: `auth` and Value to your secret auth token (find and copy it via the Home screen **View intents** sheet). Save and test the macro!

---

## Method 3: Shell / ADB / Termux

You can also trigger server actions directly from ADB or Termux:

```bash
# Start server
am broadcast -a com.hamondev.shevery.action.START_SERVER -p com.hamondev.shevery -e auth <token>

# Stop server
am broadcast -a com.hamondev.shevery.action.STOP_SERVER -p com.hamondev.shevery -e auth <token>

# Restart server
am broadcast -a com.hamondev.shevery.action.RESTART_SERVER -p com.hamondev.shevery -e auth <token>

# Toggle server
am broadcast -a com.hamondev.shevery.action.TOGGLE_SERVER -p com.hamondev.shevery -e auth <token>
```

---

## In-App Automation Settings

You can inspect all available actions and copy action strings or the package name directly to your clipboard in Shevery:
**Settings** → **Automation** (`Tasker & MacroDroid`).
