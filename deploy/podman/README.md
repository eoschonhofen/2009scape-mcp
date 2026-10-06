# MariaDB in a podman quadlet

A persistent MariaDB with the `global` schema, bound to loopback, as a rootless
user service. This is the database the public AI-only profile
(`Server/worldprops/public.conf`) connects to with `ProductionAuthenticator` +
`SQLStorageProvider`.

## Requirements

- podman 4.4 or newer (for the `HealthCmd` quadlet keys)
- a working **systemd user session** — quadlets are started by `systemd --user`,
  not by podman directly
- this checkout, and a `mysql.env` next to it

## Install

```bash
# 1. Credentials. mysql.env is gitignored; never commit it.
cp mysql.env.example mysql.env
$EDITOR mysql.env          # set MYSQL_USER / MYSQL_PASSWORD, see below

# 2. The data directory. podman does not create a bind-mount source: without this
#    the first start fails with "statfs .../db: no such file or directory".
mkdir -p ~/.local/share/2009scape/db

# 3. Quadlet. Replace <REPO> with the absolute path of this checkout.
sed "s#<REPO>#$PWD#g" deploy/podman/2009scape-db.container \
    > ~/.config/containers/systemd/2009scape-db.container

# 4. Start it. The first start pulls the image and seeds the schema.
systemctl --user daemon-reload
systemctl --user start 2009scape-db
```

`mysql.env` for the public profile should use a dedicated user rather than root,
and the values must be **unquoted**:

```ini
MYSQL_DATABASE=global
MYSQL_USER=scape
MYSQL_PASSWORD=<random>
MYSQL_RANDOM_ROOT_PASSWORD=yes
```

podman's `--env-file` keeps quote characters, so `MYSQL_PASSWORD="secret"` makes
the password `"secret"` *including the quotes* — and the `scape` login then fails
with `Access denied`, while an operator who reads the file with a shell sees the
unquoted value. Docker Compose strips the quotes, so the example file, which is
written for Compose, has them; remove them for the quadlet.

The image creates `MYSQL_USER` and grants it every privilege on `MYSQL_DATABASE`
before the init files run, so `global.sql` needs no grants of its own. `global.sql`
still starts with `CREATE DATABASE IF NOT EXISTS global;`: the entrypoint has
already created `MYSQL_DATABASE`, and the plain `CREATE DATABASE global;` this dump
used to carry makes the mysql client abort at line 1, leaving the schema empty. If
the grant is ever missing, uncomment the statements in `grants.sql` (or run them
once by hand) and recreate the volume.

To reset the world, stop the service and remove the volume **inside the user
namespace** — the files belong to the container's mapped user, so a plain
`rm -rf` fails with `Permission denied`:

```bash
systemctl --user stop 2009scape-db
podman unshare rm -rf ~/.local/share/2009scape/db
mkdir -p ~/.local/share/2009scape/db
systemctl --user start 2009scape-db
```

## Verify

```bash
systemctl --user status 2009scape-db
podman exec 2009scape-db mariadb -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" global \
    -e 'SHOW TABLES'
ss -ltn | grep 3306        # must show 127.0.0.1:3306, never 0.0.0.0:3306
```

`SHOW TABLES` must list `members`. To prove persistence:

```bash
systemctl --user restart 2009scape-db
podman exec 2009scape-db mariadb -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" global \
    -e 'SELECT COUNT(*) FROM members'
```

The data lives in `~/.local/share/2009scape/db` (`%h` in the quadlet), so it also
survives a reboot; the unit carries `WantedBy=default.target` and is pulled in by
`default.target`, which `systemctl --user is-enabled 2009scape-db` reports as
`generated`.

## Backup and restore

`mysqldump` is the migration path AIO-17 reuses:

```bash
podman exec 2009scape-db mariadb-dump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
    --single-transaction global > global-$(date +%F).sql
# restore, on this host or a VPS:
podman exec -i 2009scape-db mariadb -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" global \
    < global-2026-10-06.sql
```

Account **saves** are not in MariaDB; copy `Server/data/players/`,
`Server/data/playerstats/` and `Server/data/eco/` as well.

## Verification on the development host (2026-10-06)

Executed end to end on the Fedora box (podman 5.8.4, image
`docker.io/library/mariadb:11.4-noble`, MariaDB 11.4.13):

```
$ systemctl --user start 2009scape-db
$ systemctl --user status 2009scape-db
     Active: active (running) since Tue 2026-10-06 17:36:12 -03

$ podman exec 2009scape-db mariadb -uscape -p*** global -e 'SHOW TABLES'
Tables_in_global
members
worlds

$ podman exec 2009scape-db mariadb -uscape -p*** global -e 'SELECT COUNT(*) FROM members'
0

$ ss -ltn | grep 3306
LISTEN 0  128  127.0.0.1:3306  0.0.0.0:*        # no 0.0.0.0 listener

$ podman healthcheck run 2009scape-db && echo OK
OK
```

Persistence was proved by inserting a row into `members`, running
`systemctl --user restart 2009scape-db`, reading it back (`1` before and after)
and deleting it again. `systemctl --user is-enabled 2009scape-db` reports
`generated`, the unit appears in `systemctl --user list-dependencies default.target`,
and it carries `Restart=always`, so the service comes back after a reboot; the data
is on the host at `~/.local/share/2009scape/db`.

Three problems only showed up on a clean first run, and all three are fixed above:
the missing bind-mount directory, the quoted `MYSQL_PASSWORD` that podman keeps
literally, and `CREATE DATABASE global;` in `Server/db_exports/global.sql`, which
aborted the import against the database the entrypoint had already created.
