# Veritas Events

A RuneLite plugin that sends your drops to a Veritas clan event board, so you
don't have to screenshot and type them in by hand.

Built for the Veritas OSRS clan. The event URL is just a setting, so **anyone
in the clan can host an event** — stand up a board, hand out its URL and
password, and players point the plugin at it. Nothing about the plugin is tied
to one event or one organiser.

## What it sends

| | When |
| --- | --- |
| **Drops** | Any loot from a kill, chest or raid, above a value you choose |
| **Pets** | When the "funny feeling" message appears |
| **Collection log** | When a new entry is added |
| **Levels** | When you advance a skill, including 99s |
| **Quests** | When you finish a quest |
| **Combat achievements** | When you finish a combat task |
| **Clues** | Completed treasure trails |
| **Personal bests** | Only a new best, not every timed kill |

Each of these has its own switch, so an event that only cares about drops can
leave the rest off.

Each message carries the item name and id, quantity, Grand Exchange value, what
you killed, your RSN and the time — plus a screenshot, if you leave that on.

Nothing is sent until you paste in an event URL, so the plugin is inert until
your organiser gives you one.

## Setup

1. Install **Veritas Events** from the RuneLite Plugin Hub
2. Open its settings (the cog next to the plugin name)
3. Paste the **Event URL** your organiser gave you
4. Paste the **Event password** for that event
5. Choose what to send, and whether to include screenshots

That's it. Play normally and your drops turn up on the board.

## Big drops

Set a **Big drop value** and anything worth at least that much is flagged in the
message as `"big": true`, so the event page can call it out instead of listing
it quietly with everything else. It defaults to 1,000,000 gp.

## The side panel

The plugin adds a panel to the RuneLite sidebar showing:

- whether you are set up, and which event you are connected to
- how many tiles your team has done, if the board reports it
- everything sent this session, with whether the board accepted it
- a **Send again** button, for when the board was down at the time

## What the server receives

A single JSON object. With screenshots switched on it is sent as
`multipart/form-data`, with the JSON in a `payload_json` part and the image in a
`file` part — the same shape Dink uses, so a server written for one can accept
the other.

```json
{
  "v": 1,
  "type": "LOOT",
  "player": "sponge",
  "sentAt": 1789000000000,
  "source": "Vardorvis",
  "sourceType": "NPC",
  "quantity": 1,
  "totalValue": 1250000,
  "big": true,
  "items": [
    { "id": 28279, "name": "Blood quartz", "quantity": 1, "priceEach": 1250000 }
  ]
}
```

`type` is `LOOT`, `PET`, `COLLECTION_LOG`, `LEVEL`, `QUEST`, `ACHIEVEMENT`,
`CLUE` or `PERSONAL_BEST`. Only `LOOT` carries `source` and `items`; every other
type carries the chat line that triggered it as `message`.

The event password is sent as an `X-Event-Key` header, never in the URL, so it
stays out of server logs and browser history.

## What the panel asks for

The plugin also sends a plain `GET` to the same URL, with the same header, to
fill in the event name and progress. Answer with JSON:

```json
{ "event": "Battle Ship Bingo", "team": "Port", "done": 12, "total": 52 }
```

Every field is optional, and a board that does not answer at all simply leaves
that part of the panel blank.

## Privacy

- Nothing leaves your client until you enter an event URL
- Only what is listed above is sent, and only to the address you paste in
- Screenshots can be turned off while everything else keeps working
- Your account is identified by RSN only

## Building it yourself

```
./gradlew build
```

To try it in a real client, run `VeritasEventsPluginTest` in `src/test/java`.
It starts RuneLite with this plugin loaded, so you do not need a separate
developer build of the client.

## Licence

BSD 2-Clause. See [LICENSE](LICENSE).
