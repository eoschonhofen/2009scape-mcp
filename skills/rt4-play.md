# Playing 2009Scape through MCP

Everything the client's UI can do is reachable through its MCP tools. This is the gameplay
manual: ground rules, the common flows, and the traps that cost the most time. Connection
mechanics are in [rt4-mcp-client.md](rt4-mcp-client.md); account creation and login are in
[auth-flows.md](auth-flows.md).

Paths below are relative to the client repo (`client/` in this workspace). The Claude Code copy of
this text is `client/.claude/skills/rt4-play/SKILL.md`; this file is the canonical, agent-neutral
version and should be updated first.

## 1. Ground rules

- **An action is only an ack.** `do_action`, `interact`, `type_text` and `login` return once the
  packet is queued. The server may still refuse it ("You can't reach that", "I can't do that", a
  level requirement). Always follow an action with `wait_for` plus a state read (`get_status`,
  `get_inventory`, `get_chat`).
- **Use `wait_for` instead of sleeping or polling.** Combine conditions with `mode: "any"` so you
  wake on success or failure, e.g. `[inventory_changed, chat_matches("can't|cannot|need"),
  idle(for_ticks=5)]`. A timeout returns `met=false`, not an error.
- **Targets come from discovery.** Get target ids from `find_entities`, `get_inventory` or
  `get_interfaces`, then check `list_actions(target)` when the op name is unclear. Never guess
  `npc:<index>`; indexes change when NPCs leave and re-enter view.
- **`find_entities` types are `npc`, `loc` (objects), `obj` (ground items) and `player`.** An
  unknown `type` returns `{entities: [], count: 0}` instead of erroring, so a typo looks exactly
  like "nothing is there". `type=item` is the common wrong guess. When unsure, drop `type` and
  filter by `name`.
- **Read the chat cursor.** Keep `next` from `get_chat` and pass it back as `since`, so you only
  see new lines. Chat is where most refusals and results show up.
- **Prefer text tools to screenshots.** Use `get_screenshot` (scale 0.25–0.5) only for unknown
  interfaces, puzzles, or a sanity check. Never screenshot to read dialogue or instructions:
  `continue_dialogue` returns the full transcript and options, and `get_interfaces` returns every
  text component (tutorial hints, prompts, progress bars).
- **Avoid `mouse_click`.** Use it only when no `if:` target or minimenu op exists, and convert
  coordinates first: `canvas = (region_x, region_y) + pixel / scale`. A pixel read off a 0.5-scale
  screenshot is half the canvas coordinate, so clicking it raw misses.
- **`wait_for` conditions and their required arguments.** Conditions are validated before the wait,
  so a wrong name fails fast and lists the valid ones:

  | condition | required | condition | required |
  |---|---|---|---|
  | `nav_done` | `task` | `chat_matches` | `regex` (not `pattern`) |
  | `interface_open` | `id` | `interface_closed` | `id` |
  | `hp_below` | `n` | `npc_gone` | `target` |
  | `item_count` | `id` | `ticks` | `n` |
  | `position` | `x` | `idle` | `for_ticks` (optional) |
  | `inventory_changed` | — | `skill_xp_changed` | — |
  | `dialogue_open` | — | `animation` | — |
  | `logged_in` / `logged_out` | — | | |

  `skill_xp_changed` is the best single condition for gathering and combat loops, because it fires
  on the xp drop whether or not an item appears.
- **`interact` finds by `name` only.** For a known target id, use `do_action(target, op)`. For
  option menus, use `choose_option(text=<substring>)` or `choose_option(index=<1-based>)`.
- **Filter the debug chat.** With debug mode on, every action appends `[IF ACTION]`,
  `[SCENERY INTERACT]`, `Iface:`, `Child:`, `Slot:`, `ID:`, `Loc:`, `RCM`,
  `Received interaction request` and `Dialogue opening` lines. The last 10 messages are then all
  noise and you will miss the refusal. Always filter:
  ```bash
  python3 -I scripts/rt4-mcp.py call get_chat since=0 limit=500 | python3 -c '
  import json,sys,re
  bad=re.compile(r"^(\[|-+|Iface|Child|Slot|ID:|Loc:|RCM|ItemID|Received|Dialogue)")
  print([t for t in (m["text"] for m in json.load(sys.stdin)["messages"]) if not bad.match(t)][-8:])'
  ```
  The noise is still useful for one thing: it echoes the component the server actually received.
- **Clear selections.** A leftover "Use item" or selected spell gets used by the next `do_action`.
  `get_status` does not report it, so call `cancel_selection` after any "Use" or spell flow that
  did not finish.
- **Check before risky actions.** `hp` before fights, the open interfaces before typing, and
  `logged_in` before anything else.

## 2. Common flows

**Log in** — on the AI-only world the password is the account token; on a local no-auth dev world
any credentials work. Never echo a token or password into chat or logs.
```
get_status                          -> logged_in=false, game_state=10 (title screen)
get_account                         -> [{name, token, created}] from accounts.json (AI-only)
login(username, password)           -> queued
wait_for([logged_in], timeout_ms=30000)
get_status                          -> position, hp, open_interfaces
```

**Walk** — read this whole block before any cross-map trip. Navigation is where most time gets
lost.
```
walk_to(x, y, radius=1)             -> { task }
wait_for([nav_done(task)], timeout_ms=60000)
nav_status                          -> last_state ARRIVED, or FAILED with last_reason
```
`walk_to` opens doors and gates on the way and re-plans as regions load. A gate between you and the
destination is handled for you: aim at a tile *past* it and check `last_doors_opened` in
`nav_status`. Stairs, ladders, shortcuts and teleports are out of scope and fail with a reason.

Four things will bite you:

1. **The destination must be inside the loaded scene.** Outside it the call errors with
   `out of loaded scene: tile:X,Y is not in the 104x104 scene around you`, roughly 50 tiles in each
   direction. Cross-map travel is several `walk_to` legs, re-checking position between them, not one
   call.
2. **`FAILED` does not mean the character stopped.** `no path on plane 0` and `stuck at x,y` are
   both common, and the character often keeps walking for several seconds afterwards. Position in
   `get_status` lags too. After a failure, `nav_cancel`, then sleep a few seconds and read
   `get_status` twice before deciding where you are. Chasing a stale position once sent a playthrough
   from Lumbridge to the Champions' Guild.
3. **A long leg that fails is not proof the route is blocked.** The same trip usually succeeds as
   three short legs along the road. Probe a few candidate tiles and keep the ones that return
   `ARRIVED`.
4. **When lost, teleport instead of pathfinding home.** Lumbridge Home Teleport is free and resets
   you to a known tile. See below. This is almost always faster than walking back.

A tiny wrapper saves a lot of typing:
```bash
#!/bin/bash   # walk.sh x y [radius]
cd /path/to/client
T=$(python3 -I scripts/rt4-mcp.py call walk_to x=$1 y=$2 radius=${3:-1} | python3 -c 'import json,sys;print(json.load(sys.stdin)["task"])')
python3 -I scripts/rt4-mcp.py call wait_for "{\"conditions\":[{\"condition\":\"nav_done\",\"task\":$T}],\"timeout_ms\":60000}" >/dev/null
python3 -I scripts/rt4-mcp.py call nav_status | python3 -c 'import json,sys;d=json.load(sys.stdin);print(d.get("last_state"),d.get("last_reason",""))'
python3 -I scripts/rt4-mcp.py call get_status | python3 -c 'import json,sys;print(json.load(sys.stdin)["position"])'
```

**Teleport**
Spells live in interface 192 and their components carry `Cast` but no text, so you cannot find a
spell by name in `get_interfaces`. Get the names from `list_actions`, whose `subject` is the spell:
```bash
for c in 0 3 9 15 18 21 23 26; do python3 -I scripts/rt4-mcp.py call list_actions target=if:192:$c \
  | python3 -c 'import json,sys;d=json.load(sys.stdin);print(d["target"],[a["subject"] for a in d["actions"]][:1])'; done
```
`if:192:0` was Lumbridge Home Teleport on this server (free, no runes). Cast it with
`do_action(target="if:192:0", op="Cast")`, then allow about 20 seconds: the animation is long and
`wait_for` has no teleport condition, so wait on `position(x=...)` or sleep and read `get_status`.
Component numbers are per-server; list them rather than trusting `if:192:0`.

**Talk to an NPC and get through the dialogue**
```
interact(name="Hans", op="Talk-to")
wait_for([dialogue_open], timeout_ms=10000)
continue_dialogue(max_steps=20)     -> { transcript, stopped: options|closed|no_continue|no_change|max_steps|deadline }
choose_option(text="...")           -> then continue_dialogue again
```

**"I can't reach that."**
The most common refusal, and it is about position, not the target. `find_entities` happily returns
things you cannot act on, and `interact`/`do_action` only walk you part of the way.
- Ground items (`obj:`) often need you standing **on** their exact tile: `walk_to(x, y, radius=0)`
  using the entity's own coordinates, then `Take`.
- Fenced areas (wheat fields, farm pens) block reaching a loc from outside even at distance 1.
  `walk_to` a tile **inside** the enclosure first and let pathfinding open the gate.
- Otherwise walk to the target's tile with `radius=1` and retry once.
Check the filtered chat after each attempt; the refusal is the only signal, as `do_action` still
returns `ok: true`.

**Use an item on a loc (hopper, furnace, altar, …)**
```
do_action(target="if:149:0:<slot>", op="Use")     -> selects the item
list_actions(target=<loc>)                        -> "Use" with subject "Grain -> Hopper",
                                                     plus a cancel_hint confirming the selection
do_action(target=<loc>, op="Use")                 -> performs it
```
`list_actions` is the way to confirm a selection is live: it reports `cancel_hint` while one is
pending. Call `cancel_selection` if the flow does not complete.

**Climb between floors**
Ladder ops vary by object: some only have `Climb-up`, some only `Climb-down`, some have `Climb` plus
both. Always `list_actions` first. After climbing, the `plane` in `get_status` changes and every
cached target id from the old floor is dead, so re-run `find_entities` on the new floor. `walk_to`
is single-plane, so each floor is its own navigation problem.

**Gather (trees, rocks, fishing spots)**
```
interact(name="Tree", op="Chop down", type="loc")
wait_for([inventory_changed, chat_matches("inventory is too full|need a|do not have"), idle(for_ticks=5)], timeout_ms=30000)
get_inventory
```
Repeat until the backpack is full (28 slots), then bank or drop.

**Bank**
```
interact(name="Banker", op="Bank")   (or a "Bank booth" loc with op "Bank")
wait_for([interface_open(<bank id>), idle(for_ticks=3)])
get_interfaces                      -> find the bank and backpack slot targets and their ops
do_action(target="if:<iface>:<child>:<slot>", op="Deposit-All")
```
Read the interface ids from `get_status.open_interfaces` and `get_interfaces`. Do not hard-code ids
you have not seen in this session.

**Fight**
```
find_entities(type=npc, name="Goblin", has_op="Attack")
do_action(target, op="Attack")
wait_for([npc_gone(target), hp_below(n), idle(for_ticks=8)], timeout_ms=60000)
```
Set `hp_below` well above zero, and eat (`interact(name=<food>, op="Eat", type="inv")`) when it
fires.

**Find an interface button.** Buttons are usually two components: a text label with no ops (e.g.
`"Next"`) and a sibling with the op (e.g. `"Select next"`). `list_actions` on the label returns
nothing. List only the components that have ops, and pick the button by its op string:
```bash
python3 -I scripts/rt4-mcp.py call get_interfaces interface_id=771 | python3 -c '
import json,sys
for c in json.load(sys.stdin)["interfaces"][0]["components"]:
    if c.get("ops"): print(c["target"], c["ops"][0])'
```
Component ids change from page to page of the same interface. The same "Next" was `if:771:58`, then
`if:771:167`, then `if:771:319` in the character designer. Re-list after every page change, and do
not reuse an id from the previous page.

**Tutorial Island (new account)**
A fresh account starts at about (3094, 3107) in the guide's house. `walk_to` fails `stuck` because
the tutorial locks doors until each step is done. Note the create flow's DOB/country screen is
separate from this and its values are ignored ([auth-flows.md](auth-flows.md)).
1. Character designer (interface 771) opens with the mouse-options page. Tab components switch
   pages (`Select mouse/gender/head/body options`). On each page, press its own `Select next`. The
   body page's button leads to a "Do you want to accept..." page, where `Confirm changes` finishes
   the design. Progress is the `if:371:1` text ("1% Done").
2. To skip the tutorial (one chance only), talk to **Skippy** (in the starting house), then
   `choose_option("Yes, I'd like to skip")`. This sets progress to 100% and moves you next to the
   Magic instructor at about (3141, 3089). **You are still on the island.**
3. Talk to the **Magic instructor** and pick `choose_option("Yes, I'm ready")` at "Leave Tutorial
   Island?". Then `continue_dialogue` until "Welcome to Lumbridge!". The starter kit shows up in the
   backpack.
4. Verify with `get_status` (position changed to Lumbridge) and `get_inventory` (non-empty) before
   reporting done. "100% Done" alone does not mean you have left the island.

Without skipping, follow the hint text in interface 372 (`get_interfaces interface_id=372`) and talk
to each instructor in turn. The RuneScape guide only repeats "follow the onscreen instructions"
until the designer is confirmed.

**Quests**
Ask the **Lumbridge Guide** (`Talk-to`, then the "Where can I find a quest to go on?" option) for a
pointer. Quest dialogue is a loop of `continue_dialogue` and `choose_option`; read `stopped` to know
which comes next. Check progress with the filtered chat (`Congratulations! Quest complete!`) and
with `get_skills skill=<name>` for the reward xp. Search the wiki rather than trusting an NPC's
directions: the in-game hints are vague and the F2P map matches the wiki.

Worked example, **Cook's Assistant** (the F2P starter, ~15 min over MCP):
1. Cook in Lumbridge Castle kitchen, about (3207, 3214). Accept, then ask for the ingredients.
2. **Milk**: `Dairy cow` loc (op `Milk`) in the pen north-east of the castle, with an empty bucket.
   The gate is a `Gate` loc with `Open`.
3. **Egg**: `obj` named Egg at the chicken farm north of the castle. Stand on its tile.
4. **Flour**: pick `Wheat` (op `Pick`) from inside a field, then Mill Lane Mill, north of Lumbridge
   and west off the road. Open the mill door, climb the ladder **two** floors, use the grain on the
   `Hopper`, `Operate` the `Hopper controls`, climb back down and `Empty` the `Flour bin` into an
   empty pot.
5. Return to the Cook. He takes all three ingredients in one dialogue.

**Items**: use the inventory slot targets from `get_inventory` with
`do_action(target="if:149:0:<slot>", op="Wield"|"Drop"|"Eat"|"Use")`. Move items with
`drag_item(from_slot, to_slot)`. For "Enter amount" prompts, use `type_text("10", enter=true)`.
After Tutorial Island the starter kit is in the backpack but nothing is worn: wield the sword and
shield before fighting and confirm with `get_equipment`.

**Log out**: `logout`, then `wait_for([logged_out])`. The server refuses it for about 10 s after
combat.

## 3. Working style

- State the goal, act in small steps, and verify each one. Do not chain several actions without a
  read in between, because a refused first action makes the rest meaningless.
- After two failed attempts of the same kind, change approach: a different target, a different
  tile, `list_actions`, or a screenshot. Do not loop, and never spam the same action in a batch. If
  `ok: true` keeps coming back but nothing changes, you are pressing the wrong component. Re-list
  the ops.
- Do not declare a blocker a "bug" until you have listed every component with ops on the open
  interface and talked to every nearby NPC. The way forward is usually there.
- Claim a milestone only after a state read proves it (position, inventory, chat line). Quote the
  evidence.
- Report progress in game terms: position, items gained, XP, and what blocked you (quote the chat
  line).
- Look up quest and location details on the wiki rather than wandering. One search for a walkthrough
  is cheaper than ten failed walks.
- For a repeated loop (combat, gathering, banking), write a small driver that calls the tools and
  checks state each iteration, instead of issuing the same calls by hand. Keep a stop condition on
  hp and on "no target found".
- A real mouse click by the human cancels `walk_to`. If a walk ends `CANCELLED`, assume the human
  took over and ask before continuing.

## 4. Limits and safety

- One plane per `walk_to`, and the destination must be inside the loaded 104×104 scene. There is no
  world hopping, and component-to-component drags are not supported.
- Ticks are wall-clock estimates (`ticks_approximate: true`).
- Each game-thread hop has a 2 s budget, and `wait_for` blocks for at most 60 s. Loop `wait_for` for
  longer jobs.
- Only play on the local server or the project's own AI-only world. Driving an account on a live
  2009Scape server this way is botting and gets accounts banned. If `ip_address` in `config.json`
  is not this machine or the project's world, stop and ask.
