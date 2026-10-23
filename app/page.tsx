import React from "react"
import { ArrowDownToLine, ArrowUpRight, FlaskConical, TriangleAlert } from "lucide-react"
import {
  BUILD_METADATA,
  DOC_LINKS,
  LIMITS_AND_CAVEATS,
  PROJECT_FACTS,
  RECENT_CHANGES,
} from "@/lib/shevery-data"
import {
  HeroBinderTopology,
  M3_POLYGON_12_LOBE,
  M3_POLYGON_4_PINCH,
  M3_POLYGON_8_COG,
  M3_POLYGON_8_FLOWER,
  M3_POLYGON_HOURGLASS,
  SectionBadge,
  StealthAndCompatInspector,
  WatchdogBackoffSimulator,
} from "@/components/shevery-interactive"

export default function SheveryHomePage() {
  return (
    <>
      {/* Sticky Top Header (No Logo, Mobile-First Wrapping) */}
      <header className="sticky top-0 z-50 border-b border-outline-variant/60 bg-surface/85 backdrop-blur-md">
        <div className="mx-auto flex h-16 max-w-7xl items-center gap-4 px-4 sm:px-6">
          <a
            href="#top"
            className="flex items-center gap-2 rounded-full pr-2 text-lg font-semibold tracking-tight text-on-surface"
          >
            <span>Shevery</span>
          </a>

          <nav aria-label="Sections" className="ml-auto hidden lg:block">
            <ul className="flex flex-wrap items-center gap-1">
              {[
                { href: "#changes", label: "Changes" },
                { href: "#stealth", label: "Stealth & API 37" },
                { href: "#watchdog", label: "Watchdog" },
                { href: "#comput", label: "Comput AI" },
                { href: "#modules", label: "Modules" },
                { href: "#dpm-lab", label: "DPM & Lab" },
                { href: "#limits", label: "Limits" },
                { href: "#build", label: "Build" },
              ].map((item) => (
                <li key={item.href}>
                  <a
                    href={item.href}
                    className="inline-flex h-10 items-center rounded-full px-4 text-sm font-medium text-on-surface-variant transition-colors hover:bg-surface-2 hover:text-on-surface"
                  >
                    {item.label}
                  </a>
                </li>
              ))}
            </ul>
          </nav>

          {/* Pill radius is half the height (20px on h-10), not rounded-full:
              calc(infinity * 1px) can't interpolate, so the press morph snapped
              instead of animating. Pressed shape: 8px per M3 Expressive (small). */}
          <a
            href="https://github.com/HmnDev-Tech/shevery"
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex shrink-0 items-center justify-center gap-2 font-medium whitespace-nowrap select-none rounded-[20px] transition-[border-radius,background-color,transform] duration-300 ease-spring active:rounded-sm active:scale-[0.97] bg-primary-container text-on-primary-container hover:bg-[oklch(0.41_0.1_275)] h-10 px-4 text-sm ml-auto lg:ml-2"
          >
            <span>GitHub</span>
            <ArrowUpRight className="size-4 shrink-0" aria-hidden="true" />
          </a>
        </div>
      </header>

      <main>
        {/* HERO SECTION */}
        <section
          id="top"
          aria-labelledby="hero-title"
          className="grain overflow-hidden"
          style={{
            background:
              "radial-gradient(60% 70% at 72% 30%, oklch(0.3 0.09 275 / 0.55), transparent 70%), radial-gradient(40% 50% at 92% 85%, oklch(0.32 0.1 325 / 0.35), transparent 70%)",
          }}
        >
          <div className="mx-auto grid max-w-7xl grid-cols-1 gap-12 px-4 pt-12 pb-16 sm:px-6 lg:grid-cols-12 lg:gap-8 lg:pt-20 lg:pb-24">
            <div className="flex min-w-0 flex-col gap-8 lg:col-span-7 lg:justify-center">
              <p className="inline-flex w-fit flex-wrap items-center gap-x-2 gap-y-1 rounded-full bg-surface-2 px-4 py-2 text-sm text-on-surface-variant">
                <span>
                  Fork of{" "}
                  <a
                    href="https://github.com/RikkaApps/Shizuku"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="font-medium text-on-surface underline decoration-outline underline-offset-4"
                  >
                    Shizuku
                  </a>
                </span>
                <span aria-hidden="true">·</span>
                <span>by HmnDev-Tech</span>
                <span aria-hidden="true">·</span>
                <span>Apache-2.0</span>
              </p>

              <h1
                id="hero-title"
                className="text-6xl leading-[0.92] font-bold tracking-tight text-balance sm:text-7xl lg:text-8xl"
              >
                Shevery
              </h1>

              <p className="max-w-[60ch] text-lg leading-relaxed text-on-surface-variant text-pretty sm:text-xl">
                Same mechanism as Shizuku: you start{" "}
                <code className="font-mono text-on-surface">shizuku_server</code> with root or ADB,
                and authorized apps receive a binder to call system APIs with its privileges. On top
                of that, this fork targets SDK 37, ships a Compose manager, integrates a 10-step
                Comput AI shell planner, acts as a Device Owner &amp; Dhizuku v1/v2 provider, runs
                ADB modules, and can rewrite its own package ID on the device.
              </p>

              {/* Press morph (M3 Expressive): pill rest radius = half of h-16 (32px),
                  pressed = 16px (extra-large size) — both finite so border-radius animates. */}
              <div className="flex flex-wrap gap-3">
                <a
                  href="https://github.com/HmnDev-Tech/shevery/releases"
                  className="inline-flex shrink-0 items-center justify-center gap-2 font-medium whitespace-nowrap select-none rounded-[32px] transition-[border-radius,background-color,transform] duration-300 ease-spring active:rounded-lg active:scale-[0.97] bg-m3-primary text-on-primary hover:bg-[oklch(0.87_0.075_275)] h-16 px-8 text-lg [&_svg]:size-6 [&_svg]:shrink-0"
                >
                  <ArrowDownToLine aria-hidden="true" />
                  <span>Download latest</span>
                </a>

                <a
                  href="https://github.com/HmnDev-Tech/shevery/actions/workflows/apk-build.yml"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex shrink-0 items-center justify-center gap-2 font-medium whitespace-nowrap select-none rounded-[32px] transition-[border-radius,background-color,transform] duration-300 ease-spring active:rounded-lg active:scale-[0.97] bg-primary-container text-on-primary-container hover:bg-[oklch(0.41_0.1_275)] h-16 px-8 text-lg [&_svg]:size-6 [&_svg]:shrink-0"
                >
                  <FlaskConical aria-hidden="true" />
                  <span>CI builds</span>
                </a>

                <a
                  href="https://github.com/HmnDev-Tech/shevery"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex shrink-0 items-center justify-center gap-2 font-medium whitespace-nowrap select-none rounded-[32px] transition-[border-radius,background-color,transform] duration-300 ease-spring active:rounded-lg active:scale-[0.97] border border-outline text-on-surface hover:bg-surface-2 h-16 px-8 text-lg"
                >
                  <span>Source</span>
                </a>
              </div>

              <div
                role="note"
                className="flex max-w-[60ch] gap-4 rounded-xl bg-error-container p-5 text-on-error-container"
              >
                <TriangleAlert className="mt-0.5 size-6 shrink-0" aria-hidden="true" />
                <p className="leading-relaxed">
                  Package id is{" "}
                  <code className="font-mono text-[0.95em] break-all">com.hamondev.shevery</code>{" "}
                  (or a random{" "}
                  <code className="font-mono text-[0.95em] break-all">
                    com.&lt;8chars&gt;.&lt;8chars&gt;
                  </code>{" "}
                  in Stealth builds). Uninstall the official Shizuku manager before installing: both
                  declare the same <code className="font-mono text-[0.95em]">moe.shizuku.privileged.api</code>{" "}
                  provider authorities and conflict.
                </p>
              </div>
            </div>

            <HeroBinderTopology />
          </div>
        </section>

        {/* PROJECT FACTS STRIP */}
        <section
          aria-label="Project facts"
          className="border-y border-outline-variant/60 bg-surface-1"
        >
          <dl className="mx-auto grid max-w-7xl grid-cols-1 gap-px bg-outline-variant/40 sm:grid-cols-2 lg:grid-cols-4">
            {PROJECT_FACTS.map((fact) => (
              <div key={fact.term} className="flex flex-col gap-2 bg-surface-1 px-6 py-8">
                <dt className="text-sm font-medium tracking-wide text-on-surface-variant uppercase">
                  {fact.term}
                </dt>
                <dd className="text-5xl font-bold tracking-tight text-m3-primary tabular-nums">
                  {fact.value}
                </dd>
                <dd className="text-sm leading-relaxed text-on-surface-variant">{fact.detail}</dd>
              </div>
            ))}
          </dl>
        </section>

        {/* SECTION 01: WHAT CHANGED IN 14.5 */}
        <section id="changes" aria-labelledby="changes-title" className="py-16 lg:py-24">
          <div className="mx-auto max-w-7xl px-4 sm:px-6">
            <header className="mb-10 grid grid-cols-1 gap-6 lg:mb-14 lg:grid-cols-12 lg:gap-8">
              <div className="flex items-center gap-4 lg:col-span-12">
                <SectionBadge number="01" polygon={M3_POLYGON_8_FLOWER} />
                <h2
                  id="changes-title"
                  className="text-3xl leading-tight font-bold tracking-tight text-balance sm:text-5xl"
                >
                  What changed in 14.5
                </h2>
              </div>
              <div className="max-w-[68ch] text-lg leading-relaxed text-on-surface-variant text-pretty lg:col-span-8">
                <p>
                  Shevery 14.5.0 (<code className="font-mono text-on-surface">versionCode 45</code>)
                  was published on 23 October 2026. Summarised directly from the repository commits
                  and pull requests; each card links to the source diff.
                </p>
              </div>
            </header>

            <ul className="grid grid-cols-1 gap-4 lg:grid-cols-12">
              {RECENT_CHANGES.map((card) => {
                const variantClasses =
                  card.variant === "primary"
                    ? "bg-primary-container text-on-primary-container rounded-2xl"
                    : card.variant === "tertiary"
                      ? "bg-tertiary-container text-on-tertiary-container rounded-2xl"
                      : "bg-surface-2 text-on-surface rounded-xl"
                const bodyColor =
                  card.variant === "surface" ? "text-on-surface-variant" : ""

                return (
                  <li
                    key={card.title}
                    className={`flex flex-col gap-4 p-6 sm:p-8 ${variantClasses} ${card.span}`}
                  >
                    <h3 className="text-2xl leading-snug font-semibold text-balance">
                      {card.title}
                    </h3>
                    <p className={`leading-relaxed text-pretty ${bodyColor}`}>{card.body}</p>
                    <p className="mt-auto flex flex-wrap gap-2 pt-2">
                      {card.commits.map((c) => (
                        <a
                          key={c.label}
                          href={c.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="inline-flex h-8 items-center rounded-full border border-current/30 px-3 font-mono text-sm transition-colors hover:bg-current/10"
                        >
                          <span className="sr-only">Reference </span>
                          {c.label}
                        </a>
                      ))}
                    </p>
                  </li>
                )
              })}
            </ul>
          </div>
        </section>

        {/* SECTION 02: STEALTH EDITION, STUBS & ANDROID 17 COMPAT */}
        <section
          id="stealth"
          aria-labelledby="stealth-title"
          className="py-16 lg:py-24 bg-surface-1"
        >
          <div className="mx-auto max-w-7xl px-4 sm:px-6">
            <header className="mb-10 grid grid-cols-1 gap-6 lg:mb-14 lg:grid-cols-12 lg:gap-8">
              <div className="flex items-center gap-4 lg:col-span-12">
                <SectionBadge number="02" polygon={M3_POLYGON_4_PINCH} />
                <h2
                  id="stealth-title"
                  className="text-3xl leading-tight font-bold tracking-tight text-balance sm:text-5xl"
                >
                  Stealth byte-swap, Stubs &amp; Android17Compat
                </h2>
              </div>
              <div className="max-w-[68ch] text-lg leading-relaxed text-on-surface-variant text-pretty lg:col-span-8">
                <p>
                  Standard builds embed{" "}
                  <code className="font-mono text-on-surface">assets/shevery-stealth.apk</code>,{" "}
                  <code className="font-mono text-on-surface">shevery-stub.apk</code>, and{" "}
                  <code className="font-mono text-on-surface">shevery-dhizuku-stub.apk</code>. Pick
                  a mechanism below to inspect how the code actually works—including an unfixed
                  reflection bug in <code className="font-mono text-on-surface">Android17Compat.java</code>.
                </p>
              </div>
            </header>

            <StealthAndCompatInspector />

            <div className="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-2">
              <div className="rounded-xl bg-surface-2 p-6">
                <h3 className="mb-2 text-lg font-semibold">
                  Why Stealth is a replacement, not a second app
                </h3>
                <p className="leading-relaxed text-on-surface-variant">
                  Both flavors in <code className="font-mono text-on-surface">manager/build.gradle</code>{" "}
                  keep the same Shizuku <code className="font-mono text-on-surface">ContentProvider</code>{" "}
                  authorities so client apps still receive privileged binders. Because Android
                  forbids duplicate provider authorities on one device, you must uninstall normal
                  Shevery before installing the generated APK from{" "}
                  <code className="font-mono text-on-surface">Downloads/</code>.
                </p>
              </div>

              <div className="rounded-xl border border-outline p-6">
                <h3 className="mb-2 text-lg font-semibold">
                  What <code className="font-mono">Android17Compat.java</code> actually does
                </h3>
                <p className="leading-relaxed text-on-surface-variant">
                  When <code className="font-mono text-on-surface">PermissionManagerApis</code>{" "}
                  throws or returns denied, <code className="font-mono text-on-surface">Android17Compat</code>{" "}
                  reflects over <code className="font-mono text-on-surface">IPermissionManager$Stub</code>{" "}
                  and <code className="font-mono text-on-surface">IPackageManager$Stub</code>. Its{" "}
                  <code className="font-mono text-on-surface">invokeDynamic</code> helper guesses
                  extra parameters by count (<code className="font-mono text-on-surface">&quot;default&quot;</code>,{" "}
                  <code className="font-mono text-on-surface">0</code>,{" "}
                  <code className="font-mono text-on-surface">&quot;shizuku&quot;</code>), and{" "}
                  <strong className="font-semibold text-on-surface">
                    swaps the two String parameters in checkPermission
                  </strong>
                  .{" "}
                  <a
                    href="https://github.com/HmnDev-Tech/shevery/blob/main/server/src/main/java/rikka/shizuku/server/util/Android17Compat.java"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-m3-primary underline underline-offset-4"
                  >
                    Read Android17Compat.java
                  </a>
                  .
                </p>
              </div>
            </div>
          </div>
        </section>

        {/* SECTION 03: DUAL-LAYER WATCHDOG */}
        <section id="watchdog" aria-labelledby="watchdog-title" className="py-16 lg:py-24">
          <div className="mx-auto max-w-7xl px-4 sm:px-6">
            <header className="mb-10 grid grid-cols-1 gap-6 lg:mb-14 lg:grid-cols-12 lg:gap-8">
              <div className="flex items-center gap-4 lg:col-span-12">
                <SectionBadge number="03" polygon={M3_POLYGON_8_COG} />
                <h2
                  id="watchdog-title"
                  className="text-3xl leading-tight font-bold tracking-tight text-balance sm:text-5xl"
                >
                  Server &amp; manager Watchdog
                </h2>
              </div>
              <div className="max-w-[68ch] text-lg leading-relaxed text-on-surface-variant text-pretty lg:col-span-8">
                <p>
                  Shevery runs two separate watchdog loops ported from NightDog: one inside{" "}
                  <code className="font-mono text-on-surface">shizuku_server</code> (
                  <code className="font-mono text-on-surface">Watchdog.java</code>) and one in the
                  manager (<code className="font-mono text-on-surface">WatchdogManager.kt</code>).
                  The bars below are computed from the constants in{" "}
                  <code className="font-mono text-on-surface">Watchdog.java</code>: 2 s initial
                  delay, then 5 s doubling to a 60 s cap across 10 attempts.
                </p>
              </div>
            </header>

            <WatchdogBackoffSimulator />

            <ul className="mt-6 grid grid-cols-1 gap-4 md:grid-cols-3">
              <li className="rounded-xl bg-surface-1 p-6">
                <h3 className="mb-2 text-lg font-semibold">
                  Server main thread (5 s beat / 60 s timeout)
                </h3>
                <p className="leading-relaxed text-on-surface-variant">
                  Inside <code className="font-mono text-on-surface">Watchdog.java</code>,{" "}
                  <code className="font-mono text-on-surface">beatRunnable</code> posts to the
                  server main handler every <code className="font-mono text-on-surface">5_000 ms</code>.
                  A separate <code className="font-mono text-on-surface">watchdog</code>{" "}
                  <code className="font-mono text-on-surface">HandlerThread</code> checks every{" "}
                  <code className="font-mono text-on-surface">60_000 ms</code>; if overdue, it calls{" "}
                  <code className="font-mono text-on-surface">Process.killProcess(myPid())</code>.
                </p>
              </li>

              <li className="rounded-xl bg-surface-1 p-6">
                <h3 className="mb-2 text-lg font-semibold">
                  System service linkToDeath (4 services)
                </h3>
                <p className="leading-relaxed text-on-surface-variant">
                  <code className="font-mono text-on-surface">Watchdog.java</code> attaches{" "}
                  <code className="font-mono text-on-surface">linkToDeath</code> to{" "}
                  <code className="font-mono text-on-surface">package</code>,{" "}
                  <code className="font-mono text-on-surface">activity</code>,{" "}
                  <code className="font-mono text-on-surface">user</code>, and{" "}
                  <code className="font-mono text-on-surface">appops</code> (unlike Nightzuku,{" "}
                  <code className="font-mono text-on-surface">permissionmgr</code> is not linked
                  here), plus <code className="font-mono text-on-surface">pingBinder()</code> on
                  every 60 s poll.
                </p>
              </li>

              <li className="rounded-xl bg-surface-1 p-6">
                <h3 className="mb-2 text-lg font-semibold">
                  Manager WatchdogManager (PR #279 fix)
                </h3>
                <p className="leading-relaxed text-on-surface-variant">
                  Polls every <code className="font-mono text-on-surface">60_000 ms</code> with{" "}
                  <code className="font-mono text-on-surface">MIN_RESTART_INTERVAL_MS = 15_000L</code>.
                  Always verifies <code className="font-mono text-on-surface">Shizuku.pingBinder()</code>{" "}
                  before restarting via the last launch mode (<code className="font-mono text-on-surface">root</code>,{" "}
                  <code className="font-mono text-on-surface">adb</code>, or{" "}
                  <code className="font-mono text-on-surface">dhizuku</code>).
                </p>
              </li>
            </ul>
          </div>
        </section>

        {/* SECTION 04: COMPUT AI AGENT & COMMANDIUM */}
        <section
          id="comput"
          aria-labelledby="comput-title"
          className="py-16 lg:py-24 bg-surface-1"
        >
          <div className="mx-auto max-w-7xl px-4 sm:px-6">
            <header className="mb-10 grid grid-cols-1 gap-6 lg:mb-14 lg:grid-cols-12 lg:gap-8">
              <div className="flex items-center gap-4 lg:col-span-12">
                <SectionBadge number="04" polygon={M3_POLYGON_12_LOBE} />
                <h2
                  id="comput-title"
                  className="text-3xl leading-tight font-bold tracking-tight text-balance sm:text-5xl"
                >
                  Comput AI Agent &amp; Commandium
                </h2>
              </div>
              <div className="max-w-[68ch] text-lg leading-relaxed text-on-surface-variant text-pretty lg:col-span-8">
                <p>
                  Integrated into the <code className="font-mono text-on-surface">Comput</code>{" "}
                  terminal tab (<code className="font-mono text-on-surface">ComputScreen.kt</code>{" "}
                  and <code className="font-mono text-on-surface">ComputAgent.kt</code>). Requests a
                  structured JSON plan of up to{" "}
                  <code className="font-mono text-on-surface">MAX_STEPS_HARD = 10</code> steps from
                  an OpenAI-compatible chat completion endpoint and executes each step with
                  configurable approval gates.
                </p>
              </div>
            </header>

            <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
              <div className="flex min-w-0 flex-col gap-4 lg:col-span-7">
                <div className="overflow-hidden rounded-xl bg-surface-2">
                  <p className="border-b border-outline-variant/60 px-5 py-3 font-mono text-sm text-on-surface-variant">
                    ComputAgent.kt — JSON plan contract (MAX_STEPS_HARD = 10)
                  </p>
                  <pre className="overflow-x-auto p-5 font-mono text-sm leading-relaxed">
                    <code>{`{
  "steps": [
    {
      "type": "shell",
      "target": "cmd package list packages -d",
      "comment": "List currently disabled packages via toybox/cmd"
    },
    {
      "type": "module_service",
      "target": "my-module",
      "comment": "Run service.sh of an installed ADB module"
    }
  ]
}`}</code>
                  </pre>
                </div>

                <ul className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                  <li className="rounded-xl bg-primary-container p-5 text-on-primary-container">
                    <h3 className="mb-1 font-semibold">Secure (default)</h3>
                    <p className="text-sm leading-relaxed">
                      Prompts before every <code className="font-mono">SHELL</code> and{" "}
                      <code className="font-mono">MODULE_SERVICE</code> step: Allow, Edit &amp;
                      Allow (<code className="font-mono">overrideTarget</code>), Deny, or Allow
                      always.
                    </p>
                  </li>
                  <li className="rounded-xl bg-primary-container p-5 text-on-primary-container">
                    <h3 className="mb-1 font-semibold">Turbo</h3>
                    <p className="text-sm leading-relaxed">
                      Executes all planned steps sequentially without pausing for user confirmation,
                      then generates a localized summary.
                    </p>
                  </li>
                  <li className="rounded-xl bg-primary-container p-5 text-on-primary-container">
                    <h3 className="mb-1 font-semibold">Custom</h3>
                    <p className="text-sm leading-relaxed">
                      Caps plan length to <code className="font-mono">3</code>,{" "}
                      <code className="font-mono">5</code> (default), or{" "}
                      <code className="font-mono">10</code> steps and toggles confirmation
                      separately for shell vs. module services.
                    </p>
                  </li>
                </ul>
              </div>

              <div className="flex min-w-0 flex-col gap-4 lg:col-span-5">
                <div className="rounded-2xl bg-surface-2 p-6">
                  <h3 className="mb-2 text-lg font-semibold">
                    Built-in provider presets (<code className="font-mono text-base">AiProviderPresets.kt</code>)
                  </h3>
                  <p className="mb-4 leading-relaxed text-on-surface-variant">
                    Uses user-supplied API keys against OpenAI-compatible{" "}
                    <code className="font-mono text-on-surface">/chat/completions</code> endpoints.
                    Also powers one-shot command generation in{" "}
                    <code className="font-mono text-on-surface">CommandiumSheet.kt</code> and error
                    explanations in <code className="font-mono text-on-surface">AiExplainUtil.kt</code>.
                  </p>
                  <ul className="flex flex-wrap gap-2">
                    {[
                      "OpenRouter (default)",
                      "OpenAI",
                      "DeepSeek",
                      "Groq",
                      "Mistral",
                      "Google Gemini",
                      "xAI",
                      "Custom URL",
                    ].map((preset) => (
                      <li
                        key={preset}
                        className="rounded-md border border-outline-variant px-3 py-1.5 font-mono text-xs text-on-surface-variant"
                      >
                        {preset}
                      </li>
                    ))}
                  </ul>
                </div>

                <div className="rounded-2xl bg-surface-2 p-6">
                  <h3 className="mb-2 text-lg font-semibold">Execution boundaries</h3>
                  <p className="leading-relaxed text-on-surface-variant">
                    <code className="font-mono text-on-surface">SHELL</code> steps run via{" "}
                    <code className="font-mono text-on-surface">
                      IShizukuService.newProcess([&quot;sh&quot;, &quot;-c&quot;, cmd])
                    </code>
                    . The system prompt explicitly forbids desktop Linux binaries absent from
                    Android&apos;s <code className="font-mono text-on-surface">toybox</code> environment
                    (<code className="font-mono text-on-surface">apt</code>,{" "}
                    <code className="font-mono text-on-surface">systemctl</code>,{" "}
                    <code className="font-mono text-on-surface">iptables</code>) and restricts{" "}
                    <code className="font-mono text-on-surface">module_service</code> to installed
                    modules where <code className="font-mono text-on-surface">hasService == true</code>.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* SECTION 05: ADB MODULES & GITHUB CATALOG */}
        <section id="modules" aria-labelledby="modules-title" className="py-16 lg:py-24">
          <div className="mx-auto max-w-7xl px-4 sm:px-6">
            <header className="mb-10 grid grid-cols-1 gap-6 lg:mb-14 lg:grid-cols-12 lg:gap-8">
              <div className="flex items-center gap-4 lg:col-span-12">
                <SectionBadge number="05" polygon={M3_POLYGON_HOURGLASS} />
                <h2
                  id="modules-title"
                  className="text-3xl leading-tight font-bold tracking-tight text-balance sm:text-5xl"
                >
                  ADB modules &amp; catalog
                </h2>
              </div>
              <div className="max-w-[68ch] text-lg leading-relaxed text-on-surface-variant text-pretty lg:col-span-8">
                <p>
                  ZIP packages stored under{" "}
                  <code className="font-mono text-on-surface">
                    /data/user/0/&lt;package&gt;/files/adb_modules/&lt;id&gt;
                  </code>{" "}
                  and executed through the active server. Started via ADB, scripts get ADB shell
                  privileges; started via root, they get root. It is a script runner with a local
                  WebUI (<code className="font-mono text-on-surface">ModuleJsBridge.kt</code>), not
                  a Magisk or KernelSU overlay: no mount hooks and no{" "}
                  <code className="font-mono text-on-surface">/data/adb/modules</code> compatibility.
                </p>
              </div>
            </header>

            <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
              <div className="flex min-w-0 flex-col gap-4 lg:col-span-7">
                <div className="overflow-hidden rounded-xl bg-surface-2">
                  <p className="border-b border-outline-variant/60 px-5 py-3 font-mono text-sm text-on-surface-variant">
                    module.prop
                  </p>
                  <pre className="overflow-x-auto p-5 font-mono text-sm leading-relaxed">
                    <code>{`id=my-module
name=My Module
version=1.0
versionCode=1
author=Author
description=Short module description
usesShellBridge=true   # required for window.Shizuku in WebUI`}</code>
                  </pre>
                </div>

                <div className="overflow-hidden rounded-xl bg-surface-2">
                  <p className="border-b border-outline-variant/60 px-5 py-3 font-mono text-sm text-on-surface-variant">
                    action.sh
                  </p>
                  <pre className="overflow-x-auto p-5 font-mono text-sm leading-relaxed">
                    <code>{`#!/system/bin/sh
echo "module=$SHIZUKU_MODULE_ID"
echo "mode=$SHIZUKU_MODULE_MODE"   # safe | custom | full
echo "trusted=$SHIZUKU_MODULE_TRUSTED"
id`}</code>
                  </pre>
                </div>

                <ul className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                  <li className="rounded-xl bg-primary-container p-5 text-on-primary-container">
                    <h3 className="mb-1 font-semibold">Safe mode</h3>
                    <p className="text-sm leading-relaxed">Manual actions only.</p>
                  </li>
                  <li className="rounded-xl bg-primary-container p-5 text-on-primary-container">
                    <h3 className="mb-1 font-semibold">Full access</h3>
                    <p className="text-sm leading-relaxed">
                      Allows stronger module behaviour. Per-module Full Trust also exists.
                    </p>
                  </li>
                  <li className="rounded-xl bg-primary-container p-5 text-on-primary-container">
                    <h3 className="mb-1 font-semibold">Background actions</h3>
                    <p className="text-sm leading-relaxed">
                      Must be enabled before any <code className="font-mono">service.sh</code> runs.
                    </p>
                  </li>
                </ul>
              </div>

              <div className="flex min-w-0 flex-col gap-4 lg:col-span-5">
                <div className="rounded-2xl bg-surface-2 p-6">
                  <h3 className="mb-2 text-lg font-semibold">GitHub catalog</h3>
                  <p className="mb-4 leading-relaxed text-on-surface-variant">
                    Searches GitHub repositories tagged{" "}
                    <code className="font-mono text-on-surface">topic:shevery-modules</code> that
                    contain a valid <code className="font-mono text-on-surface">module.prop</code>.
                    Installs from repository sources (<code className="font-mono text-on-surface">SourceZipBuilder.kt</code>)
                    or from a release ZIP. Cached locally for 6 hours. Max 4 modules per author,{" "}
                    <code className="font-mono text-on-surface">HmnDev-Tech</code> exempt. Without a
                    token GitHub allows 60 requests per hour (5,000/hr with a PAT).
                  </p>
                  <p className="rounded-lg bg-error-container p-4 text-sm leading-relaxed text-on-error-container">
                    The GitHub token (<code className="font-mono">ghp_</code> /{" "}
                    <code className="font-mono">github_pat_</code>) is stored in plain{" "}
                    <code className="font-mono">SharedPreferences</code>, not encrypted.{" "}
                    <code className="font-mono">docs/github-catalog.md</code> calls it a trade-off
                    for emulator compatibility. Use a fine-grained token with no scopes beyond
                    public read.
                  </p>
                </div>

                <div className="rounded-2xl bg-surface-2 p-6">
                  <h3 className="mb-3 text-lg font-semibold">Install &amp; runtime limits</h3>
                  <ul className="flex flex-wrap gap-2">
                    <li className="rounded-md border border-outline-variant px-3 py-1.5 font-mono text-sm text-on-surface-variant">
                      2048 ZIP entries
                    </li>
                    <li className="rounded-md border border-outline-variant px-3 py-1.5 font-mono text-sm text-on-surface-variant">
                      200 MB extracted
                    </li>
                    <li className="rounded-md border border-outline-variant px-3 py-1.5 font-mono text-sm text-on-surface-variant">
                      64 KB output kept per stream
                    </li>
                    <li className="rounded-md border border-outline-variant px-3 py-1.5 font-mono text-sm text-on-surface-variant">
                      120 s script timeout (exit 124)
                    </li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* SECTION 06: DEVICE OWNER, LAB UTILITIES & AUTOMATION */}
        <section
          id="dpm-lab"
          aria-labelledby="dpm-lab-title"
          className="py-16 lg:py-24 bg-surface-1"
        >
          <div className="mx-auto max-w-7xl px-4 sm:px-6">
            <header className="mb-10 grid grid-cols-1 gap-6 lg:mb-14 lg:grid-cols-12 lg:gap-8">
              <div className="flex items-center gap-4 lg:col-span-12">
                <SectionBadge number="06" polygon={M3_POLYGON_12_LOBE} />
                <h2
                  id="dpm-lab-title"
                  className="text-3xl leading-tight font-bold tracking-tight text-balance sm:text-5xl"
                >
                  Device Owner, Lab utilities &amp; Tasker
                </h2>
              </div>
              <div className="max-w-[68ch] text-lg leading-relaxed text-on-surface-variant text-pretty lg:col-span-8">
                <p>
                  Privileged subsystems bundled into the manager beyond standard Shizuku binder
                  dispatch. Every command and class name below comes directly from{" "}
                  <code className="font-mono text-on-surface">docs/device-owner.md</code>,{" "}
                  <code className="font-mono text-on-surface">docs/lab-and-extras.md</code>, and{" "}
                  <code className="font-mono text-on-surface">docs/tasker-plugin.md</code>.
                </p>
              </div>
            </header>

            <ul className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <li className="flex flex-col gap-5 rounded-2xl bg-surface-2 p-6">
                <div className="rounded-xl bg-surface p-4 font-mono text-xs leading-relaxed text-on-surface-variant overflow-x-auto">
                  <p className="text-m3-primary mb-1.5"># 1-tap or ADB activation</p>
                  <p className="text-on-surface break-all">
                    dpm set-device-owner
                    com.hamondev.shevery/moe.shizuku.manager.receiver.SheveryDeviceAdminReceiver
                  </p>
                  <p className="text-tertiary mt-3 mb-1"># 5 Delegated DPM scopes</p>
                  <p>delegation-app-restrictions</p>
                  <p>delegation-block-uninstall</p>
                  <p>delegation-permission-grant</p>
                  <p>delegation-package-access</p>
                  <p>delegation-enable-system-app</p>
                </div>
                <div>
                  <h3 className="mb-1 text-xl font-semibold">
                    Device Owner &amp; Dhizuku Provider
                  </h3>
                  <p className="leading-relaxed text-on-surface-variant">
                    Exposes <code className="font-mono text-on-surface">DhizukuProvider</code> (
                    <code className="font-mono text-on-surface">IDhizuku</code> v1 &amp; v2) with
                    Strict Whitelist Mode, DPM-assisted wireless ADB toggling, and{" "}
                    <code className="font-mono text-on-surface">transferOwnership()</code> to hand
                    off Device Owner without wiping.
                  </p>
                </div>
              </li>

              <li className="flex flex-col gap-5 rounded-2xl bg-surface-2 p-6">
                <div className="rounded-xl bg-surface p-4 font-mono text-xs leading-relaxed text-on-surface-variant overflow-x-auto">
                  <p className="text-m3-primary mb-1.5"># Per-app eBPF Firewall (API 30+)</p>
                  <p className="text-on-surface">cmd connectivity set-chain3-enabled true</p>
                  <p className="text-on-surface">
                    cmd connectivity set-package-networking-enabled false &lt;pkg&gt;
                  </p>
                  <p className="text-tertiary mt-3 mb-1"># USB Debugging Hide (1s poll)</p>
                  <p className="text-on-surface">settings put global adb_enabled 0</p>
                  <p># Restores previous state on app exit</p>
                </div>
                <div>
                  <h3 className="mb-1 text-xl font-semibold">
                    Chain3 Firewall, USB Hide &amp; A11y Pinning
                  </h3>
                  <p className="leading-relaxed text-on-surface-variant">
                    Blocks per-package networking via Android&apos;s eBPF{" "}
                    <code className="font-mono text-on-surface">chain3</code> (no VPN slot used),
                    hides <code className="font-mono text-on-surface">ADB_ENABLED</code> while
                    selected apps are in the foreground, and re-enables pinned{" "}
                    <code className="font-mono text-on-surface">AccessibilityService</code>s if
                    killed.
                  </p>
                </div>
              </li>

              <li className="flex flex-col gap-5 rounded-2xl bg-surface-2 p-6">
                <div className="rounded-xl bg-surface p-4 font-mono text-xs leading-relaxed text-on-surface-variant overflow-x-auto">
                  <p className="text-m3-primary mb-1.5"># Token-authenticated broadcast</p>
                  <p className="text-on-surface break-all">
                    am broadcast -a com.hamondev.shevery.action.START_SERVER -p
                    com.hamondev.shevery -e auth &lt;token&gt;
                  </p>
                  <p className="text-tertiary mt-3 mb-1"># LPE Activator ContentProvider</p>
                  <p className="text-on-surface break-all">
                    content://moe.shizuku.privileged.api.connector
                  </p>
                </div>
                <div>
                  <h3 className="mb-1 text-xl font-semibold">
                    Tasker Plugin, Intents &amp; Connectors
                  </h3>
                  <p className="leading-relaxed text-on-surface-variant">
                    Ships a <code className="font-mono text-on-surface">:tasker</code> module (4
                    actions + <code className="font-mono text-on-surface">Server is running</code>{" "}
                    state condition) and <code className="font-mono text-on-surface">PluginReceiver</code>{" "}
                    for MacroDroid/ADB. Both require{" "}
                    <code className="font-mono text-on-surface">shizuku_connector_enabled</code>.
                  </p>
                </div>
              </li>
            </ul>
          </div>
        </section>

        {/* SECTION 07: LIMITS AND CAVEATS */}
        <section id="limits" aria-labelledby="limits-title" className="py-16 lg:py-24">
          <div className="mx-auto max-w-7xl px-4 sm:px-6">
            <header className="mb-10 grid grid-cols-1 gap-6 lg:mb-14 lg:grid-cols-12 lg:gap-8">
              <div className="flex items-center gap-4 lg:col-span-12">
                <SectionBadge number="07" polygon={M3_POLYGON_4_PINCH} />
                <h2
                  id="limits-title"
                  className="text-3xl leading-tight font-bold tracking-tight text-balance sm:text-5xl"
                >
                  Limits and caveats
                </h2>
              </div>
              <div className="max-w-[68ch] text-lg leading-relaxed text-on-surface-variant text-pretty lg:col-span-8">
                <p>
                  What you should know before installing. Taken directly from the repository docs
                  and source files, not guessed.
                </p>
              </div>
            </header>

            <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {LIMITS_AND_CAVEATS.map((item) => (
                <li
                  key={item.title}
                  className={
                    item.emphasized
                      ? "rounded-xl bg-tertiary-container p-6 text-on-tertiary-container"
                      : "rounded-xl border border-outline-variant p-6"
                  }
                >
                  <h3 className="mb-2 text-lg leading-snug font-semibold text-balance">
                    {item.title}
                  </h3>
                  <p
                    className={
                      item.emphasized
                        ? "leading-relaxed"
                        : "leading-relaxed text-on-surface-variant"
                    }
                  >
                    {item.body}
                  </p>
                </li>
              ))}
            </ul>
          </div>
        </section>

        {/* SECTION 08: BUILD AND DOCS */}
        <section
          id="build"
          aria-labelledby="build-title"
          className="py-16 lg:py-24 bg-surface-1"
        >
          <div className="mx-auto max-w-7xl px-4 sm:px-6">
            <header className="mb-10 grid grid-cols-1 gap-6 lg:mb-14 lg:grid-cols-12 lg:gap-8">
              <div className="flex items-center gap-4 lg:col-span-12">
                <SectionBadge number="08" polygon={M3_POLYGON_8_FLOWER} />
                <h2
                  id="build-title"
                  className="text-3xl leading-tight font-bold tracking-tight text-balance sm:text-5xl"
                >
                  Build and docs
                </h2>
              </div>
              <div className="max-w-[68ch] text-lg leading-relaxed text-on-surface-variant text-pretty lg:col-span-8">
                <p>
                  Building <code className="font-mono text-on-surface">standardDebug</code> or{" "}
                  <code className="font-mono text-on-surface">standardRelease</code> automatically
                  compiles the <code className="font-mono text-on-surface">stealth</code> flavor
                  first and copies{" "}
                  <code className="font-mono text-on-surface">shevery-stealth.apk</code> into the
                  standard APK&apos;s assets. In Android Studio enable &ldquo;Always install with
                  package manager&rdquo; so <code className="font-mono text-on-surface">shizuku_server</code>{" "}
                  uses the latest code.
                </p>
              </div>
            </header>

            <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
              <div className="min-w-0 overflow-hidden rounded-2xl bg-surface-2 lg:col-span-7">
                <p className="border-b border-outline-variant/60 px-5 py-3 font-mono text-sm text-on-surface-variant">
                  shell
                </p>
                <pre className="overflow-x-auto p-5 font-mono text-sm leading-relaxed">
                  <code>{`git clone --recurse-submodules https://github.com/HmnDev-Tech/shevery.git
cd shevery

# Debug build (bundles shevery-stealth.apk, shevery-stub.apk & rish dex)
./gradlew :manager:assembleStandardDebug

# Or build the standalone Stealth flavor directly
./gradlew :manager:assembleStealthRelease`}</code>
                </pre>

                <dl className="grid grid-cols-2 gap-px border-t border-outline-variant/60 bg-outline-variant/40 sm:grid-cols-4">
                  {BUILD_METADATA.map((meta) => (
                    <div key={meta.term} className="bg-surface-2 px-5 py-4">
                      <dt className="text-xs tracking-wide text-on-surface-variant uppercase">
                        {meta.term}
                      </dt>
                      <dd className="font-mono text-sm break-words">{meta.value}</dd>
                    </div>
                  ))}
                </dl>
              </div>

              <nav
                aria-label="Documentation"
                className="rounded-2xl bg-surface-2 p-2 lg:col-span-5"
              >
                <ul className="flex flex-col gap-0.5">
                  {DOC_LINKS.map((doc, idx) => {
                    const isFirst = idx === 0
                    const isLast = idx === DOC_LINKS.length - 1
                    const cornerClass = isFirst
                      ? "rounded-t-xl rounded-b-sm"
                      : isLast
                        ? "rounded-t-sm rounded-b-xl"
                        : "rounded-sm"

                    return (
                      <li key={doc.href}>
                        <a
                          href={doc.href}
                          target="_blank"
                          rel="noopener noreferrer"
                          className={`group flex min-h-14 items-center justify-between gap-4 bg-surface-3 px-5 py-3 transition-[border-radius,background-color] duration-300 ease-spring hover:rounded-xl hover:bg-surface-4 ${cornerClass}`}
                        >
                          <span className="font-medium break-words">{doc.label}</span>
                          <ArrowUpRight
                            className="size-5 shrink-0 text-on-surface-variant transition-transform duration-300 ease-spring group-hover:translate-x-0.5 group-hover:-translate-y-0.5"
                            aria-hidden="true"
                          />
                        </a>
                      </li>
                    )
                  })}
                </ul>
              </nav>
            </div>
          </div>
        </section>
      </main>

      {/* FOOTER */}
      <footer className="border-t border-outline-variant/60 bg-surface">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-4 py-10 text-sm leading-relaxed text-on-surface-variant sm:px-6 lg:flex-row lg:justify-between">
          <p className="max-w-[70ch]">
            Shevery is maintained by{" "}
            <a
              href="https://github.com/HmnDev-Tech"
              target="_blank"
              rel="noopener noreferrer"
              className="text-on-surface underline underline-offset-4"
            >
              HmnDev-Tech
            </a>
            . Based on{" "}
            <a
              href="https://github.com/RikkaApps/Shizuku"
              target="_blank"
              rel="noopener noreferrer"
              className="text-on-surface underline underline-offset-4"
            >
              Shizuku by RikkaApps
            </a>
            , with UI/catalog/Android 17 contributions from{" "}
            <a
              href="https://github.com/kerneldroid"
              target="_blank"
              rel="noopener noreferrer"
              className="text-on-surface underline underline-offset-4"
            >
              kerneldroid
            </a>
            , Device Owner architecture from{" "}
            <a
              href="https://github.com/iamr0s/Dhizuku"
              target="_blank"
              rel="noopener noreferrer"
              className="text-on-surface underline underline-offset-4"
            >
              iamr0s/Dhizuku
            </a>
            , and fixes by{" "}
            <a
              href="https://github.com/LandonMoran"
              target="_blank"
              rel="noopener noreferrer"
              className="text-on-surface underline underline-offset-4"
            >
              Landon Moran
            </a>
            . Licensed under Apache-2.0.
          </p>
          <p className="shrink-0">
            <a
              href="https://github.com/HmnDev-Tech/shevery/issues"
              target="_blank"
              rel="noopener noreferrer"
              className="text-on-surface underline underline-offset-4"
            >
              Issues
            </a>{" "}
            ·{" "}
            <a
              href="https://github.com/HmnDev-Tech/shevery/releases"
              target="_blank"
              rel="noopener noreferrer"
              className="text-on-surface underline underline-offset-4"
            >
              Releases
            </a>{" "}
            ·{" "}
            <a
              href="https://github.com/HmnDev-Tech/shevery/wiki"
              target="_blank"
              rel="noopener noreferrer"
              className="text-on-surface underline underline-offset-4"
            >
              Wiki
            </a>
          </p>
        </div>
      </footer>
    </>
  )
}
