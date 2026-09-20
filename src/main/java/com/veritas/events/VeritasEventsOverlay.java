/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * Stamps the game window with the event password and the time in UTC, so a
 * screenshot of a drop can be shown to have been taken during the event and
 * not dug out of an old folder.
 */
class VeritasEventsOverlay extends OverlayPanel
{
	private static final int PADDING = 14;

	private static final DateTimeFormatter CLOCK =
		DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm").withZone(ZoneOffset.UTC);

	private final VeritasEventsPlugin plugin;
	private final VeritasEventsConfig config;

	@Inject
	VeritasEventsOverlay(VeritasEventsPlugin plugin, VeritasEventsConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		// Alt and drag moves it; this puts it in the overlay menu so it can be
		// put back again without hunting for where it went.
		getMenuEntries().add(new OverlayMenuEntry(
			MenuAction.RUNELITE_OVERLAY_CONFIG, OverlayManager.OPTION_CONFIGURE, "Veritas overlay"));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay())
		{
			return null;
		}

		String password = plugin.password();
		String clock = config.showDateTime() ? CLOCK.format(Instant.now()) + " UTC" : "";
		if (password.isEmpty() && clock.isEmpty())
		{
			return null;
		}

		// One row, password on the left and the clock on the right, rather than
		// two stacked lines. Either half alone sits on the left on its own.
		boolean both = !password.isEmpty() && !clock.isEmpty();
		String left = password.isEmpty() ? clock : password;
		String right = both ? clock : "";

		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.leftColor(password.isEmpty() ? config.dateTimeColor() : config.passwordColor())
			.right(right)
			.rightColor(config.dateTimeColor())
			.build());

		// The panel is a fixed width by default, which would overlap the two
		// halves, so widen it to whatever the text actually needs.
		panelComponent.setPreferredSize(new Dimension(
			graphics.getFontMetrics().stringWidth(left + (both ? "    " + right : "")) + PADDING, 0));

		return super.render(graphics);
	}
}
