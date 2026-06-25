# Local Files

## `config/shinydex-link.json`

Server config. `apiBaseUrl` may be `mock` or blank to avoid real HTTP calls.

Hunt-counter keys:

- `enableHuntCounter` (default `true`) — master switch for the hunt overlay, `/shinydex hunt`
  commands, and the Cobblemon battle/egg hunt hooks.
- `huntCountEncounters` (default `true`) — default for new hunts: auto +1 when the target
  species is encountered in a wild battle.
- `huntCountEggHatches` (default `true`) — default for new hunts: auto +1 when an egg of the
  target species hatches.

## `config/shinydex-link/linked_players.json`

Stores local link state by Minecraft UUID:

```json
{
  "minecraft-uuid-here": {
    "minecraftName": "Thamescape",
    "linked": true,
    "linkedAt": "2026-06-24T20:12:00Z",
    "lastSyncAt": "2026-06-24T20:20:00Z",
    "linkedAccountId": "user_123"
  }
}
```

## `config/shinydex-link/event_queue.json`

Stores failed catch events for retry. The server token is not persisted in queued events; it is added only when sending HTTP.

## `config/shinydex-link/hunts.json`

Each player's active shiny hunt by Minecraft UUID. Survives restarts; cleared for a player when
their hunt is caught/hatched or stopped.

```json
{
  "minecraft-uuid-here": {
    "species": "mareep",
    "displayName": "Mareep",
    "form": null,
    "encounters": 42,
    "eggs": 0,
    "manual": 3,
    "countEncounters": true,
    "countEggs": true,
    "startedAt": "2026-06-25T18:00:00Z",
    "updatedAt": "2026-06-25T18:42:00Z"
  }
}
```
