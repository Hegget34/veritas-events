/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events.party;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Which prayers one party member has on.
 *
 * Only the ones that are lit, not the whole book. In a sidebar a couple of
 * hundred pixels wide the question being asked is "is he on Rigour", and
 * thirty dimmed icons is a worse answer to that than three bright ones.
 *
 * Sent as the ordinals of net.runelite.api.Prayer rather than its names, so
 * the message stays small enough to send on a prayer flick.
 */
public class VeritasPrayers extends PartyMemberMessage
{
	private int[] active;

	public VeritasPrayers()
	{
	}

	public VeritasPrayers(int[] active)
	{
		this.active = active;
	}

	public int[] getActive()
	{
		return active == null ? new int[0] : active;
	}
}
