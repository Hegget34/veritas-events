/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events.party;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * What one party member is carrying and wearing.
 *
 * RuneLite's own party protocol carries health, prayer, run and spec and
 * nothing else, so a panel that wants to show supplies has to send them
 * itself. Any plugin may add its own messages to the party, which is what
 * this is: the party server passes it along without caring what is in it,
 * and only clients that registered the same class will read it back.
 *
 * Named with the clan in front because the name is what identifies the
 * message on the wire. A plain "Gear" would collide with any other plugin
 * that had the same idea.
 *
 * Slots are kept rather than packed down to what is occupied, so the grid
 * drawn from this is laid out the same way the player's own inventory is
 * and a glance finds things where they expect them. An empty slot is id -1.
 */
public class VeritasGear extends PartyMemberMessage
{
	/**
	 * Inventory, as id and quantity one after the other.
	 *
	 * Two parallel arrays would say the same thing; one array of pairs keeps
	 * the two halves of a slot from ever drifting apart in transit.
	 */
	private int[] carried;

	/** Worn equipment, in the same shape. */
	private int[] worn;

	public VeritasGear()
	{
	}

	public VeritasGear(int[] carried, int[] worn)
	{
		this.carried = carried;
		this.worn = worn;
	}

	/** Never null, so a half filled message cannot break a draw. */
	public int[] getCarried()
	{
		return carried == null ? new int[0] : carried;
	}

	public int[] getWorn()
	{
		return worn == null ? new int[0] : worn;
	}
}
