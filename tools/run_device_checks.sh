#!/usr/bin/env bash
set -euo pipefail
phase="install fixtures"
output=/dev/null
report_failure() {
  local result=$?
  local details
  details=$(tail -c 6000 "$output" 2>/dev/null || true)
  details=${details//%/%25}
  details=${details//$'\r'/%0D}
  details=${details//$'\n'/%0A}
  printf '::error title=Device check failure::%s (exit %s)%%0A%s\n' "$phase" "$result" "$details"
  exit "$result"
}
trap report_failure ERR
adb install app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
if [[ "${UI_PROBE_ONLY:-false}" == true ]]; then
  python3 tools/ui_device_probe.py
else
  for test in TemporalDeviceTest ThreadsDeviceTest EditorDeviceTest EditorR3DeviceTest WallpaperDeviceTest WallpaperVisualDeviceTest; do
    phase="$test"
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
  echo '::notice title=Native editor coverage::Temporal, Threads, Editor, R3 editor/IME/capsule, wallpaper invariants and captures passed.'
  phase="cold-process reminders and real ten-minute snooze"
  mkdir -p app/build/reports/reminder-device
  output=app/build/reports/reminder-device/runner.txt
  python3 tools/reminder_device_check.py 2>&1 | tee "$output"
  echo '::notice title=Native reminder coverage::Cold-process delivery, real ten-minute Snooze and Done passed.'
  phase="native visual probes"
  output=app/build/reports/ui-device/runner.txt
  python3 tools/ui_device_probe.py 2>&1 | tee "$output"
fi
