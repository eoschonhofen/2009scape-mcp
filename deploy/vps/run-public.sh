#!/usr/bin/env bash
# AIO-17 — container entrypoint for the public profile.
#
# The image's own ./run always builds and then starts the server with the default
# profile. The public stack wants the already-built jar and worldprops/public.conf,
# so build only when the jar is missing and then pass the profile explicitly.
set -euo pipefail

cd /app

if [ ! -f builddir/server.jar ]; then
  echo "building server.jar..."
  bash build -qg
fi

cd /app/Server
exec java -Dnashorn.args=--no-deprecation-warning -jar /app/builddir/server.jar worldprops/public.conf
