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

# 2. Quadlet. Replace <REPO> with the absolute path of this checkout.
sed "s#<REPO>#$PWD#g" deploy/podman/2009scape-db.container \
    > ~/.config/containers/systemd/2009scape-db.container

# 3. Start it.
systemctl --user daemon-reload
systemctl --user start 2009scape-db
```

`mysql.env` for the public profile should use a dedicated user rather than root:

```ini
MYSQL_DATABASE="global"
MYSQL_USER="scape"
MYSQL_PASSWORD="<random>"
MYSQL_RANDOM_ROOT_PASSWORD="yes"
```

The image creates `MYSQL_USER` and grants it every privilege on `MYSQL_DATABASE`
before the init files run, so `global.sql` needs no grants of its own. If the
grant is ever missing, uncomment the statements in `grants.sql` (or run them once
by hand) and recreate the volume.

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
survives a reboot. Removing that directory resets the world.

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

The quadlet files were written and the install steps above were exercised as far
as the agent's sandbox allows. Container startup could **not** be executed there:

```
$ systemctl --user status
Failed to connect to user scope bus via local transport: No data available

$ podman info
Failed to obtain podman configuration: set sticky bit on: chmod /run/user/1000/libpod: read-only file system

$ XDG_RUNTIME_DIR=<writable> podman info
Error: cannot set up namespace using "/usr/bin/newuidmap": exit status 1
    newuidmap: write to uid_map failed: Operation not permitted
```

Both are sandbox restrictions, not host problems: Hermes, SearXNG and the vision
quadlets already run as user services on this box. **Run steps 1–3 and the
verification commands from a normal desktop terminal**, and record their output
here. `mariadbd` (mariadb-server 10.11) is also installed natively, which is the
fallback if the quadlet cannot be used at all.
