# Shizuku ADB Modules API

ADB Modules are ZIP packages installed into Shizuku private app storage and executed through the currently active Shizuku server. If Shizuku is running from ADB, module scripts run with ADB shell privileges. If Shizuku is running from root, scripts run with root privileges.

This is not a root overlay system. It is a Shizuku-backed module runner for actions, WebUI, service hooks, and controlled ADB/root shell access.

## Package Format

A module is a `.zip` file with `module.prop` at the ZIP root.

```text
module.zip
├── module.prop
├── banner.png
├── action.sh
├── service.sh
└── webui/
    └── index.html
```

All paths must be relative. Absolute paths and `..` traversal are rejected during install.

## module.prop

Required fields:

```properties
id=my-module
name=My Module
version=1.0
versionCode=1
author=Author
description=Short description
```

Optional fields:

```properties
banner=banner.png
webui=webui
action=action.sh
```

Rules:

- `id` must match `[A-Za-z][A-Za-z0-9._-]{1,63}`.
- `banner` can point to `.png`, `.jpg`, `.jpeg`, or `.webp`.
- If `banner` is omitted, Shizuku checks `banner.png`, `banner.jpg`, `banner.jpeg`, then `banner.webp`.
- If `webui` is omitted, Shizuku checks `webroot`, `webui`, then `web`.
- WebUI is available only when `<webui>/index.html` exists.
- `action` defaults to `action.sh`.
- `service.sh` is detected automatically.

## Install Behavior

Install flow:

1. User selects a module ZIP with Android file picker.
2. Shizuku copies it into cache.
3. Shizuku validates `module.prop`.
4. Shizuku extracts into a staging directory.
5. Shizuku rejects unsafe paths.
6. Shizuku marks `.sh` files executable.
7. Shizuku replaces any existing module with the same `id`.
8. Shizuku stores the module under app-private storage.

Safety limits:

- Max ZIP entries: `2048`.
- Max extracted size: `200 MB`.
- Script output retained in memory/log: last `64 KB` per stream.
- Script timeout: `120 seconds`.

## Runtime Environment

Scripts run through Shizuku server process creation. The command is:

```sh
sh /path/to/module/action.sh
```

or:

```sh
sh /path/to/module/service.sh
```

Working directory is the module directory.

Environment variables:

```sh
MODDIR=/data/user/0/<package>/files/adb_modules/<id>
ASH_STANDALONE=1
SHIZUKU_MODULE_ID=<id>
SHIZUKU_MODULE_MODE=safe|full
SHIZUKU_MODULE_BACKGROUND=0|1
```

Use `MODDIR` for all module-local files. Do not assume root paths such as `/data/adb/modules`.

## Actions

`action.sh` is a manual user action. It can be launched from the module card when the module is enabled.

Action result:

- stdout/stderr are shown in a dialog.
- Last output is written to `logs/action-last.log` inside the module directory.
- Timeout returns exit code `124`.

Minimal `action.sh`:

```sh
#!/system/bin/sh
echo "module=$SHIZUKU_MODULE_ID"
echo "mode=$SHIZUKU_MODULE_MODE"
id
cmd package list packages | head
```

## Services

`service.sh` is the background/service hook.

Execution policy:

- `Safe` mode: blocked.
- `Full access` mode: allowed only when `Allow background actions` is enabled.
- Disabled modules are skipped.
- Service scripts auto-run once per Shizuku binder session when Shizuku becomes available from the manager.
- Last output is written to `logs/service-last.log`.
- Timeout returns exit code `124`.

Minimal `service.sh`:

```sh
#!/system/bin/sh
echo "service for $SHIZUKU_MODULE_ID"
date
```

This is intentionally controlled. Modules do not get an always-on daemon by default.

## WebUI

WebUI is loaded from the installed module directory:

```text
webui/index.html
```

Current WebView policy:

- JavaScript: enabled.
- DOM storage: enabled.
- File access: enabled for module-local files.
- HTTPS network loads: blocked unless Custom mode explicitly enables WebView internet.
- Universal file access from file URLs: disabled.
- Mixed content: blocked.
- Content access: disabled.
- Third-party cookies: disabled.
- `window.Shizuku` is exposed only for enabled module-local WebUI when the access policy allows WebUI bridge and WebView internet is off.
- Full Trust modules can use WebView internet and `window.Shizuku` together.

WebUI should treat module files as local UI assets. Remote dependencies are blocked by default.

Example using a pinned BeerCSS package from jsDelivr:

```html
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/beercss@4.0.21/dist/cdn/beer.min.css">
<script type="module" src="https://cdn.jsdelivr.net/npm/beercss@4.0.21/dist/cdn/beer.min.js"></script>
```

### JavaScript-to-Shell Bridge

The `window.Shizuku` object can be exposed to module-local offline WebUI so pages can read module info. Shell methods are allowed only when:

- the module is enabled;
- `module.prop` declares `usesShellBridge=true`;
- access mode is **Full access**, or **Custom** with WebUI shell bridge enabled;
- WebView internet is off.

Full Trust modules bypass the `usesShellBridge=true`, WebUI bridge, WebView internet, ReCommand, Action, Service, background, and WebUI download policy gates. Path traversal, process timeout, output caps, and download size caps still apply.

#### Module Info

You can retrieve the current module's metadata, paths, and settings:

```javascript
const info = JSON.parse(window.Shizuku.getModuleInfo());
console.log(info.id);         // e.g. "my-module"
console.log(info.enabled);    // true
console.log(info.accessMode); // "full"
console.log(info.trusted);    // true for Full Trust modules
console.log(info.moduleDir);  // absolute path to module storage
```

#### Shell Execution

```javascript
// Execute a shell command through Shizuku
const resultJson = window.Shizuku.exec("id");
const result = JSON.parse(resultJson);

console.log(result.ok);       // true when exitCode === 0 and no timeout
console.log(result.exitCode); // e.g. 0
console.log(result.stdout);   // e.g. "uid=2000(shell) gid=2000(shell)..."
console.log(result.stderr);   // e.g. ""
console.log(result.timedOut); // false
```

Advanced execution with timeout, stdin, cwd, and extra environment variables:

```javascript
const result = JSON.parse(window.Shizuku.execWithOptions("cat && pwd && echo $FOO", JSON.stringify({
  timeoutSeconds: 30,
  stdin: "hello\n",
  cwd: "webui",
  env: {
    FOO: "bar"
  }
})));
```

Rules:

- `exec()` timeout defaults to 120 seconds.
- `execWithOptions().timeoutSeconds` is clamped to `1..600` seconds.
- `stdin` is limited to 64 KB.
- stdout/stderr return only the last 64 KB per stream, but the process streams are still drained to avoid deadlocks.
- `cwd` must stay inside the module directory.
- extra env keys must match `[A-Za-z_][A-Za-z0-9_]*`.

If the module is disabled, missing `usesShellBridge=true`, or blocked by Safe/Custom policy, shell calls return JSON with `ok: false`, `exitCode: -1`, and the error text in `stderr`. Full Trust modules do not require `usesShellBridge=true`.

### Full Trust

Full Trust is a per-module override for modules the user explicitly trusts. In the modules screen, long-press a module card to reveal **Trust**. Long-press again to hide the action. After trusting, the module gets a Full Trust chip. Long-press the trusted module to reveal **Untrust**.

Trusted modules:

- can run Action and Service regardless of the global Safe/Custom/Full mode;
- can run background Service without the global background toggle;
- can expose `window.Shizuku` without `usesShellBridge=true`;
- can use WebView internet while the bridge is exposed;
- skip ReCommand prompts;
- can use `download()` even when global WebUI download is blocked;
- can overwrite WebUI entry files through `download()`.

### ReCommand

When ReCommand is enabled, WebUI shell execution and Action execution show a confirmation dialog before the command runs. The dialog shows the command, supports icon-only expand/collapse and copy controls, and has one close action plus one execute action. Expand/collapse is shown only when the command is longer than the preview.

The optional AI checker is fully integrated into the manager:
- Gemini console explanation is available in the Comput screen.
- The API key is securely encrypted using Android Keystore.
- Configurable settings are directly accessible under Comput Console settings.

### WebUI Internet File Loader

`window.Shizuku.download(url, relativeWebPath)` downloads an HTTPS URL into the module WebUI directory. This is meant for optional runtime caching of CSS, JS, fonts, and other WebUI assets.

```javascript
const result = JSON.parse(window.Shizuku.download(
  "https://cdn.jsdelivr.net/npm/beercss@4.0.21/dist/cdn/beer.min.css",
  "vendor/beer.min.css"
));

if (result.ok) {
  console.log(`saved ${result.bytes} bytes`);
} else {
  console.error(result.error);
}
```

Rules:

- URL must be `https://`.
- destination path is relative to the module WebUI root.
- `..`, absolute paths, and path traversal are rejected.
- `index.html` and nested `*/index.html` cannot be overwritten.
- Safe mode blocks `download()`.
- max file size is 20 MB.
- redirects are followed only if they stay on HTTPS.

## Enable, Disable, Delete

Disable creates:

```text
disable
```

inside the module directory.

Effects:

- `action.sh` is blocked.
- `service.sh` is skipped.
- UI dims the card/banner.

Delete removes the whole module directory.

## Test Module

The repository includes a test module:

```text
test-modules/adb-test-module.zip
```

It contains:

- `module.prop`
- `banner.png`
- `action.sh`
- `service.sh`
- `webui/index.html`

Expected action output includes the current UID, SDK version, module id, and module mode.

## Current Scope

Implemented:

- ZIP install.
- Module metadata parsing.
- Path traversal protection.
- Size and entry limits.
- Enable/disable/delete.
- Banner rendering.
- WebUI rendering.
- HTTPS WebUI asset loading.
- WebUI HTTPS file download into the module WebUI root.
- Manual `action.sh`.
- Policy-gated `service.sh`.
- One service run per Shizuku binder session.
- Last action/service logs.
- Direct JavaScript-to-shell bridge with optional timeout/stdin/cwd/env.

Not implemented:

- Systemless filesystem overlays.
- Magisk/KSU mount semantics.
- Long-running service supervision.

Those are separate features and should not be implied by the current ADB module API.
