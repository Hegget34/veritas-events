/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel: whether the plugin is set up, and what it has sent this session.
 */
class VeritasEventsPanel extends PluginPanel
{
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
	private static final int HISTORY = 12;
	private static final Color GOOD = new Color(76, 175, 80);
	private static final Color BAD = new Color(220, 80, 70);
	private static final Color IDLE = new Color(160, 160, 160);

	private final Deque<String> history = new ArrayDeque<>();
	private final JLabel status = new JLabel();
	private final JLabel detail = new JLabel();
	private final JPanel feed = new JPanel();
	private final VeritasEventsConfig config;

	VeritasEventsPanel(VeritasEventsConfig config)
	{
		this.config = config;
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Veritas Events");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);

		status.setFont(FontManager.getRunescapeSmallFont());
		status.setAlignmentX(Component.LEFT_ALIGNMENT);

		detail.setFont(FontManager.getRunescapeSmallFont());
		detail.setForeground(IDLE);
		detail.setAlignmentX(Component.LEFT_ALIGNMENT);

		top.add(title);
		top.add(status);
		top.add(detail);
		top.add(javax.swing.Box.createVerticalStrut(10));

		JLabel sent = new JLabel("Sent this session");
		sent.setFont(FontManager.getRunescapeSmallFont());
		sent.setForeground(IDLE);
		sent.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(sent);

		feed.setLayout(new BoxLayout(feed, BoxLayout.Y_AXIS));
		feed.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		feed.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		JPanel bottom = new JPanel(new GridLayout(0, 1, 0, 4));
		bottom.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bottom.add(link("Open the event board", () -> config.eventUrl()));

		add(top, BorderLayout.NORTH);
		add(feed, BorderLayout.CENTER);
		add(bottom, BorderLayout.SOUTH);

		refresh();
	}

	/** Re-reads the config and repaints the status line. */
	void refresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			boolean configured = config.eventUrl() != null && !config.eventUrl().trim().isEmpty();
			if (!configured)
			{
				status.setText("Not set up");
				status.setForeground(BAD);
				detail.setText("Paste your event URL in the settings");
			}
			else
			{
				status.setText("Ready");
				status.setForeground(GOOD);
				detail.setText(describeWhatIsSent());
			}
			redrawFeed();
		});
	}

	private String describeWhatIsSent()
	{
		StringBuilder sb = new StringBuilder("Sending: ");
		boolean any = false;
		if (config.sendLoot())
		{
			sb.append("drops");
			any = true;
		}
		if (config.sendPets())
		{
			sb.append(any ? ", pets" : "pets");
			any = true;
		}
		if (config.sendCollectionLog())
		{
			sb.append(any ? ", clog" : "clog");
			any = true;
		}
		return any ? sb.toString() : "Nothing selected in settings";
	}

	/** Records one send for the session feed. */
	void record(String what, boolean ok)
	{
		synchronized (history)
		{
			history.addFirst((ok ? "✓ " : "✗ ") + LocalTime.now().format(CLOCK) + "  " + what);
			while (history.size() > HISTORY)
			{
				history.removeLast();
			}
		}
		SwingUtilities.invokeLater(this::redrawFeed);
	}

	private void redrawFeed()
	{
		feed.removeAll();
		synchronized (history)
		{
			if (history.isEmpty())
			{
				JLabel none = new JLabel("Nothing yet");
				none.setFont(FontManager.getRunescapeSmallFont());
				none.setForeground(IDLE);
				feed.add(none);
			}
			else
			{
				for (String line : history)
				{
					JLabel row = new JLabel(line);
					row.setFont(FontManager.getRunescapeSmallFont());
					row.setForeground(line.startsWith("✓") ? Color.LIGHT_GRAY : BAD);
					feed.add(row);
				}
			}
		}
		feed.revalidate();
		feed.repaint();
	}

	private JButton link(String text, java.util.function.Supplier<String> url)
	{
		JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.setPreferredSize(new Dimension(0, 26));
		button.addActionListener(e ->
		{
			String target = url.get();
			if (target == null || target.trim().isEmpty())
			{
				return;
			}
			try
			{
				URI uri = new URI(target.trim());
				if (Desktop.isDesktopSupported())
				{
					Desktop.getDesktop().browse(uri);
				}
			}
			catch (Exception ignored)
			{
				// nothing useful to do if the browser will not open
			}
		});
		return button;
	}
}
