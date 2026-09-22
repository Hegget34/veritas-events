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

Answer any 2xx and the player sees it accepted: a teal line in their chat
box saying it counted, and the drop marked in their Loot Tracker. Answer
anything else and they get a red line instead, the box is marked refused, and
**Send again** lights up so they can retry once you are back.

This matters more than it sounds. Players used to have no idea whether a drop
had reached you until they checked a website later, so a host who went down
for an hour lost entries nobody knew were missing.

`type` is `LOOT` or `PET`. Pets carry the chat line as `message` rather than
items, since a pet is not a dropped item.

## 3. Tell the clan

Two parts, and both are worth doing.

**Hand out the address.** Members paste it into the plugin under
**Veritas Events -> Event URL**, and your key, if you set one, into
**Event key**. That is all the setup there is, and it is the only way drops
reach you: the plugin never sends anywhere a player has not typed in itself.
Post the address in Discord where people will see it, and say when to clear
the field again.

**Put the event on the clan board.** Ask a staff member to add it at
<https://board.veritasclan.cc/admin>, on the **Events** tab, one line:

```
Veritas Roulette | 10/09/2026 @ 6 PM EST | 10/11/2026 @ 11 PM EST | YourName | https://your-event-address | key: whatever-you-chose
```

Only the name is required, and dates can be `2026-10-09` or `10/09/2026`.
Labels like `Starts:` and `Hosted by:` are ignored, so write it how you like.

That does not switch anything on by itself. What it does is put the event on
the clan site's calendar and in everyone's plugin under **Schedule**, with the
address written out, so people know it is coming and know what to paste.

### About the key

`key: ...` is optional and it is your choice.

**Publish it** on the board and members can copy the address and the key from
the same place. Bear in mind the clan board is public, so a published key is
not a secret. It is a speed bump in front of a board that should be queueing
submissions for approval anyway.

**Leave it out** and hand it round privately in Discord instead.

Either way the key comes back on every post as an `X-Event-Key` header, so you
can tell your players' drops from anyone else's. Check it and reject the rest.

### Why members have to type it

The plugin is on RuneLite's Plugin Hub, and the Hub requires that every address
a plugin sends to is either written into the source or typed in by the player.
An address the plugin picks up from a server at runtime is not allowed, because
nobody reviewing the code can see where the data ends up. So the board can show
your event, but it cannot arm anyone's plugin.

### What members see without being told

Once an event of yours is on the clan board, every member's plugin notices it
by name and says so: a notification when it starts and another when it ends,
and it appears on their **This week** page. That is about telling people an
event exists; it does not switch anything on, because the address still has
to be typed in by hand.

### Ending it

Ask members to clear **Event URL** when you are done, and stop answering at
your end. Anything that arrives late gets a non 2xx and the player sees it
refused.

## Things worth knowing

**Screenshots** are taken of the very next frame the client draws after the
drop, not of whatever the player happens to be looking at when they get round
to sending one. They arrive as JPEGs of about 250 KB, with the event password
and the time in UTC drawn over the game, so a drop can be shown to have
happened during your event and not been dug out of an old folder.

Nothing is asked of the player for this. They do not press anything and they
cannot forget. The only way it does not happen is if they turn **Include a
screenshot** off in the settings.

**Set a password** by returning `"password": "YOURWORD"` in your GET. Every
player's overlay shows it, so nobody has to be told it. Anyone who typed their
own keeps theirs.

**Extra pages.** Anything in `pages` becomes its own entry in the plugin's view
chooser, built from `heading`, `text`, `stat`, `table` and `link` blocks. Rules,
standings, sign-up links - whatever your event needs. See README.md for the
shapes.

**Testing.** `veritas-test-server.py`, kept with the clan tools, is a small
stand-in board that serves a JSON file and prints drops as they arrive. Point
the plugin at it before you point it at anything real, by typing its address
into **Event URL** rather than putting it on the clan board.

**Check it went live.** <https://board.veritasclan.cc/> shows `liveEvent`. It
is your address while your event is running and empty the rest of the time,
which is exactly what every member's plugin is reading.

## The smallest thing that works

A single endpoint that returns your item list on GET and returns 200 on POST is
a working event. Everything else is optional.
