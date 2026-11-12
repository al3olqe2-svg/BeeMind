#!/usr/bin/env bash
set -euo pipefail
adb install app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
if [[ "${UI_PROBE_ONLY:-false}" == true ]]; then
  python3 tools/ui_device_probe.py
else
  for test in TemporalDeviceTest ThreadsDeviceTest EditorDeviceTest EditorR3DeviceTest WallpaperDeviceTest WallpaperVisualDeviceTest; do
    output="${test}.txt"
    timeout 180 adb shell am instrument -w -r -e class "com.beemind.personal.$test" com.beemind.personal.test/androidx.test.runner.AndroidJUnitRunner | tee "$output"
    if [[ "$test" == EditorR3DeviceTest ]]; then
      mkdir -p app/build/reports/ui-device
      for scene in normal-empty normal-long-ime primary-ime expressive glow anger none reminder date clock capsule-confirmation locked; do
        adb exec-out run-as com.beemind.personal cat "files/r3-native-$scene.png" > "app/build/reports/ui-device/r3-$scene.png" || true
      done
    fi
    grep -q 'OK (1 test)' "$output"
  done
  mkdir -p app/build/reports/ui-device
  for scene in fallback sea bright dark portrait detail; do
    adb exec-out run-as com.beemind.personal cat "files/r2-native-$scene.png" > "app/build/reports/ui-device/r2-$scene.png"
    test -s "app/build/reports/ui-device/r2-$scene.png"
  done
  for scene in normal-empty normal-long-ime primary-ime expressive glow anger none reminder date clock capsule-confirmation locked; do
    adb exec-out run-as com.beemind.personal cat "files/r3-native-$scene.png" > "app/build/reports/ui-device/r3-$scene.png"
    test -s "app/build/reports/ui-device/r3-$scene.png"
  done
  python3 tools/reminder_device_check.py
  python3 tools/ui_device_probe.py
fi
