# Local development

Build, run and restart the server and the client on this machine. Everything here is verified on
the dev box; the scripts already encode most of it, so prefer them over hand-rolled commands.

## Requirements and where things live

| Thing | Location | Note |
|---|---|---|
| JDK 11 | `.toolchain/jdk-11.0.32.1+1` | **Required.** The system `java` is JDK 25 and cannot run the server (Nashorn, removed after 14) |
| JDK selection | `scripts/jdk11.sh` | Sourced by `rebuild.sh`, `start-server.sh`, `start-client.sh`; first JDK 11 from `.toolchain/jdk-11*`, `$JAVA_HOME`, then `PATH` |
| Maven | `Server/mvnw` | There is no `mvn` on `PATH` |
| Maven home | `.mavenhome` | `HOME`/`MAVEN_USER_HOME` are redirected here; the real `~/.m2` is not writable |
| Gradle home | `.gradlehome` | Set `GRADLE_USER_HOME`; caches are already populated, so `--offline` works |
| Game cache | `$HOME/.local/share/2009scape/cache`, else `/tmp/cache/runescape` | `SignLink` picks the first writable location; the first client start downloads it (~13 min once) |
| Display | `DISPLAY=:0`, `/dev/dri` | The client needs GL. Start it from a shell with the desktop session's environment, not a sandbox |

The scripts put JDK 11 on `PATH` themselves. When you call Gradle or Maven by hand, set it first:

```bash
ROOT=$PWD
export JAVA_HOME="$ROOT/.toolchain/jdk-11.0.32.1+1" PATH="$ROOT/.toolchain/jdk-11.0.32.1+1/bin:$PATH"
```

## Server

```bash
./rebuild.sh                       # Build builddir/server.jar (mvn package -DskipTests; full build, minutes)
./start-server.sh                  # default.conf: the local no-auth world
./start-server.sh ../config/public.conf   # any other profile
```

- **The profile path is resolved relative to `Server/`, not the repo root.** `ServerConfigParser.parsePath`
  strips the leading `/`, so an absolute path becomes `Server/home/...` and the server exits with
  `... does not exist`. Pass a path relative to `Server/` (e.g. `../config/public.conf`) or one
  that already is.
- Startup is quick (`2009Scape started in ~5000 milliseconds`); wait for that line in the log, not
  a fixed sleep. The `~12 minutes` figure in the README is the first-ever boot.
- The local `default.conf` world has `use_auth = false` and `noauth_default_admin = true`: any
  username and password works and you are an administrator.
- The JVM heap is capped at 3 GB by `start-server.sh`; override with `SERVER_HEAP=4G`.
- Logs go to stdout plus `write_logs` files under `Server/data/logs`. Bot activity fills
  `logs/server.log` fast; filter before reading.

Tests:

```bash
ROOT=$PWD; . scripts/jdk11.sh
export HOME="$ROOT/.mavenhome" MAVEN_USER_HOME="$ROOT/.mavenhome"
export MAVEN_OPTS="-Dmaven.repo.local=$ROOT/.mavenhome/.m2/repository -Xmx3g"
cd Server
./mvnw test                        # full suite
./mvnw -o -q test -Dtest=AgentTokenTest,RegistrationLimiterTest   # a subset, offline
```

Surefire XML results land in `Server/target/surefire-reports/`.

## Client

The client is a separate repo ([workspace-map.md](workspace-map.md) — build the right branch).

```bash
cd client
export GRADLE_USER_HOME="$ROOT/.gradlehome"
./gradlew --offline :client:test                 # unit tests
./gradlew --offline :client:test --tests 'rt4.aionly.*'
./gradlew :client:run                            # dev client (add -PaiOnly=false to unlock input)
./gradlew :client:releaseJar -PpublicHost=127.0.0.1          # locked AI-only jar, dev key pair
./gradlew :client:verifyReleaseJar -PpublicHost=127.0.0.1    # asserts lockdown=true + baked host
```

The dev run and the release jar are different products:

| | `:client:run` | `:client:releaseJar` |
|---|---|---|
| Lockdown | locked by default; `-PaiOnly=false` unlocks | always locked (`-PaiOnly=false` fails the build) |
| Config | `client/client/config.json` (tracked) | bundled default, copied to `config.json` beside the jar on first run |
| Input | real input works only when unlocked | real input dropped, MCP-only |

Running the jar:

```bash
mkdir -p aionly-test/client && cd aionly-test/client
java -jar ../../client/build/libs/rt4-aionly-1.0.0.jar
```

- The client loads `config.json` **from the working directory** and writes `accounts.json` next to
  it, so run each test client in its own directory to keep accounts separate.
- Set `mcp_enabled: true` and a `mcp_port`. Several clients auto-increment from the configured
  port (43600–43609); the window title shows the bound port.
- The startup log prints the ready-to-paste connection line:
  `[MCP] listening on http://127.0.0.1:4360N/mcp` plus a `claude mcp add ...` example. The bearer
  token comes from `mcp_token` in `config.json` on `ai-only`; on `mcp-server`/`master` it may come
  from a `mcp_token` sidecar file instead (see [workspace-map.md](workspace-map.md)).
- `openSocket(127.0.0.1:43595)` in the log means the game socket connected. The client is a Swing
  window; starting it does not require you to look at it.

## Restarting a test pair

```bash
pkill -f 'rt4-aionly-1.0.0.jar'      # exact jar name; never pkill -f java
pkill -f 'builddir/server.jar'
```

Start the server first, wait for `started in`, then the client, then wait for the `[MCP] listening`
line. See [testing.md](testing.md) for a scripted version.

## Gotchas

- **JDK 25 on `PATH`** is the default. Any `java`/`javac`/`./mvnw` run without `scripts/jdk11.sh`
  produces confusing Nashorn or class-version errors.
- **Absolute profile paths fail** (see above).
- **The client caches in `/tmp`**: after a reboot the cache is re-downloaded unless
  `~/.local/share/2009scape/cache` is writable.
- **`client/plugins/` is generated**; a locked build does not load plugins at all, so "plugins are
  disabled" in the log is expected.
- **Gradle daemons linger.** A stale daemon from another branch can hold file locks; `./gradlew
  --stop` before blaming a build.
- The server and client both need JDK 11, but the client's Gradle wrapper runs fine on it too.
