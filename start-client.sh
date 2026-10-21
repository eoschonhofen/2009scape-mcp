#!/usr/bin/env bash
# Launch the 2009Scape client (RT4) against the local server.
#
# IMPORTANT: run this from your own desktop terminal, not from a sandboxed shell.
# It needs /dev/dri for HD (OpenGL) rendering, and it keeps the game cache in
# your real home directory so it only downloads once.
#
# The game server must already be running (./stack.sh up).
#
# This builds the development client (-PaiOnly=false): the native login form and
# account creation work and real mouse/keyboard input reaches the game, so humans
# can play. The embedded MCP server still starts, so an agent logs in the normal
# way too (MCP `login` with the account's username and password). For the locked
# AI-only build use:  ./gradlew :client:run -PaiOnly=true
set -euo pipefail

ROOT="$(cd -- "$(dirname "$0")" && pwd)"
# shellcheck source=scripts/jdk11.sh
. "$ROOT/scripts/jdk11.sh"

if [ ! -x "$ROOT/client/gradlew" ]; then
  echo "RT4 client not found at $ROOT/client - clone the client repository there (see SETUP.md)" >&2
  exit 1
fi

export GRADLE_USER_HOME="$ROOT/.gradlehome"

cd "$ROOT/client"
echo "Connecting to 127.0.0.1 (server_port 43594 + world 1 = port 43595)"
exec ./gradlew :client:run --console=plain -PaiOnly=false
