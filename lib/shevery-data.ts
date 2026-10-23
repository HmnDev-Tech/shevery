export interface ChangeCard {
  title: string
  body: string
  commits: { label: string; url: string }[]
  span: string
  variant: "primary" | "tertiary" | "surface"
}

export interface CaveatCard {
  title: string
  body: string
  emphasized?: boolean
}

export interface DocLinkItem {
  label: string
  href: string
}

export const PROJECT_FACTS = [
  {
    term: "compileSdk / targetSdk",
    value: "37",
    detail: "Android 17 (buildToolsVersion 37.0.0 in root build.gradle).",
  },
  {
    term: "minSdk",
    value: "24",
    detail: "Android 7.0. Stealth generator requires API 29+; eBPF firewall requires API 30+.",
  },
  {
    term: "Flavors & stubs",
    value: "2+2",
    detail: "standard & stealth flavors, plus embedded shevery-stub.apk and shevery-dhizuku-stub.apk.",
  },
  {
    term: "current release",
    value: "14.5.0",
    detail: "versionCode 45, published 2026-10-23. Built from api_version_name 14.5.0.",
  },
]

export const RECENT_CHANGES: ChangeCard[] = [
  {
    title: "14.5: per-app Shizuku privileges, About drill-in screens, agent chat history",
    body: "Tapping an app in Application Management opens a detail screen with a Shizuku Privileges group — access switch, session-only grant and one switch per dangerous permission granted or revoked through Shizuku. Settings -> About now drills into a Contributors and Credits screen and a Recommendations screen, and the Comput agent persists chat history so a previous conversation can be continued. Server version bumped to 14.5 (versionCode 45).",
    commits: [
      { label: "feb7ebaa", url: "https://github.com/HmnDev-Tech/shevery/commit/feb7ebaa" },
      { label: "953fa7f1", url: "https://github.com/HmnDev-Tech/shevery/commit/953fa7f1" },
      { label: "4f84b3a7", url: "https://github.com/HmnDev-Tech/shevery/commit/4f84b3a7" },
    ],
    span: "lg:col-span-12",
    variant: "primary",
  },
  {
    title: "Watchdog checks the binder before acting on a stale heartbeat",
    body: "WatchdogManager.kt previously treated a 60 s overdue timestamp as a dead server even while the binder was still responsive. Commit ecd465a (PR #279) calls Shizuku.pingBinder() first: if the binder answers, it refreshes beat() and returns instead of restarting a healthy server on a fixed schedule.",
    commits: [
      { label: "ecd465a", url: "https://github.com/HmnDev-Tech/shevery/commit/ecd465a" },
      { label: "PR #279", url: "https://github.com/HmnDev-Tech/shevery/pull/279" },
    ],
    span: "lg:col-span-7",
    variant: "primary",
  },
  {
    title: "On-device Stealth APK rewriter & stub manifest fix",
    body: "StealthApkGenerator.kt embeds assets/shevery-stealth.apk compiled with com.stealthp.lacehold (21 chars) and swaps it in-place across AndroidManifest.xml, resources.arsc, and classes*.dex in both US-ASCII and UTF-16LE. PR #268 fixed the Shizuku compatibility stub crash by using fully qualified component class names.",
    commits: [
      { label: "PR #268", url: "https://github.com/HmnDev-Tech/shevery/pull/268" },
      { label: "2b6b6f7", url: "https://github.com/HmnDev-Tech/shevery/commit/2b6b6f7" },
    ],
    span: "lg:col-span-5",
    variant: "tertiary",
  },
  {
    title: "Comput AI Agent & Commandium",
    body: "ComputAgent.kt plans up to MAX_STEPS_HARD = 10 steps of SHELL (IShizukuService.newProcess) or MODULE_SERVICE actions against 7 OpenAI-compatible provider presets plus custom URLs, gated by secure, turbo, or custom approval modes.",
    commits: [
      { label: "078a1e3", url: "https://github.com/HmnDev-Tech/shevery/commit/078a1e3" },
    ],
    span: "lg:col-span-4",
    variant: "surface",
  },
  {
    title: "Device Owner, 5 DPM scopes & Dhizuku v1/v2",
    body: "DeviceOwnerManager.kt and DhizukuProvider.kt implement 1-tap DPM activation through Shizuku, five per-app delegation scopes, ownership transfer via DevicePolicyManager.transferOwnership(), and a 5-second countdown before deactivation.",
    commits: [
      { label: "PR #270", url: "https://github.com/HmnDev-Tech/shevery/pull/270" },
    ],
    span: "lg:col-span-4",
    variant: "surface",
  },
  {
    title: "Foreground USB debugging hiding & Chain3 firewall",
    body: "PR #269 added UsbDebuggingHideService.kt (polls top package every 1 s via PACKAGE_USAGE_STATS and sets ADB_ENABLED = 0 while a target app is visible) and NetworkFirewallManager.kt (toggles Android 11+ eBPF connectivity chain3 per package without a VPN service).",
    commits: [
      { label: "PR #269", url: "https://github.com/HmnDev-Tech/shevery/pull/269" },
    ],
    span: "lg:col-span-4",
    variant: "surface",
  },
  {
    title: "Android 16+ resource tables & aapt2 collapse guard",
    body: "PR #267 keeps resources.arsc uncompressed (noCompress += 'arsc') and skips collapseFlavoredReleaseResourceNames if the intermediate .ap_ path differs across AGP versions, keeping release APKs installable on Android 16 and 17.",
    commits: [
      { label: "PR #267", url: "https://github.com/HmnDev-Tech/shevery/pull/267" },
    ],
    span: "lg:col-span-6",
    variant: "surface",
  },
  {
    title: "Server-side Watchdog & monitor notification shield",
    body: "rikka.shizuku.server.Watchdog posts a 5 s heartbeat on the server main looper, checks freshness every 60 s, and holds linkToDeath on package, activity, user, and appops. Commit 1d9154b (PR #277) replaced the monitor notification icon with a dedicated shield drawable.",
    commits: [
      { label: "1d9154b", url: "https://github.com/HmnDev-Tech/shevery/commit/1d9154b" },
      { label: "PR #277", url: "https://github.com/HmnDev-Tech/shevery/pull/277" },
    ],
    span: "lg:col-span-6",
    variant: "surface",
  },
]

export const LIMITS_AND_CAVEATS: CaveatCard[] = [
  {
    title: "Conflicts with official Shizuku (and with its own Stealth copy)",
    body: "com.hamondev.shevery and every generated Stealth APK declare the moe.shizuku.privileged.api ContentProvider authority. Android blocks side-by-side installation with INSTALL_FAILED_CONFLICTING_PROVIDER. Uninstall Shizuku before installing Shevery, and uninstall standard Shevery before installing a Stealth APK.",
    emphasized: true,
  },
  {
    title: "Android17Compat reflection swaps String args and passes \"default\"",
    body: "In Android17Compat.java (lines 121 and 253), when PermissionManagerApis.checkPermission falls back to reflection, invokeDynamic passes (packageName, permissionName, \"default\", userId). On Android 15+ IPermissionManager expects (permissionName, packageName, \"default:0\", userId).",
    emphasized: true,
  },
  {
    title: "Stealth APK drops v2/v3 signing blocks for v1 JAR signing",
    body: "StealthApkGenerator.kt strips existing META-INF signature files during the in-place byte swap and signs CERT.RSA with a freshly generated 2048-bit RSA keypair using v1 JAR signing. Stale APK Signature Scheme v2/v3 blocks are dropped on rebuild.",
  },
  {
    title: "Comput Agent is a 10-step shell & module planner, not a UI tapper",
    body: "ComputAgent.kt plans up to MAX_STEPS_HARD = 10 steps of SHELL (toybox commands via IShizukuService.newProcess) or MODULE_SERVICE (AdbModuleManager). It does not inspect accessibility trees or inject touch gestures.",
  },
  {
    title: "GitHub Catalog token is stored in plain SharedPreferences",
    body: "TokenStore.kt saves the ghp_ / github_pat_ personal access token unencrypted (documented in docs/github-catalog.md as an emulator compatibility trade-off). Any root process or app backup can read it.",
  },
  {
    title: "ADB modules are script runners, not systemless overlays",
    body: "Modules extract to /data/user/0/<pkg>/files/adb_modules/<id>. There are no mount hooks, no Magisk or KernelSU /data/adb/modules compatibility, and each script execution times out after 120 seconds (exit code 124).",
  },
  {
    title: "Connectors expose the server startup command when enabled",
    body: "Off by default. Turning on shizuku_connector_enabled in Settings -> Automation exposes content://moe.shizuku.privileged.api.connector so local privilege-escalation activators can query the raw server start command.",
  },
  {
    title: "Internal Java/Kotlin packages remain upstream",
    body: "Only the applicationId (com.hamondev.shevery) and receiver action prefixes changed. Source packages remain moe.shizuku.manager.* and rikka.shizuku.server.*, and the native starter is still built with CMake + BoringSSL + libcxx.",
  },
]

export const BUILD_METADATA = [
  { term: "AGP", value: "9.4.0-alpha07" },
  { term: "Gradle", value: "9.7.0-rc-2" },
  { term: "Kotlin", value: "2.4.20-Beta2" },
  { term: "Compose BOM", value: "2026.06.01" },
  { term: "material3", value: "1.5.0-alpha25" },
  { term: "NDK", value: "29.0.14206865" },
  { term: "C++ / Crypto", value: "CMake 3.22.1 · BoringSSL" },
  { term: "JDK target", value: "21" },
]

export const DOC_LINKS: DocLinkItem[] = [
  {
    label: "Comput AI Agent guide",
    href: "https://github.com/HmnDev-Tech/shevery/blob/main/docs/comput-agent.md",
  },
  {
    label: "Device Owner & Dhizuku bridge",
    href: "https://github.com/HmnDev-Tech/shevery/blob/main/docs/device-owner.md",
  },
  {
    label: "Stealth Edition & Compatibility Stubs",
    href: "https://github.com/HmnDev-Tech/shevery/blob/main/docs/stealth-and-stubs.md",
  },
  {
    label: "ADB Modules guide",
    href: "https://github.com/HmnDev-Tech/shevery/blob/main/docs/adb-modules-guide.md",
  },
  {
    label: "ADB Modules API",
    href: "https://github.com/HmnDev-Tech/shevery/blob/main/docs/adb-modules-api.md",
  },
  {
    label: "GitHub Catalog (topic:shevery-modules)",
    href: "https://github.com/HmnDev-Tech/shevery/blob/main/docs/github-catalog.md",
  },
  {
    label: "Accessibility, USB Hide & Chain3 Firewall",
    href: "https://github.com/HmnDev-Tech/shevery/blob/main/docs/lab-and-extras.md",
  },
  {
    label: "Tasker & MacroDroid automation",
    href: "https://github.com/HmnDev-Tech/shevery/blob/main/docs/tasker-plugin.md",
  },
  {
    label: "Public & internal Intents",
    href: "https://github.com/HmnDev-Tech/shevery/blob/main/docs/intents.md",
  },
  {
    label: "Shizuku Connectors API",
    href: "https://github.com/HmnDev-Tech/shevery/blob/main/docs/shizuku-connectors.md",
  },
  {
    label: "Android 17 compatibility doc",
    href: "https://github.com/HmnDev-Tech/shevery/blob/main/docs/android-17-compatibility.md",
  },
]
