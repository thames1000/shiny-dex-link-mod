# ShinyDex Hunt Counter — User Guide

This guide covers everything needed to build, install, and use the shiny hunt counter added to
ShinyDex Link. If you only want catch syncing (no overlay), you can skip the client install — see
[Who needs to install what](#who-needs-to-install-what).

---

## 1. What it does

- Shows a **hunt counter overlay** for every species you're hunting — you can run several hunts at
  once (up to `maxConcurrentHunts`, default 10).
- **Counts automatically** when you battle a target (encounters) or hatch an egg of a target.
- **Counts manually** with per-hunt `+`/`-` buttons in the hunt screen, for spawns you only see or
  for fixups.
- **Place the overlay anywhere** — drag it to your preferred spot; the choice is saved per client.
- When you finally **catch or hatch a shiny**, that hunt's final count is attached to the catch
  that syncs to the Shiny Dex website, then that hunt clears.

---

## 2. Requirements

| Component | Version |
|---|---|
| Minecraft | `1.21.1` |
| Fabric Loader | `0.17.2`+ |
| Fabric API | `0.116.6+1.21.1` |
| Cobblemon | `1.7.3` |
| Java | `21` |

The hunt counter is most useful with Cobblemon installed (for auto encounter/egg counting and
species suggestions). Without Cobblemon, the manual keybind still works.

---

## 3. Build the mod

From the project root:

```bash
./gradlew build
```

The mod jar is produced at:

```
build/libs/shinydex-link-0.1.0.jar
```

(Ignore the `-sources.jar`.)

---

## 4. Install

### Who needs to install what

| Setup | Server has mod | Client has mod | Result |
|---|---|---|---|
| **Single-player** | n/a (your game is both) | ✅ | Full feature set, overlay included |
| **Dedicated/LAN server** | ✅ | ✅ | Full feature set, overlay included |
| **Server only** | ✅ | ❌ | Linking + catch sync work; **no overlay/keybinds** for that player |

The hunt overlay, keybinds, and screen are **client-side**, so anyone who wants the overlay must
have the mod in their own client. The hunt logic and syncing are server-authoritative.

### Steps

1. Install **Fabric Loader**, **Fabric API**, and **Cobblemon `1.7.3`**.
2. Drop `shinydex-link-0.1.0.jar` into the `mods/` folder:
   - **Single-player / client:** your client `mods/` folder.
   - **Dedicated server:** the server `mods/` folder **and** each player's client `mods/` folder.
3. Start once to generate `config/shinydex-link.json`, then configure it (next section).

---

## 5. Configure

Edit `config/shinydex-link.json` (on the server, or your single-player instance):

```json
{
  "enabled": true,
  "apiBaseUrl": "https://cobblemon-shiny-dex.vercel.app",
  "serverToken": "your-real-token",
  "serverId": "cobbleverse-main",

  "enableHuntCounter": true,
  "huntCountEncounters": true,
  "huntCountEggHatches": true,
  "maxConcurrentHunts": 10,
  "syncHuntProgress": true
}
```

- `apiBaseUrl` — set to your backend URL. Leave as `mock` (or blank) to test without real HTTP.
- `serverToken` — your backend auth token (never share it with players).
- `enableHuntCounter` — master switch for the whole hunt feature.
- `huntCountEncounters` / `huntCountEggHatches` — the **defaults** applied to each new hunt; you
  can still toggle either per hunt in-game.
- `maxConcurrentHunts` — how many hunts one player may run at the same time (default 10).
- `syncHuntProgress` — when on (and you're linked), your hunts are saved to the website as you
  **disconnect**, and a hunt you **start** resumes from the site's saved count if it has one. With
  `mock`/blank `apiBaseUrl` the fetch just reports "no saved hunt", so hunts start at 0.

Restart after editing.

---

## 6. Link your account (one-time, for syncing)

Catch sync only happens for linked players (unless `syncUnlinkedPlayers` is on). The overlay/counter
work whether or not you're linked, but the **completion won't sync** until you link.

1. On the Shiny Dex website, generate a one-time link code.
2. In-game: `/shinydex link <code>`
3. Confirm with `/shinydex status`.

---

## 7. Start and run a hunt

You can drive hunts from **commands** or the **hunt screen** — both act on the same
server-side hunts. You may have several hunts running at once (default cap 10).

### Commands

| Command | Action |
|---|---|
| `/shinydex hunt <species>` | Start hunting a species (e.g. `/shinydex hunt mareep`). Adds a new hunt, or resets it to 0 if already running. |
| `/shinydex hunt <species> <form>` | Hunt a specific form (e.g. `/shinydex hunt vulpix alolan`). A separate hunt from the any-form one. |
| `/shinydex hunt status` | List every active hunt with its total and encounter/egg/manual breakdown. |
| `/shinydex hunt reset [species [form]]` | Reset one hunt's counter to 0 (or **all** if no species given). |
| `/shinydex hunt stop [species [form]]` | End one hunt (or **all** if no species given). |
| `/shinydex hunt encounters [species [form]]` | Toggle auto-counting battles for one hunt (or all). |
| `/shinydex hunt eggs [species [form]]` | Toggle auto-counting egg hatches for one hunt (or all). |

> For the per-hunt commands, if you give only a species and a single hunt matches it, that hunt is
> used; if several forms of that species are active, add the form to disambiguate.

> Use the species **id** (lowercase, e.g. `mareep`, `mr_mime`), not necessarily the display name.

### Hunt screen (default key: `H`)

- Type a species — **live suggestions** appear from Cobblemon's registry; click one to fill it in.
  (Type `species form`, e.g. `vulpix alolan`, to pin a form.)
- **Add hunt** — starts a new hunt for the typed species (up to the cap).
- Each active hunt gets its **own row** with: `-` / `+` (manual count), **Enc:On/Off** and
  **Egg:On/Off** (toggle auto-counting), **Reset**, and **Stop**.
- **Edit overlay position** — opens the drag editor (see below).
- **Stop all hunts** — clears every hunt at once.

### Overlay placement

The overlay starts in the **top-right** corner, clear of the battle info Cobblemon draws top-left.
To move it, open the hunt screen → **Edit overlay position**, drag the panel anywhere, then press
**Done**. The position is saved per client in `config/shinydex-link-client.json` (use **Reset to
default** to return it to the corner).

### Keybinds (rebindable in Options → Controls → category "ShinyDex Link")

| Default key | Action |
|---|---|
| `H` | Open the hunt screen (per-hunt `+`/`-` live there) |
| *(unbound)* | Toggle the overlay on/off (set a key if you want it) |

---

## 8. How counting works

While a hunt is active, the counter goes up from:

- **Encounters** — entering a wild battle against the target species (auto, if enabled). Wild
  spawns by themselves aren't counted because they aren't tied to a specific player — the battle
  is the first attributable moment. Use the row's `+` button to count every spawn you see.
- **Egg hatches** — hatching an egg of the target species (auto, if enabled).
- **Manual** — the row's `+` / `-` buttons, for anything the auto counters can't see.

The overlay shows each hunt's **total** plus an `(E… G…)` encounters/eggs breakdown. A single
battle or hatch increments **every** matching hunt (e.g. an any-form and a form-specific hunt for
the same species).

---

## 9. Finishing a hunt (what gets sent)

- **Encounter hunts:** when you **catch** the target shiny, the catch syncs to the website with the
  final hunt count attached, and the hunt clears.
- **Egg hunts:** when a **shiny target hatches**, that hatch completes the hunt — it's synced as a
  catch (with the count) and the hunt clears. (Hatching never triggers a normal capture, so the mod
  handles this case specially.)

The attached fields on the synced catch are `huntCount`, `huntKind` (`encounters` / `eggs` /
`mixed`), and `huntStartedAt`. See [`backend-api.md`](backend-api.md). Ordinary (non-hunt) catches
don't include these.

If the backend is unreachable at that moment, the catch is queued and retried automatically — the
hunt count rides along in the queued event.

---

## 10. Troubleshooting

- **No overlay appears.** Make sure the mod is in your **client** `mods/` folder (not just the
  server), `enableHuntCounter` is `true`, and you've started a hunt. Press `F1`? The overlay hides
  with the vanilla HUD. Check you didn't toggle it off.
- **Counter doesn't auto-increment on battles/eggs.** Confirm Cobblemon `1.7.3` is installed and
  check the server log at startup for `Registered ShinyDex Cobblemon hunt hooks`. If a hook failed
  to attach (Cobblemon API change), the log says so and the **manual `+` button** still works.
- **No species suggestions in the screen.** That list comes from Cobblemon's registry via the
  client; it's empty if Cobblemon isn't on the client. You can still type the species id manually.
- **Catch didn't sync the count.** You must be **linked** (`/shinydex link`), and the caught/hatched
  species (and form, if you pinned one) must match the active hunt target.
- **Keys conflict.** Rebind in Options → Controls → "ShinyDex Link".

---

## 11. Quick start (TL;DR)

```text
1. ./gradlew build  → copy build/libs/shinydex-link-0.1.0.jar to mods/ (client + server)
2. Install Fabric API + Cobblemon 1.7.3
3. Launch once, set apiBaseUrl + serverToken in config/shinydex-link.json, restart
4. /shinydex link <code>           (one-time, from the website)
5. /shinydex hunt mareep           (or press H and add a species; start more hunts if you like)
6. Battle / hatch / use a row's + to count; catch or hatch the shiny → count syncs automatically
```
