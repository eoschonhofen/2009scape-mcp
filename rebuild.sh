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
# shellcheck source=scripts/jdk11.sh
. "$ROOT/scripts/jdk11.sh"

export HOME="$ROOT/.mavenhome"
export MAVEN_USER_HOME="$ROOT/.mavenhome"
export MAVEN_OPTS="-Dmaven.repo.local=$ROOT/.mavenhome/.m2/repository -Xmx3g"
mkdir -p "$HOME"

cd "$ROOT"
bash build -qg
echo
echo "Built: $ROOT/builddir/server.jar"
