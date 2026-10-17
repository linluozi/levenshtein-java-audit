#!/bin/sh
# Compile and run the audit probes.
#
# Every measurement spawns its own JVM on purpose: a long-input case run first
# in the same process deoptimises the short-input case by 3.8x (see README).
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
BUILD="$ROOT/build/audit-classes"

winpath() {
    printf '%s' "$1" | sed -e 's|^/\([a-zA-Z]\)/|\1:/|'
}

BUILD_W="$(winpath "$BUILD")"
mkdir -p "$BUILD_W"

javac -encoding UTF-8 -d "$BUILD_W" "$(winpath "$ROOT")"/audit/*.java

CP="$BUILD_W"
run() {
    java -Dfile.encoding=UTF-8 -cp "$CP" "$@"
}

echo "== deterministic: DP cells =="
run audit.Cells

echo
echo "== deterministic: interface contract =="
run audit.Contract

echo
echo "== mutants vs. the shipped test logic =="
run audit.Poison

echo
echo "== mutants vs. the corrected suite =="
run audit.Poison2

echo
echo "== cross-case pollution: 8 000 x 24, fresh vs. polluted process =="
run audit.Pollute2 clean
run audit.Pollute2 p1-opt

echo
echo "== one case, two arms, interleaved =="
for case in P1 P2 P3 R1 R2 R3 T1 T2; do
    run audit.Two "$case" P Q
done
