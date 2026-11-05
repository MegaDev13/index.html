#!/usr/bin/env bash
set -euo pipefail
mkdir -p neon-os/verification
adb install -r neon-os/app/build/outputs/apk/debug/app-debug.apk
adb install -r neon-os/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w dev.neonos.poco.test/dev.neonos.SmokeInstrumentation | tee neon-os/verification/instrumentation.txt
# Custom instrumentation reports RESULT_OK (-1). Fail on assertion failure or crash.
python3 - <<'PY'
from pathlib import Path
s=Path('neon-os/verification/instrumentation.txt').read_text()
assert 'NEON emulator smoke passed:' in s and 'INSTRUMENTATION_CODE: -1' in s, s
PY
adb pull /sdcard/Android/data/dev.neonos.poco/files/verification/. neon-os/verification/
adb shell dumpsys meminfo dev.neonos.poco > neon-os/verification/dumpsys-memory.txt
adb logcat -d -s AndroidRuntime:E > neon-os/verification/crash-log.txt
