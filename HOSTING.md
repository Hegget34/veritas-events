# Hosting an event

Anyone in the clan can run an event with this plugin. Nothing about it is tied
to one event or one organiser: players paste in your address and their drops
come to you.

You need a web address that does two things. That is the whole requirement.

## 1. Answer a GET with your event

The plugin asks your address for the event every few minutes. Answer with JSON:

```json
{
  "event": "Summer Bingo",
  "phase": "live",
  "endsAt": 1790132385066,
  "wanted": [
    { "name": "Voidwaker blade", "id": 27684, "note": "Legendary" },
    { "name": "Dragon pickaxe", "id": 11920, "note": "Rare" }
  ]
}
```

`wanted` is the important part. It is the list of items your event is after,
and it does two jobs:

- it appears in each player's Loot Tracker, under **Items to go for**
- **it is the only loot that gets sent to you.** While you publish a list,
  players' other drops stay on their own machines. Publish nothing and you
  receive everything, which is rarely what you want.

`endsAt` is unix milliseconds. `id` is the item id, used for the icon, and is
optional. Every field is optional - answer `{}` and the plugin still works, it
just shows less.

An item can also carry `"pending": true` while you are deciding, or
`"found": true` once it counts.

## 2. Accept a POST when someone gets one

When a player receives something on your list, the plugin posts it:

```json
{
  "type": "LOOT",
  "player": "I Jinx I",
  "sentAt": 1789865848178,
  "source": "Vardorvis",
  "totalValue": 1250000,
  "big": true,
  "items": [
    { "id": 27684, "name": "Voidwaker blade", "quantity": 1, "priceEach": 1250000 }
  ]
}
```

With screenshots switched on it arrives as `multipart/form-data`: the JSON in a
`payload_json` part and a JPEG in a `file` part. Otherwise it is plain JSON.

Answer any 2xx and the player sees it accepted. Answer anything else and they
see it refused, and can press **Send again**.

`type` is `LOOT` or `PET`. Pets carry the chat line as `message` rather than
items, since a pet is not a dropped item.

## 3. Hand out the address

Players put two things in the plugin's **Event** settings:

- **Event URL** - your address
- **Event key** - anything you like

The key comes back to you on every post as an `X-Event-Key` header, so you can
tell your players' drops from anyone else's. Check it and reject the rest.

## Things worth knowing

**Screenshots** arrive as JPEGs of about 250 KB. They carry the event password
and the time in UTC drawn over the game, so a drop can be shown to have
happened during your event.

**Set a password** by returning `"password": "YOURWORD"` in your GET. Every
player's overlay shows it, so nobody has to be told it. Anyone who typed their
own keeps theirs.

**Extra pages.** Anything in `pages` becomes its own entry in the plugin's view
chooser, built from `heading`, `text`, `stat`, `table` and `link` blocks. Rules,
standings, sign-up links - whatever your event needs. See README.md for the
shapes.

**Testing.** `veritas-test-server.py`, kept with the clan tools, is a small
stand-in board that serves a JSON file and prints drops as they arrive. Point
the plugin at it before you point it at anything real.

## The smallest thing that works

A single endpoint that returns your item list on GET and returns 200 on POST is
a working event. Everything else is optional.
