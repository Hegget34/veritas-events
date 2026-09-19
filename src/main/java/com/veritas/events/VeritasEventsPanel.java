/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events;

import java.awt.Color;
import java.awt.Component;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/** Shows whether the plugin is set up, and what it has sent this session. */
class VeritasEventsPanel extends PluginPanel
{
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
	private static final int HISTORY = 20;

	private final VeritasEventsConfig config;
	private final Deque<String> sent = new ArrayDeque<>();

	VeritasEventsPanel(VeritasEventsConfig config)
	{
		this.config = config;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		refresh();
	}

	/** Rebuilds the panel. Called on start, on a config change, and after each send. */
	void refresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			removeAll();

			JLabel title = new JLabel("Veritas Events");
			title.setFont(FontManager.getRunescapeBoldFont());
			title.setForeground(Color.WHITE);
			title.setAlignmentX(Component.LEFT_ALIGNMENT);
			add(title);

			boolean ready = !config.eventUrl().trim().isEmpty();
			add(label(ready ? "Connected to your event" : "Paste your event URL in the settings",
				ready ? Color.GREEN : Color.ORANGE));

			synchronized (sent)
			{
				add(label(sent.isEmpty() ? "Nothing sent yet" : "Sent this session", Color.GRAY));
				for (String line : sent)
				{
					add(label(line, Color.LIGHT_GRAY));
				}
			}

			revalidate();
			repaint();
		});
	}

	/** Records one send for the list. */
	void record(String what, boolean ok)
	{
		synchronized (sent)
		{
			sent.addFirst((ok ? "+ " : "! ") + LocalTime.now().format(CLOCK) + " " + what);
			while (sent.size() > HISTORY)
			{
				sent.removeLast();
			}
		}
		refresh();
	}

	private static JLabel label(String text, Color colour)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(colour);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}
}
