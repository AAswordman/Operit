#!/usr/bin/env bash
# Runs the OAuth protocol/session/listener tests without an Android SDK or Gradle.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
KOTLINC="${KOTLINC:-$(command -v kotlinc)}"
KOTLIN_HOME="${KOTLIN_HOME:-$(cd "$(dirname "$(readlink -f "$KOTLINC")")/.." && pwd)}"
COROUTINES_JAR="${COROUTINES_JAR:-$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar}"
if [[ ! -f "$COROUTINES_JAR" ]]; then
  echo 'Set COROUTINES_JAR to a kotlinx-coroutines-core JVM jar.' >&2
  exit 1
fi
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
MAIN="$ROOT/app/src/main/java/com/ai/assistance/operit/core/auth"
TEST="$ROOT/app/src/test/java/com/ai/assistance/operit/core/auth"
"$KOTLINC" "$MAIN/ProviderOAuthProtocol.kt" "$MAIN/ProviderOAuthSessions.kt" \
  "$MAIN/ProviderOAuthConfigCodec.kt" "$MAIN/ProviderOAuthTokenResponse.kt" \
  "$MAIN/ProviderOAuthLoopbackServer.kt" "$TEST/ProviderOAuthRegressionCases.kt" \
  "$ROOT/tools/toolpkg_oauth/CoreTestMain.kt" \
  -cp "$COROUTINES_JAR" -include-runtime -d "$BUILD/tests.jar"
java -cp "$BUILD/tests.jar:$COROUTINES_JAR" com.ai.assistance.operit.core.auth.CoreTestMainKt
