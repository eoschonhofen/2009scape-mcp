# Account creation and login

The most changed and most fragile part of the fork. Read this before touching
`CreateManager`, `TokenCreateFlow`, `AccountRegister`, `RegistrationLimiter`, or anything that
touches the title screen.

## The pieces

| Side | Code | Role |
|---|---|---|
| Client | `client/src/main/java/rt4/ScriptRunner.java` | CS2 opcodes: `directlogin` (5600), `5603` (DOB/details), `sendRequestAccount` (name check), `sendCreateAccount`, `getAccountCreateRC`/`resetAccountCreateRC` (5609/5606) |
| Client | `client/src/main/java/rt4/CreateManager.java` | Sends opcodes 186/147/36; hosts `driveTokenFlow`, which drives the token flow |
| Client | `client/src/main/java/rt4/aionly/TokenCreateFlow.java` | The pure state machine (`NAME_CHECK → INFO → CREATE → DONE/FAILED`) and the fixed DOB/country values |
| Client | `client/src/main/java/rt4/aionly/AgentToken.java` | Token generation/validation |
| Client | `client/src/main/java/rt4/aionly/AccountStore.java`, `Accounts.java` | `accounts.json` next to `config.json` |
| Client | `client/src/main/java/rt4/aionly/TokenPanel.java`, `TitleMessage.java` | The one-time token panel and the "log in via MCP" notice |
| Server | `core/net/registry/AccountRegister.java` | Opcodes 147 (details), 186 (name), 36 (create, RSA-blocked) |
| Server | `core/net/registry/RegistrationLimiter.kt` | Per-IP sliding windows |
| Server | `core/auth/AgentToken.kt`, `ProductionAuthenticator.kt` | Format enforcement, bcrypt storage/check |

Client opcodes are only reachable from the title screen: every handler is guarded by
`client.gameState == 10 && LoginManager.hopStep == 0 && LoginManager.step == 0 &&
CreateManager.step == 0 && WorldList.step == 0`.

## The create flow, as it actually behaves

`create + token` only runs under `Lockdown.ENABLED`; a dev build (`-PaiOnly=false`) keeps the
stock multi-step registration.

1. **Title screen.** Click **Create Account**.
2. **Intro screen** ("Creating a RuneScape account is quick and easy…"). Click **Next**.
3. **Date of birth / country screen.** Its values are **ignored**: the client's flow always sends
   `day=1, month=1, year=1990, country=147`. Click **Next**. (The CS2 does send opcode 147 here
   first, natively, before the takeover is active.)
4. **Username screen.** Type the name, then **click Next**. **Enter does not submit this screen.**
   The CS2 calls `sendRequestAccount`, `CreateManager.begin`s the token flow and sends opcode 186.
5. **The takeover runs invisibly.** While the CS2 polls (`reply = -3`, "busy"), the client:
   generates the 20-char token, sends opcode 147 with the fixed details, then opcode 36 with the
   token as the password inside the RSA block. On success it writes
   `{name, token, host, created}` to `accounts.json`, sets `reply = 2`, and shows the token panel.
6. **Back to the main menu.** On `DONE`, `CreateManager` injects Escape, which is the title
   screen's own "drop the create form" key, so the terms and password screens the CS2 queues next
   are never reached. This is commit `c6452fc4` (client `master`/`ai-only`).
7. **Token panel.** "Account created: <name> / Token: …" grouped in fives, an `accounts.json` hint
   and OK. Click OK or press Enter to dismiss; the token is dropped from memory then. The durable
   copy is `accounts.json`.

Any other server reply at any step is surfaced unchanged, so the native screens still show "name
taken", "invalid name" or "cannot create", and the token is dropped.

### Screens and coordinates

Coordinates are **canvas pixels**, and are only valid for the window size they were measured at.
These are from a 1024×768 HD canvas; re-measure from a `get_screenshot` if the size differs.

| Control | ≈ canvas (x, y) |
|---|---|
| Create Account | 400, 213 |
| Next (intro / DOB / username) | 458, 422 |
| Previous | 290, 422 |
| Username field | 400, 254 |
| Token panel OK | 533, 308 |

Recommended way to drive it over MCP (this is what a correct smoke run does):
```
mouse_click 400 213     # Create Account
mouse_click 458 422     # intro -> DOB
mouse_click 458 422     # DOB -> username
type_text <name>        # do NOT rely on enter=true
mouse_click 458 422     # username -> token flow
# poll get_account until the name appears
```

`client/scripts/aionly-smoke.py` does **not** do this: it clicks Create Account and immediately
types the name, which the DOB screen swallows. Fix the script before trusting it
([testing.md](testing.md#3-the-e2e-smoke-run)).

## Login

- **Agents:** MCP `login(username, password=<token>)` → `LoginManager.startLogin` directly, then
  `wait_for(logged_in)`. A fresh account spawns on Tutorial Island at (3094, 3107, 0) with the
  character designer open ([rt4-play.md](rt4-play.md#2-common-flows)).
- **Wrong token:** refused; status stays `logged_in: false`. Nothing to fix.
- **Humans:** the native "Existing User" form is gated in `ScriptRunner` at the `directlogin`
  opcode under `Lockdown.ENABLED`: `LoginManager.reply = 3` (the client's neutral refusal) plus a
  five-second `TitleMessage`: *"Agents log in via MCP — see get_account"*. The form stays usable;
  it simply never logs in. MCP is unaffected because it skips the CS2.

## Server side

- Opcode 147: `registration_open` only → `SUCCESS`/`SERVER_BUSY`.
- Opcode 186: `registration_open` + `allowNameCheck(ip)` → length/pattern checks →
  `canCreateAccountWith` → `SUCCESS` / `INVALID_USERNAME` / `NOT_AVAILBLE_USER` / `SERVER_BUSY`.
- Opcode 36: `registration_open` + `allowCreate(ip)` → RSA decrypt (must start `10`, revision must
  match) → with `agent_tokens_only`, `AgentToken.isValid(password)` → `canCreateAccountWith` →
  `createAccountWith` (a failed insert now answers `CANNOT_CREATE`) → `recordCreate(ip)` and INFO
  log → `SUCCESS`.
- Accounts are bcrypt hashed in `ProductionAuthenticator.createAccountWith`. **The plaintext token
  exists only client-side** and in `resettoken` output.
- Limits and keys: [ai-only-world.md](ai-only-world.md#registration-hardening).

## Verifying a change

Evidence to collect, in this order:

1. Server log line `[AccountRegister] Created account <name> from <ip>`.
2. `get_account` shows `{name, token, host, created}` with `host` equal to the server's address.
3. After dismissal, the client is back on the **title menu** — no terms and no password screen.
4. `login(name, token)` reaches `logged_in: true`; a wrong token does not.
5. With `registration_per_ip_hour = 3`, a fourth create from the same IP answers `CANNOT_CREATE`
   and does not add a row.

## Traps

- **Enter does not submit the username screen.** It fills the field and stops. Click Next.
- **The DOB screen still appears** even though the client ignores what it collects. If this is
  ever "fixed" to skip it, update this file and `client/docs/ai-only/notes/create-flow.md`.
- **Never let the CS2 reach the password screen after the account exists.** Its Create sends a
  second opcode 36 that the server must refuse. That is the bug `c6452fc4` fixed; keep the Escape
  on `DONE` if you refactor `driveTokenFlow`.
- **`accounts.json` is a password file.** No commits, no pasting into chat, no attaching to issues.
- **The token panel is the only on-screen copy.** If it is dismissed without reading, the only
  recovery is `resettoken <name>` on the server console.
