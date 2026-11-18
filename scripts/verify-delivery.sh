#!/usr/bin/env bash
# Verify the downloadable/debug APK, not the output of a new build. No network or keys required.
set -euo pipefail
cd "$(dirname "$0")/.."
apk=${1:-delivery/JARVIS-NEXUS.apk}
evidence=docs/evidence/android
: "${ANDROID_HOME:?Set the Android SDK root (with build-tools)}"
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
bt=${BUILD_TOOLS:-$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)}
actual=$(sha256sum "$apk" | cut -d' ' -f1)
expected=$(tr -d '[:space:]' < "$evidence/apk.sha256")
[[ "$actual" = "$expected" ]] || { echo 'APK hash differs from the tested candidate' >&2; exit 1; }
grep -Fxq 'Installed base.apk equals build output byte-for-byte.' "$evidence/install.txt"
if grep -q '^Build lane: Android SDK' "$evidence/compile.txt"; then grep -Eq '^PASS: [0-9]+ host checks$' "$evidence/compile.txt"; fi
grep -Fxq 'NEXUS_TESTS_PASSED' "$evidence/run.txt"
grep -Fxq 'Device job: success' "$evidence/run-source.txt"
grep -Fxq 'Compatibility jobs: success' "$evidence/run-source.txt"
source_revision=$(sed -n 's/^Source: //p' "$evidence/run-source.txt")
[[ "$source_revision" =~ ^[[:xdigit:]]{40}$ ]]
if git rev-parse --git-dir >/dev/null 2>&1 && git cat-file -e "$source_revision^{commit}" 2>/dev/null; then
  git diff --quiet "$source_revision" -- app/src/main app/build.gradle build.gradle gradle/ gradlew gradle.properties settings.gradle scripts/sdk-build.sh || { echo 'Application/build inputs changed since device verification.' >&2; exit 1; }
fi
signer=$("$bt/apksigner" verify --verbose --print-certs "$apk")
printf '%s\n' "$signer"
pattern='s/^Signer #1 certificate SHA-256 digest: //p'
cert=$(sed -n "$pattern" "$evidence/signature.txt")
[[ "$cert" =~ ^[[:xdigit:]]{64}$ && $(sed -n "$pattern" <<<"$signer") = "$cert" ]]
grep -Fxq 'Verified using v2 scheme (APK Signature Scheme v2): true' <<<"$signer"
"$bt/zipalign" -c 4 "$apk"
badging=$("$bt/aapt2" dump badging "$apk")
# Build Tools versions call this field either sdkVersion or minSdkVersion.
grep -Eq "^(sdkVersion|minSdkVersion):'26'$" <<<"$badging"
for str in "package: name='ai.nexus'" "targetSdkVersion:'36'" "launchable-activity: name='ai.nexus.MainActivity'" 'application-debuggable'; do grep -Fq "$str" <<<"$badging"; done
# Compare the complete manifest permission set, not just a short denylist.
declared=$(sed -nE "s/^uses-permission(-sdk-[0-9]+)?: name='([^']+)'.*/\2/p" <<<"$badging" | LC_ALL=C sort -u)
expected_permissions=$(printf '%s\n' android.permission.{INTERNET,POST_NOTIFICATIONS,READ_CONTACTS,RECEIVE_BOOT_COMPLETED,RECORD_AUDIO} | LC_ALL=C sort)
[[ "$declared" = "$expected_permissions" ]] || { printf 'Unexpected APK permissions:\n%s\n' "$declared" >&2; exit 1; }
python3 - "$apk" "$evidence" <<'PY'
import hashlib, json, pathlib, sys, zipfile
p, folder = map(pathlib.Path, sys.argv[1:])
digest = hashlib.sha256(p.read_bytes()).hexdigest()
with zipfile.ZipFile(p) as apk:
    assert apk.testzip() is None and 'classes.dex' in apk.namelist()
    for name in apk.namelist():
        if name.endswith('.dex'):
            dex = apk.read(name)
            assert b'Lai/nexus/test/FixtureContacts;' not in dex and b'Lai/nexus/test/FixtureWindow;' not in dex
            assert b'Lai/nexus/test/NexusInstrumentation;' not in dex
for level in (26, 36):
    proof = json.loads((folder / f'compat-{level}.json').read_text())
    assert proof['api'] == str(level) and proof['status'] == 'pass' and proof['apk_sha256'] == digest
    assert len(proof['passed']) >= 4 and 'no dialer replay' in proof['passed'][-1]
print(f'Verified archive, SHA-256, debug signer, package, manifest and exact API 26/35/36 candidate: {digest}')
PY
