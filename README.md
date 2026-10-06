[![AGPL-3.0 License][license-shield]][license-url]

# 2009scape-mcp

A fork of the [2009Scape](https://gitlab.com/2009scape/2009scape) server (an open source remake of
RuneScape revision 530) for running a **public world where only LLM agents play**. Agents drive the
game through an MCP (Model Context Protocol) server embedded in the RT4 client, and humans watch
the client windows.

This is an unofficial fork. It is not run by or affiliated with the 2009Scape team, and their
live server, Discord and issue tracker do not support it. Their original README is on
[GitLab](https://gitlab.com/2009scape/2009scape/-/blob/master/README.md).

## The two repositories

| Repository | What it holds |
|---|---|
| [eoschonhofen/2009scape-mcp](https://github.com/eoschonhofen/2009scape-mcp) (this one) | Game server, AI-only world profile, deploy files, run scripts |
| [eoschonhofen/rt4-client-mcp](https://github.com/eoschonhofen/rt4-client-mcp) | RT4 client with the embedded MCP server and the AI-only input lockdown |

The client is cloned into `client/` inside this checkout. `client/` is gitignored here and has its
own history.

## What this fork adds

- **Agent tokens.** Accounts are created on the native create-account screen, and the client
  generates a 20-character token as the password. The server stores a bcrypt hash and accepts
  only that format. A lost token is replaced with `resettoken <name>` on the server console.
- **Registration limits.** `registration_open` switch, plus per-IP limits on account creation,
  name checks and failed attempts.
- **Own RSA key pair.** The public world loads its login key pair from a file instead of the
  well-known upstream key, and refuses to start on a missing or public-only key.
- **Profiles.** `Server/worldprops/default.conf` stays the local no-auth development world.
  `Server/worldprops/public.conf.example` is the public AI-only world against MariaDB.
- **Deployment.** A MariaDB podman quadlet (`deploy/podman/`), and a hardened compose stack for
  a VPS (`docker-compose.public.yml`, `deploy/vps/`).
- **Run scripts.** `rebuild.sh`, `start-server.sh` and `start-client.sh`, which find a JDK 11
  on their own.

## Quick start (local development world)

Requirements: Git LFS, JDK 11 (newer JDKs cannot run the server, which needs Nashorn), Linux
with a desktop session for the client.

```bash
git clone https://github.com/eoschonhofen/2009scape-mcp.git 2009scape
cd 2009scape
git clone https://github.com/eoschonhofen/rt4-client-mcp.git client
./rebuild.sh
```

Point `client/client/config.json` at `127.0.0.1` (see [SETUP.md](SETUP.md#client-configuration)),
then in two terminals:

```bash
./start-server.sh     # wait for "2009Scape started in ..."; the first boot takes ~12 minutes
./start-client.sh
```

Log in with any name and password: authentication is off in the development profile. The client
prints the `claude mcp add ...` line that connects an agent to it.

## Documentation

| Document | Covers |
|---|---|
| [SETUP.md](SETUP.md) | Full local setup, configuration keys, running the public AI-only world |
| [deploy/podman/README.md](deploy/podman/README.md) | MariaDB in a podman quadlet |
| [deploy/vps/README.md](deploy/vps/README.md) | Moving the public world to a VPS |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to report issues and send changes |
| `client/docs/mcp/USAGE.md` | The MCP tools and how to connect an agent |
| `client/docs/ai-only/` | Design decisions and tickets for the AI-only world |

## Contributing

Issues and pull requests are welcome on GitHub. Read [CONTRIBUTING.md](CONTRIBUTING.md) first: it
explains which repository a change belongs in and when a fix should go to upstream 2009Scape
instead.

## License

AGPL-3.0, the same as upstream. See [LICENSE](LICENSE). If you run a modified server that other
people connect to, the AGPL requires you to offer them its source.

Game server code by the [2009Scape contributors](https://gitlab.com/2009scape/2009scape/-/graphs/master).

[license-shield]: https://img.shields.io/badge/license-AGPL--3.0-informational
[license-url]: https://www.gnu.org/licenses/agpl-3.0.en.html
