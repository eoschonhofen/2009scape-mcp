#!/usr/bin/env bash
# Control the local 2009Scape podman stack: game server + MariaDB.
#
# Usage: ./stack.sh up|down|status|logs
set -euo pipefail

ROOT="$(cd -- "$(dirname "$0")" && pwd)"
cd "$ROOT"

COMPOSE=(podman-compose -p 2009scape -f docker-compose.yml)

health() {
  podman inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
    "$1" 2>/dev/null || echo missing
}

case "${1:-}" in
  up)
    "${COMPOSE[@]}" up -d
    echo
    echo "Waiting for the game server to listen on 43595..."
    echo "(the container rebuilds the jar first; a clean checkout takes several minutes)"
    for _ in $(seq 1 450); do
      if [ "$(health 2009scape_server_1)" = "healthy" ]; then
        echo "Server is up: 127.0.0.1:43595"
        exit 0
      fi
      sleep 2
    done
    echo "Server did not come up within 15 minutes. Check ./stack.sh logs" >&2
    exit 1
    ;;
  down)
    "${COMPOSE[@]}" down
    ;;
  status)
    podman ps -a --filter name=2009scape \
      --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
    ;;
  logs)
    podman logs --tail 80 2009scape_server_1
    ;;
  *)
    echo "usage: $0 up|down|status|logs" >&2
    exit 2
    ;;
esac
