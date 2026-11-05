#!/usr/bin/env bash
set -euo pipefail
mkdir -p neon-os/verification
system_home="$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1)"
adb shell dumpsys activity lastanr > neon-os/verification/emulator-lastanr-before.txt
adb install -r neon-os/app/build/outputs/apk/debug/app-debug.apk
adb install -r neon-os/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Keep the emulator's original HOME role and stop its cold-boot Quickstep activity.
# This affects this disposable emulator only; it is not part of the app.
if [[ "$system_home" == */* ]]; then
  adb shell cmd package set-home-activity "$system_home"
  adb shell am force-stop "${system_home%%/*}"
fi
adb shell am instrument -w -r dev.neonos.poco.test/dev.neonos.SmokeInstrumentation | tee neon-os/verification/instrumentation.txt
# The success marker is emitted only after every assertion; fail on test failure or crash.
python3 - <<'PY'
from pathlib import Path
s=Path('neon-os/verification/instrumentation.txt').read_text()
assert 'NEON emulator smoke passed:' in s and 'FAIL:' not in s and 'INSTRUMENTATION_FAILED' not in s, s
PY
adb pull /sdcard/Android/data/dev.neonos.poco/files/verification/. neon-os/verification/
adb shell dumpsys meminfo dev.neonos.poco > neon-os/verification/dumpsys-memory.txt
adb logcat -d -s AndroidRuntime:E > neon-os/verification/crash-log.txt
