# Release, deploy and git workflow

## Building the release client jar

```bash
cd client            # or .worktrees/client-merge for the integrated branch
export GRADLE_USER_HOME="$ROOT/.gradlehome"
./gradlew :client:releaseJar -PpublicHost=<host> [-PpublicWorld=1] [-PrsaModulus=<decimal>]
./gradlew :client:verifyReleaseJar -PpublicHost=<host>      # asserts lockdown=true and the baked host
```

- `releaseJar` is **always locked**; `-PaiOnly=false` fails the build. There is no runtime switch.
- `-PpublicHost` is baked into `rt4/aionly/default-config.json` along with `mcp_enabled` and
  `mcp_port`. `-PrsaModulus` must be the public modulus of the server's `data/rsa` pair, or the
  client's built-in modulus if the server keeps the development pair.
- First run copies the bundled default to `config.json` beside the jar and never overwrites an
  existing one. The token is generated into `config.json` (or the `mcp_token` sidecar on the
  `mcp-server` line — [workspace-map.md](workspace-map.md)).
- Output: `client/build/libs/rt4-aionly-<version>.jar`.

A developer client is `./gradlew :client:run` (add `-PaiOnly=false` to allow real input). Never
point a dev build at the public world: whoever is at the keyboard can play by hand.

## Running the public world

1. **Database.** MariaDB as a rootless podman quadlet: [deploy/podman/README.md](../deploy/podman/README.md).
   The gotchas that cost time there:
   - `mysql.env` values must be **unquoted** for podman (`--env-file` keeps the quotes; Compose
     strips them). A quoted password means `Access denied`.
   - Podman does not create a bind-mount source: `mkdir -p ~/.local/share/2009scape/db` first.
   - Quadlets need a working `systemd --user` session; `systemctl --user start 2009scape-db`.
   - `SHOW TABLES` on `global` must list `members`, and nothing may listen on `0.0.0.0:3306`.
2. **RSA key pair.** `java -cp builddir/server.jar core.tools.RSAKeyGen data/rsa`, then set
   `rsa_key_path = "data/rsa/private.key"`. The server refuses to start on a missing or
   public-only key. See [ai-only-world.md](ai-only-world.md#rsa-key-pair).
3. **Profile.** Copy `Server/worldprops/public.conf.example` to a gitignored `config/public.conf`
   (or `Server/worldprops/public.conf`) and set the database credentials, `rsa_key_path`, the
   registration limits and `agent_tokens_only = true`.
4. **Start and open the firewall.** `./start-server.sh config/public.conf`; publish **43595/tcp
   only**. MariaDB stays on loopback, and the MCP ports are per-client loopback.
5. **Admin** is `UPDATE members SET rights = 2 WHERE username = '<name>';` in MariaDB, then relog.
   A lost token is `resettoken <name>` on the server console (stdout only).

VPS migration (`docker-compose.public.yml`, [deploy/vps/README.md](../deploy/vps/README.md)): the
public stack publishes only 43595, has **no JDWP debugger** (the dev compose's `5005` is
unauthenticated remote code execution), and mounts `public.conf` plus `Server/data/rsa` read-only.
Secrets live in the gitignored `mysql.env` and `config/public.conf`; neither goes in an image, a
repo or a ticket. Migrate with `mariadb-dump --single-transaction` plus a tarball of
`Server/data/players`, `Server/data/playerstats` and `Server/data/eco`.

## Git workflow

Two repos, two histories; a cross-repo change is two commits, linked in the PR description.

| Remote | Server repo | Client repo |
|---|---|---|
| `origin` | `github.com/eoschonhofen/2009scape-mcp` | `github.com/eoschonhofen/rt4-client-mcp` |
| `upstream` | `gitlab.com/2009scape/2009scape` | `gitlab.com/2009scape/rt4-client` |

- **Atomic gitmoji commits**, one logical change each, no attribution trailers. The full table is
  the `commit-atomico-gitmoji` skill; this repo's examples are in [CONTRIBUTING.md](../CONTRIBUTING.md).
- **Branch roles:** server `master` is the integration branch (root checkout); server `ai-only`
  lives in `.worktrees/aio-main`. Client `mcp-server` lives in `client/`, client `ai-only` in
  `.worktrees/aio-client`, client `master` in `.worktrees/client-merge`. Full map:
  [workspace-map.md](workspace-map.md).
- **Merging AI-only client work to master** (the established flow):

  ```bash
  cd .worktrees/client-merge          # client master, clean
  git fetch origin && git merge ai-only -m "🔀 Merge ai-only into master"
  git push origin master
  ```

  `git merge-tree $(git merge-base master ai-only) master ai-only` previews conflicts before you
  touch the worktree. Do not force-push, rebase or amend shared branches.
- **LFS:** the game cache is an LFS object served from upstream GitLab via `.lfsconfig`. GitHub
  rejects new LFS objects on a public fork, so never `git lfs push` to `origin`. A docs-only
  worktree can skip the download with `GIT_LFS_SKIP_SMUDGE=1 git worktree add ...`.
- **Keeping up with upstream:** `git fetch upstream && git merge upstream/master`. `README.md` is
  this fork's own: keep ours on conflict and port anything relevant by hand.
- **Content goes upstream.** Quests, NPCs, items, drops and dialogue belong in a merge request to
  the 2009Scape GitLab, which has its own rules (Kotlin for new code, no AI-written MR text).
  Never report this fork's bugs to the 2009Scape team.

## Secrets checklist before pushing

`mysql.env`, `config/public.conf`, `Server/data/rsa/*`, any client's `accounts.json` or
`mcp_token`, and `.podman/` (registry credentials). If one was committed, treat it as compromised:
rotate it and tell the user; removing it in a new commit does not remove it from history.
