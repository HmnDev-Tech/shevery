# System prompt #1: repository context

## About the project

- **Name:** Shevery
- **One-line description:** A specialized Android application bridging system APIs using Shizuku/Dhizuku with Material 3 Expressive UI, ADB Modules, and Comput AI console.
- **Audience:** Android developers, power users, ADB module maintainers, and system tweak enthusiasts.
- **Links:** README — README.md, docs — docs/, CONTRIBUTING — CONTRIBUTING.md

## Tech stack

- **Languages/versions:** Kotlin, Java, C++ (NDK/JNI), Shell scripts, Go (for CI triage bot).
- **Frameworks/libraries:** Jetpack Compose, Material 3 Expressive, Shizuku API, AndroidX, Gradle.
- **Infrastructure:** Android Studio / Gradle, GitHub Actions CI for APK builds and triage.

## Repository structure

| Path | Purpose |
|---|---|
| `/manager` | Main Android application code (Jetpack Compose UI, module management, settings, Comput console). |
| `/api` | Client IPC library and interfaces for communicating with the Shizuku server. |
| `/common` | Shared data models, constants, and utilities across manager and server. |
| `/server` | Core Shizuku server process running under root/ADB privileges. |
| `/starter` | Native executable and initialization logic for starting the server process. |
| `/shell` | Shell helper scripts, macro runners, and binary wrappers. |
| `/docs` | API documentation, Android 17 compatibility guides, and ADB module guides. |

## Conventions and code style

- Jetpack Compose components follow Material 3 Expressive guidelines.
- Kotlin code uses modern coroutines and structured concurrency.
- API changes must preserve backward compatibility for Shizuku binder calls.

## What counts as a valid issue

- Detailed bug reports with reproduction steps and logcat output.
- Module installation, execution, or policy enforcement issues.
- Feature requests related to ADB modules, Comput console, or Dhizuku integration.
- Issues reproducing on the latest build of Shevery.

## What's out of scope / known not-planned

- Support for Android versions older than API level 26 (Android 8.0).
- Issues stemming from unmodified upstream Shizuku behavior not related to Shevery's fork enhancements.

## Known limitations (so the AI doesn't mistake a feature for a bug)

- ADB permissions differ based on Android OS version.

## Examples of TRASH specific to this repository

- Spam or automated bot submissions without actionable technical details or code references.
- Demands for root-only exploits or illegal bypasses.

## Examples of good issues/PRs for this repository

- PR fixing a Compose UI overflow on Android 16/17 devices.
- Issue describing an ADB module parsing failure with attached module logs.
