# Moving the public world to a VPS (AIO-17)

This is the **deferred** migration path: run the Fedora deployment until it is stable, then
move it. `docker-compose.public.yml` is already the hardened stack; these are the steps around
it.

## 1. What changes from the development compose

| | `docker-compose.yml` (dev) | `docker-compose.public.yml` |
|---|---|---|
| Published ports | `43594-43600` | `43595` only |
| Debugger | `5005` open, JVM started with `-agentlib:jdwp=…address=*:5005` | none |
| Database | `db` reachable on the compose network | same, and never published |
| Profile | `./config` mounted read-write | `public.conf` and `Server/data/rsa` mounted read-only |
| Entry point | `./run` (builds, then `default.conf`) | `deploy/vps/run-public.sh` (builds if needed, then `public.conf`) |

The JDWP listener was the real problem: on a public host it is unauthenticated remote code
execution. It must not exist in the public stack.

## 2. Secrets

`mysql.env` holds the database user and password; it is gitignored. `config/public.conf` holds
the same password for the game server and is also gitignored (`/config` in the root
`.gitignore`). Neither belongs in an image, a repository or a ticket.

## 3. Data migration

```bash
# On the Fedora box, with the world stopped:
podman exec 2009scape-db mariadb-dump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
    --single-transaction global > global-$(date +%F).sql

# Saves are files, not rows:
tar czf 2009scape-data-$(date +%F).tgz \
    Server/data/players Server/data/playerstats Server/data/eco

# On the VPS:
docker compose -f docker-compose.public.yml up -d db
docker compose -f docker-compose.public.yml exec -T db \
    mariadb -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" global < global-2026-10-06.sql
tar xzf 2009scape-data-2026-10-06.tgz
docker compose -f docker-compose.public.yml up -d
```

Copy `Server/data/rsa/` too, unchanged (see below).

## 4. Keep the same RSA key pair

Every released jar has the public modulus baked in. Generating a new pair means every operator
must download a new client, so copy `Server/data/rsa/private.key` across byte for byte and
leave `rsa_key_path` as it is.

## 5. DNS

Point `publicHost` at a **hostname**, not an IP address, from day one. The release jar bakes
the host in, so a hostname lets you move the world by changing one DNS record; an IP address
means a new release for every operator.

## 6. Dry run

Migrate into a local compose stack first and run the AIO-16 smoke script against it:

```bash
python3 -I client/scripts/aionly-smoke.py --url http://127.0.0.1:43600/mcp --token <token>
```

## Acceptance

- An existing agent logs in with its old token on the VPS and its save is intact.
- `nmap` against the VPS shows only `43595` (plus SSH). Nothing else: no 5005, no 3306.
