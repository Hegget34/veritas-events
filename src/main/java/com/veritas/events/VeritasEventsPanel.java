/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.ProgressBar;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.QuantityFormatter;

/** The sidebar panel: what the event is, how the teams stand, and what has been sent. */
class VeritasEventsPanel extends PluginPanel
{
	private static final int HISTORY = 15;
	private static final Color GOLD = new Color(0xC8, 0xA0, 0x00);
	private static final Color BLUE = new Color(0x46, 0x8F, 0xB1);
	private static final int BAR_HEIGHT = 16;

	private final VeritasEventsConfig config;
	private final ItemManager itemManager;
	private final Deque<Sent> sent = new ArrayDeque<>();

	private int sends;
	private int failures;
	private long sessionLoot;

	private final JLabel rsn = new JLabel();
	private final JLabel status = new JLabel();

	private final JPanel eventTab = column();
	private final JPanel teamTab = column();
	private final JPanel clanTab = column();
	private final JPanel activityTab = column();

	private final JComboBox<String> pageSelect = new JComboBox<>();
	private final JPanel pageContent = column();
	private JsonObject lastDetails;
	private boolean fillingPages;

	private final JButton resend = new JButton("Send again");
	private final Runnable onRefresh;

	VeritasEventsPanel(VeritasEventsConfig config, ItemManager itemManager,
		@Nullable ImageIcon logo, Runnable onResend, Runnable onRefresh)
	{
		this.config = config;
		this.itemManager = itemManager;
		this.onRefresh = onRefresh;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		resend.setFont(FontManager.getRunescapeSmallFont());
		resend.setEnabled(false);
		resend.setToolTipText("Send the last thing again, if the board missed it");
		resend.addActionListener(e -> onResend.run());

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		top.add(header(logo));
		top.add(Box.createVerticalStrut(8));

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);
		MaterialTabGroup tabs = new MaterialTabGroup(display);
		tabs.setLayout(new GridLayout(1, 4, 4, 0));
		tabs.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		MaterialTab first = new MaterialTab("Event", tabs, eventTab);
		tabs.addTab(first);
		tabs.addTab(new MaterialTab("Teams", tabs, teamTab));
		tabs.addTab(new MaterialTab("Clan", tabs, clanTab));
		tabs.addTab(new MaterialTab("Sent", tabs, activityTab));
		tabs.select(first);
		tabs.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(tabs);

		add(top, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);

		pageSelect.setFont(FontManager.getRunescapeSmallFont());
		pageSelect.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		pageSelect.setForeground(Color.WHITE);
		pageSelect.setFocusable(false);
		pageSelect.setAlignmentX(Component.LEFT_ALIGNMENT);
		pageSelect.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		pageSelect.addActionListener(e ->
		{
			if (!fillingPages)
			{
				drawPage();
			}
		});
		clanTab.add(pageSelect);
		clanTab.add(Box.createVerticalStrut(6));
		clanTab.add(pageContent);

		setEvent(null);
		drawActivity();
		refresh();
	}

	/** Logo, plugin name, who you are playing as, and whether the board is reachable. */
	private JPanel header(@Nullable ImageIcon logo)
	{
		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);

		if (logo != null)
		{
			header.add(new JLabel(logo), BorderLayout.WEST);
		}

		JPanel names = new JPanel();
		names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
		names.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Veritas Events");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(GOLD);
		rsn.setFont(FontManager.getRunescapeSmallFont());
		rsn.setForeground(Color.WHITE);
		status.setFont(FontManager.getRunescapeSmallFont());

		names.add(title);
		names.add(rsn);
		names.add(status);
		header.add(names, BorderLayout.CENTER);
		return header;
	}

	/** The name you are playing as, shown under the title. */
	void setPlayer(String name)
	{
		SwingUtilities.invokeLater(() -> rsn.setText(name));
	}

	/** Updates the connection line. */
	void refresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			boolean ready = !config.eventUrl().trim().isEmpty();
			status.setText("● " + (ready ? "Connected" : "No event set"));
			status.setForeground(ready ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
		});
	}

	/**
	 * Fills the Event and Teams tabs from whatever the board reported. Every field
	 * is optional, so a board that answers with nothing still leaves a usable panel.
	 */
	void setEvent(@Nullable JsonObject details)
	{
		SwingUtilities.invokeLater(() ->
		{
			lastDetails = details;
			drawEvent(details);
			drawTeams(details);
			drawPages(details);
		});
	}

	private void drawEvent(@Nullable JsonObject details)
	{
		eventTab.removeAll();

		String name = text(details, "event");
		if (name.isEmpty())
		{
			eventTab.add(hint(config.eventUrl().trim().isEmpty()
				? "Paste the event URL your organiser gave you into the settings."
				: "Connected, but this board is not reporting event details yet."));
		}
		else
		{
			JLabel heading = new JLabel(name);
			heading.setFont(FontManager.getRunescapeBoldFont());
			heading.setForeground(Color.WHITE);
			heading.setAlignmentX(Component.LEFT_ALIGNMENT);
			eventTab.add(heading);

			String phase = text(details, "phase");
			if (!phase.isEmpty())
			{
				JLabel badge = new JLabel(phase.toUpperCase());
				badge.setFont(FontManager.getRunescapeSmallFont());
				badge.setForeground("live".equalsIgnoreCase(phase)
					? ColorScheme.PROGRESS_COMPLETE_COLOR
					: "ended".equalsIgnoreCase(phase) ? Color.GRAY : GOLD);
				badge.setAlignmentX(Component.LEFT_ALIGNMENT);
				eventTab.add(badge);
			}

			String left = remaining(details);
			if (!left.isEmpty())
			{
				eventTab.add(line(left, Color.GRAY));
			}

			eventTab.add(Box.createVerticalStrut(8));
			eventTab.add(stats(details));

			JsonObject progress = object(details, "progress");
			if (progress != null && has(progress, "total"))
			{
				eventTab.add(Box.createVerticalStrut(8));
				eventTab.add(bar(progress));
			}

			blocks(eventTab, array(details, "blocks"));
		}

		eventTab.add(Box.createVerticalStrut(8));
		JButton refreshButton = new JButton("Refresh");
		refreshButton.setFont(FontManager.getRunescapeSmallFont());
		refreshButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		refreshButton.addActionListener(e -> onRefresh.run());
		eventTab.add(refreshButton);

		eventTab.revalidate();
		eventTab.repaint();
	}

	/**
	 * A button that opens one of the board's links. Only ordinary web addresses
	 * are offered, and nothing opens until the player clicks it.
	 */
	@Nullable
	private static JButton link(String label, String url)
	{
		if (label.isEmpty() || !(url.startsWith("https://") || url.startsWith("http://")))
		{
			return null;
		}
		JButton button = new JButton(label);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setToolTipText(url);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
		button.addActionListener(e -> LinkBrowser.browse(url));
		return button;
	}

	/** Your team, plus whatever figures this event cares about. */
	private JPanel stats(@Nullable JsonObject details)
	{
		JPanel stats = new JPanel(new GridLayout(0, 2, 4, 4));
		stats.setBackground(ColorScheme.DARK_GRAY_COLOR);
		stats.setAlignmentX(Component.LEFT_ALIGNMENT);
		stats.add(stat("Your team", orDash(text(details, "team"))));

		JsonArray extra = array(details, "stats");
		if (extra != null)
		{
			for (JsonElement element : extra)
			{
				JsonObject entry = element.getAsJsonObject();
				stats.add(stat(text(entry, "label"), orDash(text(entry, "value"))));
			}
		}
		else
		{
			stats.add(stat("Your drops", String.valueOf(sends)));
		}
		return stats;
	}

	/** The event's own measure of progress, whatever it counts. */
	private static ProgressBar bar(JsonObject progress)
	{
		int done = number(progress, "done");
		int total = Math.max(1, number(progress, "total"));
		String label = text(progress, "label");

		ProgressBar bar = new ProgressBar();
		bar.setMaximumValue(total);
		bar.setValue(done);
		bar.setCenterLabel(done + " / " + total + (label.isEmpty() ? "" : " " + label));
		bar.setLeftLabel("");
		bar.setRightLabel("");
		bar.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		bar.setForeground(GOLD);
		// ProgressBar paints its fill exactly BAR_HEIGHT tall, so the component
		// has to be that tall too or the unfilled colour shows under it.
		bar.setPreferredSize(new Dimension(0, BAR_HEIGHT));
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, BAR_HEIGHT));
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		return bar;
	}

	private void drawTeams(@Nullable JsonObject details)
	{
		teamTab.removeAll();
		String you = text(details, "team");

		JsonArray standings = array(details, "standings");
		if (standings == null)
		{
			teamTab.add(hint("Standings show here once the board reports them."));
		}
		else
		{
			teamTab.add(title(orElse(text(details, "standingsLabel"), "Standings")));
			int rank = 1;
			for (JsonElement element : standings)
			{
				JsonObject entry = element.getAsJsonObject();
				String team = text(entry, "name");
				teamTab.add(row(rank++ + ". " + team, text(entry, "value"), team.equals(you)));
			}
		}

		JsonArray top = array(details, "top");
		if (top != null)
		{
			teamTab.add(Box.createVerticalStrut(8));
			teamTab.add(title(orElse(text(details, "topLabel"), "Most drops")));
			for (JsonElement element : top)
			{
				JsonObject entry = element.getAsJsonObject();
				String player = text(entry, "name");
				teamTab.add(row(player, text(entry, "value"), player.equals(rsn.getText())));
			}
		}

		teamTab.revalidate();
		teamTab.repaint();
	}

	/** Fills the page chooser from the board, keeping whatever page was open. */
	private void drawPages(@Nullable JsonObject details)
	{
		String open = (String) pageSelect.getSelectedItem();
		JsonArray pages = array(details, "pages");

		fillingPages = true;
		pageSelect.removeAllItems();
		if (pages != null)
		{
			for (JsonElement element : pages)
			{
				pageSelect.addItem(text(element.getAsJsonObject(), "name"));
			}
			if (open != null)
			{
				pageSelect.setSelectedItem(open);
			}
		}
		fillingPages = false;

		pageSelect.setVisible(pageSelect.getItemCount() > 0);
		drawPage();
	}

	/** Draws whichever page is chosen. */
	private void drawPage()
	{
		pageContent.removeAll();

		JsonArray pages = array(lastDetails, "pages");
		int index = pageSelect.getSelectedIndex();
		if (pages == null || index < 0 || index >= pages.size())
		{
			pageContent.add(hint("Clan pages show here once the board reports them."));
		}
		else
		{
			blocks(pageContent, array(pages.get(index).getAsJsonObject(), "blocks"));
		}

		pageContent.revalidate();
		pageContent.repaint();
	}

	/**
	 * Draws a page out of the board's building blocks. Everything a clan page
	 * needs - a heading, some lines, a table of names and figures, a big number
	 * or a link - without the plugin knowing what any of it means.
	 */
	private void blocks(JPanel into, @Nullable JsonArray blocks)
	{
		if (blocks == null)
		{
			return;
		}

		for (JsonElement element : blocks)
		{
			JsonObject block = element.getAsJsonObject();
			switch (text(block, "type"))
			{
				case "heading":
					into.add(Box.createVerticalStrut(8));
					into.add(title(text(block, "text")));
					break;

				case "text":
					JsonArray lines = array(block, "lines");
					if (lines != null)
					{
						for (JsonElement entry : lines)
						{
							into.add(line(entry.getAsString(), Color.LIGHT_GRAY));
						}
					}
					break;

				case "stat":
					JLabel figure = line(text(block, "value"), GOLD);
					figure.setFont(FontManager.getRunescapeBoldFont());
					into.add(figure);
					into.add(line(text(block, "label"), Color.GRAY));
					break;

				case "table":
					into.add(table(block));
					break;

				case "link":
					JButton button = link(text(block, "label"), text(block, "url"));
					if (button != null)
					{
						into.add(Box.createVerticalStrut(2));
						into.add(button);
					}
					break;

				default:
					break;
			}
		}
	}

	/** A table of columns and rows, as the hiscore style pages use. */
	private static JPanel table(JsonObject block)
	{
		JsonArray columns = array(block, "columns");
		JsonArray rows = array(block, "rows");
		int width = columns == null ? 0 : columns.size();

		JPanel table = new JPanel(new GridLayout(0, Math.max(1, width), 4, 2));
		table.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		table.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		table.setAlignmentX(Component.LEFT_ALIGNMENT);

		if (columns != null)
		{
			for (JsonElement column : columns)
			{
				JLabel head = new JLabel(column.getAsString());
				head.setFont(FontManager.getRunescapeSmallFont());
				head.setForeground(BLUE);
				table.add(head);
			}
		}

		if (rows != null)
		{
			for (JsonElement element : rows)
			{
				JsonArray cells = element.getAsJsonArray();
				for (int i = 0; i < Math.max(width, cells.size()); i++)
				{
					JLabel cell = new JLabel(i < cells.size() ? cells.get(i).getAsString() : "");
					cell.setFont(FontManager.getRunescapeSmallFont());
					// The last column carries the figure, so pick it out.
					cell.setForeground(i == width - 1 && width > 1 ? GOLD : Color.WHITE);
					table.add(cell);
				}
			}
		}

		table.setMaximumSize(new Dimension(Integer.MAX_VALUE, table.getPreferredSize().height));
		return table;
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
			sends++;
			if (!ok)
			{
				failures++;
			}
			sessionLoot += value;
		}
		SwingUtilities.invokeLater(() ->
		{
			resend.setEnabled(true);
			drawActivity();
		});
	}

	private void drawActivity()
	{
		activityTab.removeAll();

		JPanel counts = new JPanel(new GridLayout(1, 3, 4, 0));
		counts.setBackground(ColorScheme.DARK_GRAY_COLOR);
		counts.setAlignmentX(Component.LEFT_ALIGNMENT);
		counts.add(stat("Sent", String.valueOf(sends)));
		counts.add(stat("Failed", String.valueOf(failures)));
		counts.add(stat("Loot", QuantityFormatter.quantityToStackSize(sessionLoot)));
		activityTab.add(counts);

		activityTab.add(Box.createVerticalStrut(6));
		resend.setAlignmentX(Component.LEFT_ALIGNMENT);
		activityTab.add(resend);
		activityTab.add(Box.createVerticalStrut(8));

		synchronized (sent)
		{
			if (sent.isEmpty())
			{
				activityTab.add(hint("Nothing sent yet."));
			}
			else
			{
				for (Sent entry : sent)
				{
					activityTab.add(box(entry));
					activityTab.add(Box.createVerticalStrut(4));
				}
			}
		}

		activityTab.revalidate();
		activityTab.repaint();
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
			value.setForeground(entry.value >= config.bigDropValue() ? GOLD : Color.GRAY);
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
			box.add(line("Not accepted by the board", ColorScheme.PROGRESS_ERROR_COLOR), BorderLayout.SOUTH);
		}
		return box;
	}

	// ---- small builders, so the tabs above stay readable ----

	private static JPanel column()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		return panel;
	}

	/** A figure over a caption, the way the loot tracker shows its totals. */
	private static JPanel stat(String caption, String value)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		JLabel top = new JLabel(value, SwingConstants.CENTER);
		top.setFont(FontManager.getRunescapeSmallFont());
		top.setForeground(GOLD);
		top.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel bottom = new JLabel(caption, SwingConstants.CENTER);
		bottom.setFont(FontManager.getRunescapeSmallFont());
		bottom.setForeground(Color.GRAY);
		bottom.setAlignmentX(Component.CENTER_ALIGNMENT);

		panel.add(top);
		panel.add(bottom);
		return panel;
	}

	/** A name on the left and a figure on the right, picked out if it is yours. */
	private static JPanel row(String left, String right, boolean mine)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel name = new JLabel(left);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(mine ? GOLD : Color.WHITE);

		JLabel figure = new JLabel(right);
		figure.setFont(FontManager.getRunescapeSmallFont());
		figure.setForeground(Color.GRAY);

		panel.add(name, BorderLayout.WEST);
		panel.add(figure, BorderLayout.EAST);
		return panel;
	}

	private static JLabel title(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(Color.WHITE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		return label;
	}

	private static JLabel line(String text, Color colour)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(colour);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/** Wrapped grey text, for the empty states. */
	private static JLabel hint(String text)
	{
		JLabel label = line("<html><body style='width:190px'>" + text + "</body></html>", Color.GRAY);
		label.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
		return label;
	}

	private static String orElse(String value, String fallback)
	{
		return value.isEmpty() ? fallback : value;
	}

	@Nullable
	private static JsonObject object(@Nullable JsonObject parent, String key)
	{
		return has(parent, key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
	}

	private static String orDash(String value)
	{
		return value.isEmpty() ? "-" : value;
	}

	private static boolean has(@Nullable JsonObject object, String key)
	{
		return object != null && object.has(key) && !object.get(key).isJsonNull();
	}

	private static String text(@Nullable JsonObject object, String key)
	{
		return has(object, key) ? object.get(key).getAsString() : "";
	}

	private static int number(@Nullable JsonObject object, String key)
	{
		try
		{
			return has(object, key) ? object.get(key).getAsInt() : 0;
		}
		catch (RuntimeException e)
		{
			return 0;
		}
	}

	@Nullable
	private static JsonArray array(@Nullable JsonObject object, String key)
	{
		return has(object, key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : null;
	}

	/** "Ends in 2d 4h", from a millisecond timestamp. */
	private static String remaining(@Nullable JsonObject details)
	{
		if (!has(details, "endsAt"))
		{
			return "";
		}
		long left = details.get("endsAt").getAsLong() - System.currentTimeMillis();
		if (left <= 0)
		{
			return "Finished";
		}
		long hours = left / 3600000L;
		return hours >= 24
			? "Ends in " + (hours / 24) + "d " + (hours % 24) + "h"
			: hours >= 1 ? "Ends in " + hours + "h " + (left / 60000L % 60) + "m"
			: "Ends in " + Math.max(1, left / 60000L) + "m";
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
