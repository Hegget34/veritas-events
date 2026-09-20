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
import java.awt.BasicStroke;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
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
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.QuantityFormatter;

/** The sidebar panel: what the event is, how the teams stand, and what has been sent. */
class VeritasEventsPanel extends PluginPanel
{
	private static final int HISTORY = 15;
	private static final Color GOLD = new Color(0xC8, 0xA0, 0x00);
	private static final Color BLUE = new Color(0x5A, 0xA6, 0xD8);
	private static final int BAR_HEIGHT = 16;
	private static final int BUTTON_HEIGHT = 26;
	private static final String[] VIEWS = {"Home", "Event", "Clan stats", "Loot Tracker"};

	/** Ordered worst last, so merging a group keeps the worst outcome. */
	static final int SENT = 0;
	static final int KEPT = 1;
	static final int FAILED = 2;

	private static final String[] SKILLS = {
		"overall", "attack", "defence", "strength", "hitpoints", "ranged", "prayer", "magic",
		"cooking", "woodcutting", "fletching", "fishing", "firemaking", "crafting", "smithing",
		"mining", "herblore", "agility", "thieving", "slayer", "farming", "runecrafting",
		"hunter", "construction", "sailing",
	};

	private static final String[] BOSSES = {
		"ehb", "abyssal_sire", "alchemical_hydra", "amoxliatl", "araxxor", "artio", "barrows_chests",
		"bryophyta", "callisto", "calvarion", "cerberus", "chambers_of_xeric",
		"chambers_of_xeric_challenge_mode", "chaos_elemental", "chaos_fanatic", "commander_zilyana",
		"corporeal_beast", "crazy_archaeologist", "dagannoth_prime", "dagannoth_rex",
		"dagannoth_supreme", "deranged_archaeologist", "doom_of_mokhaiotl", "duke_sucellus",
		"general_graardor", "giant_mole", "grotesque_guardians", "hespori", "kalphite_queen",
		"king_black_dragon", "kraken", "kreearra", "kril_tsutsaroth", "lunar_chests", "mimic", "nex",
		"nightmare", "obor", "phantom_muspah", "phosanis_nightmare", "sarachnis", "scorpia",
		"scurrius", "skotizo", "sol_heredit", "spindel", "tempoross", "the_corrupted_gauntlet",
		"the_gauntlet", "the_hueycoatl", "the_leviathan", "the_royal_titans", "the_whisperer",
		"theatre_of_blood", "theatre_of_blood_hard_mode", "thermonuclear_smoke_devil",
		"tombs_of_amascut", "tombs_of_amascut_expert", "tzkal_zuk", "tztok_jad", "vardorvis",
		"venenatis", "vetion", "vorkath", "wintertodt", "yama", "zalcano", "zulrah",
	};

	private static final String[][] PERIODS = {
		{"Today", "day"}, {"This week", "week"}, {"This month", "month"}, {"This year", "year"},
	};

	private static final String[][] COMMUNITY = {
		{"Discord", "https://discord.gg/veritascc"},
		{"Clan website", "https://osrs-bingo-arbd.onrender.com/"},
	};

	private static final String[][] TRACKING = {
		{"DropTracker", "https://www.droptracker.io/groups/356"},
		{"TempleOSRS", "https://templeosrs.com/groups/overview.php?id=2418"},
		{"Wise Old Man", "https://wiseoldman.net/groups/13727"},
	};

	private final VeritasEventsConfig config;
	private final ItemManager itemManager;
	private final Deque<Sent> sent = new ArrayDeque<>();

	private int kills;
	private int sends;
	private long sessionLoot;

	private final JLabel rsn = new JLabel();
	private final JLabel status = new JLabel();

	private final JPanel homeTab = column();
	private final JPanel statsTab = column();
	private final JPanel gainedContent = column();
	private final JComboBox<String> typeSelect = new JComboBox<>();
	private final JComboBox<String> metricSelect = new JComboBox<>();
	private final JLabel metricCaption = caption("Skill");
	private String[] metrics = SKILLS;
	private final JComboBox<String> periodSelect = new JComboBox<>();
	private String metric = SKILLS[0];
	private boolean statsAsked;
	private final BiConsumer<String, String> onGained;
	private final JPanel eventTab = column();
	private final JPanel activityTab = column();

	private final JComboBox<String> viewSelect = new JComboBox<>();
	private final JPanel pageTab = column();
	private String problem = "";
	private final JPanel display = new JPanel(new BorderLayout());
	private JsonObject lastDetails;
	private boolean fillingPages;

	private final JButton resend = new JButton("Send again");
	private final JComboBox<String> lootSelect = new JComboBox<>();
	private final JButton groupButton = new JButton();
	private final JButton collapseButton = new JButton();
	private boolean grouped = true;
	private boolean collapsed;
	private final Runnable onRefresh;

	VeritasEventsPanel(VeritasEventsConfig config, ItemManager itemManager,
		@Nullable ImageIcon logo, Runnable onResend, Runnable onRefresh,
		BiConsumer<String, String> onGained)
	{
		this.config = config;
		this.itemManager = itemManager;
		this.onRefresh = onRefresh;
		this.onGained = onGained;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		resend.setFont(FontManager.getRunescapeFont());
		resend.setEnabled(false);
		resend.setToolTipText("Send the last thing again, if the board missed it");
		resend.addActionListener(e -> onResend.run());

		style(lootSelect);
		lootSelect.addActionListener(e ->
		{
			if (!fillingPages)
			{
				drawActivity();
			}
		});
		for (JButton button : new JButton[]{groupButton, collapseButton})
		{
			button.setPreferredSize(new Dimension(28, 24));
			button.setFocusable(false);
		}
		groupButton.addActionListener(e ->
		{
			grouped = !grouped;
			drawActivity();
		});
		collapseButton.addActionListener(e ->
		{
			collapsed = !collapsed;
			drawActivity();
		});

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		top.add(header(logo));
		top.add(Box.createVerticalStrut(10));
		top.add(rule());
		top.add(Box.createVerticalStrut(10));

		display.setBackground(ColorScheme.DARK_GRAY_COLOR);

		drawViews(null);
		style(viewSelect);
		viewSelect.addActionListener(e ->
		{
			if (!fillingPages)
			{
				showView();
			}
		});
		top.add(caption("View"));
		top.add(viewSelect);
		top.add(Box.createVerticalStrut(12));

		add(top, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);
		showView();

		buildStats();
		setEvent(null, null);
		drawActivity();
		refresh();
	}

	/** XP and kills the clan has put on, straight from Wise Old Man. */
	private void buildStats()
	{
		typeSelect.addItem("Skill");
		typeSelect.addItem("Boss");
		fillMetrics();
		for (String[] period : PERIODS)
		{
			periodSelect.addItem(period[0]);
		}
		periodSelect.setSelectedIndex(1);

		style(typeSelect);
		style(metricSelect);
		style(periodSelect);

		typeSelect.addActionListener(e ->
		{
			boolean boss = typeSelect.getSelectedIndex() == 1;
			metrics = boss ? BOSSES : SKILLS;
			metricCaption.setText(boss ? "BOSS" : "SKILL");
			fillMetrics();
			askGained(metrics[0]);
		});
		metricSelect.addActionListener(e ->
		{
			if (!fillingPages)
			{
				askGained(metrics[Math.max(0, metricSelect.getSelectedIndex())]);
			}
		});
		periodSelect.addActionListener(e -> askGained(metric));

		statsTab.add(caption("Type"));
		statsTab.add(typeSelect);
		statsTab.add(Box.createVerticalStrut(10));
		statsTab.add(metricCaption);
		statsTab.add(metricSelect);
		statsTab.add(Box.createVerticalStrut(10));
		statsTab.add(caption("Period"));
		statsTab.add(periodSelect);
		statsTab.add(Box.createVerticalStrut(12));
		statsTab.add(gainedContent);
	}

	/** Refills the second chooser after the first one changes. */
	private void fillMetrics()
	{
		fillingPages = true;
		metricSelect.removeAllItems();
		for (String key : metrics)
		{
			metricSelect.addItem(label(key));
		}
		fillingPages = false;
	}

	private void askGained(String key)
	{
		metric = key;
		statsAsked = true;

		gainedContent.removeAll();
		gainedContent.add(hint("Asking Wise Old Man..."));
		gainedContent.revalidate();
		gainedContent.repaint();

		int period = Math.max(0, periodSelect.getSelectedIndex());
		onGained.accept(key, PERIODS[period][1]);
	}

	/** The leaderboard Wise Old Man sent back. */
	void setGained(@Nullable JsonArray rows, @Nullable String problem)
	{
		SwingUtilities.invokeLater(() ->
		{
			gainedContent.removeAll();

			if (rows == null)
			{
				gainedContent.add(hint(problem == null ? "Nothing came back." : problem));
			}
			else
			{
				String you = rsn.getText();
				int rank = 1;
				for (JsonElement element : rows)
				{
					JsonObject entry = element.getAsJsonObject();
					long gained = number(object(entry, "data"), "gained");
					if (gained <= 0)
					{
						// Most of a 300 member clan gains nothing at a given boss.
						continue;
					}
					String who = text(object(entry, "player"), "displayName");
					gainedContent.add(row(rank++ + ". " + who,
						QuantityFormatter.quantityToStackSize(gained), who.equals(you)));
				}
				if (rank == 1)
				{
					gainedContent.add(hint("Nobody has gained anything here yet."));
				}
			}

			gainedContent.revalidate();
			gainedContent.repaint();
		});
	}

	/** "the_royal_titans" reads as "The royal titans". */
	private static String label(String key)
	{
		if ("overall".equals(key))
		{
			return "Overall XP";
		}
		if ("ehb".equals(key))
		{
			return "Efficient hours bossed";
		}
		String words = key.replace('_', ' ');
		return Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	/**
	 * The front page: who we are and the places the clan keeps its business.
	 * The links are built in so this reads properly before any board answers,
	 * and the board can add to it underneath.
	 */
	private void drawHome(@Nullable JsonObject details)
	{
		homeTab.removeAll();

		homeTab.add(line("Old School RuneScape clan", Color.GRAY));
		homeTab.add(Box.createVerticalStrut(12));

		homeTab.add(caption("Community"));
		links(homeTab, COMMUNITY);
		homeTab.add(Box.createVerticalStrut(12));

		homeTab.add(caption("Tracking"));
		links(homeTab, TRACKING);

		blocks(homeTab, array(details, "home"));

		homeTab.revalidate();
		homeTab.repaint();
	}

	private void links(JPanel into, String[][] links)
	{
		for (String[] entry : links)
		{
			JButton button = link(entry[0], entry[1]);
			if (button != null)
			{
				into.add(button);
				into.add(Box.createVerticalStrut(4));
			}
		}
	}

	/** Shows whichever view is chosen, including the board's own pages. */
	private void showView()
	{
		int chosen = viewSelect.getSelectedIndex();
		JPanel view;

		if (chosen == 1)
		{
			view = eventTab;
		}
		else if (chosen == 2)
		{
			view = statsTab;
			if (!statsAsked)
			{
				askGained(metric);
			}
		}
		else if (chosen == 3)
		{
			view = activityTab;
		}
		else if (chosen >= VIEWS.length)
		{
			drawBoardPage(chosen - VIEWS.length);
			view = pageTab;
		}
		else
		{
			view = homeTab;
		}

		display.removeAll();
		display.add(view, BorderLayout.NORTH);
		display.revalidate();
		display.repaint();
	}

	/** The two choosers, told apart by a caption and a gold edge. */
	private static void style(JComboBox<String> combo)
	{
		combo.setFont(FontManager.getRunescapeFont());
		combo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		combo.setForeground(GOLD);
		combo.setFocusable(false);
		combo.setAlignmentX(Component.LEFT_ALIGNMENT);
		combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		combo.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 2, 1, 0, GOLD),
			BorderFactory.createEmptyBorder(3, 6, 3, 4)));
	}

	/** A small blue heading above a control, so it is obvious what it picks. */
	private static JLabel caption(String text)
	{
		JLabel label = new JLabel(text.toUpperCase());
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(BLUE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(0, 1, 3, 0));
		return label;
	}

	/** Stacked bars: four for a list of kills, two for a grouped one. */
	private static ImageIcon bars(int count)
	{
		BufferedImage image = new BufferedImage(14, 14, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.LIGHT_GRAY);
		int step = 12 / count;
		for (int i = 0; i < count; i++)
		{
			graphics.fillRect(1, 1 + i * step, 12, Math.max(1, step - 1));
		}
		graphics.dispose();
		return new ImageIcon(image);
	}

	/** A chevron, pointing down to expand and up to collapse. */
	private static ImageIcon chevron(boolean down)
	{
		BufferedImage image = new BufferedImage(14, 14, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(Color.LIGHT_GRAY);
		graphics.setStroke(new BasicStroke(2f));
		int top = down ? 4 : 9;
		int bottom = down ? 9 : 4;
		graphics.drawLine(2, top, 7, bottom);
		graphics.drawLine(7, bottom, 12, top);
		graphics.dispose();
		return new ImageIcon(image);
	}

	/** A hairline, to break the panel into parts. */
	private static JPanel rule()
	{
		JPanel rule = new JPanel();
		rule.setBackground(ColorScheme.BORDER_COLOR);
		rule.setAlignmentX(Component.LEFT_ALIGNMENT);
		rule.setPreferredSize(new Dimension(Integer.MAX_VALUE, 1));
		rule.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		return rule;
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

		JLabel title = new JLabel("Veritas");
		title.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
		title.setForeground(GOLD);
		rsn.setFont(FontManager.getRunescapeFont());
		rsn.setForeground(Color.WHITE);
		status.setFont(FontManager.getRunescapeFont());

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
	 * Fills the views from whatever the board reported. Every field
	 * is optional, so a board that answers with nothing still leaves a usable panel.
	 */
	void setEvent(@Nullable JsonObject details, @Nullable String problem)
	{
		SwingUtilities.invokeLater(() ->
		{
			this.problem = problem == null ? "" : problem;
			lastDetails = details;
			drawHome(details);
			drawEvent(details);
			drawViews(details);
			drawActivity();
		});
	}

	private void drawEvent(@Nullable JsonObject details)
	{
		eventTab.removeAll();

		String name = text(details, "event");
		if (name.isEmpty())
		{
			eventTab.add(hint(!problem.isEmpty() ? problem
				: config.eventUrl().trim().isEmpty()
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
				badge.setFont(FontManager.getRunescapeFont());
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
				eventTab.add(cells(orElse(text(progress, "label"), "Progress"),
					number(progress, "done") + " / " + number(progress, "total"), GOLD, Color.WHITE));
				eventTab.add(bar(progress));
			}

			blocks(eventTab, array(details, "blocks"));
		}

		teams(eventTab, details);

		eventTab.add(Box.createVerticalStrut(12));
		JButton refreshButton = new JButton("Refresh");
		refreshButton.setFont(FontManager.getRunescapeFont());
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
		button.setFont(FontManager.getRunescapeFont());
		button.setToolTipText(url);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, BUTTON_HEIGHT));
		button.setPreferredSize(new Dimension(Integer.MAX_VALUE, BUTTON_HEIGHT));
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
			stats.add(stat("Your drops", String.valueOf(kills)));
		}
		return stats;
	}

	/** The event's own measure of progress, whatever it counts. */
	private static ProgressBar bar(JsonObject progress)
	{
		// ProgressBar counts in ints; a tile count never comes near the limit.
		int done = (int) number(progress, "done");
		int total = (int) Math.max(1, number(progress, "total"));
		ProgressBar bar = new ProgressBar();
		bar.setMaximumValue(total);
		bar.setValue(done);
		// The centre label only gets a third of the width and would be cut off,
		// so the wording goes on its own line above the bar instead.
		bar.setCenterLabel(bar.getPercentage() + "%");
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

	/** Where the teams stand, and who has sent the most, under the event itself. */
	private void teams(JPanel into, @Nullable JsonObject details)
	{
		String you = text(details, "team");

		JsonArray standings = array(details, "standings");
		if (standings != null)
		{
			into.add(Box.createVerticalStrut(12));
			into.add(title(orElse(text(details, "standingsLabel"), "Standings")));
			int rank = 1;
			for (JsonElement element : standings)
			{
				JsonObject entry = element.getAsJsonObject();
				String team = text(entry, "name");
				into.add(row(rank++ + ". " + team, text(entry, "value"), team.equals(you)));
			}
		}

		JsonArray top = array(details, "top");
		if (top != null)
		{
			into.add(Box.createVerticalStrut(12));
			into.add(title(orElse(text(details, "topLabel"), "Most drops")));
			for (JsonElement element : top)
			{
				JsonObject entry = element.getAsJsonObject();
				String player = text(entry, "name");
				into.add(row(player, text(entry, "value"), player.equals(rsn.getText())));
			}
		}
	}

	/** Lists the fixed views, then a view per page the board publishes. */
	private void drawViews(@Nullable JsonObject details)
	{
		String open = (String) viewSelect.getSelectedItem();
		JsonArray pages = array(details, "pages");

		fillingPages = true;
		viewSelect.removeAllItems();
		for (String view : VIEWS)
		{
			viewSelect.addItem(view);
		}
		if (pages != null)
		{
			for (JsonElement element : pages)
			{
				viewSelect.addItem(text(element.getAsJsonObject(), "name"));
			}
		}
		if (open != null)
		{
			viewSelect.setSelectedItem(open);
		}
		fillingPages = false;

		showView();
	}

	/** One of the board's pages. */
	private void drawBoardPage(int index)
	{
		pageTab.removeAll();

		JsonArray pages = array(lastDetails, "pages");
		if (pages == null || index >= pages.size())
		{
			pageTab.add(hint(!problem.isEmpty() ? problem
				: "This page is no longer being published."));
		}
		else
		{
			blocks(pageTab, array(pages.get(index).getAsJsonObject(), "blocks"));
		}

		pageTab.revalidate();
		pageTab.repaint();
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
					into.add(Box.createVerticalStrut(12));
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

	/**
	 * A table, drawn as one line per row: everything but the last column on the
	 * left and the figure on the right. Equal width columns waste most of a
	 * 225px sidebar on the rank number.
	 */
	private static JPanel table(JsonObject block)
	{
		JsonArray columns = array(block, "columns");
		JsonArray rows = array(block, "rows");

		JPanel table = column();
		table.setAlignmentX(Component.LEFT_ALIGNMENT);

		if (columns != null && columns.size() > 0)
		{
			table.add(cells(lead(columns), last(columns), BLUE, BLUE));
		}

		if (rows != null)
		{
			for (JsonElement element : rows)
			{
				JsonArray row = element.getAsJsonArray();
				table.add(cells(lead(row), last(row), Color.WHITE, GOLD));
			}
		}
		return table;
	}

	/** Everything but the last cell, run together. */
	private static String lead(JsonArray row)
	{
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < row.size() - 1; i++)
		{
			text.append(text.length() == 0 ? "" : "  ").append(row.get(i).getAsString());
		}
		return text.length() == 0 && row.size() == 1 ? row.get(0).getAsString() : text.toString();
	}

	private static String last(JsonArray row)
	{
		return row.size() > 1 ? row.get(row.size() - 1).getAsString() : "";
	}

	/**
	 * Records one lot of loot, whether or not it was sent anywhere. The tracker
	 * is worth having with no event running, so everything is kept and only the
	 * outcome differs.
	 */
	void record(String source, List<int[]> items, long value, int state)
	{
		synchronized (sent)
		{
			sent.addFirst(new Sent(source, items, value, state));
			while (sent.size() > HISTORY)
			{
				sent.removeLast();
			}
			kills++;
			if (state == SENT)
			{
				sends++;
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

		JsonArray wanted = array(lastDetails, "wanted");
		String open = (String) lootSelect.getSelectedItem();
		fillingPages = true;
		lootSelect.removeAllItems();
		lootSelect.addItem("Your loot");
		if (wanted != null || !text(lastDetails, "event").isEmpty())
		{
			lootSelect.addItem("Items to go for");
		}
		if (open != null)
		{
			lootSelect.setSelectedItem(open);
		}
		fillingPages = false;

		// Worth the room only while an event is running; without one there is
		// nothing to go for and the chooser would just be in the way.
		boolean event = wanted != null || !text(lastDetails, "event").isEmpty();
		if (event)
		{
			activityTab.add(caption("Show"));
			activityTab.add(lootSelect);
			activityTab.add(Box.createVerticalStrut(12));
		}

		if (event && lootSelect.getSelectedIndex() == 1)
		{
			drawWanted(wanted);
		}
		else
		{
			drawLoot();
		}

		activityTab.revalidate();
		activityTab.repaint();
	}

	/** What this event is asking for, so you know what is worth going after. */
	private void drawWanted(@Nullable JsonArray wanted)
	{
		activityTab.add(title(orElse(text(lastDetails, "wantedLabel"), "Still to find")));

		if (wanted == null || wanted.size() == 0)
		{
			activityTab.add(hint("This event is not publishing a list of items."));
			return;
		}

		for (JsonElement element : wanted)
		{
			JsonObject item = element.getAsJsonObject();
			boolean found = has(item, "found") && item.get("found").getAsBoolean();

			JPanel line = new JPanel(new BorderLayout(6, 0));
			line.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			line.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
			line.setAlignmentX(Component.LEFT_ALIGNMENT);

			if (has(item, "id"))
			{
				JLabel icon = new JLabel();
				icon.setPreferredSize(new Dimension(36, 32));
				itemManager.getImage((int) number(item, "id")).addTo(icon);
				line.add(icon, BorderLayout.WEST);
			}

			JLabel name = new JLabel(text(item, "name"));
			name.setFont(FontManager.getRunescapeFont());
			name.setForeground(found ? Color.GRAY : Color.WHITE);
			line.add(name, BorderLayout.CENTER);

			JLabel note = new JLabel(found ? "found" : text(item, "note"));
			note.setFont(FontManager.getRunescapeFont());
			note.setForeground(found ? ColorScheme.PROGRESS_COMPLETE_COLOR : GOLD);
			line.add(note, BorderLayout.EAST);

			line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
			activityTab.add(line);
			activityTab.add(Box.createVerticalStrut(2));
		}
	}

	private void drawLoot()
	{
		JPanel counts = new JPanel(new GridLayout(1, 3, 4, 0));
		counts.setBackground(ColorScheme.DARK_GRAY_COLOR);
		counts.setAlignmentX(Component.LEFT_ALIGNMENT);
		counts.add(stat("Kills", String.valueOf(kills)));
		counts.add(stat("Sent", String.valueOf(sends)));
		counts.add(stat("Loot", QuantityFormatter.quantityToStackSize(sessionLoot)));
		activityTab.add(counts);
		activityTab.add(Box.createVerticalStrut(8));

		groupButton.setIcon(bars(grouped ? 4 : 2));
		groupButton.setToolTipText(grouped ? "Show each kill separately" : "Group loot by source");
		collapseButton.setIcon(chevron(collapsed));
		collapseButton.setToolTipText(collapsed ? "Expand all" : "Collapse all");

		JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		tools.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tools.setAlignmentX(Component.LEFT_ALIGNMENT);
		tools.add(groupButton);
		tools.add(collapseButton);
		tools.setMaximumSize(new Dimension(Integer.MAX_VALUE, tools.getPreferredSize().height));
		activityTab.add(tools);
		activityTab.add(Box.createVerticalStrut(4));
		activityTab.add(resend);
		activityTab.add(Box.createVerticalStrut(10));

		List<Sent> entries = entries();
		if (entries.isEmpty())
		{
			activityTab.add(hint("Nothing sent yet."));
			return;
		}
		for (Sent entry : entries)
		{
			activityTab.add(box(entry));
			activityTab.add(Box.createVerticalStrut(4));
		}
	}

	/** Every send, or one line per source with the kills added up. */
	private List<Sent> entries()
	{
		synchronized (sent)
		{
			if (!grouped)
			{
				return new ArrayList<>(sent);
			}

			Map<String, Sent> bySource = new LinkedHashMap<>();
			for (Sent one : sent)
			{
				Sent already = bySource.get(one.source);
				if (already == null)
				{
					bySource.put(one.source, one.copy());
				}
				else
				{
					already.merge(one);
				}
			}
			return new ArrayList<>(bySource.values());
		}
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

		JLabel source = new JLabel(entry.count > 1 ? entry.source + " x " + entry.count : entry.source);
		source.setFont(FontManager.getRunescapeFont());
		source.setForeground(entry.state == FAILED ? ColorScheme.PROGRESS_ERROR_COLOR : Color.WHITE);
		top.add(source, BorderLayout.WEST);

		if (entry.value > 0)
		{
			JLabel value = new JLabel(QuantityFormatter.quantityToStackSize(entry.value) + " gp");
			value.setFont(FontManager.getRunescapeFont());
			value.setForeground(entry.value >= config.bigDropValue() ? GOLD : Color.GRAY);
			top.add(value, BorderLayout.EAST);
		}
		box.add(top, BorderLayout.NORTH);

		if (!entry.items.isEmpty() && !collapsed)
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

		if (entry.state == FAILED)
		{
			box.add(line("Not accepted by the board", ColorScheme.PROGRESS_ERROR_COLOR), BorderLayout.SOUTH);
		}
		else if (entry.state == KEPT)
		{
			box.add(line("Not sent", Color.GRAY), BorderLayout.SOUTH);
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
		top.setFont(FontManager.getRunescapeFont());
		top.setForeground(GOLD);
		top.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel bottom = new JLabel(caption, SwingConstants.CENTER);
		bottom.setFont(FontManager.getRunescapeFont());
		bottom.setForeground(Color.GRAY);
		bottom.setAlignmentX(Component.CENTER_ALIGNMENT);

		panel.add(top);
		panel.add(bottom);
		return panel;
	}

	/** A name on the left and a figure on the right, picked out if it is yours. */
	private static JPanel row(String left, String right, boolean mine)
	{
		return cells(left, right, mine ? GOLD : Color.WHITE, Color.LIGHT_GRAY);
	}

	/** One full width line: something on the left, something on the right. */
	private static JPanel cells(String left, String right, Color leftColour, Color rightColour)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel name = new JLabel(left);
		name.setFont(FontManager.getRunescapeFont());
		name.setForeground(leftColour);
		panel.add(name, BorderLayout.WEST);

		if (!right.isEmpty())
		{
			JLabel figure = new JLabel(right);
			figure.setFont(FontManager.getRunescapeFont());
			figure.setForeground(rightColour);
			panel.add(figure, BorderLayout.EAST);
		}

		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
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
		label.setFont(FontManager.getRunescapeFont());
		label.setForeground(colour);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/** Wrapped grey text, for the empty states. */
	private static JLabel hint(String text)
	{
		JLabel label = line("<html><body style='width:200px'>" + text + "</body></html>", Color.GRAY);
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

	private static long number(@Nullable JsonObject object, String key)
	{
		try
		{
			return has(object, key) ? object.get(key).getAsLong() : 0;
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
		private int state;
		private long value;
		private int count;

		Sent(String source, List<int[]> items, long value, int state)
		{
			this.source = source;
			this.items = items;
			this.value = value;
			this.state = state;
			this.count = 1;
		}

		/** A copy that can be merged into without touching what was recorded. */
		Sent copy()
		{
			List<int[]> copied = new ArrayList<>();
			for (int[] item : items)
			{
				copied.add(new int[]{item[0], item[1]});
			}
			return new Sent(source, copied, value, state);
		}

		void merge(Sent other)
		{
			count += other.count;
			value += other.value;
			state = Math.max(state, other.state);
			for (int[] add : other.items)
			{
				boolean known = false;
				for (int[] have : items)
				{
					if (have[0] == add[0])
					{
						have[1] += add[1];
						known = true;
						break;
					}
				}
				if (!known)
				{
					items.add(new int[]{add[0], add[1]});
				}
			}
		}
	}
}
