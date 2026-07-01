# ShinyDex Link

Fabric companion mod for linking Cobblemon players to Shiny Dex, syncing catch events, and
running an in-game shiny hunt counter overlay.

Target stack:

- Minecraft `1.21.1`
- Fabric Loader `0.17.2`
- Fabric API `0.116.6+1.21.1`
- Java `21`
- Cobblemon `1.7.3`

Linking and catch sync run server-side and need no client install. The **shiny hunt counter**
(overlay, keybinds, hunt screen) is client-side, so players who want it install the mod on their
client too; the server runs the authoritative hunt state and works in single-player as well.

## MVP Plan

1. Generate `config/shinydex-link.json` on first launch.
2. Store linked players in `config/shinydex-link/linked_players.json`.
3. Store failed catch sync events in `config/shinydex-link/event_queue.json`.
4. Register `/shinydex link <code>`, `/shinydex unlink`, `/shinydex status`, `/shinydex sync`, and `/shinydex test`.
5. Use async HTTP requests with mock mode when `apiBaseUrl` is `mock` or blank.
6. Subscribe to the verified Cobblemon 1.7.3 capture event when Cobblemon is installed.
7. Queue failed catch events and retry them on a background worker.
8. Keep full Pokédex snapshot sync as a future extension behind `CobblemonPokedexReader`.

## Project Structure

```text
src/main/java/com/thames/shinydexlink/
  ShinyDexLinkMod.java
  api/
    ApiEndpoints.java
    ShinyDexApiClient.java
    dto/
  cobblemon/
    CobblemonCaptureListener.java
    CobblemonPokedexReader.java
  command/
    ShinyDexCommand.java
  config/
    ConfigManager.java
    ShinyDexConfig.java
  data/
    EventQueue.java
    LinkedPlayerStore.java
  sync/
    CatchEventFactory.java
    SyncService.java
  util/
```

## Cobblemon API Verification

The capture and evolution hooks were verified from the Cobblemon `1.7.3` source:

- `com.cobblemon.mod.common.api.events.CobblemonEvents.POKEMON_CAPTURED`
- `com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent`
- Event properties: `pokemon`, `player`, `pokeBallEntity`
- `com.cobblemon.mod.common.api.events.CobblemonEvents.EVOLUTION_COMPLETE`
- `com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionCompleteEvent`
- Event property `pokemon` (the evolved result), plus `Pokemon.getOwnerPlayer()` for the owner

`CobblemonCaptureListener` and `CobblemonEvolutionListener` use reflection so this project stays buildable without bundling Cobblemon as a compile-time dependency. If Cobblemon is missing or the API shape changes, linking and test commands still work and sync logs a clear warning.

**Evolution sync:** evolving fires no capture event, so an evolved shiny (e.g. shiny Ralts → Kirlia) would otherwise never reach the dex. `CobblemonEvolutionListener` hooks `EVOLUTION_COMPLETE` and submits the evolved form as an ordinary catch — the backend records the new species as caught, and shiny-caught when the mon is shiny — exactly as a real capture would. Evolutions don't touch hunt counters. Disable with `syncEvolutions: false`.

**Evolved-away shiny pruning:** shininess carries through evolution, so evolving a shiny consumes the pre-evolution shiny. After a shiny evolution the listener scans the player's party and PC (via Cobblemon's storage API) for another shiny of the pre-evolution species; if none remains, it calls `POST /minecraft/catches/remove` so the dex clears that species' shiny-caught state and tracks *currently-owned* shinies rather than ever-owned ones. Normal-caught state is left intact. This is best-effort (not retried on failure). Disable with `pruneEvolvedShinies: false`.

Each capture also forwards the Pokémon's Cobblemon **aspects** (e.g. `alolan`, `region-bias-alola`) in the catch payload. The website matches these against its **Variants** tab so regional/cosmetic forms are tracked in addition to the national dex — see `docs/backend-api.md` and `docs/site-export.md`.

## Config

Generated at `config/shinydex-link.json`:

```json
{
  "enabled": true,
  "serverId": "cobbleverse-main",
  "apiBaseUrl": "mock",
  "serverToken": "change-me",
  "syncUnlinkedPlayers": false,
  "retryFailedEvents": true,
  "retryIntervalSeconds": 60,
  "logSuccessfulSyncs": true,
  "announceShinySyncToPlayer": true,
  "syncEvolutions": true,
  "pruneEvolvedShinies": true,
  "requestTimeoutSeconds": 10,
  "linkCooldownSeconds": 15,
  "testCooldownSeconds": 30
}
```

Use `apiBaseUrl: "mock"` while developing. The mod logs redacted JSON payloads instead of sending HTTP.

## Commands

- `/shinydex link <code>` verifies a one-time website code and stores the local link.
- `/shinydex unlink` marks the local link disconnected and notifies the backend if reachable.
- `/shinydex status` shows linked state, last sync time, and queued events.
- `/shinydex sync` is a stub for future full Pokédex sync.
- `/shinydex berries` scans your inventory, ender chest, and containers for Cobblemon berries and syncs the set to the website. Vanilla container items (shulker boxes) are scanned too; **Sophisticated Backpacks** contents are read best-effort when that mod is installed (see `BerryScanner` / `SophisticatedBackpacksReader`).
- `/shinydex test` sends a fake shiny Mareep test event for API connectivity.
- `/shinydex hunt <species> [form]` starts a shiny hunt for a species (optionally pinned to a
  form). You can run several hunts at once (up to `maxConcurrentHunts`, default 10). The per-hunt
  commands take an optional species/form to pick one hunt and otherwise apply to all:
  `/shinydex hunt status`, `/shinydex hunt stop [species [form]]`, `/shinydex hunt reset [species
  [form]]`, and `/shinydex hunt encounters|eggs [species [form]]` (toggle auto-counting). Stopping
  **all** hunts (when 2+ are active) asks for confirmation — run `/shinydex hunt stop confirm`
  within 15s to proceed.
- `/shinydex hunt surprise` (alias `/shinydex hunt random`) starts a hunt for a random species,
  preferring one you aren't already hunting. If the rolled target isn't one you fancy, back out
  with `/shinydex hunt stop <species>` (or `/shinydex hunt stop` to clear them all).

## Shiny hunt counter

See **[`docs/hunt-guide.md`](docs/hunt-guide.md)** for the full install + usage guide.

A client overlay lists every active hunt with its target and counter. Each hunt's counter
increments:

- **Automatically on encounters** — entering a wild battle against the target species.
- **Automatically on egg hatches** — hatching an egg of the target species. A shiny target hatch
  completes the hunt and is synced as a catch (since hatching fires no Cobblemon capture event).
- **Manually** — the `+`/`-` buttons on each hunt's row in the hunt screen, for counting spawns you
  only see, or fixups.

Either auto source can be toggled per hunt. When a target shiny is **caught or hatched**, that
hunt's final count is attached to the catch synced to the website
(`huntCount`/`huntKind`/`huntStartedAt`, see `docs/backend-api.md`) and the hunt clears.

**Cross-session resume** (when `syncHuntProgress` is on and you're linked): your hunts are pushed to
the website when you **disconnect**, and starting a hunt **pulls** any saved progress back so the
counter resumes where you left off. See `/minecraft/hunts/sync` and `/minecraft/hunts/fetch` in
`docs/backend-api.md`.

**Overlay placement:** the overlay starts in the top-right (clear of the battle info Cobblemon
draws top-left). Open the hunt screen → **Edit overlay position** to drag it anywhere; the spot is
saved per client in `config/shinydex-link-client.json`.

Default keybinds (rebindable in Options → Controls, category "ShinyDex Link"): `H` open hunt screen,
plus an unbound "toggle overlay". The hunt screen adds hunts (with live species suggestions), and
each active hunt has its own `+`/`-`, encounter/egg toggles, reset, and stop buttons, plus a
"Stop all hunts" button (two-click confirm). Hunt state is server-authoritative and works in single-player.

## Apex/Fabric Setup

1. Build the mod with `./gradlew build`.
2. Upload `build/libs/shinydex-link-0.1.0.jar` to the server `mods/` folder.
3. Ensure Fabric Loader, Fabric API, and Cobblemon `1.7.3` are installed server-side.
4. Start the server once to generate config.
5. Edit `config/shinydex-link.json`:
   - Set `serverId` to your server identifier.
   - Set `apiBaseUrl` to your backend base URL.
   - Set `serverToken` to a backend-generated secret.
6. Restart the server.

Do not share `serverToken` with players or paste it in public logs.

## Testing Without Minecraft

- Run unit tests: `./gradlew test`.
- Run `./gradlew build` to compile and package the mod.
- Leave `apiBaseUrl` as `mock` to inspect payloads without a live backend.
- The `/shinydex test` command can verify command registration and payload generation on a local Fabric server without catching a Pokémon.

## Future Work

- Full Cobblemon Pokédex snapshot sync.
- Admin resync for all linked players.
- Live website overlay updates.
- Discord shiny announcements.
- Server bounty or hunt recommendation features.
