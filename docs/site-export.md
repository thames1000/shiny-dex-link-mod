# Site Export Format

The Shiny Dex website (`../cobblemon-shiny-dex`) can import a file this mod writes,
updating the player's dex straight from their save. Add an in-game command (e.g.
`/shinydex export`) that writes one of the shapes below to a file the player can
download/copy, then load via **Data → Minecraft mod sync → Import mod export**.

The import is **upgrade-only**: it raises a species from
`none → seen → caught → ✨ shiny` but never downgrades, and never touches a
manually `📦 boxed` mon (boxed is a website-only step above shiny). So the same
file can be re-imported safely, and a full snapshot every time is fine.

## Preferred shape — full Pokédex snapshot

```json
{
  "shinydexLink": 1,
  "minecraftName": "Thamescape",
  "exportedAt": "2026-06-24T20:20:00Z",
  "pokemon": [
    { "species": "mareep",  "caught": true, "shiny": true },
    { "species": "pikachu", "caught": true, "shiny": false },
    { "species": "rattata", "seen": true }
  ]
}
```

- `species` — the Cobblemon species name, lowercase (e.g. `mareep`, `nidoran-f`,
  `mr-mime`). Separators are normalized, so `mr_mime` / `Mr. Mime` also match.
  You may send a national-dex number as `dex` instead of (or alongside) `species`.
- State is taken from flags, highest wins: `shiny:true` → ✨ shiny;
  else `caught:true` → caught; else `seen:true` → seen.
- The wrapper key may also be `entries`, `catches`, or `events` instead of
  `pokemon`. `minecraftName` / `exportedAt` / `shinydexLink` are optional metadata.

## Also accepted — catch-event list

A bare array of the same `CatchEventRequest` payloads the backend already
receives. An entry with a `species` and no flags is treated as a catch;
`eventType: "pokemon_caught"` and `shiny: true` are honored.

```json
[
  { "species": "mareep", "displayName": "Mareep", "shiny": true,  "eventType": "pokemon_caught" },
  { "species": "zubat",  "displayName": "Zubat",  "shiny": false, "eventType": "pokemon_caught" }
]
```

The existing `config/shinydex-link/event_queue.json` (an array of `QueuedEvent`,
each wrapping its payload under `event`) is accepted as-is — the importer unwraps
`.event` automatically. That means even without a new command, a player can load
their queue file to apply any catches that hadn't synced.

## Notes

- Unknown species names / dex numbers are counted and reported, not fatal.
- Forms/regional variants aren't mapped onto the website's separate Variants tab
  yet — only the national dex (caught/shiny) is updated. The `form` field is
  ignored for now.
