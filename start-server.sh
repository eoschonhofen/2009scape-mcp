#!/usr/bin/env bash
# Start the 2009Scape game server with JDK 11 (see scripts/jdk11.sh).
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
#  - The heap is capped explicitly. Without -Xmx the JVM defaults to 1/4 of
#    physical RAM (~7.8G on this box), so a forgotten server can balloon.
#    Override with e.g. SERVER_HEAP=4G ./start-server.sh
set -euo pipefail

ROOT="$(cd -- "$(dirname "$0")" && pwd)"
SERVER_HEAP="${SERVER_HEAP:-3G}"

# shellcheck source=scripts/jdk11.sh
. "$ROOT/scripts/jdk11.sh"
if [ ! -f "$ROOT/builddir/server.jar" ]; then
  echo "Missing $ROOT/builddir/server.jar - run ./rebuild.sh first" >&2
  exit 1
fi

export HOME="$ROOT/.mavenhome"
mkdir -p "$ROOT/logs" "$HOME"

cd "$ROOT/Server"
exec java -Xmx"$SERVER_HEAP" -Dnashorn.args=--no-deprecation-warning -jar "$ROOT/builddir/server.jar" "$@"
