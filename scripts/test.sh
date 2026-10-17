#!/bin/sh
# Run the corrected implementation's tests without Maven.
# The test class uses JUnit 5 annotations; this script compiles it only when a
# JUnit jar is on the classpath, otherwise it falls back to the standalone probe.
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
BUILD="$ROOT/build/test-classes"
rm -rf "$BUILD"
mkdir -p "$BUILD"

MAIN="$ROOT/src/main/java/com/thealgorithms/dynamicprogramming/LevenshteinDistance.java"
TEST="$ROOT/src/test/java/com/thealgorithms/dynamicprogramming/LevenshteinDistanceTests.java"

# javac on MSYS resolves /e/... to \e\...; hand it Windows-style paths instead.
winpath() {
    printf '%s' "$1" | sed -e 's|^/\([a-zA-Z]\)/|\1:/|'
}

MAIN_W="$(winpath "$MAIN")"
TEST_W="$(winpath "$TEST")"
BUILD_W="$(winpath "$BUILD")"

if [ -n "${JUNIT_CP:-}" ]; then
    javac -encoding UTF-8 -cp "$JUNIT_CP" -d "$BUILD_W" "$MAIN_W" "$TEST_W"
    java -cp "$BUILD_W:$JUNIT_CP" org.junit.platform.console.ConsoleLauncher \
        --select-class com.thealgorithms.dynamicprogramming.LevenshteinDistanceTests
else
    echo "JUNIT_CP not set; running the dependency-free probe instead."
    echo "Set JUNIT_CP to a junit-platform-console-standalone jar to run the real suite."
    PROBE="$ROOT/src/test/java/com/thealgorithms/dynamicprogramming/LevenshteinDistanceProbe.java"
    PROBE_W="$(winpath "$PROBE")"
    javac -encoding UTF-8 -d "$BUILD_W" "$MAIN_W" "$PROBE_W"
    java -cp "$BUILD_W" com.thealgorithms.dynamicprogramming.LevenshteinDistanceProbe
fi
