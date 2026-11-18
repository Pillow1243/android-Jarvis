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
adb shell pm grant ai.nexus.test android.permission.READ_CONTACTS
adb shell pm grant ai.nexus.test android.permission.WRITE_CONTACTS
adb shell am broadcast --include-stopped-packages -a ai.nexus.test.SEED -n ai.nexus.test/ai.nexus.test.FixtureContacts | tee "$out/contact-fixture.txt"
grep -q 'result=42' "$out/contact-fixture.txt" || { echo '::error::Contacts fixture precondition failed; see contact-fixture.txt'; exit 1; }
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
  # Measure two different workloads. After standby settles it should not redraw perpetually.
  sleep 3
  adb shell dumpsys gfxinfo ai.nexus reset >/dev/null
  sleep 5
  adb shell dumpsys gfxinfo ai.nexus > "$out/idle-frames.txt"
  adb shell dumpsys gfxinfo ai.nexus reset >/dev/null
  for i in 1 2 3; do adb shell input swipe 480 1800 480 500 250; adb shell input swipe 480 500 480 1800 250; done
  # TrafficStats includes this application UID only; no system-wide packet dump or payload capture.
  uid=$(adb shell pm list packages -U ai.nexus | sed -n 's/^package:ai.nexus uid:\([0-9]*\).*/\1/p' | tr -d '\r')
  if [[ "$uid" =~ ^[0-9]+$ ]]; then
    { echo "UID: $uid. OS accounting snapshot on disposable emulator (not a long-run power or network benchmark).";
      adb shell dumpsys netstats detail | grep -E "uid=$uid([^0-9]|$)" | head -30 || true; } > "$out/network.txt"
    adb shell dumpsys batterystats --charged ai.nexus | head -450 > "$out/power.txt" || true
  fi
fi
adb shell dumpsys meminfo ai.nexus > "$out/memory.txt"
adb shell dumpsys gfxinfo ai.nexus > "$out/frames.txt"
adb logcat -d -s AndroidRuntime:E > "$out/runtime-errors.txt"
if grep -Fq 'FATAL EXCEPTION' "$out/runtime-errors.txt" && grep -Fq 'Process: ai.nexus' "$out/runtime-errors.txt"; then echo '::error::App crash in emulator log'; exit 1; fi
sha256sum "$apk" | cut -d' ' -f1 > "$out/apk.sha256"
if ! grep -q NEXUS_TESTS_PASSED "$out/run.txt"; then adb shell uiautomator dump /sdcard/nexus-qa.xml >/dev/null || true; adb pull /sdcard/nexus-qa.xml "$out/failure-ui.xml" || true; echo "::error::$(grep -E 'AssertionError|Exception|Timed out' "$out/run.txt" | head -1)"; exit 1; fi
adb shell am broadcast -a ai.nexus.test.SEED -n ai.nexus.test/ai.nexus.test.FixtureContacts --ez clear true
# Non-UI postcondition: installation is exactly the binary being delivered.
installed=$(adb shell pm path ai.nexus | tr -d '\r' | sed 's/^package://'); adb pull "$installed" "$out/installed.apk" >/dev/null
cmp -s "$apk" "$out/installed.apk"; rm "$out/installed.apk"
echo 'Installed base.apk equals build output byte-for-byte.' >> "$out/install.txt"
