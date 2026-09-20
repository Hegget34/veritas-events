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
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		String password = plugin.password();
		boolean clock = config.showDateTime();
		if (!config.showOverlay() || (password.isEmpty() && !clock))
		{
			return null;
		}

		if (!password.isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(password)
				.leftColor(config.passwordColor())
				.build());
		}

		if (clock)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(CLOCK.format(Instant.now()) + " UTC")
				.leftColor(config.dateTimeColor())
				.build());
		}

		return super.render(graphics);
	}
}
