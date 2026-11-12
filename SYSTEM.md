# System

Last verified: 2026-11-12

## OS

- Platform: Debian GNU/Linux 12 (bookworm), kernel 6.12.105+
- Arch: x86_64
- Shell: /bin/bash
- User: spark (uid=1235, gid=1235, non-root)

## Hosting

- Context: coding-agent sandbox container
- Persistence: ephemeral outside `/working_dir/c_63525094295d2a6d/artifacts/`
- Host CLI/IDE: sandbox VM (`execute_bash`)

## Tooling available

- Languages/runtimes: OpenJDK 17.0.21 (`2026-10-20`), Python 3.11.2
- Build configuration (Shevery):
  - Android SDK target: `compileSdk = 37`, `targetSdk = 37`, `minSdk = 24`, `buildToolsVersion = "37.0.0"`, `ndkVersion = "29.0.14206865"`
  - Gradle wrapper: `gradle-9.8.2-bin.zip`, Android Gradle Plugin `9.4.0-alpha07`, Kotlin `2.4.20-Beta2`
  - Modules: `:manager`, `:server`, `:starter`, `:shell`, `:stub`, `:common`, `:errorprotect`, `:tasker`, plus `Shevery-API` (`:aidl`, `:rish`, `:shared`, `:api`, `:provider`, `:server-shared`)
  - Dhizuku dependency in `:manager`: `io.github.iamr0s:Dhizuku-API` (upgraded from `2.5.4` to `2.7.0` for Server API 8 / `unbindUserServiceByConnection` / `IDhizukuLockTaskModeListener` support)
- Other: `git 2.39.5`, `zip`, `unzip`, `patch`

## Constraints

- Filesystem: `/working_dir/c_63525094295d2a6d/` writable; `/etc`, `/usr`, `/opt` read-only
- Network: outbound network disabled inside sandbox VM (`gradlew` cannot download `gradle-9.8.2-bin.zip` or Android SDK inside container)
- Privilege: regular user (`spark`), no `sudo`

## Notes

- Local Android SDK (`ANDROID_HOME`) and cached Gradle distribution are not pre-installed in the container image; verification of syntax, imports, AIDL contracts, and `git format-patch` / `git apply --check` is performed directly against the repository tree.
