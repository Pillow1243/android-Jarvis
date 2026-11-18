#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?}" "${JAVA_HOME:?}"
classes=build/sdk-debug/classes
[ -d "$classes" ] || { echo 'Run scripts/sdk-build.sh first.' >&2; exit 1; }
cp="$classes:$ANDROID_HOME/platforms/android-36/android.jar"
mkdir -p build/host-tests
"$JAVA_HOME/bin/javac" --release 8 -encoding UTF-8 -cp "$cp" -d build/host-tests app/src/test/java/ai/nexus/CoreChecks.java
"$JAVA_HOME/bin/java" -cp "build/host-tests:$cp" ai.nexus.CoreChecks
