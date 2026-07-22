# ADB Modules Guide

This guide is for module authors and testers. For the exact API contract, see
[ADB Modules API reference](adb-modules-api.md).

## What This System Is

ADB Modules are ZIP packages installed into Shizuku private storage and executed through
the active Shizuku server.

- ADB-started Shizuku: scripts run with ADB shell privileges.
- Root-started Shizuku: scripts run with root privileges.
- Safe mode: manual actions only.
- Full access: allows stronger module behavior.
- Background actions: required before `service.sh` can run.

This is a real module runner, not a visual stub. It installs ZIPs, parses metadata,
stores module files, runs shell scripts through Shizuku, opens local WebUI, tracks enabled
state, deletes modules, and writes last-run logs.

It is not a Magisk/KSU systemless overlay implementation. There are no mount hooks,
no `/data/adb/modules` compatibility promise, and no long-running daemon supervisor yet.

## Minimal Module

Create this structure:

```text
my-module/
├── module.prop
├── action.sh
└── webui/
    └── index.html
```

`module.prop`:

```properties
id=my-module
name=My Module
version=1.0
versionCode=1
author=Author
description=Short module description
```

`action.sh`:

```sh
#!/system/bin/sh
echo "module=$SHIZUKU_MODULE_ID"
echo "mode=$SHIZUKU_MODULE_MODE"
id
```

Package it:

```sh
cd my-module
zip -r ../my-module.zip .
```

Install `my-module.zip` from the ADB Modules screen.

## Optional Files

Banner:

```text
banner.png
```

WebUI:

```text
webui/index.html
```

Background/service hook:

```text
service.sh
```

Custom paths can be declared in `module.prop`:

```properties
banner=assets/banner.webp
webui=webui
usesShellBridge=true
action=scripts/action.sh
```

`usesShellBridge=true` is mandatory for WebUI pages that need `window.Shizuku`.

## Script Environment

Scripts run from the module directory. Use these variables:

```sh
MODDIR=/data/user/0/<package>/files/adb_modules/<id>
ASH_STANDALONE=1
SHIZUKU_MODULE_ID=<id>
SHIZUKU_MODULE_MODE=safe|custom|full
SHIZUKU_MODULE_TRUSTED=0|1
SHIZUKU_MODULE_BACKGROUND=0|1
```

Do not hardcode Magisk/KSU paths. Use `$MODDIR`.

## Action vs Service

Use `action.sh` for a user-triggered command.

Use `service.sh` for controlled background setup. It runs only when:

- the module is enabled;
- access mode is Full, or Custom with Service enabled;
- background actions are enabled;
- Shizuku binder is available.

The manager auto-runs enabled services once per Shizuku binder session. Manual Service
button execution uses the same policy.

## Full Trust

Full Trust is a per-module override for modules you explicitly trust. Long-press
a module card to reveal **Trust**. Long-press again to hide the action. After
trusting, the module gets a Full Trust chip. Long-press the trusted module to
reveal **Untrust**.

Trusted modules bypass the global Action, Service, background, WebUI bridge,
WebView internet, WebUI download, `usesShellBridge=true`, and ReCommand gates.
Core safety limits such as path traversal rejection, process timeouts, output
caps, and download size caps still apply.

## ReCommand and AI Checker

ReCommand can show a confirmation dialog before WebUI shell execution and Action
execution. The dialog uses icon-only expand/collapse and copy controls, and the
expand/collapse control is shown only for long commands.

The optional Gemini checker is hidden by default. In Settings, tap the
translation contributors row five times quickly to reveal the AI settings row and
the Gemini star in ReCommand dialogs. Repeat the same five-tap gesture to hide it
again. The Gemini API key is stored encrypted with Android Keystore.

## Safety Limits

- ZIP path traversal is rejected.
- Max entries: `2048`.
- Max extracted size: `200 MB`.
- Script timeout: `120 seconds`.
- Output retained: last `64 KB` per stdout/stderr stream.

Timeout exit code is `124`.

## Logs

Logs are written inside the installed module directory:

```text
logs/action-last.log
logs/service-last.log
```

Each log includes module id, script name, exit code, access mode, stdout, and stderr.

Use it to verify install, enable/disable, Action, Service, WebUI, and banner rendering.
