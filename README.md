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

## The side panel

The plugin adds a panel to the RuneLite sidebar with two tabs:

- **Home** — the event you are connected to and its phase, your team, how many
  drops you have submitted, how many were approved, and how many hit
- **Activity** — everything this client has sent, with a tick or cross for
  whether the server took it

The panel fills in when your event server answers a status request (below). If
it does not, the panel simply shows that you are set up and sending, and
everything else keeps working.

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
  "items": [
    { "id": 28279, "name": "Blood quartz", "quantity": 1, "priceEach": 1250000 }
  ]
}
```

`type` is `LOOT`, `PET` or `COLLECTION_LOG`. A `PET` message carries only the
common fields; a `COLLECTION_LOG` message adds `item`.

The event password is sent as an `X-Event-Key` header, never in the URL, so it
stays out of server logs and browser history.

## Optional: the status endpoint

Every couple of minutes the plugin sends a `GET` to the same URL with the
player's RSN appended, and fills the panel from whatever comes back:

```
GET https://your-board/api/drop?player=sponge
X-Event-Key: ...
```

```json
{
  "event":  { "name": "Veritas Battle Ship Bingo", "phase": "Live",
              "url": "https://veritas-bs-bingo.pages.dev" },
  "player": { "known": true, "team": "Team 2",
              "submissions": 12, "approved": 11, "hits": 4 }
}
```

Every field is optional, and a server that does not implement `GET` at all is
fine — the plugin treats any failure as "no status available" and carries on
sending drops.

## Privacy

- Nothing leaves your client until you enter an event URL
- Only what is listed above is sent, and only to the address you paste in
- Screenshots can be turned off while everything else keeps working
- Your account is identified by RSN only

## Building it yourself

```
./gradlew build
```

To run it in RuneLite while developing, use the client's developer mode with
this project on the classpath.

## Licence

BSD 2-Clause. See [LICENSE](LICENSE).
