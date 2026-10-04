#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ]]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
  elif [[ -x /usr/libexec/java_home ]]; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  elif ! command -v java >/dev/null 2>&1; then
    echo 'Install JDK 17 and set JAVA_HOME, or add java to PATH.' >&2
    exit 1
  fi
fi
if [[ $# -eq 0 ]]; then
  set -- :app:testCommunityDebugUnitTest :app:assembleCommunityDebug
fi
./gradlew "$@"
