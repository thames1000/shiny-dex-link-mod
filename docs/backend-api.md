# Backend API Contract

All requests include `serverToken` from `config/shinydex-link.json`. The mod never sends events for unlinked players unless `syncUnlinkedPlayers` is enabled.

> **Reference implementation:** the Shiny Dex website ships these endpoints as
> Vercel serverless functions backed by Cloud Firestore — see
> `../cobblemon-shiny-dex/api/minecraft/` and `SETUP-MOD-SYNC.md` in that repo.
> Point `apiBaseUrl` at `https://<project>.vercel.app/api` (the `/api` prefix is
> where Vercel serves the functions). Link codes are matched case-insensitively.

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
  "aspects": ["alolan"],
  "gender": "female",
  "level": 18,
  "ball": "quick_ball",
  "caughtAt": "2026-06-24T20:15:31Z",
  "huntCount": 187,
  "huntKind": "encounters",
  "huntStartedAt": "2026-06-24T18:02:11Z"
}
```

`huntCount`, `huntKind`, and `huntStartedAt` are present **only** when this catch (or egg hatch)
completed an active shiny hunt for the player. `huntCount` is the total attempts it took
(encounters + egg hatches + manual bumps), and `huntKind` is `"encounters"`, `"eggs"`, or
`"mixed"`. All three are omitted for an ordinary catch. Egg hunts complete on hatch — for those,
the payload arrives with `ball` null and `shiny` true, since hatching never fires a capture.

`aspects` is the Cobblemon aspect list for the captured Pokémon (e.g.
`["alolan"]`, `["region-bias-alola"]`). It is omitted for a plain form. The
backend matches `aspects` (falling back to `form`) against the website's Variants
catalog — national dex number + aspect/form name — to update the player's
**Variants** tab in addition to the national dex. The `shiny` aspect is stripped
by the mod since the dedicated `shiny` flag already carries it.

Response:

```json
{
  "success": true,
  "duplicate": false,
  "updated": {
    "normalCaught": true,
    "shinyCaught": true,
    "newDexEntry": true,
    "variantId": "Mareep-alolan",
    "variantCaught": true,
    "variantShinyCaught": true
  }
}
```

`variantId` is null and the `variant*` flags false when no variant matched.
Variants only have caught/shiny states (no seen, no boxed). The backend should
treat `eventId` as idempotent and ignore duplicates.

## POST `/minecraft/berries`

Reports the berries a linked player holds (from `/shinydex berries`). Berries are
a set-only collection, so the call is idempotent.

Request:

```json
{
  "serverToken": "secret",
  "serverId": "cobbleverse-main",
  "minecraftUuid": "uuid",
  "minecraftName": "Thamescape",
  "berries": ["occa", "lum", "sitrus"]
}
```

`berries` ids may be bare (`occa`) or full item ids (`cobblemon:occa_berry`); the
backend normalizes and ignores anything not in its berry list.

Response:

```json
{
  "success": true,
  "message": "OK",
  "added": 2,
  "total": 3,
  "received": 3,
  "ignored": 0
}
```

## POST `/minecraft/test-event`

Same payload shape as `/minecraft/catches`, but used only for manual connectivity tests.
