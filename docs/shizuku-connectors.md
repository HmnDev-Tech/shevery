# Shizuku Connectors API

Shizuku Connectors is an experimental feature (available in **Lab Features**) that allows third-party apps, referred to as "Activators", to securely query the command required to start the Shizuku server locally.

## Why Shizuku Connectors?

Often, enthusiasts discover Local Privilege Escalation (LPE) exploits (e.g., Dirty Pipe, FOTA exploits, mktimer) that can be used to gain temporary elevated privileges without a PC or full root access. Shizuku Connectors provides a standardized interface for these "Activators" (usually small 1MB APKs) to retrieve the internal Shizuku startup command, apply their specific exploit, and bootstrap the Shizuku server directly on the device.

## Prerequisites

For safety, this feature is **disabled by default**. 
To use it, the user must navigate to the Shizuku Settings -> **Lab Features**, enable **Shizuku Connectors**, and accept the safety warning.

## How to use

When Shizuku Connectors is enabled, Shizuku exposes a local, exported `ContentProvider` at the following URI:

```
content://moe.shizuku.privileged.api.connector
```

### Querying the Provider

You can query this URI to retrieve a `Cursor` containing exactly one row and one column named `command`.

#### Android Java/Kotlin Example:

```kotlin
val uri = Uri.parse("content://moe.shizuku.privileged.api.connector")
contentResolver.query(uri, null, null, null, null)?.use { cursor ->
    if (cursor.moveToFirst()) {
        val commandIndex = cursor.getColumnIndex("command")
        if (commandIndex != -1) {
            val shizukuCommand = cursor.getString(commandIndex)
            // Execute the command using your local exploit payload
            Log.d("Activator", "Got command: $shizukuCommand")
        }
    }
}
```

#### Shell Script Example:

```bash
OUTPUT=$(content query --uri content://moe.shizuku.privileged.api.connector)
if [[ $OUTPUT == *"command="* ]]; then
    CMD=$(echo "$OUTPUT" | grep -o 'command=.*' | cut -d= -f2-)
    # Run the command with elevated privileges
    eval "$CMD"
else
    echo "Shizuku Connectors is not enabled or Shizuku is not installed."
fi
```

### Return Values

- If **Shizuku Connectors** is **enabled**, the provider will return the full shell command string required to start the internal Shizuku server.
- If **Shizuku Connectors** is **disabled** (or the user has not accepted the warning), the provider will return `null` or an empty result set, depending on the client's query mechanism.
