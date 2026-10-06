# Security Policy

## Supported versions

| Version | Supported |
|---|---|
| `dev` (unreleased) | Fixes land here first |
| Latest published release (`v*` tag marked **Latest**) | Yes |
| Older releases | No backports — update to the latest release |

Only the newest release is supported. Older tags are kept for reference and receive no security fixes.

## Reporting a vulnerability

Report privately through **GitHub Private Vulnerability Reporting**:

https://github.com/HmnDev-Tech/shevery/security/advisories/new

The report opens as a **draft security advisory** — it is visible only to the maintainers until you (or the maintainers) publish it. Do not open a public issue, discussion, or pull request for an exploitable bug.

Include, where possible:

- affected version, tag, or commit,
- what the issue is and what an attacker could achieve,
- concrete reproduction steps or a minimal proof of concept,
- any suggested fix or mitigation.

Maintainers triage the report first, then coordinate a fix and a disclosure date with the reporter. Credit is given in the advisory unless anonymity is requested.

## Scope

**In scope** — code in this repository:

- the Shevery manager app (`manager/`), starter, server glue, and compatibility stubs,
- the Tasker / MacroDroid plugin (`tasker/`) and the public automation intents,
- the ADB module catalog, install and update pipeline,
- app self-update, backup/restore, and Device Owner / Dhizuku helpers,
- CI and release configuration under `.github/`.

**Out of scope**:

- upstream [Shizuku](https://github.com/RikkaApps/Shizuku) and [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) — report those to RikkaApps,
- third-party modules and apps discovered through the module catalog,
- issues that already assume root, an attacker with physical access, or a compromised OS,
- social engineering, phishing, and denial-of-service against the app UI or the GitHub project.

**Intended behavior, not vulnerabilities**: Shevery runs shell commands with ADB/root privileges through the Shizuku server once the user has granted the app permission. Anything that requires an already-granted Shizuku permission (shell execution, module actions with FULL policy, `WRITE_SECURE_SETTINGS` shortcuts) is a designed feature.

## Repository hardening

- Secret scanning and push protection are enabled on this repository.
- Private vulnerability reporting is enabled.
- Releases are created only by the repository owner.
- Treat tokens, signing keys (`keystore.jks`, `signing.properties`), and AI provider keys as secrets: never commit them. `local.properties` and signing files are ignored by design.
