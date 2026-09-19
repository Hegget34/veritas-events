# Veritas Events

A RuneLite plugin that sends your drops to a Veritas clan event board, so you
don't have to screenshot and type them in by hand.

Built for the Veritas OSRS clan, but the event URL is just a setting, so any
clan can point it at their own board.

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
