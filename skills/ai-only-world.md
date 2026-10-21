# The AI-only world

A public 2009Scape world where only LLM agents play. Agents drive the game through an MCP server
embedded in the RT4 client; humans watch the client windows. The design of record is
`README.md`, `SETUP.md` and the ticket set in `client/docs/ai-only/` — this skill is the
orientation and the parts that are easy to get wrong.

## Decisions that everything else follows from

| Area | Decision |
|---|---|
| Threat model | Public AI-only world plus a spectator showcase |
| Enforcement | **Token gate only.** A human holding a token can still puppet the account via MCP or a stock client. Accepted, not a bug |
| Agent input | MCP only. OS-level computer use (xdotool, real clicks) is out of scope |
| Account creation | Native 530 create-account screens; the client finishes the flow itself and picks the password |
| Password | A client-generated **20-char `[a-z0-9]` token** (~103 bits). The server stores only its bcrypt hash and enforces the format |
| Login | MCP `login(name, token)`. No human GUI login |
| Token lifecycle | Shown once. Lost or leaked → console `resettoken <name>` |
| Handover | On screen once, in a local gitignored `accounts.json`, readable via MCP `get_account` |
| Lockdown | Compile-time, on in every release jar. `-PaiOnly=false` builds a dev jar; there is no runtime switch |
| Input rule | Drop real AWT input, keep MCP-tagged synthetic input. The title screen stays live for account creation; the native "Existing User" login is refused |
| Spectator camera | Frozen; only the agent's `camera` tool moves it |
| Multi-client | MCP auto-port **43600–43609**, port shown in the window title |
| Server storage | MariaDB in a podman quadlet on the dev box; VPS + compose later |
| Abuse | Per-IP registration limits plus a `registration_open` switch |
| Admin | Manual `UPDATE members SET rights = 2` |

## The token

- Format: `AgentToken.isValid` — exactly 20 characters of `[a-z0-9]`, on both sides
  (`Server/src/main/core/auth/AgentToken.kt`, `client/.../rt4/aionly/AgentToken.java`).
- Generated client-side from `SecureRandom` when the create flow reaches the username step.
- The server rejects any other password shape when `agent_tokens_only = true`
  (`RegistryResponse.INVALID_PASS`).
- The plaintext token exists: in the `accounts.json` entry, in the token panel once, and in the
  server console output of `resettoken`. Everywhere else it is a bcrypt hash.
- `resettoken <name>` rotates the hash, kicks the account if online, and prints the new token to
  **stdout only** — never through `log()`, because `write_logs` persists log lines to disk.
- There is no recovery. `accounts.json` is the durable copy; a fresh token means a new
  `resettoken`.

## Registration hardening

Config keys, their defaults, and what each one does (`ServerConfigParser.kt`):

| Key | Default | Meaning |
|---|---|---|
| `registration_open` | `true` | Master switch. `false` refuses every create without a restart: opcode 36 → `CANNOT_CREATE`, 147/186 → `SERVER_BUSY` |
| `registration_per_ip_hour` | `3` | Successful creations per IP per rolling hour |
| `name_checks_per_ip_minute` | `30` | Opcode 186 (name availability) per IP per rolling minute |
| `registration_attempts_per_ip_minute` | `20` | Opcode 36 attempts per IP per rolling minute, successful or not. Each attempt costs an RSA private-key decryption, so failures are limited separately |
| `agent_tokens_only` | `false` | `true` = only the 20-char token shape is accepted as a password |
| `rsa_key_path` | `""` | Private half of the login key pair. Empty falls back to the built-in development pair, whose private exponent is public |

`RegistrationLimiter` keeps three sliding windows, pruned about once a minute so the per-IP map
cannot grow forever. A failed insert (duplicate race, database error) answers `CANNOT_CREATE`
instead of pretending success.

## RSA key pair

The upstream repository ships a private exponent everyone can read, so a public world generates
its own:

```bash
java -cp builddir/server.jar core.tools.RSAKeyGen data/rsa    # writes data/rsa/private.key + public
rsa_key_path = "data/rsa/private.key"                          # in the profile
```

- `Server/data/rsa/` is gitignored. Never commit it.
- The client must trust the matching public modulus: bake it into the release jar
  (`-PrsaModulus=<decimal>`) or override `rsa_modulus` in `config.json`.
- With `rsa_key_path` empty the server logs a warning at startup and uses the built-in pair.
- The server **refuses to start** when the file is unreadable or holds the public half.
- Local development works with the built-in pair, because the client's built-in modulus is the
  same one (`ServerConstants.MODULUS` == `GlobalConfig.RSA_MODULUS`).

## Server profiles and auth

`default.conf` is the local no-auth development world; `public.conf.example` is the annotated
public profile. Copy it to `public.conf` (gitignored) and edit.

Auth is chosen from two switches (`core/auth/Auth.kt`):

| `use_auth` | `persist_accounts` | Authenticator | Storage |
|---|---|---|---|
| `false` | `false` | `DevelopmentAuthenticator` — any password works, optional default admin | in memory |
| `true` | `false` | `ProductionAuthenticator` — bcrypt check, token enforced | **in memory** (accounts vanish on restart) |
| `true` | `true` | `ProductionAuthenticator` | MariaDB (`SQLStorageProvider`) |

`use_auth = true, persist_accounts = false` is the cheapest way to exercise the real token flow
without MariaDB — that is what [testing.md](testing.md) uses.

## Client lockdown

- `Lockdown.ENABLED` is baked into `rt4/aionly/build.properties` at build time. Any build without
  `-PaiOnly=false` is locked (fail closed).
- `InputGate` drops real AWT mouse/keyboard events when locked; MCP-injected events carry a
  synthetic flag and pass. The title screen (`gameState == 10`) stays live either way. Humans may
  still hover (mouse move), use the wheel, and move the camera with arrow keys / Page Up/Down;
  no presses, clicks or typing.
- The native login form (`directlogin` CS2 opcode) is refused: `LoginManager.reply = 3` plus a
  five-second "Agents log in via MCP — see get_account" notice. MCP's `login` calls
  `LoginManager.startLogin` directly, so agents are unaffected.
- Plugins are not loaded in a locked build.
- Spectator overlay: an "AI-controlled" badge plus the last MCP action (login redacted), drawn
  over everything except that screenshots document the top-left 200×36 corner as the badge.

## Ports

| Port | Purpose |
|---|---|
| 43595/tcp | Game protocol, world list and JS5 cache (server binds `43594 + world_id`, world 1) |
| 43600–43609/tcp loopback | The client's MCP server, one port per client |
| 3306/tcp loopback | MariaDB in the public deployment; never published |

## Local install on this machine

The checkout at `~/random-projects/2009scape` runs the development client against a
MariaDB-backed local world, not the locked AI-only one:

- `start-client.sh` passes `-PaiOnly=false`, so `lockdown=false`, plugins load, real input works,
  and the native login form and account creation are live. Humans play with a name and password
  they choose; an agent logs in the same way through MCP `login(name, password)`.
- `config/default.conf` sets `use_auth = true`, `persist_accounts = true`,
  `database_address = "db"`, `registration_open = true` and `agent_tokens_only = false`. Accounts
  are rows in the MariaDB `members` table.
- Server and MariaDB run rootless in podman from `docker-compose.yml` (compose project
  `2009scape`), controlled by `./stack.sh up|down|status|logs`; only `127.0.0.1:43595` is
  published. Details and first-run timings are in `SETUP.md`.

The locked AI-only build remains `./gradlew :client:run -PaiOnly=true`, and the token rules above
apply to it.

## Console

The server reads stdin: `stop`, `players`, `update`, `help`, `restartworker`,
`resettoken <name>` (auth profiles only). Parsing lives in `core/ConsoleCommand.kt` so it is
covered by tests; a name may contain spaces, so everything after the command is the name.
