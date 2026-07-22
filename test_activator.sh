#!/bin/bash
echo "Testing Shizuku Connector..."
OUTPUT=$(adb shell content query --uri content://moe.shizuku.privileged.api.connector)
echo "Content Provider Output: $OUTPUT"

if [[ $OUTPUT == *"command="* ]]; then
    CMD=$(echo "$OUTPUT" | grep -o 'command=.*' | cut -d= -f2-)
    echo "Executing command from provider: $CMD"
    adb shell "$CMD"
else
    echo "No command found. Please enable 'Shizuku Connectors' in Lab Features."
    adb shell am start -n moe.shizuku.privileged.api/moe.shizuku.manager.settings.LabFeaturesActivity
fi