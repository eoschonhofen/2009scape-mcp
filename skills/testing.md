# Testing

Three layers, cheapest first. A change to auth, registration or the world is not done until the
third one has run, because the create/login flows only exist end to end.

## 1. Unit tests

Server:

```bash
ROOT=$PWD; . scripts/jdk11.sh
export HOME="$ROOT/.mavenhome" MAVEN_USER_HOME="$ROOT/.mavenhome"
export MAVEN_OPTS="-Dmaven.repo.local=$ROOT/.mavenhome/.m2/repository -Xmx3g"
cd Server
./mvnw test
./mvnw -o -q test -Dtest=AgentTokenTest,RegistrationLimiterTest,ConsoleCommandTest,ServerConfigParserTest
```

The fork's own tests, and what they pin down:

| Test | Covers |
|---|---|
| `core/auth/AgentTokenTest` | 20-char `[a-z0-9]` validation |
| `core/net/registry/RegistrationLimiterTest` | The three sliding windows, sweeping, `trackedIpCount` |
| `core/ConsoleCommandTest` | Command parsing incl. names with spaces |
| `ServerConfigParserTest` | New config keys default correctly |
| `RsaKeyPairTest` | Key file loading and the private-half check |

Results: `Server/target/surefire-reports/*.xml`. Tests need the cache in the config (not a
symlink) and JDK 11. Some suites (`ItemDefinitionTests`) read the game cache, so they are slower.

Client (from the client checkout that has the change — [workspace-map.md](workspace-map.md)):

```bash
cd client        # or .worktrees/aio-client
export GRADLE_USER_HOME="$ROOT/.gradlehome"
./gradlew --offline :client:test
./gradlew --offline :client:test --tests 'rt4.aionly.*'
```

Results: `client/build/test-results/test/TEST-*.xml`. The AI-only suites are `AccountStoreTest`,
`ActionSummaryTest`, `AgentTokenTest`, `ConfigBootstrapTest`, `InputGateTest`, `LockdownTest`,
`TokenCreateFlowTest`, `TokenFormatTest`, `TokenPanelTest` — 40 tests, all green at the time of
writing.

## 2. The local AI-only world (no MariaDB)

`use_auth = true` + `persist_accounts = false` selects `ProductionAuthenticator` with the
**in-memory** storage provider: real bcrypt checks and real token enforcement, no database. That
is enough to exercise everything in [auth-flows.md](auth-flows.md).

```bash
# 1. A profile that keeps the dev RSA pair (matches the client's built-in modulus).
sed -e 's/^persist_accounts = true/persist_accounts = false/' \
    -e 's|^rsa_key_path = "data/rsa/private.key"|rsa_key_path = ""|' \
    Server/worldprops/public.conf.example > aionly-test/aionly-local.conf

# 2. Start it. Paths are relative to Server/ (skills/local-dev.md#server).
./start-server.sh ../aionly-test/aionly-local.conf
# wait for: 2009Scape started in ...   and the two expected startup warnings
#   - rsa_key_path unset (built-in development pair)
#   - msip is loopback while auth is on

# 3. A locked client in its own directory, pointed at this box.
mkdir -p aionly-test/client && cd aionly-test/client
cat > config.json <<'JSON'
{ "ip_management": "127.0.0.1", "ip_address": "127.0.0.1", "world": 1,
  "server_port": 43594, "wl_port": 43595, "js5_port": 43595,
  "mcp_enabled": true, "mcp_port": 43600, "mcp_token": "<64 hex chars>" }
JSON
java -jar ../../.worktrees/aio-client/client/build/libs/rt4-aionly-1.0.0.jar
# wait for: [MCP] listening on http://127.0.0.1:4360N/mcp
```

Accounts are in memory: **restarting the server loses them**, and so do the per-IP registration
counters. Reuse the profile across a long test run.

## 3. The E2E smoke run

`client/scripts/aionly-smoke.py` (ai-only branch) drives create → `get_account` → login → walk →
logout → wrong-token rejection through one client's MCP endpoint.

```bash
python3 -I scripts/aionly-smoke.py \
  --url http://127.0.0.1:43600/mcp --token <mcp token> \
  --create-x 400 --create-y 213
```

Caveats found by running it against the real flow — fix these before trusting a FAIL:

- **The create step is wrong.** It clicks Create Account and immediately types the name; the DOB
  screen swallows it. The real sequence needs two Next clicks before typing and one after
  ([auth-flows.md](auth-flows.md#screens-and-coordinates)). Without `--create-x/--create-y` the
  create step is SKIPped and the script just uses an existing `accounts.json`.
- **The walk target is wrong for a fresh account.** `WALK = (3222, 3217)` is next to Lumbridge's
  home spawn, but a brand-new account spawns on Tutorial Island at (3094, 3107) and the tutorial
  locks movement. Either use an account that has left the island or walk to a Tutorial Island tile.
- Coordinates depend on the window size. Measure them from a `get_screenshot`, do not copy the
  numbers above blindly.

The manual checklist that cannot be scripted (from `client/docs/ai-only/OPERATOR.md`): real input
does nothing in game, the native login shows the notice, the badge and last action are visible,
three clients bind 43600/43601/43602, `resettoken` kicks and rotates, and the fourth create in an
hour fails while existing accounts still log in.

## A minimal MCP client for tests

No MCP tooling loaded? The handshake is required or every call is HTTP 400 — see the snippet in
[rt4-mcp-client.md](rt4-mcp-client.md#raw-http-initialize-first). `client/scripts/rt4-mcp.py` is
the maintained client; `client/scripts/mcp-smoke.py` is the MCP-tool smoke run for the general tool
surface.

## Reading the result

- Server log: `[AccountRegister] Created account <name> from <ip>` per real creation; INFO only.
- Client `accounts.json`: one entry per creation, `{name, token, host, created}`.
- `get_status`: `logged_in`, `game_state` (10 title, 30 in game), `position`, `open_interfaces`.
- `get_chat` (filtered): the server's own refusals, e.g. "cannot create".
- A screenshot only when a text tool cannot answer.

## Gotchas

- **Another session may be on the same client.** The MCP port identifies the client, not the
  owner. If state changes that you did not cause, list the clients (`rt4-mcp.py ports`) and pin a
  port. A human clicking the window also changes state.
- **Ports auto-increment.** A second client ignores `mcp_port` and binds the next free port in
  43600–43609. Read the `[MCP] listening` line; do not assume 43600.
- **Shared registration budget.** `registration_per_ip_hour` counts every creation from
  127.0.0.1, including ones another process made in the last hour.
- **`pkill` by exact jar name only** (`pkill -f rt4-aionly-1.0.0.jar`), never `pkill -f java`, or
  you kill Gradle/Maven/the server too.
