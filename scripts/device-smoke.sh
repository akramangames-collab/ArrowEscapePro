#!/usr/bin/env bash
set -euo pipefail
mkdir -p device-results
adb install -r dist/ArrowEscape-PuzzleMaze-V16-test.apk
adb install -r dist/ArrowEscape-V16-tests.apk
adb shell am instrument -w com.arrowescape.pro.debug.test/androidx.test.runner.AndroidJUnitRunner | tee device-results/instrumentation.txt
adb pull /sdcard/Android/data/com.arrowescape.pro.debug/files/screenshots device-results/screenshots
python3 - <<'PY'
from pathlib import Path
s=Path('device-results/instrumentation.txt').read_text()
assert 'OK (1 test)' in s and 'FAILURES' not in s, s
PY
adb shell am force-stop com.arrowescape.pro.debug
adb shell am start -W -n com.arrowescape.pro.debug/com.arrowescape.pro.MainActivity
adb shell pidof com.arrowescape.pro.debug > device-results/process.txt
adb logcat -d -s AndroidRuntime > device-results/crashes.txt
