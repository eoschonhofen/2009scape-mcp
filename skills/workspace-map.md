# Workspace map — repos, branches, worktrees

> Read this before editing. The AI-only client code is **not** in the `client/` checkout's default
> branch, and the fork runs two diverging client lines (`mcp-server` and `ai-only`) that are only
> partly integrated. Getting this wrong means editing a file that is not the one you are testing.

## The two repositories

| Path | Repository | Git root |
|---|---|---|
| `.` | [eoschonhofen/2009scape-mcp](https://github.com/eoschonhofen/2009scape-mcp) (server) | `.git/` |
| `client/` | [eoschonhofen/rt4-client-mcp](https://github.com/eoschonhofen/rt4-client-mcp) (RT4 client) | `client/.git/` |

They are separate git histories. `client/` is gitignored by the server repo. A commit that spans
both is two commits in two repos (see [release-and-deploy.md](release-and-deploy.md#git-workflow)).

Remotes, both repos: `origin` = the GitHub fork, `upstream` = the original GitLab project
(`gitlab.com/2009scape/2009scape`, `gitlab.com/2009scape/rt4-client`).

## Server repo worktrees

| Checkout | Branch | What it is |
|---|---|---|
| `.` (root) | `master` | **The integration branch.** All AI-only server work (AIO-01..05, RSA, resettoken) is merged here. Build and run from here. |
| `.worktrees/aio-main` | `ai-only` | The server-side AI-only feature branch, synced with `origin/ai-only`. Older than `master`; `master` is ahead. |

Other server branches: `aio-review-fixes` (merged into `master`, kept for reference),
`mcp-token-sidecar` (a docs-only branch, same tip as a doc commit).

## Client repo worktrees

| Checkout | Branch | What it is |
|---|---|---|
| `client/` | `mcp-server` | The MCP-server line: `rt4/mcp/`, the tool surface, `scripts/rt4-mcp.py`, `docs/mcp/`. **No AI-only code.** |
| `.worktrees/aio-client` | `ai-only` | The AI-only line: `rt4/aionly/` (Lockdown, TokenCreateFlow, TokenPanel, AccountStore), the release jar, `scripts/aionly-smoke.py`, `docs/ai-only/`. |
| `.worktrees/client-merge` | `master` | **The client integration branch**, pushed to `origin/master`. Merge of both lines at the last merge point. |

The client `master` is the branch a release jar is cut from, but it currently **lacks five
`mcp-server` commits** that have not been merged across: the `mcp_token` sidecar
(`c17d6c32`, `9db447dd`, `326a77c6`), overhead chat in `find_entities` (`d362db90`) and the
`client/plugins/` gitignore (`81dc03ed`). Check before assuming a token-handling or
`find_entities` behaviour is the same on both branches.

| Task | Use |
|---|---|
| MCP server / tools / transport | `client/` on `mcp-server` |
| AI-only lockdown, token create flow, release jar | `.worktrees/aio-client` on `ai-only` |
| Release jar for the public world, or anything that needs both lines | `.worktrees/client-merge` on `master` |

## Where the code lives

```
Server/src/main/                     Game server (Kotlin for new code, legacy Java to fix)
  core/auth/AgentToken.kt            Token format + generation (server side)
  core/auth/{Development,Production}Authenticator.kt
  core/net/registry/AccountRegister.java   Opcodes 147 / 186 / 36
  core/net/registry/RegistrationLimiter.kt Per-IP sliding windows
  core/ConsoleCommand.kt             Console parsing (stop, players, update, help, restartworker, resettoken)
  core/game/system/config/ServerConfigParser.kt  Every config key
Server/worldprops/default.conf       Local no-auth dev world
Server/worldprops/public.conf.example  The public AI-only profile, annotated
Server/src/test/kotlin/              Server tests
scripts/jdk11.sh                     Puts JDK 11 on PATH; sourced by the run scripts
rebuild.sh start-server.sh start-client.sh
deploy/podman|vps/                   MariaDB quadlet, VPS stack

client/client/src/main/java/rt4/mcp/      MCP server + 26 tools
client/client/src/main/java/rt4/aionly/   AI-only: Lockdown, InputGate, TokenCreateFlow, TokenPanel, AccountStore, SpectatorOverlay
client/scripts/rt4-mcp.py                 stdio bridge + CLI for the MCP endpoint
client/scripts/aionly-smoke.py            End-to-end create/login smoke run
client/docs/ai-only/, client/docs/mcp/    Ticket sets and the tool reference (USAGE.md)
```

## Generated and untracked (never commit)

- `client/plugins/` — built by `plugin-playground`; ignored on client `master`, untracked on
  `ai-only`.
- `client/client/config.json`, `accounts.json`, `mcp_token` — the runtime config and credentials.
  `config.json` is tracked on some branches; treat any token in it as compromised if committed.
- `.podman/`, `.tmp_s_*.jsonl`, `logs/`, `builddir/`, `Server/target/`, `aionly-test/` scratch.
- `Server/data/players`, `Server/data/serverstore`, `Server/data/rsa` — runtime state and the
  private key.

## Adding a worktree

```bash
git worktree add .worktrees/<name> -b <branch> master      # server repo
git -C client worktree add ../.worktrees/<name> -b <branch> master   # client repo
```

`GIT_LFS_SKIP_SMUDGE=1` avoids re-downloading the game cache for a docs-only worktree. Remove a
worktree with `git worktree remove <path>`; never `rm -rf` it.
