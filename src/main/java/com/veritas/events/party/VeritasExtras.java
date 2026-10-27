/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events.party;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * The two things RuneLite's party does not carry.
 *
 * There used to be three messages here, for inventory, prayers and levels.
 * RuneLite 1.13.4 took all of that into the party itself, so they went: a
 * plugin sending what the client already sends is only making the party
 * server carry it twice.
 *
 * What is left is the world somebody is on, which matters when half the
 * team has hopped, and which spellbook they have open, which decides
 * whether there is a vengeance coming or a barrage. Two integers, sent
 * only when one of them changes, which is seldom.
 *
 * Named with the clan in front because the name identifies the message on
 * the wire, and a plain "Extras" would collide with anyone else's.
 */
public class VeritasExtras extends PartyMemberMessage
{
	private int world;

	/** As the game's own varbit numbers them; -1 before anything is heard. */
	private int spellbook;

	public VeritasExtras()
	{
	}

	public VeritasExtras(int world, int spellbook)
	{
		this.world = world;
		this.spellbook = spellbook;
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
