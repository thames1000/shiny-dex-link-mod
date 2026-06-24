# Backend API Contract

All requests include `serverToken` from `config/shinydex-link.json`. The mod never sends events for unlinked players unless `syncUnlinkedPlayers` is enabled.

## POST `/minecraft/link/verify`

Request:

```json
{
  "serverToken": "secret",
  "serverId": "cobbleverse-main",
  "linkCode": "8F4K-22QX",
  "minecraftUuid": "uuid",
  "minecraftName": "Thamescape"
}
```

Response:

```json
{
  "success": true,
  "message": "Linked successfully",
  "linkedAccountId": "user_123"
}
```

## POST `/minecraft/unlink`

Request:

```json
{
  "serverToken": "secret",
  "serverId": "cobbleverse-main",
  "minecraftUuid": "uuid"
}
```

Response:

```json
{
  "success": true,
  "message": "Unlinked"
}
```

## POST `/minecraft/catches`

Request:

```json
{
  "serverToken": "secret",
  "eventId": "cobbleverse-main:uuid:1782322531000:mareep:abcd1234",
  "serverId": "cobbleverse-main",
  "minecraftUuid": "uuid",
  "minecraftName": "Thamescape",
  "eventType": "pokemon_caught",
  "species": "mareep",
  "displayName": "Mareep",
  "shiny": true,
  "form": "normal",
  "gender": "female",
  "level": 18,
  "ball": "quick_ball",
  "caughtAt": "2026-06-24T20:15:31Z"
}
```

Response:

```json
{
  "success": true,
  "duplicate": false,
  "updated": {
    "normalCaught": true,
    "shinyCaught": true,
    "newDexEntry": true
  }
}
```

The backend should treat `eventId` as idempotent and ignore duplicates.

## POST `/minecraft/test-event`

Same payload shape as `/minecraft/catches`, but used only for manual connectivity tests.
