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

Drops and pets, and nothing else. This is for running events, not for
tracking someone's account day to day.

Each message carries the item name and id, quantity, Grand Exchange value, what
you killed, your RSN and the time — plus a screenshot, if you leave that on.

Nothing is sent until you paste in an event URL, so the plugin is inert until
your organiser gives you one.

## Setup

1. Install **Veritas Events** from the RuneLite Plugin Hub
2. Open its settings (the cog next to the plugin name)
3. Paste the **Event URL** your organiser gave you
4. Paste the **Event key** for that event
5. Choose what to send, and whether to include screenshots

That's it. Play normally and your drops turn up on the board.

## Big drops

Set a **Big drop value** and anything worth at least that much is flagged in the
message as `"big": true`, so the event page can call it out instead of listing
it quietly with everything else. It defaults to 1,000,000 gp.

## The side panel

The sidebar panel has three tabs:

- **Event** - the event name, whether it is live, how long is left, your team,
  and a bar showing how many tiles your team has done
- **Teams** - the standings, and who has sent the most drops, with your team
  and your own name picked out
- **Activity** - how much you have sent this session, everything that went out
  with whether the board accepted it, and a **Send again** button for when the
  board was down at the time

All of it comes from the board, so an event that reports nothing still leaves a
panel that works - it just shows less.

## What the server receives

A single JSON object. With screenshots switched on it is sent as
`multipart/form-data`, with the JSON in a `payload_json` part and the image in a
`file` part as a JPEG — the same shape Dink uses, so a server written for one can accept
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

`type` is `LOOT` or `PET`. `LOOT` carries `source` and `items`; `PET`
carries the chat line that triggered it as `message`.

The event password is sent as an `X-Event-Key` header, never in the URL, so it
stays out of server logs and browser history.

## What the panel asks for

The plugin also sends a plain `GET` to the same URL, with the same header, and
fills the panel from the answer:

```json
{
  "event": "Battle Ship Bingo",
  "phase": "live",
  "endsAt": 1789200000000,
  "team": "Port",
  "done": 12,
  "total": 52,
  "standings": [
    { "team": "Port", "tiles": 12 },
    { "team": "Starboard", "tiles": 9 }
  ],
  "top": [
    { "player": "I Jinx I", "drops": 14 }
  ]
}
```

Every field is optional. A board that answers with nothing, or does not answer
at all, still leaves a working panel - those parts just stay empty.

## The event password

Events are often run with a password the organiser announces, so a screenshot
can be shown to have been taken during the event rather than dug out of an old
folder. The plugin draws it over the game along with the date and time in UTC,
in colours you pick.

Type it into **Event password** under Overlay, or let the board set it for
everyone by returning `password` in its answer, and nobody has to be told it
at all.

It starts in the top left. Hold **Alt** and drag it wherever you want it; right
click it for the usual overlay menu.

## Screenshots

Sent as a JPEG at the client's own resolution, so a drop can still be checked
close up afterwards. Only clients wider than 1920 are scaled down. A PNG of the
game is around 500 KB a frame; this is nearer 120 KB, which matters once a whole
clan is sending them for days.

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
