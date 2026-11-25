#!/usr/bin/env bash
# Kiem tra host cho logic Folme Spring va XperiaAppBarScrollGate (khong can Android SDK).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
COMMON_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$COMMON_DIR/../../../.." && pwd)"
KOTLINC="${KOTLINC:-$REPO_ROOT/external/kotlinc/bin/kotlinc}"
OUT="${TMPDIR:-/tmp}/xperia-miuix-header-host-check"
rm -rf "$OUT"
mkdir -p "$OUT/classes"

KOTLIN_LIB="$(cd "$(dirname "$KOTLINC")/../lib" && pwd)"
STDLIB="$KOTLIN_LIB/kotlin-stdlib.jar"

"$KOTLINC" -cp "$STDLIB" -d "$OUT/classes" \
    "$COMMON_DIR/src/com/xperia/settings/ui/XperiaMiuixTitleMotion.kt" \
    "$COMMON_DIR/src/com/xperia/settings/ui/XperiaStretchMath.kt" \
    "$COMMON_DIR/src/com/xperia/settings/ui/XperiaScrollPhase.kt" \
    "$COMMON_DIR/src/com/xperia/settings/ui/XperiaTouchAxisOwnership.kt" \
    "$COMMON_DIR/tools/HostXperiaMiuixHeaderCheck.kt" \
    "$COMMON_DIR/tools/HostXperiaMiuixTouchCheck.kt"

java -cp "$OUT/classes:$STDLIB" com.xperia.settings.ui.tools.HostXperiaMiuixHeaderCheck
java -cp "$OUT/classes:$STDLIB" com.xperia.settings.ui.tools.HostXperiaMiuixTouchCheck
