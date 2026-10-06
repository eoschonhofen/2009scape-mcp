#!/usr/bin/env bash
# Start the 2009Scape game server using the locally installed JDK 11.
#
# Usage:
#   ./start-server.sh              # uses Server/worldprops/default.conf
#   ./start-server.sh <conf-file>  # uses a specific config file
#
# Notes:
#  - JDK 11 is REQUIRED. The server runs JavaScript content via Nashorn
#    (-Dnashorn.args=...), which was removed from the JDK after 14.
#  - HOME and MAVEN_USER_HOME are redirected into the project because
#    ~/.m2 is not writable in this environment.
set -euo pipefail

ROOT="$(cd -- "$(dirname "$0")" && pwd)"
JAVA_HOME="$ROOT/.toolchain/jdk-11.0.32.1+1"

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "JDK 11 not found at $JAVA_HOME" >&2
  exit 1
fi
if [ ! -f "$ROOT/builddir/server.jar" ]; then
  echo "Missing $ROOT/builddir/server.jar - run ./rebuild.sh first" >&2
  exit 1
fi

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
export HOME="$ROOT/.mavenhome"
mkdir -p "$ROOT/logs" "$HOME"

cd "$ROOT/Server"
exec java -Dnashorn.args=--no-deprecation-warning -jar "$ROOT/builddir/server.jar" "$@"
