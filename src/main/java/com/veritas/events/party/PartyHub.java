/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events.party;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.SoundEffectID;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import com.veritas.events.VeritasEventsConfig;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.plugins.party.messages.StatusUpdate;
import net.runelite.client.plugins.party.messages.TilePing;
import net.runelite.client.util.Text;

/**
 * The party, as far as the Veritas panel is concerned.
 *
 * RuneLite's own party carries four numbers per person: health, prayer, run
 * and spec. What a raid team actually wants to see is each other's supplies,
 * so this sends inventory, worn gear, prayers and levels alongside them, as
 * messages of its own that the party server passes along without reading.
 *
 * It speaks RuneLite's protocol too, deliberately. Anyone still on the
 * built-in Party plugin or Hub Party Panel keeps seeing Veritas players
 * normally, and Veritas players keep seeing them, so the clan can move over
 * one person at a time rather than all on the same evening.
 *
 * <h2>Why everything is polled rather than watched</h2>
 *
 * Inventory, prayers and levels each have their own event, and subscribing
 * to the three of them means three paths that can each fire several times a
 * tick. Reading the lot once a tick and sending only what actually differs
 * from last time is both simpler and quieter: a tick of delay nobody can
 * see, in exchange for a hard ceiling of one message per kind per tick
 * however frantic things get.
 */
@Singleton
@Slf4j
public class PartyHub
{
	/** Inventory is 28 slots and worn gear 14, kept as id and quantity pairs. */
	private static final int CARRIED_SLOTS = 28;
	private static final int WORN_SLOTS = 14;

	/**
	 * Words a passphrase is built from.
	 *
	 * Short, plainly spelt, and nothing that sounds like anything else when
	 * said down a voice channel, which is how most of these get shared.
	 */
	private static final String[] WORDS = {
		"amber", "anchor", "arrow", "badger", "bramble", "brass", "cedar",
		"cinder", "copper", "crimson", "dagger", "ember", "falcon", "flint",
		"garnet", "granite", "harbour", "heron", "ivory", "jasper", "kestrel",
		"lantern", "marble", "meadow", "onyx", "otter", "pewter", "quarry",
		"raven", "saffron", "slate", "sparrow", "teal", "thistle", "timber",
		"velvet", "walnut", "willow",
	};

	private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

	private static final Prayer[] PRAYERS = Prayer.values();
	private static final Skill[] SKILLS = Skill.values();

	@Inject
	private Client client;

	@Inject
	private PartyService party;

	@Inject
	private WSClient ws;

	@Inject
	private EventBus eventBus;

	@Inject
	private VeritasEventsConfig config;

	/** Everyone in the party, newest last, keyed by the party's own id. */
	private final Map<Long, PartyMate> mates = new LinkedHashMap<>();

	/** The last of each kind sent, so an unchanged one is never sent again. */
	private int[] sentCarried = new int[0];
	private int[] sentWorn = new int[0];
	private int[] sentPrayers = new int[0];
	private int[] sentReal = new int[0];
	private int[] sentBoosted = new int[0];
	private int sentWorld;
	private int sentSpellbook = -1;
	private StatusUpdate sentStatus;

	/** Told when something changed, so the panel can redraw and nothing else. */
	private Runnable onChange = () ->
	{
	};

	/**
	 * Whether the built-in Party plugin is switched off.
	 *
	 * Asked rather than remembered, because somebody can switch it on at any
	 * moment and the answer has to change with them. While it is running we
	 * neither send RuneLite's own status messages nor draw anything in the
	 * game world, since it is already doing both and two of everything is
	 * worse than one.
	 */
	private BooleanSupplier standingIn = () -> true;

	/**
	 * Told where somebody pinged, in whose color, and by whom.
	 *
	 * An interface rather than a BiConsumer gone to three: at that point
	 * the type parameters stop saying what any of them are for.
	 */
	public interface PingSeen
	{
		void at(WorldPoint where, java.awt.Color color, String who);
	}

	private PingSeen onPing = (where, color, who) ->
	{
	};

	public void start(Runnable onChange, PingSeen onPing, BooleanSupplier standingIn)
	{
		this.onPing = onPing;
		this.standingIn = standingIn;
		this.onChange = onChange;
		ws.registerMessage(VeritasGear.class);
		ws.registerMessage(VeritasPrayers.class);
		ws.registerMessage(VeritasLevels.class);
		eventBus.register(this);
		rebuild();
	}

	public void stop()
	{
		eventBus.unregister(this);
		ws.unregisterMessage(VeritasGear.class);
		ws.unregisterMessage(VeritasPrayers.class);
		ws.unregisterMessage(VeritasLevels.class);
		mates.clear();
		forget();
	}

	/** Whether we are standing in for RuneLite's own Party plugin. */
	public boolean standingIn()
	{
		return standingIn.getAsBoolean();
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

	/**
	 * Whether a party is actually joined.
	 *
	 * The examples deliberately do not count. They exist so the member boxes
	 * can be looked at, and if they claimed a party was joined then Create,
	 * Join and Leave would all be pretending too, which is the half of this
	 * most worth being able to press.
	 */
	public boolean isInParty()
	{
		return party.isInParty();
	}

	public String passphrase()
	{
		String said = party.getPartyPassphrase();
		return said == null ? "" : said;
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
	 * as it is possible to be, and every press of Create threw for it.
	 *
	 * Three short words, because the thing people actually do with a
	 * passphrase is read it out loud to someone.
	 */
	public String makePassphrase()
	{
		StringBuilder out = new StringBuilder();
		for (int n = 0; n < 3; n++)
		{
			out.append(n == 0 ? "" : "-")
				.append(WORDS[RANDOM.nextInt(WORDS.length)]);
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

		party.changeParty(said);
		// remembered now rather than on leaving, because a client that is
		// closed mid raid never leaves
		config.setPartyPrevious(said);
		forget();
		onChange.run();
	}

	public void leave()
	{
		party.changeParty(null);
		mates.clear();
		forget();
		onChange.run();
	}

	/** Everyone in the party, with whatever has been heard about each. */
	public List<PartyMate> mates()
	{
		rebuild();
		return new ArrayList<>(mates.values());
	}

	/**
	 * Brings the list in line with who the party says is in it.
	 *
	 * Names live on RuneLite's own member list rather than on anything sent
	 * here, so they are read across each time rather than tracked separately
	 * and allowed to drift.
	 */
	private void rebuild()
	{
		for (PartyMember member : party.getMembers())
		{
			PartyMate mate = mates.computeIfAbsent(member.getMemberId(), PartyMate::new);
			mate.setName(Text.sanitize(String.valueOf(member.getDisplayName())));
			mate.setLoggedIn(member.isLoggedIn());
		}

		// and anyone the party no longer lists has gone
		mates.keySet().removeIf((id) -> party.getMemberById(id) == null);
	}

	private void forget()
	{
		sentCarried = new int[0];
		sentWorn = new int[0];
		sentPrayers = new int[0];
		sentReal = new int[0];
		sentBoosted = new int[0];
		sentWorld = 0;
		sentSpellbook = -1;
		sentStatus = null;
	}

	// ------------------------------------------------------- sending ------

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!party.isInParty() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return;
		}

		sendGear();
		sendPrayers();
		sendLevels(me);
		if (standingIn.getAsBoolean())
		{
			sendStatus(me);
		}
	}

	private void sendGear()
	{
		int[] carried = slots(InventoryID.INV, CARRIED_SLOTS);
		int[] worn = slots(InventoryID.WORN, WORN_SLOTS);

		if (Arrays.equals(carried, sentCarried) && Arrays.equals(worn, sentWorn))
		{
			return;
		}
		sentCarried = carried;
		sentWorn = worn;
		mine().take(new VeritasGear(carried, worn));
		party.send(new VeritasGear(carried, worn));
		onChange.run();
	}

	private void sendPrayers()
	{
		int count = 0;
		int[] lit = new int[PRAYERS.length];
		for (int n = 0; n < PRAYERS.length; n++)
		{
			try
			{
				if (client.isPrayerActive(PRAYERS[n]))
				{
					lit[count++] = n;
				}
			}
			catch (Exception unknownPrayer)
			{
				// a prayer this client cannot read is simply not reported
			}
		}
		int[] active = Arrays.copyOf(lit, count);

		if (Arrays.equals(active, sentPrayers))
		{
			return;
		}
		sentPrayers = active;
		mine().take(new VeritasPrayers(active));
		party.send(new VeritasPrayers(active));
		onChange.run();
	}

	private void sendLevels(Player me)
	{
		int[] real = new int[SKILLS.length];
		int[] boosted = new int[SKILLS.length];
		for (int n = 0; n < SKILLS.length; n++)
		{
			try
			{
				real[n] = client.getRealSkillLevel(SKILLS[n]);
				boosted[n] = client.getBoostedSkillLevel(SKILLS[n]);
			}
			catch (Exception unknownSkill)
			{
				// a skill this client does not have is left at zero
			}
		}

		int world = client.getWorld();
		int spellbook = client.getVarbitValue(VarbitID.SPELLBOOK);
		if (Arrays.equals(real, sentReal) && Arrays.equals(boosted, sentBoosted)
			&& world == sentWorld && spellbook == sentSpellbook)
		{
			return;
		}
		sentReal = real;
		sentBoosted = boosted;
		sentWorld = world;
		sentSpellbook = spellbook;

		VeritasLevels levels = new VeritasLevels(real, boosted,
			me.getCombatLevel(), world, spellbook);
		mine().take(levels);
		party.send(levels);
		onChange.run();
	}

	/**
	 * RuneLite's own four numbers, for the benefit of everyone else.
	 *
	 * Sent as a difference the way the built-in plugin sends it: only the
	 * fields that moved are filled in, and the rest are left null for the
	 * other end to remember. A full message every tick would be four times
	 * the traffic to say the same thing.
	 */
	private void sendStatus(Player me)
	{
		StatusUpdate now = new StatusUpdate();
		now.setCharacterName(Text.sanitize(String.valueOf(me.getName())));
		now.setHealthCurrent(client.getBoostedSkillLevel(Skill.HITPOINTS));
		now.setHealthMax(client.getRealSkillLevel(Skill.HITPOINTS));
		now.setPrayerCurrent(client.getBoostedSkillLevel(Skill.PRAYER));
		now.setPrayerMax(client.getRealSkillLevel(Skill.PRAYER));
		now.setRunEnergy(client.getEnergy() / 100);
		now.setSpecEnergy(client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT) / 10);
		// so everyone else draws your pings and your name in your color
		now.setMemberColor(config.partyColor());

		mine().take(now.getHealthCurrent(), now.getHealthMax(), now.getPrayerCurrent(),
			now.getPrayerMax(), now.getRunEnergy(), now.getSpecEnergy());

		StatusUpdate difference = only(now, sentStatus);
		sentStatus = now;
		if (difference != null)
		{
			party.send(difference);
			onChange.run();
		}
	}

	/** What changed since last time, or nothing if none of it did. */
	@Nullable
	private static StatusUpdate only(StatusUpdate now, @Nullable StatusUpdate before)
	{
		if (before == null)
		{
			return now;
		}

		StatusUpdate out = new StatusUpdate();
		boolean any = false;
		if (!now.getCharacterName().equals(before.getCharacterName()))
		{
			out.setCharacterName(now.getCharacterName());
			any = true;
		}
		if (!now.getHealthCurrent().equals(before.getHealthCurrent()))
		{
			out.setHealthCurrent(now.getHealthCurrent());
			any = true;
		}
		if (!now.getHealthMax().equals(before.getHealthMax()))
		{
			out.setHealthMax(now.getHealthMax());
			any = true;
		}
		if (!now.getPrayerCurrent().equals(before.getPrayerCurrent()))
		{
			out.setPrayerCurrent(now.getPrayerCurrent());
			any = true;
		}
		if (!now.getPrayerMax().equals(before.getPrayerMax()))
		{
			out.setPrayerMax(now.getPrayerMax());
			any = true;
		}
		if (!now.getRunEnergy().equals(before.getRunEnergy()))
		{
			out.setRunEnergy(now.getRunEnergy());
			any = true;
		}
		if (!now.getSpecEnergy().equals(before.getSpecEnergy()))
		{
			out.setSpecEnergy(now.getSpecEnergy());
			any = true;
		}
		return any ? out : null;
	}

	/**
	 * One container as id and quantity pairs, empty slots and all.
	 *
	 * The empty ones are kept so the grid drawn from this lines up with the
	 * player's own inventory and a glance finds things where it expects them.
	 */
	private int[] slots(int which, int howMany)
	{
		int[] out = new int[howMany * 2];
		Arrays.fill(out, -1);

		ItemContainer container = client.getItemContainer(which);
		if (container == null)
		{
			return out;
		}

		Item[] items = container.getItems();
		for (int n = 0; n < howMany && n < items.length; n++)
		{
			Item item = items[n];
			out[n * 2] = item == null ? -1 : item.getId();
			out[n * 2 + 1] = item == null ? 0 : item.getQuantity();
		}
		return out;
	}

	/** My own row, so the panel shows me alongside everyone else. */
	private PartyMate mine()
	{
		PartyMember me = party.getLocalMember();
		long id = me == null ? 0L : me.getMemberId();
		return mates.computeIfAbsent(id, PartyMate::new);
	}

	// ----------------------------------------------------- receiving ------

	@Subscribe
	public void onVeritasGear(VeritasGear gear)
	{
		mates.computeIfAbsent(gear.getMemberId(), PartyMate::new).take(gear);
		onChange.run();
	}

	@Subscribe
	public void onVeritasPrayers(VeritasPrayers prayers)
	{
		mates.computeIfAbsent(prayers.getMemberId(), PartyMate::new).take(prayers);
		onChange.run();
	}

	@Subscribe
	public void onVeritasLevels(VeritasLevels levels)
	{
		mates.computeIfAbsent(levels.getMemberId(), PartyMate::new).take(levels);
		onChange.run();
	}

	/**
	 * RuneLite's own status, whoever sent it.
	 *
	 * Taken whether or not we are sending our own, so somebody running the
	 * built-in Party plugin still shows their four bars here.
	 */
	@Subscribe
	public void onStatusUpdate(StatusUpdate status)
	{
		PartyMate mate = mates.computeIfAbsent(status.getMemberId(), PartyMate::new);
		mate.take(status.getHealthCurrent(), status.getHealthMax(),
			status.getPrayerCurrent(), status.getPrayerMax(),
			status.getRunEnergy(), status.getSpecEnergy());
		if (status.getMemberColor() != null)
		{
			mate.setColor(status.getMemberColor());
		}
		onChange.run();
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

		// drawn in the sender's color, so a party of five can tell whose
		// ping is whose without anybody saying so
		PartyMate from = mates.get(ping.getMemberId());
		onPing.at(ping.getPoint(), from == null ? null : from.getColor(),
			from == null ? "" : from.getName());

		if (config.partyPingSound())
		{
			client.playSoundEffect(SoundEffectID.UI_BOOP);
		}
	}

	@Subscribe
	public void onUserJoin(UserJoin join)
	{
		rebuild();
		// everything we know about ourselves goes out again, because whoever
		// just arrived has heard none of it
		forget();
		onChange.run();
	}

	@Subscribe
	public void onUserPart(UserPart part)
	{
		mates.remove(part.getMemberId());
		onChange.run();
	}
}
