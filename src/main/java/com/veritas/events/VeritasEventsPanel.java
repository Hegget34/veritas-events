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
import java.awt.GridLayout;
import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * Side panel: whether the plugin is connected, what the event looks like,
 * how the player is doing, and what this client has sent.
 */
class VeritasEventsPanel extends PluginPanel
{
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
	private static final int HISTORY = 20;
	private static final Color GOOD = new Color(76, 175, 80);
	private static final Color BAD = new Color(220, 80, 70);
	private static final Color IDLE = new Color(150, 150, 150);

	private final VeritasEventsConfig config;
	private final Deque<String> history = new ArrayDeque<>();

	private final JLabel status = new JLabel();
	private final JPanel eventBox = new JPanel();
	private final JPanel youBox = new JPanel();
	private final JPanel feed = new JPanel();

	@Nullable
	private EventStatus latest;

	VeritasEventsPanel(VeritasEventsConfig config)
	{
		this.config = config;
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		add(header(), BorderLayout.NORTH);

		JPanel home = column();
		home.add(section("Event", eventBox));
		home.add(Box.createVerticalStrut(8));
		home.add(section("You", youBox));
		home.add(Box.createVerticalStrut(8));
		home.add(openButton());

		JPanel activity = column();
		feed.setLayout(new BoxLayout(feed, BoxLayout.Y_AXIS));
		feed.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		feed.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		activity.add(feed);

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);

		MaterialTabGroup tabs = new MaterialTabGroup(display);
		tabs.addTab(new MaterialTab("Home", tabs, home));
		MaterialTab activityTab = new MaterialTab("Activity", tabs, activity);
		tabs.addTab(activityTab);
		tabs.select(tabs.getTab(0));

		JPanel body = new JPanel(new BorderLayout());
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.add(tabs, BorderLayout.NORTH);
		body.add(display, BorderLayout.CENTER);
		add(body, BorderLayout.CENTER);

		refresh();
	}

	private JPanel header()
	{
		JPanel top = column();
		JLabel title = new JLabel("Veritas Events");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(title);
		top.add(status);
		top.add(Box.createVerticalStrut(8));
		return top;
	}

	private static JPanel column()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(ColorScheme.DARK_GRAY_COLOR);
		return p;
	}

	private static JPanel section(String name, JPanel content)
	{
		JPanel wrap = column();
		JLabel label = new JLabel(name.toUpperCase());
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(IDLE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		content.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		content.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.add(label);
		wrap.add(content);
		return wrap;
	}

	private JButton openButton()
	{
		JButton button = new JButton("Open the event board");
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.setPreferredSize(new Dimension(0, 26));
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.addActionListener(e -> browse(boardUrl()));
		return button;
	}

	private String boardUrl()
	{
		EventStatus s = latest;
		if (s != null && s.boardUrl != null && !s.boardUrl.isEmpty())
		{
			return s.boardUrl;
		}
		return config.eventUrl();
	}

	private static void browse(String target)
	{
		if (target == null || target.trim().isEmpty())
		{
			return;
		}
		try
		{
			if (Desktop.isDesktopSupported())
			{
				Desktop.getDesktop().browse(new URI(target.trim()));
			}
		}
		catch (Exception ignored)
		{
			// if the browser will not open there is nothing useful to do
		}
	}

	/** Called when settings change or a status fetch comes back. */
	void refresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			boolean configured = config.eventUrl() != null && !config.eventUrl().trim().isEmpty();
			EventStatus s = latest;

			if (!configured)
			{
				status.setText("Not set up");
				status.setForeground(BAD);
			}
			else if (s == null)
			{
				status.setText("Ready");
				status.setForeground(GOOD);
			}
			else
			{
				status.setText("Connected");
				status.setForeground(GOOD);
			}

			eventBox.removeAll();
			if (!configured)
			{
				eventBox.add(line("Paste your event URL in the settings", IDLE));
			}
			else if (s == null || s.eventName == null)
			{
				eventBox.add(line("Sending to your event", Color.LIGHT_GRAY));
				eventBox.add(line(describeWhatIsSent(), IDLE));
			}
			else
			{
				eventBox.add(line(s.eventName, Color.WHITE));
				if (s.phase != null)
				{
					eventBox.add(line(s.phase, IDLE));
				}
				eventBox.add(line(describeWhatIsSent(), IDLE));
			}

			youBox.removeAll();
			if (s != null && s.playerKnown)
			{
				if (s.team != null)
				{
					youBox.add(line(s.team, Color.WHITE));
				}
				youBox.add(line(s.submissions + " drops, " + s.approved + " approved", Color.LIGHT_GRAY));
				youBox.add(line(s.hits + " hits", Color.LIGHT_GRAY));
			}
			else if (configured)
			{
				youBox.add(line("Not on this event's player list yet", IDLE));
			}
			else
			{
				youBox.add(line("-", IDLE));
			}

			redrawFeed();
			revalidate();
			repaint();
		});
	}

	private static JLabel line(String text, Color colour)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(colour);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private String describeWhatIsSent()
	{
		StringBuilder sb = new StringBuilder();
		if (config.sendLoot())
		{
			sb.append("drops");
		}
		if (config.sendPets())
		{
			sb.append(sb.length() == 0 ? "pets" : ", pets");
		}
		if (config.sendCollectionLog())
		{
			sb.append(sb.length() == 0 ? "clog" : ", clog");
		}
		return sb.length() == 0 ? "Nothing selected in settings" : "Sending " + sb;
	}

	void setStatus(@Nullable EventStatus status)
	{
		this.latest = status;
		refresh();
	}

	/** Records one send for the Activity tab. */
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
				feed.add(line("Nothing sent yet", IDLE));
			}
			else
			{
				for (String row : history)
				{
					feed.add(line(row, row.startsWith("✓") ? Color.LIGHT_GRAY : BAD));
				}
			}
		}
		feed.revalidate();
		feed.repaint();
	}

	/** What the event server tells us about itself and the player. */
	static class EventStatus
	{
		String eventName;
		String phase;
		String boardUrl;
		boolean playerKnown;
		String team;
		int submissions;
		int approved;
		int hits;
	}
}
