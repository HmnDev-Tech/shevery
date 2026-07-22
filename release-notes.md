# Shizuku Modern - Service Enhancement Update

This update introduces significant stability fixes and new connection methods for the Shizuku service.

## New Features

### TCP Mode
A persistent ADB connection method that allows starting the Shizuku service via local TCP (127.0.0.1:5555). This eliminates the need for repeated wireless ADB pairing on supported devices.

### Dhizuku Integration
Full support for the Dhizuku API. Shizuku can now bind to Dhizuku's device management service to execute commands, providing an alternative to traditional ADB or Root startup methods.

## Improvements and Fixes

### Stability
- Fixed a critical application crash when accessing the "About" screen.
- Resolved build-time encoding issues for AIDL-generated services on Windows environments.
- Optimized coroutine management for service startup routines.

### Build System
- Enabled AIDL compilation support.
- Configured UTF-8 encoding for all Java and Kotlin compilation tasks to ensure cross-platform build consistency.
- Updated Dhizuku API integration to version 2.5.4.

## Technical Details
- Added custom IDhizukuService implementation for secure command execution.
- Implemented tools:overrideLibrary to maintain compatibility with Android 7.0+.

---
This is an independent fork based on the upstream Shizuku project.