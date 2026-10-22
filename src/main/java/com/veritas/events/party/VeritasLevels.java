/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events.party;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * One party member's levels, as they are and as they have been boosted.
 *
 * Both, because the gap between them is the useful part: a glance should
 * say whether someone's overload has run out without asking them.
 *
 * Indexed by the ordinal of net.runelite.api.Skill, so a client reading a
 * message from a newer game with an extra skill in it simply has a longer
 * array than it knows what to do with, rather than a shifted one.
 */
public class VeritasLevels extends PartyMemberMessage
{
	/** The level actually trained to. */
	private int[] real;

	/** The level right now, drained or boosted. */
	private int[] boosted;

	/** Combat level, which is worked out rather than being a skill of its own. */
	private int combat;

	/** The world they are on, for the clan members who hop mid raid. */
	private int world;

	/**
	 * Which spellbook they are on, as the game's own varbit numbers it.
	 *
	 * Carried here rather than in a message of its own because it changes
	 * about as often as a level does, which is to say hardly ever, and a
	 * second message to say so would be a second message mostly saying
	 * nothing.
	 */
	private int spellbook;

	public VeritasLevels()
	{
	}

	public VeritasLevels(int[] real, int[] boosted, int combat, int world, int spellbook)
	{
		this.real = real;
		this.boosted = boosted;
		this.combat = combat;
		this.world = world;
		this.spellbook = spellbook;
	}

	public int[] getReal()
	{
		return real == null ? new int[0] : real;
	}

	public int[] getBoosted()
	{
		return boosted == null ? new int[0] : boosted;
	}

	public int getCombat()
	{
		return combat;
	}

	public int getWorld()
	{
		return world;
	}

	public int getSpellbook()
	{
		return spellbook;
	}
}
