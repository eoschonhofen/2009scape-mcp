# Agent skills — 2009scape-mcp

Operational knowledge for working in this workspace, written for **any** coding agent. Each file
is plain markdown: no tool-specific format, no build step. Load one by reading it.

If you only read one thing, read [workspace-map.md](workspace-map.md) — the branch/worktree layout
is the single most common way to waste an hour here.

## Index

| Skill | Load it when… |
|---|---|
| [workspace-map.md](workspace-map.md) | You are about to edit anything, or cannot find the AI-only client code. Branches, worktrees, which repo owns what. |
| [ai-only-world.md](ai-only-world.md) | You need to understand the design: token gate, registration hardening, RSA keys, lockdown, profiles, spectator mode. |
| [local-dev.md](local-dev.md) | You need to build, run or restart the server or client, or hit a JDK/cache/port problem. |
| [rt4-mcp-client.md](rt4-mcp-client.md) | You need to connect to, inspect or drive a running client over MCP (raw HTTP, the `rt4-mcp.py` script, screenshots). |
| [rt4-play.md](rt4-play.md) | You are actually playing the game through MCP: login, walk, dialogue, interfaces, Tutorial Island, quests, combat, banking. |
| [auth-flows.md](auth-flows.md) | You are working on, or testing, account creation and login (the most changed, most fragile part of this fork). |
| [testing.md](testing.md) | You changed behaviour and need to prove it: unit tests, the local AI-only world, the end-to-end smoke run. |
| [release-and-deploy.md](release-and-deploy.md) | You are building the release jar, running the public world, deploying, or committing/pushing. |

## Where this knowledge comes from

- The fork's own docs: `README.md`, `SETUP.md`, `CONTRIBUTING.md`, `deploy/`, and the ticket sets
  in `client/docs/ai-only/` and `client/docs/mcp/`. Those are the design of record.
- The sessions that built the AI-only world: every AIO-01..17 ticket, the E2E smoke run, the first
  full playthrough, and the login/create-account test round (2026-10-06).
- `client/.claude/skills/rt4-play/SKILL.md` is the Claude Code copy of [rt4-play.md](rt4-play.md);
  this folder is the agent-neutral canonical text.

If you need to re-mine the source conversations (they are not in the repo):

- DSH sessions: `~/.dsh/sessions/--home-eschonhofen-random-projects-2009scape--/*/session.v4.jsonl.zstd`
  (zstd-compressed JSONL; `zstd -dc` to read).
- Claude Code sessions: `~/.claude/projects/-home-eschonhofen-random-projects-2009scape/*.jsonl`
  and `...-2009scape-client/*.jsonl`, plus the distilled notes in that project's `memory/`.
- `.tmp_s_*.jsonl` in the repo root is one dropped DSH transcript (gitignored).

## Using these skills from a specific agent

Nothing here needs wiring — an agent reads the files. If you want them auto-discovered:

- **Claude Code**: symlink or copy a skill directory under `<repo>/.claude/skills/<name>/SKILL.md`
  (the client repo already does this for `rt4-play`).
- **DSH**: symlink the file-to-be into `~/.dsh/skills/<name>/SKILL.md`.
- **AGENTS.md-aware agents** (Codex, Cursor, DSH, …): [../AGENTS.md](../AGENTS.md) points here.

## Keeping them honest

These skills are part of the repo: fix them in the same commit as the change that made them wrong.
A skill that describes old behaviour is worse than no skill. The same rules as the rest of the
fork apply — atomic gitmoji commits, no secrets ([release-and-deploy.md](release-and-deploy.md#git-workflow)).
