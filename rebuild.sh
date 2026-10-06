#!/usr/bin/env bash
# Rebuild the 2009Scape server jar (skipping tests) and place it in builddir/server.jar.
#
# Usage:
#   ./rebuild.sh
#
# Wraps the repo's own `build` script with the JDK 11 + Maven environment this
# machine needs (Maven's local repo cannot live in ~/.m2 here).
set -euo pipefail

ROOT="$(cd -- "$(dirname "$0")" && pwd)"
JAVA_HOME="$ROOT/.toolchain/jdk-11.0.32.1+1"

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "JDK 11 not found at $JAVA_HOME" >&2
  exit 1
fi

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
export HOME="$ROOT/.mavenhome"
export MAVEN_USER_HOME="$ROOT/.mavenhome"
export MAVEN_OPTS="-Dmaven.repo.local=$ROOT/.mavenhome/.m2/repository -Xmx3g"
mkdir -p "$HOME"

cd "$ROOT"
bash build -qg
echo
echo "Built: $ROOT/builddir/server.jar"
