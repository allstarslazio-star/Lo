#!/usr/bin/env bash
# Offline compile + JUnit run of everything that does not need the Android SDK (all simulation code and tests).
# NOT a replacement for `gradle testDebugUnitTest assembleDebug` (CI is the source of truth): it skips the
# Compose UI files, so UI compile errors are only caught by CI.
# Uses the Kotlin compiler that ships inside Gradle (no network needed). Usage: tools/jvm-check.sh [TestClass ...]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE_LIB="${GRADLE_LIB:-$(ls -d /opt/gradle*/lib 2>/dev/null | head -1)}"
[ -d "$GRADLE_LIB" ] || { echo "Set GRADLE_LIB to a Gradle lib directory containing kotlin-compiler-embeddable"; exit 2; }
KC="$(ls "$GRADLE_LIB"/kotlin-compiler-embeddable-*.jar | head -1)"
VER="$(basename "$KC" | sed 's/kotlin-compiler-embeddable-\(.*\)\.jar/\1/')"
STD="$GRADLE_LIB/kotlin-stdlib-$VER.jar"
CP="$KC:$STD:$GRADLE_LIB/kotlin-script-runtime-$VER.jar:$GRADLE_LIB/kotlin-reflect-$VER.jar:$GRADLE_LIB/kotlin-daemon-embeddable-$VER.jar:$(ls "$GRADLE_LIB"/trove4j-*.jar | head -1):$(ls "$GRADLE_LIB"/annotations-*.jar | head -1):$(ls "$GRADLE_LIB"/kotlinx-coroutines-core-jvm-*.jar | head -1)"
JUNIT="$(ls "$GRADLE_LIB"/junit-4*.jar | head -1):$(ls "$GRADLE_LIB"/hamcrest-core-*.jar | head -1)"
OUT="${OUT:-$(mktemp -d)}"
MAIN="$ROOT/app/src/main/java/com/nerdfm/game"
TEST="$ROOT/app/src/test/java/com/nerdfm/game"
UI="BusinessUi MainActivity Management MatchUi Menu Shell Squad TacticsLeague Theme TransferUi HomeUi DiagnosticsUi PlayerBrowserUi UiComponents"
FILES=()
for f in "$MAIN"/*.kt; do b="$(basename "$f" .kt)"; case " $UI " in *" $b "*) ;; *) FILES+=("$f");; esac; done
echo "compiling ${#FILES[@]} main + $(ls "$TEST"/*.kt | wc -l) test files with Kotlin $VER ..."
LOG="$(mktemp)"
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -cp "$STD:$JUNIT" -jvm-target 17 -d "$OUT" "${FILES[@]}" "$TEST"/*.kt > "$LOG" 2>&1 || true
grep -v "^warning:\|JAVA_TOOL_OPTIONS" "$LOG" || true
if grep -q "error:" "$LOG"; then echo "COMPILE FAILED"; exit 1; fi
if [ $# -gt 0 ]; then CLASSES="$*"; else
  CLASSES="$(cd "$OUT" && find . -name '*Test.class' ! -name '*$*' | sed 's|^\./||; s|\.class$||; s|/|.|g' | sort | tr '\n' ' ')"; fi
cd "$ROOT/app"   # Gradle runs unit tests with the module directory as working directory; tests rely on it
java -cp "$OUT:$ROOT/app/src/test/resources:$STD:$JUNIT" org.junit.runner.JUnitCore $CLASSES 2>&1 | grep -v JAVA_TOOL_OPTIONS
