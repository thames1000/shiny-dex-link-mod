# ShinyDex Link

Server-side Fabric companion mod for linking Cobblemon players to Shiny Dex and syncing catch events.

Target stack:

- Minecraft `1.21.1`
- Fabric Loader `0.17.2`
- Fabric API `0.116.6+1.21.1`
- Java `21`
- Cobblemon `1.7.3`

The mod is intentionally server-only: no blocks, items, recipes, screens, keybinds, rendering, or required client install.

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

The capture hook was verified from the Cobblemon `1.7.3` source:

- `com.cobblemon.mod.common.api.events.CobblemonEvents.POKEMON_CAPTURED`
- `com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent`
- Event properties: `pokemon`, `player`, `pokeBallEntity`

`CobblemonCaptureListener` uses reflection so this project stays buildable without bundling Cobblemon as a compile-time dependency. If Cobblemon is missing or the API shape changes, linking and test commands still work and capture sync logs a clear warning.

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
