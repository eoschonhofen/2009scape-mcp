# Driving the client over MCP

The RT4 client embeds an MCP server (`rt4/mcp/`) as streamable HTTP on loopback. Agents drive the
game through it; there is no other supported input path in a locked build. The tool reference is
`client/docs/mcp/USAGE.md`; this skill is the connection mechanics and the traps.

## Endpoint and auth

- URL: `http://127.0.0.1:4360N/mcp`, N = 0..9. The port is in the client's window title and in its
  startup log line `[MCP] listening on ...`.
- Auth: `Authorization: Bearer <token>`. The token is `mcp_token` in the client's `config.json`
  (`ai-only` branch) or in a `mcp_token` sidecar file beside it (newer `mcp-server`/`master`).
  Find it with `client/scripts/rt4-mcp.py`, or read the file — never print it into logs, chat or a
  commit.
- Several clients run side by side, one port each. If a tool answers "not logged in" or the wrong
  character responds, you are pointed at another client's port.

## Use the script unless you need raw HTTP

`client/scripts/rt4-mcp.py` (stdlib Python, `python3 -I`) discovers the port and token itself:

```bash
python3 -I scripts/rt4-mcp.py ports                       # which clients are answering
python3 -I scripts/rt4-mcp.py tools -v                    # tool list with descriptions
python3 -I scripts/rt4-mcp.py call get_status --pretty
python3 -I scripts/rt4-mcp.py call find_entities type=npc name=Banker has_op=Bank
python3 -I scripts/rt4-mcp.py call wait_for '{"conditions":[{"condition":"idle","for_ticks":3}],"timeout_ms":15000}'
python3 -I scripts/rt4-mcp.py call get_screenshot scale=0.5     # writes a PNG, path on stderr
```

`key=value` arguments are parsed as JSON when they can be. Exit code 2 means the tool returned
`isError` and the message is printed. If your agent loads the MCP server directly, use the tools
under their own names (`rt4_get_status`, …) instead.

To make the tools permanent for a client, register the stdio bridge once (it re-reads the token
and survives restarts):

```bash
claude mcp add rt4 -s user -- python3 -I <abs>/client/scripts/rt4-mcp.py bridge
# several clients: add --port 43601 before `bridge`
```

## Raw HTTP: initialize first

A `tools/call` without the MCP handshake is rejected with **HTTP 400**. The session id comes back
on the `initialize` response header and must be echoed. This bites every hand-written client:

```python
import http.client, json, urllib.parse
url, token = "http://127.0.0.1:43600/mcp", "<token>"
p = urllib.parse.urlsplit(url)

def post(payload, session=None):
    c = http.client.HTTPConnection(p.hostname, p.port, timeout=120)
    h = {"Content-Type": "application/json",
         "Accept": "application/json, text/event-stream",
         "Authorization": "Bearer " + token}
    if session:
        h["Mcp-Session-Id"] = session
    c.request("POST", p.path, json.dumps(payload), h)
    r = c.getresponse(); body = r.read().decode(); sid = r.getheader("Mcp-Session-Id")
    c.close(); return r.status, body, sid

_, _, sid = post({"jsonrpc": "2.0", "id": 0, "method": "initialize",
                  "params": {"protocolVersion": "2025-06-18", "capabilities": {},
                             "clientInfo": {"name": "my-agent", "version": "1"}}})
status, body, _ = post({"jsonrpc": "2.0", "id": 1, "method": "tools/call",
                        "params": {"name": "get_status", "arguments": {}}}, sid)
result = json.loads(body)["result"]
text = "".join(b.get("text", "") for b in result.get("content", []) if b.get("type") == "text")
structured = result.get("structuredContent", {})
```

Tool results carry `structuredContent` (what you want to parse), a text mirror, and
`isError: true` with a message when the tool refused.

## Screenshots

`get_screenshot` returns a base64 PNG in an `image` content block plus a text line describing the
encoding. The text line is the coordinate contract:

```
{"width":512,"height":384,"scale":0.5,"region_x":0,"region_y":0,"region_width":1024,"region_height":768,...}
canvas_x = region_x + pixel_x / scale
```

- Prefer text tools. A screenshot costs far more tokens than `get_status`, `get_interfaces` or
  `continue_dialogue`.
- In a locked build the **top-left 200×36 canvas pixels are the spectator overlay**, not game
  content. Ignore them when reading a frame.
- Scale 0.25–1.0. Read the scale from the text line; never assume 1.0.

## Tool surface

26 tools in the current build. By group:

- **State**: `get_status`, `get_inventory`, `get_equipment`, `get_skills`, `get_chat`
- **World**: `find_entities`, `list_actions`, `do_action`, `interact`, `nav_status`, `nav_cancel`,
  `walk_to`
- **Interfaces and dialogue**: `get_interfaces`, `continue_dialogue`, `choose_option`
- **Input**: `type_text`, `press_key`, `mouse_click`, `drag_item`, `camera`, `cancel_selection`
- **Session**: `login`, `logout`, `get_account`, `get_screenshot`, `wait_for`

`tools/list` is authoritative — the count grows as tickets land. Gameplay use of each is in
[rt4-play.md](rt4-play.md); the auth ones are in [auth-flows.md](auth-flows.md).

## Safety

- Only drive a client pointed at **this machine's server or the project's AI-only world**. Driving
  an account on a live 2009Scape server is botting: it breaks their rules and gets accounts
  banned. Check `ip_address` in `config.json` first; if it is not this box or the project's world,
  stop and ask.
- The client's MCP events are marked synthetic so the input gate lets them through. Real input at
  the keyboard is dropped in a locked build — a human cannot fight you for control, but a real
  click can still cancel a walk ([rt4-play.md](rt4-play.md#3-working-style)).
