/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events;

import com.veritas.events.party.PartyHub;
import com.veritas.events.party.PartyMate;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/**
 * What the party looks like from inside the game.
 *
 * Two things, both of which exist so the built-in Party plugin can be
 * switched off rather than left running alongside: the tiles people ping,
 * and a thin pair of bars over the heads of everyone in the party.
 *
 * The bars are deliberately small. Somebody mid raid is reading the boss,
 * not the overlay, and the only question this has to answer at a glance is
 * who is in trouble. Anything bigger would be in the way of the fight it is
 * meant to help with.
 */
class VeritasPartyOverlay extends Overlay
{
	/**
	 * How long a ping lasts, and how many rings run out of it.
	 *
	 * A tick and a half. Four ticks was long enough that a ping was still
	 * on screen after the moment it was pointing at had passed, which is
	 * worse than it not being there.
	 */
	private static final int PING_MILLIS = 900;
	private static final int PING_RINGS = 2;

	private static final int BAR_WIDTH = 34;
	private static final int BAR_HEIGHT = 3;

	private static final Color HEALTH = new Color(0xC8, 0x3C, 0x3C);
	private static final Color PRAYER = new Color(0x6F, 0xB7, 0xD8);
	private static final Color BEHIND = new Color(0x14, 0x14, 0x18, 0xC0);

	private final Client client;
	private final VeritasEventsConfig config;

	private final PartyHub party;

	/**
	 * Every ping still fading, oldest first.
	 *
	 * A list and not a map keyed by tile. Keyed by tile, a second ping on
	 * the same spot replaced the first and spam clicking only restarted one
	 * animation; kept separately they overlap, their fills add up, and the
	 * tile goes solid while you are clicking and thins out as each expires.
	 */
	/*
	 * Written from the websocket thread and read while the scene is being
	 * drawn, so it cannot be a plain list. Pings are few and arrive only
	 * when somebody clicks, which is what this one is good at.
	 */
	private final List<Ping> pings = new CopyOnWriteArrayList<>();

	/** One ping: where, when, whose color, and whose name. */
	private static final class Ping
	{
		private final long at = System.currentTimeMillis();
		private final WorldPoint where;
		private final Color color;
		private final String who;

		Ping(WorldPoint where, Color color, String who)
		{
			this.where = where;
			this.color = color;
			this.who = who == null ? "" : who;
		}
	}

	@Inject
	VeritasPartyOverlay(Client client, VeritasEventsConfig config, PartyHub party)
	{
		this.client = client;
		this.config = config;
		this.party = party;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	/** Somebody pinged a tile. Kept until it has finished fading. */
	void ping(WorldPoint where, @Nullable Color color, @Nullable String who)
	{
		if (where == null)
		{
			return;
		}

		// a ceiling, so leaning on the key cannot grow this without end
		if (pings.size() > 60)
		{
			pings.remove(0);
		}
		pings.add(new Ping(where, color == null ? config.partyColor() : color, who));
	}

	void forget()
	{
		pings.clear();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		/*
		 * Nothing at all while RuneLite's own Party plugin is running, since
		 * it draws the same two things and one of everything is the point.
		 */
		if (!party.isInParty() || !party.standingIn())
		{
			pings.clear();
			return null;
		}

		// drawn even with the team overlay off, since a ping is something
		// somebody did on purpose rather than something always on screen

		drawPings(graphics);
		if (config.partyOverlay())
		{
			drawTeam(graphics);
		}
		return null;
	}

	/**
	 * Rings that run outward from a pinged tile and fade as they go.
	 *
	 * Movement rather than a static marker, because the point of a ping is
	 * to catch an eye that is looking somewhere else.
	 */
	private void drawPings(Graphics2D graphics)
	{
		if (pings.isEmpty())
		{
			return;
		}

		long now = System.currentTimeMillis();
		pings.removeIf((one) -> now - one.at > PING_MILLIS);

		Stroke was = graphics.getStroke();
		for (Ping ping : pings)
		{
			LocalPoint local = LocalPoint.fromWorld(client.getTopLevelWorldView(), ping.where);
			if (local == null)
			{
				continue;
			}

			java.awt.Polygon tile = Perspective.getCanvasTilePoly(client, local);
			if (tile == null)
			{
				continue;
			}

			float through = (now - ping.at) / (float) PING_MILLIS;
			Color color = ping.color;

			/*
			 * A wash over the tile as well as the rings.
			 *
			 * One ping is a faint tint. Several, because somebody is leaning
			 * on the key, lay over each other and the tile goes solid, which
			 * is the whole point of leaning on it.
			 */
			graphics.setColor(new Color(color.getRed(), color.getGreen(),
				color.getBlue(), clamp((int) (90 * (1 - through)))));
			graphics.fill(tile);

			for (int ring = 0; ring < PING_RINGS; ring++)
			{
				// each ring sets off a little after the one before it
				float at = through * PING_RINGS - ring;
				if (at < 0 || at > 1)
				{
					continue;
				}

				graphics.setColor(new Color(color.getRed(), color.getGreen(),
					color.getBlue(), clamp((int) (220 * (1 - at)))));
				graphics.setStroke(new BasicStroke(2f));

				java.awt.geom.AffineTransform grow = java.awt.geom.AffineTransform
					.getTranslateInstance(tile.getBounds2D().getCenterX(),
						tile.getBounds2D().getCenterY());
				grow.scale(0.3 + at, 0.3 + at);
				grow.translate(-tile.getBounds2D().getCenterX(), -tile.getBounds2D().getCenterY());
				graphics.draw(grow.createTransformedShape(tile));
			}

			/*
			 * Who pinged it.
			 *
			 * In a party of five the color alone tells you it was not you,
			 * and a name tells you who to look at. Held at full strength for
			 * the first half so it is readable rather than a flash.
			 */
			if (config.partyPingNames() && !ping.who.isEmpty())
			{
				int fade = clamp((int) (255 * Math.min(1f, 2 * (1 - through))));
				int middle = (int) tile.getBounds2D().getCenterX();
				int top = (int) tile.getBounds2D().getCenterY() - 12;
				int wide = graphics.getFontMetrics().stringWidth(ping.who);

				graphics.setColor(new Color(0, 0, 0, fade));
				graphics.drawString(ping.who, middle - wide / 2 + 1, top + 1);
				graphics.setColor(new Color(color.getRed(), color.getGreen(),
					color.getBlue(), fade));
				graphics.drawString(ping.who, middle - wide / 2, top);
			}
		}
		graphics.setStroke(was);
	}

	/** Alpha has to land between 0 and 255 or the Color constructor throws. */
	private static int clamp(int alpha)
	{
		return Math.max(0, Math.min(255, alpha));
	}

	/**
	 * Health and prayer over the heads of the party.
	 *
	 * Only people actually on screen, matched by name, because that is the
	 * only thing the party and the scene have in common. Somebody on another
	 * world or out of sight simply has nothing drawn for them, which is the
	 * honest answer rather than a stale bar in the wrong place.
	 */
	private void drawTeam(Graphics2D graphics)
	{
		List<PartyMate> mates = party.mates();
		if (mates.size() < 2)
		{
			return;
		}

		Map<String, PartyMate> byName = new HashMap<>();
		for (PartyMate mate : mates)
		{
			if (!mate.getName().isEmpty())
			{
				byName.put(mate.getName().toLowerCase(), mate);
			}
		}

		for (Player player : client.getPlayers())
		{
			if (player == null || player.getName() == null)
			{
				continue;
			}

			PartyMate mate = byName.get(Text.sanitize(player.getName()).toLowerCase());
			if (mate == null || player == client.getLocalPlayer())
			{
				continue;
			}

			Point above = player.getCanvasTextLocation(graphics, "", player.getLogicalHeight() + 30);
			if (above == null)
			{
				continue;
			}

			int top = above.getY();

			/*
			 * The name, in whatever color they chose for themselves.
			 *
			 * This is what Mark party names does, and until now it did
			 * nothing at all: the setting was in the panel and read nowhere.
			 */
			if (config.partyNames())
			{
				Color theirs = mate.getColor() == null ? config.partyColor() : mate.getColor();
				Point at = player.getCanvasTextLocation(graphics, player.getName(),
					player.getLogicalHeight() + 44);
				if (at != null)
				{
					graphics.setColor(Color.BLACK);
					graphics.drawString(player.getName(), at.getX() + 1, at.getY() + 1);
					graphics.setColor(theirs);
					graphics.drawString(player.getName(), at.getX(), at.getY());
				}
			}

			if (bar(graphics, above.getX(), top, mate.getHealth(), mate.getHealthMax(), HEALTH))
			{
				top += BAR_HEIGHT + 1;
			}
			bar(graphics, above.getX(), top, mate.getPrayer(), mate.getPrayerMax(), PRAYER);
		}
	}

	/** One bar, or nothing at all when there is no figure to draw yet. */
	private boolean bar(Graphics2D graphics, int middle, int top, int now, int most, Color color)
	{
		if (now < 0 || most <= 0)
		{
			return false;
		}

		int left = middle - BAR_WIDTH / 2;
		graphics.setColor(BEHIND);
		graphics.fillRect(left - 1, top - 1, BAR_WIDTH + 2, BAR_HEIGHT + 2);
		graphics.setColor(color);
		graphics.fillRect(left, top, Math.max(0, Math.min(BAR_WIDTH,
			BAR_WIDTH * now / most)), BAR_HEIGHT);
		return true;
	}
}
