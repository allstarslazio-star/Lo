#!/usr/bin/env bash
# Offline TYPE-CHECK of the new Eco*Ui.kt screens (plus Theme.kt and BusinessUi.kt as a known-good reference)
# against hand-written minimal Compose stubs in tools/compose-stubs. It catches unresolved references, wrong argument
# types and typos. It does NOT run Compose, check @Composable call rules, or replace `gradle assembleDebug` in CI.
# Calibration: the stubs were validated by compiling the existing, CI-proven Theme.kt and BusinessUi.kt cleanly.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE_LIB="${GRADLE_LIB:-$(ls -d /opt/gradle*/lib 2>/dev/null | head -1)}"
KC="$(ls "$GRADLE_LIB"/kotlin-compiler-embeddable-*.jar | head -1)"
VER="$(basename "$KC" | sed 's/kotlin-compiler-embeddable-\(.*\)\.jar/\1/')"
STD="$GRADLE_LIB/kotlin-stdlib-$VER.jar"; COR="$(ls "$GRADLE_LIB"/kotlinx-coroutines-core-jvm-*.jar | head -1)"
CP="$KC:$STD:$GRADLE_LIB/kotlin-script-runtime-$VER.jar:$GRADLE_LIB/kotlin-reflect-$VER.jar:$GRADLE_LIB/kotlin-daemon-embeddable-$VER.jar:$(ls "$GRADLE_LIB"/trove4j-*.jar | head -1):$(ls "$GRADLE_LIB"/annotations-*.jar | head -1):$COR"
MAIN="$ROOT/app/src/main/java/com/nerdfm/game"
UI_EXCLUDE="BusinessUi MainActivity Management MatchUi Menu Shell Squad TacticsLeague Theme TransferUi"
FILES=()
for f in "$MAIN"/*.kt; do b="$(basename "$f" .kt)"
  case " $UI_EXCLUDE " in *" $b "*) ;; *) FILES+=("$f");; esac; done
FILES+=("$MAIN/Theme.kt" "$MAIN/BusinessUi.kt")
OUT="$(mktemp -d)"; LOG="$(mktemp)"
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -cp "$STD:$COR" -jvm-target 17 -d "$OUT" "${FILES[@]}" "$ROOT"/tools/compose-stubs/*.kt > "$LOG" 2>&1 || true
if grep -q "error:" "$LOG"; then grep "error:" "$LOG" | sed "s|$ROOT/||"; echo "UI TYPE-CHECK FAILED"; exit 1; fi
echo "UI type-check OK (${#FILES[@]} files + stubs)"
