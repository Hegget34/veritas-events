/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events;

import com.veritas.events.party.PartyHub;
import java.awt.event.KeyEvent;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Tile;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;

/**
 * Hold a key, click the ground, and the party sees the tile.
 *
 * The key and the click arrive separately, and the interesting state is the
 * overlap between them: the key being down at the moment of the click.
 *
 * The ping is sent as RuneLite's own TilePing rather than a message of our
 * own, deliberately. It means a Veritas ping lands on the screen of
 * everyone in the party whatever they are running, and theirs land on ours,
 * so the clan is not split into people whose pings work on each other and
 * people whose do not.
 */
class VeritasPartyPings implements KeyListener
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

	/**
	 * The click that marks the tile.
	 *
	 * Taken off the menu rather than off the mouse. A mouse listener runs on
	 * the window's own thread, and the hovered tile read from there is the
	 * scene read while the game is in the middle of changing it. This
	 * arrives on the client's thread with the click already worked out.
	 *
	 * Only a plain walk is ever taken, so holding the key and clicking an
	 * NPC still attacks it and clicking an item still picks it up. The walk
	 * itself is eaten, because marking a tile for somebody else is not a
	 * reason to go and stand on it.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!held
			|| event.getMenuAction() != MenuAction.WALK
			|| client.isMenuOpen()
			|| !config.partyPings()
			|| !party.isInParty())
		{
			return;
		}

		Tile tile = client.getSelectedSceneTile();
		if (tile == null)
		{
			return;
		}

		event.consume();
		party.ping(tile.getWorldLocation());
	}
}
