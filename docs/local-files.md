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
- `maxConcurrentHunts` (default `10`) — how many distinct hunts one player may run at once.
- `syncHuntProgress` (default `true`) — push a player's hunts to the website when they disconnect,
  and pull saved progress when a hunt starts so the counter resumes. Needs the player linked (or
  `syncUnlinkedPlayers`).

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

Each player's active shiny hunts by Minecraft UUID — a **list** per player, since one player can
run several hunts at once (up to `maxConcurrentHunts`). Survives restarts; a hunt is removed when it
is caught/hatched or stopped. Files written by the original single-hunt version (a single object per
UUID instead of a list) are read and migrated automatically.

```json
{
  "minecraft-uuid-here": [
    {
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
  ]
}
```

## `config/shinydex-link-client.json`

Client-only overlay preferences (not synced to the server): the saved overlay position and whether
it is shown. `x`/`y` of `-1` mean "not yet placed", so the overlay falls back to its default
top-right corner.

```json
{
  "x": 312,
  "y": 8,
  "visible": true
}
```
