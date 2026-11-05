#!/usr/bin/env bash
set -euo pipefail
mkdir -p neon-os/verification
python3 neon-os/backend/tests/emulator_fixture.py > neon-os/verification/backend-fixture.log 2>&1 &
fixture_pid=$!
collect_diagnostics() {
  adb logcat -d -s AndroidRuntime:E > neon-os/verification/crash-log.txt 2>&1 || true
  adb shell dumpsys activity lastanr > neon-os/verification/emulator-lastanr-after.txt 2>&1 || true
  kill "$fixture_pid" 2>/dev/null || true
}
trap collect_diagnostics EXIT
sleep 1
system_home="$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1)"
adb shell dumpsys activity lastanr > neon-os/verification/emulator-lastanr-before.txt
adb install -r neon-os/app/build/outputs/apk/debug/app-debug.apk
adb install -r neon-os/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Select NEON as HOME only in this disposable AVD and stop cold-boot Quickstep.
# This affects this disposable emulator only; it is not part of the app.
if [[ "$system_home" == */* ]]; then
  adb shell cmd package set-home-activity dev.neonos.poco/dev.neonos.MainActivity
  adb shell am force-stop "${system_home%%/*}"
fi
adb shell am instrument -w -r dev.neonos.poco.test/dev.neonos.SmokeInstrumentation | tee neon-os/verification/instrumentation.txt
adb pull /sdcard/Android/data/dev.neonos.poco/files/verification/. neon-os/verification/ || true
# The success marker is emitted only after every assertion; fail on test failure or crash.
python3 - <<'PY'
from pathlib import Path
s=Path('neon-os/verification/instrumentation.txt').read_text()
assert 'NEON emulator smoke passed:' in s and 'FAIL:' not in s and 'INSTRUMENTATION_FAILED' not in s, s
PY
adb shell dumpsys meminfo dev.neonos.poco > neon-os/verification/dumpsys-memory.txt
adb logcat -d -s AndroidRuntime:E > neon-os/verification/crash-log.txt

# Verify persisted state after a real target-process restart, not just Activity recreation.
adb shell am force-stop dev.neonos.poco
adb shell am start -n dev.neonos.poco/dev.neonos.MainActivity
sleep 2
python3 - <<'PYTEST'
import subprocess,xml.etree.ElementTree as E,json
from pathlib import Path
root=E.fromstring(subprocess.check_output(['adb','shell','run-as','dev.neonos.poco','cat','shared_prefs/neon.xml']))
theme=root.find("./string[@name='theme']").text
assert theme=='dark_terminal',theme
favorites=root.find("./set[@name='favorites']")
assert favorites is not None and len(favorites)==2
Path('neon-os/verification/process-restart.json').write_text(json.dumps({'theme':theme,'favorites':len(favorites),'process_restart_verified':True},indent=2))
PYTEST
adb exec-out screencap -p > neon-os/verification/dark-terminal-after-process-restart.png
