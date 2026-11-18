#!/usr/bin/env bash
# Run on an expendable API 35+ AVD. Seeds two synthetic contacts in a TEST-ONLY APK.
set -euo pipefail
cd "$(dirname "$0")/.."
out=build/device-evidence; mkdir -p "$out"; apk=app/build/outputs/apk/debug/app-debug.apk
adb wait-for-device
adb shell wm size 1080x2340; adb shell wm density 420
adb shell settings put system font_scale 1.0
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 1
adb shell input keyevent 82
adb install -r "$apk" | tee "$out/install.txt"; grep -q Success "$out/install.txt"
adb install -r -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am start -W -n ai.nexus.test/ai.nexus.test.SeedContactActivity
adb shell am force-stop ai.nexus
adb shell am start -W -n ai.nexus/.MainActivity | tee "$out/cold-start.txt"
adb shell getprop ro.build.fingerprint > "$out/system-image.txt"
adb shell dumpsys package ai.nexus | grep -E 'versionCode=|versionName=|targetSdk=' >> "$out/system-image.txt"
adb logcat -c
adb shell am instrument -w -r ai.nexus.test/ai.nexus.test.NexusInstrumentation | tee "$out/run.txt"
# run-as is possible only on this debug build. No contacts database is copied.
for f in $(adb shell run-as ai.nexus ls files/evidence/ | tr -d '\r' | grep -E '^[a-zA-Z0-9_-]+\.(png|txt)$'); do adb exec-out run-as ai.nexus cat "files/evidence/$f" > "$out/$f"; done
if grep -q NEXUS_TESTS_PASSED "$out/run.txt"; then
  # Instrumentation ends the target process. Sample the actual relaunched foreground app.
  adb shell am start -W -n ai.nexus/.MainActivity > "$out/relaunch.txt"
  adb shell dumpsys gfxinfo ai.nexus reset >/dev/null
  sleep 5
fi
adb shell dumpsys meminfo ai.nexus > "$out/memory.txt"
adb shell dumpsys gfxinfo ai.nexus > "$out/frames.txt"
adb logcat -d -s AndroidRuntime:E > "$out/runtime-errors.txt"
sha256sum "$apk" | cut -d' ' -f1 > "$out/apk.sha256"
if ! grep -q NEXUS_TESTS_PASSED "$out/run.txt"; then adb shell uiautomator dump /sdcard/nexus-qa.xml >/dev/null || true; adb pull /sdcard/nexus-qa.xml "$out/failure-ui.xml" || true; echo "::error::$(grep -E 'AssertionError|Exception|Timed out' "$out/run.txt" | head -1)"; exit 1; fi
adb shell am start -W -n ai.nexus.test/ai.nexus.test.SeedContactActivity --ez clear true
# Non-UI postcondition: installation is exactly the binary being delivered.
installed=$(adb shell pm path ai.nexus | tr -d '\r' | sed 's/^package://'); adb pull "$installed" "$out/installed.apk" >/dev/null
cmp -s "$apk" "$out/installed.apk"; rm "$out/installed.apk"
echo 'Installed base.apk equals build output byte-for-byte.' >> "$out/install.txt"
