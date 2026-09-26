#!/usr/bin/env bash
set -euo pipefail
mkdir -p device-results

# Keep emulator/launcher infrastructure warnings from contaminating the app's
# visual-regression screenshots. App crashes are still captured and checked
# separately from logcat/process state below.
adb shell settings put global hide_error_dialogs 1 || true
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
adb logcat -c || true

adb install -r dist/INSTALL-THIS-ArrowEscape-1.0.0-test.apk
adb install -r dist/ArrowEscape-1.0.0-tests.apk
adb shell am instrument -w com.arrowescape.pro.debug.test/androidx.test.runner.AndroidJUnitRunner | tee device-results/instrumentation.txt
adb pull /sdcard/Android/data/com.arrowescape.pro.debug/files/screenshots device-results/screenshots
python3 - <<'PY'
from pathlib import Path
import re
s=Path('device-results/instrumentation.txt').read_text()
result=re.search(r'OK \((\d+) tests?\)',s)
assert result and int(result.group(1))>=10 and 'FAILURES' not in s, s
PY

# Capture the actual premium navigation surfaces as rendered by Android rather
# than only the canvas/gameplay views. These are human-review artifacts, not
# brittle pixel assertions. UIAutomator text lookup keeps the smoke harness
# resilient to display-density differences.
capture_screen() {
  local name="$1"
  adb exec-out screencap -p > "device-results/screenshots/${name}.png"
}

tap_text() {
  local needle="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml device-results/window.xml >/dev/null 2>&1
  local point
  point=$(python3 - "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET
needle=sys.argv[1].lower()
root=ET.parse('device-results/window.xml').getroot()
for node in root.iter('node'):
    hay=(' '.join([node.attrib.get('text',''),node.attrib.get('content-desc','')])).lower()
    if needle in hay:
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            print((x1+x2)//2,(y1+y2)//2)
            raise SystemExit
raise SystemExit(2)
PY
  ) || return 1
  adb shell input tap $point
  sleep 0.35
}

adb shell am force-stop com.arrowescape.pro.debug
adb shell am start -W -n com.arrowescape.pro.debug/com.arrowescape.pro.MainActivity
sleep 1.2
# Dismiss the optional one-per-day reminder so the clean Home capture represents
# the persistent hub itself. The reminder is separately covered by app tests.
adb shell input keyevent KEYCODE_BACK || true
sleep 0.3
capture_screen "09-home"

if tap_text "ACHIEVEMENTS"; then
  capture_screen "10-achievements"
  if tap_text "Daily Streak"; then capture_screen "11-daily-streak"; fi
  tap_text "Back to Home" || adb shell input keyevent KEYCODE_BACK || true
fi

if tap_text "DAILY CHALLENGE"; then
  capture_screen "12-daily-challenge"
  tap_text "Back to Home" || adb shell input keyevent KEYCODE_BACK || true
fi

if tap_text "STORE"; then
  capture_screen "13-store"
  tap_text "Back to Home" || adb shell input keyevent KEYCODE_BACK || true
fi

if tap_text "Settings"; then
  capture_screen "14-settings"
  tap_text "BACK TO HOME" || adb shell input keyevent KEYCODE_BACK || true
fi

adb shell pidof com.arrowescape.pro.debug > device-results/process.txt
adb logcat -d -s AndroidRuntime > device-results/crashes.txt
# Fail only for crashes from our package; unrelated emulator/launcher issues are
# preserved in logs but must not invalidate or visually obscure app QA.
if grep -qE 'FATAL EXCEPTION.*com\.arrowescape\.pro|Process: com\.arrowescape\.pro\.debug' device-results/crashes.txt; then
  cat device-results/crashes.txt
  exit 1
fi
