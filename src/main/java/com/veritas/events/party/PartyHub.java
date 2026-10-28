/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events.party;

import com.veritas.events.VeritasEventsConfig;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.SoundEffectID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.party.PartyDataService;
import net.runelite.client.party.PartyDataType;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.PartyData;
import net.runelite.client.plugins.party.messages.MemberColorUpdate;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.party.messages.TilePing;
import net.runelite.client.util.Text;

/**
 * The party, as far as the Veritas panel is concerned.
 *
 * This used to send inventory, worn gear, prayers and levels itself, over
 * messages of its own, because RuneLite's party carried four numbers and
 * nothing else. As of 1.13.4 the party carries all of it, so none of that
 * is here any more: PartyMember already knows what everyone is holding and
 * sending it again would only make the party server carry it twice.
 *
 * What is left is small. Two things core does not carry, which are the
 * world and the spellbook. The colour you picked, so everyone draws your
 * pings and your name in it. And tile pings, which are RuneLite's own
 * message so they land for everyone in the party whatever they run.
 *
 * It still stands aside for the built-in Party plugin. With that running we
 * draw nothing in the game world, because it is already drawing the same
 * things and two of everything is worse than one.
 */
@Singleton
@Slf4j
public class PartyHub
{
	@Inject
	private Client client;

	@Inject
	private PartyService party;

	@Inject
	private WSClient ws;

	@Inject
	private PartyDataService partyData;

	@Inject
	private EventBus eventBus;

	@Inject
	private VeritasEventsConfig config;

	/** The world and spellbook of everyone who has told us, by member id. */
	private final Map<Long, VeritasExtras> extras = new HashMap<>();

	/** The colour each member picked for themselves. */
	private final Map<Long, Color> colors = new HashMap<>();

	/**
	 * The phrase as people say it, for the party we are actually in.
	 *
	 * Not the same string as the party we joined: see {@link #roomOf}. This
	 * is the one that gets shown and copied, because it is the one somebody
	 * can read out.
	 */
	private String spoken = "";

	/** Subscribed as, so the same plugin can be unsubscribed on the way out. */
	private Plugin asPlugin;

	/** The last of each thing sent, so an unchanged one is never resent. */
	private int sentWorld;
	private int sentSpellbook = -1;
	private Color sentColor;

	/** Told when something changed, so the panel can redraw and nothing else. */
	private Runnable onChange = () ->
	{
	};

	/**
	 * Told where somebody pinged, in whose color, and by whom.
	 *
	 * An interface rather than a BiConsumer gone to three: at that point the
	 * type parameters stop saying what any of them are for.
	 */
	public interface PingSeen
	{
		void at(WorldPoint where, Color color, String who);
	}

	private PingSeen onPing = (where, color, who) ->
	{
	};

	/**
	 * Whether the built-in Party plugin is switched off.
	 *
	 * Asked rather than remembered, because somebody can switch it on at any
	 * moment and the answer has to change with them.
	 */
	private BooleanSupplier standingIn = () -> true;

	public void start(Plugin plugin, Runnable onChange, PingSeen onPing,
		BooleanSupplier standingIn)
	{
		this.asPlugin = plugin;
		this.onChange = onChange;
		this.onPing = onPing;
		this.standingIn = standingIn;
		this.spoken = lastParty();
		ws.registerMessage(VeritasExtras.class);

		/*
		 * Ask for the things the panel draws.
		 *
		 * Nothing is sent for a kind of data until some plugin has asked for
		 * it, so without this the cards sit empty whenever the built-in Party
		 * plugin is switched off, which is the case this panel exists for.
		 * The four it does not ask for are coordinates, which the overlay
		 * does not need because it finds people by name, and vengeance, rune
		 * pouch and quiver, which it does not draw.
		 */
		partyData.subscribe(plugin, PartyDataType.CHARACTER_NAME, PartyDataType.INV,
			PartyDataType.WORN, PartyDataType.ACTIVE_PRAYERS, PartyDataType.ENERGY,
			PartyDataType.SPEC);
		partyData.subscribe(plugin, Skill.values());

		/*
		 * TilePing and MemberColorUpdate are registered by the built-in Party
		 * plugin, and we tell people to switch that off. Registered here as
		 * well so they still arrive when it is not running, which is exactly
		 * the case this panel exists for.
		 */
		ws.registerMessage(TilePing.class);
		ws.registerMessage(MemberColorUpdate.class);

		eventBus.register(this);
	}

	public void stop()
	{
		eventBus.unregister(this);

		if (asPlugin != null)
		{
			partyData.unsubscribe(asPlugin, PartyDataType.values());
			partyData.unsubscribe(asPlugin, Skill.values());
			asPlugin = null;
		}

		// only ours. TilePing and MemberColorUpdate may belong to the
		// built-in Party plugin as well, and pulling them out from under it
		// would break it; leaving them registered costs nothing.
		ws.unregisterMessage(VeritasExtras.class);
		extras.clear();
		colors.clear();
		spoken = "";
		forget();
	}

	/** Whether we are standing in for RuneLite's own Party plugin. */
	public boolean standingIn()
	{
		return standingIn.getAsBoolean();
	}

	public boolean isInParty()
	{
		return party.isInParty();
	}

	/**
	 * The passphrase to show, which is the one somebody could say out loud.
	 *
	 * The party we are actually in is named after it rather than by it, so
	 * the raw name is forty characters of hex and no use to anybody. If it
	 * is not the one our phrase maps to then this party was joined somewhere
	 * other than here, and the raw name is the only true answer we have.
	 */
	public String passphrase()
	{
		String room = party.getPartyPassphrase();
		if (room == null || room.isEmpty())
		{
			return "";
		}
		return room.equals(roomOf(spoken)) ? spoken : room;
	}

	/**
	 * The party a spoken phrase names.
	 *
	 * A Veritas party is a room nobody can type their way into. The phrase
	 * people share is run through SHA-256 with a word of our own in front of
	 * it, and the result is what gets joined, so somebody entering the same
	 * phrase into RuneLite's own party panel lands in a different room and
	 * sees nobody. It takes the plugin to work out where everyone went.
	 *
	 * This is a gate on running Veritas, not on being in the clan: anybody
	 * holding the phrase and the plugin still gets in, which is what a
	 * passphrase is for.
	 */
	static String roomOf(String spoken)
	{
		String said = spoken.trim().toLowerCase(Locale.ROOT);
		if (said.isEmpty())
		{
			return "";
		}

		byte[] sum;
		try
		{
			sum = MessageDigest.getInstance("SHA-256")
				.digest((ROOM_SALT + said).getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException never)
		{
			// every Java runtime is required to carry SHA-256
			throw new IllegalStateException(never);
		}

		StringBuilder room = new StringBuilder("veritas-");
		for (int n = 0; n < ROOM_BYTES; n++)
		{
			room.append(Character.forDigit((sum[n] >> 4) & 0xf, 16))
				.append(Character.forDigit(sum[n] & 0xf, 16));
		}
		return room.toString();
	}

	/** The last passphrase joined, for getting back into it. */
	public String lastParty()
	{
		String said = config.partyPrevious();
		return said == null ? "" : said.trim();
	}

	/**
	 * A phrase to start a party on.
	 *
	 * Made here rather than borrowed from PartyService, whose own generator
	 * reads the game's word list and asserts it is on the client thread.
	 * This is called from a button, which is as far from the client thread
	 * as it is possible to be.
	 *
	 * Three short words, because the thing people actually do with a
	 * passphrase is read it out loud to someone.
	 */
	public String makePassphrase()
	{
		StringBuilder out = new StringBuilder();
		for (int n = 0; n < 3; n++)
		{
			out.append(n == 0 ? "" : "-").append(WORDS[RANDOM.nextInt(WORDS.length)]);
		}
		return out.toString();
	}

	public void join(String passphrase)
	{
		String said = passphrase.trim();
		if (said.isEmpty())
		{
			return;
		}

		spoken = said;
		party.changeParty(roomOf(said));
		// remembered now rather than on leaving, because a client that is
		// closed mid raid never leaves. The phrase, not the room: the room
		// can always be worked out again and nobody can read it out.
		config.setPartyPrevious(said);
		forget();
		onChange.run();
	}

	public void leave()
	{
		party.changeParty(null);
		extras.clear();
		colors.clear();
		spoken = "";
		forget();
		onChange.run();
	}

	/**
	 * Everyone in the party, with what we know on top of what core knows.
	 *
	 * Built fresh each time rather than kept, because PartyMember is the
	 * thing that actually changes and holding copies of it would only be
	 * somewhere for them to go stale.
	 */
	public List<PartyMate> mates()
	{
		List<PartyMate> all = new ArrayList<>();
		for (PartyMember member : party.getMembers())
		{
			all.add(new PartyMate(member, extras.get(member.getMemberId()),
				colors.get(member.getMemberId())));
		}
		return all;
	}

	/** Marks a tile for everyone in the party. */
	public void ping(WorldPoint where)
	{
		if (where == null || !party.isInParty())
		{
			return;
		}

		// RuneLite's own message, so the ping lands for everyone in the party
		// whatever they happen to be running
		party.send(new TilePing(where));

		// shown here at once rather than waiting for it to come back round
		PartyMember me = party.getLocalMember();
		onPing.at(where, config.partyColor(),
			me == null ? "" : Text.sanitize(String.valueOf(me.getDisplayName())));
	}

	private void forget()
	{
		sentWorld = 0;
		sentSpellbook = -1;
		sentColor = null;
	}

	// ------------------------------------------------------- sending ------

	/**
	 * The world, the spellbook and your colour, when any of them moves.
	 *
	 * Read once a tick and sent only on a difference. Everything else the
	 * panel shows is the client's own job now.
	 */
	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!party.isInParty() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		int world = client.getWorld();
		int spellbook = client.getVarbitValue(VarbitID.SPELLBOOK);
		if (world != sentWorld || spellbook != sentSpellbook)
		{
			sentWorld = world;
			sentSpellbook = spellbook;
			party.send(new VeritasExtras(world, spellbook));

			PartyMember me = party.getLocalMember();
			if (me != null)
			{
				extras.put(me.getMemberId(), new VeritasExtras(world, spellbook));
			}
			onChange.run();
		}

		Color mine = config.partyColor();
		if (!mine.equals(sentColor))
		{
			sentColor = mine;
			party.send(new MemberColorUpdate(mine));

			PartyMember me = party.getLocalMember();
			if (me != null)
			{
				colors.put(me.getMemberId(), mine);
			}
		}
	}

	// ----------------------------------------------------- receiving ------

	@Subscribe
	public void onVeritasExtras(VeritasExtras said)
	{
		extras.put(said.getMemberId(), said);
		onChange.run();
	}

	/**
	 * Somebody's supplies, prayers, levels or energy moving.
	 *
	 * The party applies this to the member itself; all we do is redraw,
	 * because the panel reads the member rather than keeping its own copy.
	 * Without this the cards hold whatever they were built with and a health
	 * bar stays where it was while the person it belongs to is being hit.
	 *
	 * Redrawing settles for a moment first, so this arriving before the
	 * party has finished applying it still draws the new figures, and five
	 * people moving at once is still one rebuild.
	 */
	@Subscribe
	public void onPartyData(PartyData said)
	{
		onChange.run();
	}

	@Subscribe
	public void onMemberColorUpdate(MemberColorUpdate said)
	{
		if (said.getColor() != null)
		{
			colors.put(said.getMemberId(), said.getColor());
			onChange.run();
		}
	}

	/**
	 * Somebody's ping, including the echo of our own.
	 *
	 * Ignored while the built-in Party plugin is running, because it is
	 * drawing the same ping and two sets of rings on one tile is noise.
	 */
	@Subscribe
	public void onTilePing(TilePing ping)
	{
		if (!standingIn.getAsBoolean())
		{
			return;
		}

		PartyMember from = party.getMemberById(ping.getMemberId());
		onPing.at(ping.getPoint(), colors.get(ping.getMemberId()),
			from == null ? "" : Text.sanitize(String.valueOf(from.getDisplayName())));

		if (config.partyPingSound())
		{
			client.playSoundEffect(SoundEffectID.UI_BOOP);
		}
	}

	@Subscribe
	public void onUserPart(UserPart part)
	{
		extras.remove(part.getMemberId());
		colors.remove(part.getMemberId());
		onChange.run();
	}

	/**
	 * Words a passphrase is built from.
	 *
	 * Short, plainly spelt, and nothing that sounds like anything else when
	 * said down a voice channel, which is how most of these get shared.
	 *
	 * There are enough of them that three picked at random are very unlikely
	 * to be three somebody else is already using. That matters more here
	 * than it looks: two groups landing on one phrase land in one party, and
	 * a party that quietly fills up with people nobody invited is the thing
	 * this panel was built to stop happening.
	 */
	private static final String[] WORDS = {
		"amber", "anchor", "arrow", "badger", "bramble", "brass", "cedar",
		"cinder", "copper", "crimson", "dagger", "ember", "falcon", "flint",
		"garnet", "granite", "harbour", "heron", "ivory", "jasper", "kestrel",
		"lantern", "marble", "meadow", "onyx", "otter", "pewter", "quarry",
		"raven", "saffron", "slate", "sparrow", "teal", "thistle", "timber",
		"velvet", "walnut", "willow", "acorn", "almond", "anvil", "aspen",
		"basalt", "beacon", "birch", "bison", "bonfire", "boulder", "bracken",
		"bronze", "bucket", "burrow", "canvas", "cavern", "chestnut", "clover",
		"cobalt", "compass", "coral", "cotton", "cypress", "daisy", "driftwood",
		"fennel", "fern", "forge", "fossil", "gable", "ginger", "glacier",
		"gorse", "gravel", "hazel", "hollow", "hornet", "indigo", "juniper",
		"kettle", "lagoon", "lilac", "linen", "magnet", "mallard", "maple",
		"marigold", "marsh", "moss", "nettle", "nutmeg", "oats", "orchard",
		"osprey", "parsnip", "peat", "pebble", "pelican", "pepper", "pigeon",
		"pine", "plum", "quartz", "quince", "rafter", "reed", "ridge", "rust",
		"sage", "sawdust", "scarlet", "shale", "sorrel", "spruce", "starling",
		"swallow", "tallow", "thatch", "thorn", "thrush", "tulip", "turnip",
		"vellum", "violet", "walrus", "warren", "weasel", "wheat", "wicker",
		"wren", "yarrow", "yew",
	};

	private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

	/**
	 * Mixed into a phrase before it is hashed, so the room a phrase names
	 * here is not the room the same phrase would name anywhere else.
	 */
	private static final String ROOM_SALT = "veritas-party-v1:";

	/** Half of the hash is already far more than anybody could guess. */
	private static final int ROOM_BYTES = 16;
}
