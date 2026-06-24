# Local Files

## `config/shinydex-link.json`

Server config. `apiBaseUrl` may be `mock` or blank to avoid real HTTP calls.

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
