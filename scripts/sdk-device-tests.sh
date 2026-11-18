#!/usr/bin/env bash
# Package the separate instrumentation APK with only public SDK/JDK tools. Run sdk-build.sh first.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set Android SDK root}" "${JAVA_HOME:?Set JDK 17 home}"
export PATH="$JAVA_HOME/bin:$PATH"
bt=${BUILD_TOOLS:-$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)}
plat=$ANDROID_HOME/platforms/android-36/android.jar
out=build/sdk-test; main=build/sdk-debug
[[ -f "$main/classes.jar" && -f "$main/JARVIS-NEXUS.apk" ]] || { echo 'Run scripts/sdk-build.sh first' >&2; exit 1; }
rm -rf "$out"; mkdir -p "$out"/{classes,dex}
python3 - "$out/AndroidManifest.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
ns='http://schemas.android.com/apk/res/android'; ET.register_namespace('android', ns); a='{'+ns+'}'
t=ET.parse('app/src/androidTest/AndroidManifest.xml'); root=t.getroot(); root.set('package','ai.nexus.test'); root.set(a+'versionCode','1')
root.insert(0,ET.Element('uses-sdk',{a+'minSdkVersion':'26',a+'targetSdkVersion':'36'}))
app=root.find('application'); app.set(a+'testOnly','true'); app.set(a+'debuggable','true'); app.set(a+'allowBackup','false')
ET.SubElement(root,'instrumentation',{a+'name':'ai.nexus.test.NexusInstrumentation',a+'targetPackage':'ai.nexus'})
t.write(sys.argv[1],encoding='utf-8',xml_declaration=True)
PY
"$bt/aapt2" link -I "$plat" --manifest "$out/AndroidManifest.xml" -o "$out/base.apk"
find app/src/androidTest/java -name '*.java' > "$out/sources"
javac --release 8 -encoding UTF-8 -cp "$plat:$main/classes.jar" -d "$out/classes" @"$out/sources"
jar cf "$out/tests.jar" -C "$out/classes" .
"$bt/d8" --min-api 26 --lib "$plat" --classpath "$main/classes.jar" --output "$out/dex" "$out/tests.jar"
(cd "$out/dex" && zip -q -0 ../base.apk classes*.dex)
"$bt/zipalign" -f 4 "$out/base.apk" "$out/aligned.apk"
"$bt/apksigner" sign --ks "${DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$out/NEXUS-TEST.apk" "$out/aligned.apk"
"$bt/apksigner" verify "$out/NEXUS-TEST.apk"
sha256sum "$out/NEXUS-TEST.apk"
