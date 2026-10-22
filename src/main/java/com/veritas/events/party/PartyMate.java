/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events.party;

import javax.annotation.Nullable;

/**
 * Everything known about one person in the party.
 *
 * Filled in from several messages that arrive separately and at their own
 * pace: the bars come in on a tick, gear when a slot changes, prayers on a
 * flick. So every part of this is optional and the panel draws whatever has
 * turned up rather than waiting for a complete picture, which is why
 * somebody who has only just joined appears at once with their name and
 * fills in over the next second or two.
 */
public class PartyMate
{
	private final long memberId;
	private String name = "";
	private boolean loggedIn;
	private int combat;
	private int world;
	private int spellbook = -1;

	/**
	 * The color this person chose for themselves.
	 *
	 * Theirs, not ours. It arrives on their status message and is drawn on
	 * our screen, which is the only way a party of five can tell whose ping
	 * is whose.
	 */
	@Nullable
	private java.awt.Color color;

	private int health = -1;
	private int healthMax = -1;
	private int prayer = -1;
	private int prayerMax = -1;
	private int run = -1;
	private int spec = -1;

	private int[] carried = new int[0];
	private int[] worn = new int[0];
	private int[] prayers = new int[0];
	private int[] real = new int[0];
	private int[] boosted = new int[0];

	public PartyMate(long memberId)
	{
		this.memberId = memberId;
	}

	public long getMemberId()
	{
		return memberId;
	}

	public String getName()
	{
		return name == null ? "" : name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public boolean isLoggedIn()
	{
		return loggedIn;
	}

	public void setLoggedIn(boolean loggedIn)
	{
		this.loggedIn = loggedIn;
	}

	public int getCombat()
	{
		return combat;
	}

	public int getWorld()
	{
		return world;
	}

	/** As the game numbers them, or -1 before anything has been heard. */
	public int getSpellbook()
	{
		return spellbook;
	}

	public int getHealth()
	{
		return health;
	}

	public int getHealthMax()
	{
		return healthMax;
	}

	public int getPrayer()
	{
		return prayer;
	}

	public int getPrayerMax()
	{
		return prayerMax;
	}

	public int getRun()
	{
		return run;
	}

	public int getSpec()
	{
		return spec;
	}

	public int[] getCarried()
	{
		return carried;
	}

	public int[] getWorn()
	{
		return worn;
	}

	public int[] getPrayers()
	{
		return prayers;
	}

	public int[] getReal()
	{
		return real;
	}

	public int[] getBoosted()
	{
		return boosted;
	}

	/**
	 * Takes the four bars from RuneLite's own status message.
	 *
	 * Every field on it is a boxed Integer and any of them may be null,
	 * because it is sent as a difference: only what changed is filled in and
	 * the rest is left for the receiver to remember. Reading it as plain ints
	 * would throw on the first message that only carried run energy.
	 */
	@Nullable
	public java.awt.Color getColor()
	{
		return color;
	}

	public void setColor(@Nullable java.awt.Color color)
	{
		this.color = color;
	}

	public void take(Integer healthNow, Integer healthOf, Integer prayerNow,
		Integer prayerOf, Integer runNow, Integer specNow)
	{
		if (healthNow != null)
		{
			health = healthNow;
		}
		if (healthOf != null)
		{
			healthMax = healthOf;
		}
		if (prayerNow != null)
		{
			prayer = prayerNow;
		}
		if (prayerOf != null)
		{
			prayerMax = prayerOf;
		}
		if (runNow != null)
		{
			run = runNow;
		}
		if (specNow != null)
		{
			spec = specNow;
		}
	}

	public void take(VeritasGear gear)
	{
		carried = gear.getCarried();
		worn = gear.getWorn();
	}

	public void take(VeritasPrayers lit)
	{
		prayers = lit.getActive();
	}

	public void take(VeritasLevels levels)
	{
		real = levels.getReal();
		boosted = levels.getBoosted();
		combat = levels.getCombat();
		world = levels.getWorld();
		spellbook = levels.getSpellbook();
	}

	/**
	 * Whether anything at all has been heard about this person.
	 *
	 * A card with nothing in it is not a person who has nothing; it is a
	 * person we have not heard from, which is a distinction nobody reading
	 * the panel mid raid should have to make. Somebody joining fills this
	 * within a tick, and somebody running a different plugin may never fill
	 * it, so the card simply waits until there is something to show.
	 *
	 * The bars count, because a clanmate on RuneLite's own Party plugin
	 * sends those and nothing else, and their health is worth a card.
	 */
	public boolean heardFrom()
	{
		return carried.length > 0 || worn.length > 0 || boosted.length > 0
			|| health >= 0 || prayer >= 0;
	}

	/**
	 * Everything on screen about this person, run together.
	 *
	 * The panel keeps the box it built last time and only builds a new one
	 * when this changes, because an item's picture is made per quantity and
	 * rebuilding on every tick meant the whole party flickering. Anything
	 * drawn has to be in here or a change to it would go unseen.
	 */
	public String stamp(int tab)
	{
		StringBuilder out = new StringBuilder(getName())
			.append('|').append(tab)
			.append('|').append(loggedIn)
			.append('|').append(combat).append('|').append(world)
			.append('|').append(spellbook)
			.append('|').append(health).append('/').append(healthMax)
			.append('|').append(prayer).append('/').append(prayerMax)
			.append('|').append(run).append('|').append(spec);

		/*
		 * Only what the open tab shows.
		 *
		 * Flicking a prayer must not rebuild a box showing an inventory, and
		 * a folded card, which is -1 here, shows no grid at all and so is
		 * not changed by anything in one.
		 */
		int[] shown = tab == 1 ? worn : tab == 2 ? prayers : tab == 3 ? boosted
			: tab == 0 ? carried : new int[0];
		for (int value : shown)
		{
			out.append('|').append(value);
		}
		return out.toString();
	}
}
