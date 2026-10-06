# Contributing to 2009scape-mcp

Thanks for helping. This fork is small, so the process is light: open an issue, discuss, send a
pull request.

## Where does the change belong?

| The change is about | Send it to |
|---|---|
| The AI-only world: agent tokens, registration limits, RSA key loading, `public.conf`, `resettoken`, deploy files, run scripts, this fork's docs | This repository, [eoschonhofen/2009scape-mcp](https://github.com/eoschonhofen/2009scape-mcp) |
| The MCP server, MCP tools, input lockdown, spectator overlay, client release jar | The client fork, [eoschonhofen/rt4-client-mcp](https://github.com/eoschonhofen/rt4-client-mcp) |
| Game content: quests, NPCs, skills, items, drop tables, dialogue, general server bugs | Upstream [2009Scape on GitLab](https://gitlab.com/2009scape/2009scape) |

Game content fixes go upstream so every 2009Scape server benefits, and this fork picks them up on
the next merge. Upstream has its own rules (merge request template, Kotlin for new code, and a
policy on AI tools that forbids AI-written MR descriptions and replies). Read their
[README](https://gitlab.com/2009scape/2009scape/-/blob/master/README.md) before opening a merge
request there. Do not report bugs from this fork to the 2009Scape team: they only support their
live server.

## Reporting issues

Open a [GitHub issue](https://github.com/eoschonhofen/2009scape-mcp/issues) with:

- what you did, what you expected, and what happened;
- which profile you ran (`default.conf` or `public.conf`) and how (scripts, podman, compose);
- the relevant lines of `logs/server.log` or `logs/client.log`.

Never paste agent tokens, database passwords, `public.conf` or anything from `Server/data/rsa/`.

### Security issues

Do not open a public issue for something that lets someone log in as another account, bypass the
token gate or registration limits, read secrets, or run code on the server. Report it privately
through [GitHub security advisories](https://github.com/eoschonhofen/2009scape-mcp/security/advisories/new)
instead.

## Setting up

Follow the quick start in [README.md](README.md#quick-start-local-development-world), and
[SETUP.md](SETUP.md) for the details. In short: Git LFS, JDK 11, clone both repositories, run
`./rebuild.sh`.

To work on the public-world code paths you also need MariaDB (`deploy/podman/README.md`) and a
generated RSA key pair (SETUP.md, *RSA key pair*).

## Making a change

1. Fork the repository and branch from `master`. Name the branch after the change
   (`registration-captcha`, `fix-resettoken-kick`).
2. Keep the change focused. Unrelated fixes go in a separate pull request.
3. Write new server code in **Kotlin**, matching upstream. Touch legacy Java only to fix it.
4. Match the surrounding code: naming, comment density, file layout.
5. Add or update tests under `Server/src/test/kotlin/` for behaviour you change. The fork's own
   tests are good examples: `core/auth/AgentTokenTest.kt`,
   `core/net/registry/RegistrationLimiterTest.kt`, `core/ConsoleCommandTest.kt`.
6. Update `SETUP.md` or the deploy READMEs when you add a config key, a console command, a port
   or a deploy step. New `public.conf` keys also go in `Server/worldprops/public.conf.example`
   with a comment.

### Building and testing

```bash
./rebuild.sh                         # build builddir/server.jar, tests skipped

ROOT=$PWD; . scripts/jdk11.sh        # put JDK 11 on PATH
cd Server
./mvnw test                          # full test suite
./mvnw test -Dtest=AgentTokenTest    # one test class
```

Before opening a pull request, run the full suite, and start the server with the profile your
change touches to check that it boots. For changes to login or registration, also log in through
the client (or an MCP agent) against your local server.

### Commits

- **Atomic**: one logical change per commit, so each commit builds and can be reverted alone.
- **Gitmoji**: start the subject with the [gitmoji](https://gitmoji.dev/) that fits, then an
  imperative sentence. Examples from the history:
  - `✨ Add resettoken to the server console`
  - `🐛 Keep a reset agent token from being undone by the logout save`
  - `🦺 Rate-limit failed registration attempts per IP`
  - `🔐 Refuse to start on an RSA key that is not the private half`
  - `📝 Document the new registration limit and the RSA startup checks`
- Explain the *why* in the body when the subject does not make it obvious.

### Pull requests

Open the pull request against `master` and describe:

- what changed and why;
- how you tested it (commands run, profile used, what you checked in game);
- any new config key, port or migration step an operator has to know about.

Changes that span both repositories need two pull requests. Link them to each other and say which
one has to merge first.

## Keeping up with upstream

Maintainers merge upstream 2009Scape into `master` from time to time:

```bash
git remote add upstream https://gitlab.com/2009scape/2009scape.git   # once
git fetch upstream
git merge upstream/master
```

`README.md` is this fork's own. On a conflict there, keep ours and port anything relevant from
upstream's version by hand.

Git LFS objects (the game cache) are fetched from upstream GitLab through `.lfsconfig`. GitHub
does not accept new LFS objects on this fork, so don't run `git lfs push` to `origin`.

## Using AI tools

This fork exists to let AI agents play, and AI coding tools are welcome here. You are still
responsible for what you submit: test it yourself, and be able to explain any line of it. Keep
in mind that upstream 2009Scape has a stricter policy for anything sent to their GitLab.

## License

By contributing, you agree that your contribution is licensed under the
[AGPL-3.0](LICENSE), the same license as the rest of the project.
