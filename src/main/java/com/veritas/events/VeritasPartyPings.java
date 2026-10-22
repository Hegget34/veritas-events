/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events;

import com.veritas.events.party.PartyHub;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Tile;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseAdapter;

/**
 * Hold a key, click the ground, and the party sees the tile.
 *
 * Two listeners rather than one, because the key and the click arrive
 * separately and the interesting state is the overlap between them: the key
 * being down at the moment the mouse goes down.
 *
 * The ping is sent as RuneLite's own TilePing rather than a message of our
 * own, deliberately. It means a Veritas ping lands on the screen of
 * everyone in the party whatever they are running, and theirs land on ours,
 * so the clan is not split into people whose pings work on each other and
 * people whose do not.
 */
class VeritasPartyPings extends MouseAdapter implements KeyListener
{
	private final Client client;
	private final VeritasEventsConfig config;
	private final PartyHub party;

	/** Whether the hotkey is down right now. */
	private boolean held;

	@Inject
	VeritasPartyPings(Client client, VeritasEventsConfig config, PartyHub party)
	{
		this.client = client;
		this.config = config;
		this.party = party;
	}

	@Override
	public void keyPressed(KeyEvent event)
	{
		if (config.partyPings() && config.partyPingKey().matches(event))
		{
			held = true;
		}
	}

	@Override
	public void keyReleased(KeyEvent event)
	{
		if (config.partyPingKey().matches(event))
		{
			held = false;
		}
	}

	@Override
	public void keyTyped(KeyEvent event)
	{
	}

	/**
	 * The window losing focus has to let the key go.
	 *
	 * Alt tabbing away mid hold means no release ever arrives, and without
	 * this the key would be stuck down: every click afterwards would ping
	 * the party until the key was pressed and released again.
	 */
	@Override
	public void focusLost()
	{
		held = false;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		if (!held || !config.partyPings() || !party.isInParty())
		{
			return event;
		}

		Tile tile = client.getSelectedSceneTile();
		if (tile == null)
		{
			return event;
		}

		party.ping(tile.getWorldLocation());

		// eaten, so the click that marked the tile does not also walk you onto it
		event.consume();
		return event;
	}
}
