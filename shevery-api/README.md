# Shevery API

Native API contract for [Shevery](https://github.com/HmnDev-Tech/shevery).

Shevery no longer declares or requests the legacy Shizuku permission
`moe.shizuku.manager.permission.API_V23`. Responsibilities are split:

| Component | Permission | Role |
|-----------|------------|------|
| Shevery manager (`com.hamondev.shevery`) | `com.hamondev.shevery.permission.PRIVILEGED_API` | Native API, declared by Shevery |
| Shizuku stub (`moe.shizuku.privileged.api`) | `moe.shizuku.manager.permission.API_V23` | Legacy Shizuku clients — the stub is the only owner |

## Contents

- `SheveryApi` — package name, permission names, uid helpers.
- `LegacyShizukuStub` — contract of the Shizuku stub (legacy V23 permission lives only there).
- `ClientPermissions` — single-pass classification of `requestedPermissions` into a bit mask.
- `ClientPermissionCache` — lock-free `(uid, package) → flags` cache. Removes the
  `getPackageInfo(GET_PERMISSIONS)` binder call the server previously made on every
  uid state change.

## Usage in Shevery

Shevery vendors this repository under `shevery-api/` (git subtree) and includes the
`:shevery-api` module (`shevery-api/api`). To build against a local checkout instead, set in
Shevery's `local.properties`:

```properties
sheveryApi.useLocal=true
sheveryApi.dir=/path/to/shevery-api
```

## License

Apache License 2.0, see [LICENSE](LICENSE).
