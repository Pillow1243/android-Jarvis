#!/usr/bin/env bash
# Small offline alternative to Gradle; uses only public Android SDK/JDK command line tools.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Install Android SDK 36 and build tools}" "${JAVA_HOME:?Set JDK 17 home}"
export PATH="$JAVA_HOME/bin:$PATH"
bt=${BUILD_TOOLS:-$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)}
plat=$ANDROID_HOME/platforms/android-36/android.jar
out=build/sdk-debug; rm -rf "$out"; mkdir -p "$out"/{gen,classes,dex} "$HOME/.android"
"$bt/aapt2" compile --dir app/src/main/res -o "$out/resources.zip"
# Gradle normally writes applicationId and uses-sdk at manifest merge time.
sed 's/<manifest /<manifest package="ai.nexus" android:versionName="1.0.0" android:versionCode="1" /;s/<application /<application android:debuggable="true" /;s/<uses-feature /<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"\/><uses-feature /' app/src/main/AndroidManifest.xml > "$out/AndroidManifest.xml"
"$bt/aapt2" link -I "$plat" --manifest "$out/AndroidManifest.xml" --java "$out/gen" -o "$out/base.apk" "$out/resources.zip"
find app/src/main/java "$out/gen" -name '*.java' > "$out/sources"
javac --release 8 -encoding UTF-8 -cp "$plat" -d "$out/classes" @"$out/sources"
jar cf "$out/classes.jar" -C "$out/classes" .
"$bt/d8" --min-api 26 --lib "$plat" --output "$out/dex" "$out/classes.jar"
(cd "$out/dex" && zip -q -0 ../base.apk classes*.dex)
"$bt/zipalign" -f 4 "$out/base.apk" "$out/aligned.apk"
ks=${DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}
if [ ! -f "$ks" ]; then keytool -genkeypair -keystore "$ks" -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US' >/dev/null; fi
"$bt/apksigner" sign --ks "$ks" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$out/JARVIS-NEXUS.apk" "$out/aligned.apk"
"$bt/apksigner" verify --verbose "$out/JARVIS-NEXUS.apk"
sha256sum "$out/JARVIS-NEXUS.apk"
