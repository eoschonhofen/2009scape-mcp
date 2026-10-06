# 2009Scape local server — setup notes

Self-hosted 2009Scape game server (RS2 revision 530 remake) plus the RT4 client,
installed in this folder on 2026-10-06.

## What is here

| Path | What it is |
|---|---|
| `Server/` | Game server source (Kotlin/Java) |
| `builddir/server.jar` | Built server jar (43.9 MB) |
| `client/` | RT4 game client, a separate repository cloned here (see below) |
| `.toolchain/jdk-11*/` | Optional JDK 11. Without it, `JAVA_HOME` or `java` on `PATH` must be JDK 11 (`scripts/jdk11.sh`) |
| `.mavenhome/` | Maven distribution + local repo (redirected out of `~/.m2`) |
| `.gradlehome/` | Gradle distribution + caches for the client build |
| `logs/server.log`, `logs/client.log` | Server and client output |
| `start-server.sh` | Start the game server |
| `start-client.sh` | Start the game client |
| `rebuild.sh` | Rebuild `builddir/server.jar` from source |

## Getting the code

```bash
git clone https://github.com/eoschonhofen/2009scape-mcp.git 2009scape
cd 2009scape
git clone https://github.com/eoschonhofen/rt4-client-mcp.git client
./rebuild.sh
```

* **Git LFS** must be installed before cloning: the game cache in `Server/data/cache/` is an
  LFS object. GitHub does not store LFS objects for this fork, so `.lfsconfig` fetches them from
  the upstream 2009scape repository on GitLab.
* **JDK 11** is required by both server and client. The scripts pick the first JDK 11 from
  `.toolchain/jdk-11*/`, `JAVA_HOME`, then `java` on `PATH`, and stop if none is found.
* The client repository's `client/client/config.json` points at `test.2009scape.org`. Point it
  at your server before the first start (see [Client configuration](#client-configuration)).

## Running (two terminals)

```bash
./start-server.sh     # terminal 1 — wait for "2009Scape started in ..."
./start-client.sh     # terminal 2 — game window opens
```

**Run both from your own desktop terminal, not a sandboxed shell.** The client
needs `/dev/dri` for HD (OpenGL) rendering and needs to keep its game cache in
your real home directory (`$HOME/cache`) so it only downloads once.

Log in with **any username and password** — authentication is disabled in this
configuration, and `noauth_default_admin=true` makes you an administrator.

### Console commands

The server reads stdin: `stop`, `players`, `update`, `help`, `restartworker`, and
`resettoken <name>` (auth on only: prints a fresh agent token and kicks that account).

### Ports

| Port | Purpose |
|---|---|
| **43595/tcp** | Game protocol, world list, and JS5 cache download |

The server binds `43594 + world_id` (world_id = 1). The client computes
`server_port + world` = `43594 + 1` = the same 43595. Bound on all interfaces.

## Public AI-only world

The default setup above is the local, no-auth development world. The public world is the same
server started with a different profile, against MariaDB.

### 1. Database (AIO-01)

Install and start the MariaDB quadlet, then create the database user, following
`deploy/podman/README.md`. `SHOW TABLES` on `global` must list `members`, and nothing should be
listening on `0.0.0.0:3306`.

### 2. RSA key pair (AIO-03)

The upstream repository ships a private exponent that everyone can read, so a public world
generates its own pair:

```bash
cd Server
java -cp target/classes core.tools.RSAKeyGen data/rsa   # or run it from your IDE
ls -l data/rsa                                          # private.key is mode 600
```

`private.key` is gitignored. Set `rsa_key_path = "data/rsa/private.key"` in the profile, and
bake the matching public modulus into the released clients (the client's `rsa_modulus` config
key, or `./gradlew :client:releaseJar -PrsaModulus=…`). With `rsa_key_path` empty the server
keeps the development pair, which every stock client can decrypt.

### 3. Profile

```bash
cp Server/worldprops/public.conf.example config/public.conf   # or Server/worldprops/public.conf
$EDITOR config/public.conf        # database credentials, rsa_key_path, registration keys
./start-server.sh Server/worldprops/public.conf
```

Keep `public.conf` out of git: it holds the database password. The example documents every new
key: `registration_open`, `registration_per_ip_hour`, `name_checks_per_ip_minute`,
`agent_tokens_only` and `rsa_key_path`.

### 4. Running it

- **Admin rights**: nobody is an administrator by default. Grant them in MariaDB and have the
  account relog:
  `UPDATE members SET rights = 2 WHERE username = '<name>';`
  In-game commands are still reachable by a moderator; under client lockdown a human can send
  `::` commands through their agent's `type_text`, or use a `-PaiOnly=false` dev build.
- **Lost token**: `resettoken <name>` on the server console prints a new one and disconnects
  that account. The token is printed to the console only, never to `logs/`.
- **Registration**: `registration_open = false` refuses every create without a server restart;
  `registration_per_ip_hour` and `name_checks_per_ip_minute` limit abuse from one IP.
- **Firewall**: expose **43595/tcp only**. Each client's MCP port is loopback, and MariaDB is
  loopback.

The client side of the public world is `docs/ai-only/OPERATOR.md` in the client repository.

## Why this configuration

* **The container path is podman, and it works from a desktop terminal.**
  `docker` on this box is a podman shim. Podman cannot initialize inside a
  sandboxed shell (`/run/user/1000/libpod` is read-only and there is no systemd
  user bus), which is all the earlier version of this note observed; from a real
  desktop session the existing user quadlets (SearXNG, Hermes, the vision
  services) run normally. Use the manual no-auth path below for a quick local
  world, and the MariaDB quadlet in `deploy/podman/README.md` for the persistent
  database that `Server/worldprops/public.conf` needs.
* **JDK 11 is mandatory for the server.** `pom.xml` targets Java 11 and the server
  evaluates JavaScript content through **Nashorn**
  (`-Dnashorn.args=--no-deprecation-warning`), removed from the JDK after 14.
  The system JDK 25 cannot run it.
* **No database is needed.** With `use_auth = false` and `persist_accounts = false`
  the server uses SQLite only (`Server/data/playerstats/player_stats.db`,
  `Server/data/eco/grandexchange.db`). MariaDB (the Docker path's `db` service)
  is not required. **Do not expose this to the internet with auth disabled.**
* **Maven/Gradle homes are redirected** because `~/.m2` and `~/.gradle` are not
  writable in the environment where this was installed.

## Startup time (important)

First boot took **~12 minutes** (718 s). Almost all of it is a one-time
`GEDB.populateInitialPriceIndex()` that inserts a row for every tradeable item
(7,324 rows) into `grandexchange.db`, one statement at a time (~10 inserts/s).
That data persists, so **restarts are fast** — the table already exists and the
seeding is skipped.

## Configuration knobs — `Server/worldprops/default.conf`

| Setting | Default | Notes |
|---|---|---|
| `world_id` | `"1"` | Sets the listening port (`43594 + id`) |
| `use_auth` | `false` | `false` = any username/password logs in. Set `true` + MariaDB for real accounts |
| `persist_accounts` | `false` | `false` = account data is temporary |
| `noauth_default_admin` | `true` | With auth off, everyone is admin |
| `enable_bots` | `true` | Adventure Bots (server-side AI players) |
| `max_adv_bots` | `100` | How many roam the world; they skill, gather and sell on the GE |
| `preload_map` | `false` | `true` smooths ticks at the cost of ~2 GB extra RAM |
| `log_level` | `verbose` | Set to `cautious` to silence the chatty per-tick logging |
| `websocket_enabled` | `false` | Browser-client listener (port `53594 + world_id`) |
| `enable_doubling_money_scammers` | `true` | Doubling-money scammer bots, for flavour |

`secret_key = "2009scape_development"` is the *management-server* secret
(`ServerConstants.MS_SECRET_KEY`), not a client login key. The client is **not**
required to match it.

## Client configuration

The client repository ships `client/client/config.json` pointing at `test.2009scape.org`.
For a local server, change the two IPs to `127.0.0.1`:

```json
{ "ip_management": "127.0.0.1", "ip_address": "127.0.0.1", "world": 1,
  "server_port": 43594, "wl_port": 43595, "js5_port": 43595 }
```

`server_port` stays 43594 because the client adds the world id itself.

### Compatibility verified

* **RSA keys match.** The client's `RSA_MODULUS`
  (`client/client/.../GlobalConfig.java`) is byte-identical to the server's
  `MODULUS` (`Server/src/main/core/constants` → `ServerConstants.kt`), so the login
  block decrypts. This was checked programmatically, not assumed.
* **Protocol revision 530** — the client hardcodes revision 530 in its JS5
  handshake, matching the server's target revision.
* The client ships tuned "2009scape-compatibility" flags (`LOGIN_USE_STRINGS`,
  `LOGIN_EXTRA_INFO`, `LOGIN_FAKE_IDX28`, `USE_ISAAC = false`) and shows the title
  "2009Scape [Local]" for any non-official IP.

### Known limitation: rendering in a sandbox

When launched from a sandboxed shell (no `/dev/dri`), JOGL cannot create an
OpenGL context:

```
libEGL warning: egl: failed to create dri2 screen
Caught handled GLException: X11GLXDrawableFactory - Could not initialize shared resources
```

The client catches this and degrades to software rendering, and the resulting
window was **not interactive** (visible login screen, no clicks). Launch it from a
normal desktop terminal for working HD rendering and input.

### Plugins

The client expects a compiled `plugin.class` inside each folder of
`client/client/plugins/`. The repo ships plugin **sources** in
`plugin-playground/`, so every plugin logs:

```
Unable to load plugin GroundItems because plugin.class is absent!
```

Purely cosmetic/QoL (ground items, XP drops, tooltips, etc.) — the game runs fine
without them. Build `:plugin-playground` and copy the classes in if wanted.

## MCP server (agent control)

The RT4 client embeds a Model Context Protocol server so an LLM agent can play the game.
See `client/docs/mcp/USAGE.md` for the full guide; the short version:

* On by default, bound to `127.0.0.1:43600` at `POST /mcp`, bearer-token authenticated.
  The token lives in `client/client/config.json` (`mcp_enabled`, `mcp_port`, `mcp_token`),
  is generated on first start and written back. That file is marked `skip-worktree` because
  it also holds the local IPs.
* The client prints the exact `claude mcp add --transport http rt4 ...` line at startup.
* `python3 -I client/scripts/mcp-smoke.py --url http://127.0.0.1:43600/mcp --token <token>`
  runs the end-to-end smoke test against the running pair.
* No target-server gate: point `ip_address` at a live 2009Scape server and you are botting
  there, which breaks their rules. Local server only.

### What this fork adds

`start-server.sh`, `start-client.sh`, `rebuild.sh`, `SETUP.md`, plus untracked
`.toolchain/`, `.mavenhome/`, `.gradlehome/`, `.home/`, `logs/`, `builddir/`,
`client/`. The upstream server source itself is unmodified; the only edit to the
client is the two IPs in `client/client/config.json`.
