/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

/** Shows what the plugin has sent, laid out like the loot tracker. */
class VeritasEventsPanel extends PluginPanel
{
	private static final int HISTORY = 15;

	private final VeritasEventsConfig config;
	private final ItemManager itemManager;
	private final Deque<Sent> sent = new ArrayDeque<>();

	private final JLabel status = new JLabel();
	private final JPanel entries = new JPanel();

	VeritasEventsPanel(VeritasEventsConfig config, ItemManager itemManager)
	{
		this.config = config;
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Veritas Events");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(title);
		header.add(status);
		header.add(javax.swing.Box.createVerticalStrut(8));

		entries.setLayout(new BoxLayout(entries, BoxLayout.Y_AXIS));
		entries.setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(header, BorderLayout.NORTH);
		add(entries, BorderLayout.CENTER);
		refresh();
	}

	/** Updates the status line. */
	void refresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			boolean ready = !config.eventUrl().trim().isEmpty();
			status.setText(ready ? "Connected to your event" : "Paste your event URL in the settings");
			status.setForeground(ready ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
			redraw();
		});
	}

	/** Records one send. items is a flat list of id, quantity pairs. */
	void record(String source, List<int[]> items, long value, boolean ok)
	{
		synchronized (sent)
		{
			sent.addFirst(new Sent(source, items, value, ok));
			while (sent.size() > HISTORY)
			{
				sent.removeLast();
			}
		}
		SwingUtilities.invokeLater(this::redraw);
	}

	private void redraw()
	{
		entries.removeAll();
		synchronized (sent)
		{
			if (sent.isEmpty())
			{
				JLabel none = new JLabel("Nothing sent yet");
				none.setFont(FontManager.getRunescapeSmallFont());
				none.setForeground(Color.GRAY);
				none.setAlignmentX(Component.LEFT_ALIGNMENT);
				entries.add(none);
			}
			else
			{
				for (Sent entry : sent)
				{
					entries.add(box(entry));
					entries.add(javax.swing.Box.createVerticalStrut(4));
				}
			}
		}
		entries.revalidate();
		entries.repaint();
	}

	/** One sent drop: source and value on top, item icons underneath. */
	private JPanel box(Sent entry)
	{
		JPanel box = new JPanel(new BorderLayout());
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		box.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel source = new JLabel(entry.source);
		source.setFont(FontManager.getRunescapeSmallFont());
		source.setForeground(entry.ok ? Color.WHITE : ColorScheme.PROGRESS_ERROR_COLOR);
		top.add(source, BorderLayout.WEST);

		if (entry.value > 0)
		{
			JLabel value = new JLabel(QuantityFormatter.quantityToStackSize(entry.value) + " gp");
			value.setFont(FontManager.getRunescapeSmallFont());
			value.setForeground(Color.GRAY);
			top.add(value, BorderLayout.EAST);
		}
		box.add(top, BorderLayout.NORTH);

		if (!entry.items.isEmpty())
		{
			JPanel icons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
			icons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			for (int[] item : entry.items)
			{
				JLabel icon = new JLabel();
				icon.setPreferredSize(new Dimension(36, 32));
				itemManager.getImage(item[0], item[1], item[1] > 1).addTo(icon);
				icons.add(icon);
			}
			box.add(icons, BorderLayout.CENTER);
		}

		if (!entry.ok)
		{
			JLabel failed = new JLabel("Not accepted by the board");
			failed.setFont(FontManager.getRunescapeSmallFont());
			failed.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
			box.add(failed, BorderLayout.SOUTH);
		}
		return box;
	}

	private static class Sent
	{
		private final String source;
		private final List<int[]> items;
		private final long value;
		private final boolean ok;

		Sent(String source, List<int[]> items, long value, boolean ok)
		{
			this.source = source;
			this.items = items;
			this.value = value;
			this.ok = ok;
		}
	}
}
