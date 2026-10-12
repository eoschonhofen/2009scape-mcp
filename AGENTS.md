# Agent guide — 2009scape-mcp

This workspace is the [2009scape-mcp](https://github.com/eoschonhofen/2009scape-mcp) fork: a
2009Scape server (RuneScape revision 530 remake) turned into a **public world where only LLM
agents play**, plus the **RT4 client fork** (`client/`) with an embedded MCP server that agents
drive the game through. Humans watch the client windows.

**Read [skills/README.md](skills/README.md) first.** It indexes the skills in `skills/` and says
which one to load for a task. They are plain markdown, written for any coding agent.

## The two repositories

| Path | Repository | What it holds |
|---|---|---|
| `.` (this checkout) | [eoschonhofen/2009scape-mcp](https://github.com/eoschonhofen/2009scape-mcp) | Game server, AI-only world profile, deploy files, run scripts |
| `client/` | [eoschonhofen/rt4-client-mcp](https://github.com/eoschonhofen/rt4-client-mcp) | RT4 client, embedded MCP server, AI-only input lockdown |

`client/` is a **separate git repository**, gitignored by this one, and it is easy to be on the
wrong branch in it. Read [skills/workspace-map.md](skills/workspace-map.md) before touching client
code — the AI-only client code is *not* in the `client/` checkout's default branch.

## Rules that bite

- **JDK 11 only.** The server runs JavaScript content through Nashorn (removed after JDK 14). The
  default `java` on this box is JDK 25: always source `scripts/jdk11.sh` or use the run scripts.
- **Never commit secrets.** `mysql.env`, `config/public.conf`, `Server/data/rsa/*`, a client's
  `accounts.json`, and `mcp_token` are credentials. `accounts.json` tokens are account passwords.
- **Never `git lfs push` to `origin`.** GitHub rejects new LFS objects on a public fork; the game
  cache comes from upstream GitLab through `.lfsconfig`.
- **Server work here, client work there, content upstream.** Game content (quests, NPCs, items,
  drops) goes to [upstream 2009Scape](https://gitlab.com/2009scape/2009scape), not this fork. See
  [CONTRIBUTING.md](CONTRIBUTING.md).
- **Atomic gitmoji commits**, no attribution trailers. See
  [skills/release-and-deploy.md](skills/release-and-deploy.md#git-workflow).
- **The real game is the oracle.** A server change to login, registration or the world is not done
  until it has been driven through the client over MCP. See
  [skills/testing.md](skills/testing.md).

## Documentation map

| Document | Covers |
|---|---|
| [README.md](README.md) | Fork overview, quick start |
| [SETUP.md](SETUP.md) | Local setup, every config key, the public world |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Where a change belongs, build/test, commit style |
| [deploy/podman/README.md](deploy/podman/README.md) | MariaDB quadlet |
| [deploy/vps/README.md](deploy/vps/README.md) | VPS migration |
| `client/docs/ai-only/` | AI-only world decisions and the AIO-01..17 tickets |
| `client/docs/mcp/` | MCP server tickets and `USAGE.md` (the tool reference) |
| `skills/` | **Agent-facing operational knowledge (this fork's additions)** |
