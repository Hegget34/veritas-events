/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicButtonUI;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.ProgressBar;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.QuantityFormatter;

/** The sidebar panel: what the event is, how the teams stand, and what has been sent. */
@Slf4j
class VeritasEventsPanel extends PluginPanel
{
	/** Kept across restarts, so a long event is not lost by logging out. */
	private static final int HISTORY = 500;

	/**
	 * How many loot boxes to draw at once.
	 *
	 * Each one is a panel with its own item pictures, so the cost of a redraw
	 * is this number rather than the length of the history behind it.
	 */
	private static final int MOST_SHOWN = 50;

	/** Items to a row in a loot box, the same as RuneLite's own tracker uses. */
	private static final int ICON_COLUMNS = 5;

	/** What shows through the gaps between item cells. */
	private static final Color GRID = new Color(0x3A, 0x3A, 0x48);

	/** The clan's teal, for the drops that reached an event. */
	private static final Color TEAL = new Color(0x5F, 0xA8, 0xD3);
	private static final String DROPS = "drops_";
	private static final Color GOLD = new Color(0xC8, 0xA0, 0x00);
	private static final Color BLUE = new Color(0x5A, 0xA6, 0xD8);

	/** The clan site's own palette, so the two look like one thing. */
	private static final Color BRASS = new Color(0xE0, 0xC0, 0x90);
	private static final Color BONE = new Color(0xF0, 0xF0, 0xE0);
	private static final Color TEAL_D = new Color(0x2D, 0x6C, 0x92);

	/**
	 * How wide wrapped text can be.
	 *
	 * The sidebar is {@link PluginPanel#PANEL_WIDTH} wide and what is left
	 * after its own border, this panel's border and the scrollbar is around
	 * 170. Anything wider is drawn off the right edge, which is what was
	 * happening to every hint in here.
	 */
	private static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 55;

	/** The same, inside a card, which has an accent and padding of its own. */
	private static final int CARD_WIDTH = TEXT_WIDTH - 20;
	private static final int BAR_HEIGHT = 16;
	private static final int BUTTON_HEIGHT = 26;
	private static final float HEADING = 17f;
	private static final float FIGURE = 20f;
	private static final String[] VIEWS = {"Home", "This week", "Event", "Clan stats", "Loot Tracker"};

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
		{"Clan website", "https://veritasclan.cc"},
		{"Events portal", "https://osrs-bingo-arbd.onrender.com/"},
	};

	private static final String[][] TRACKING = {
		{"DropTracker", "https://www.droptracker.io/groups/356"},
		{"TempleOSRS", "https://templeosrs.com/groups/overview.php?id=2418"},
		{"Wise Old Man", "https://wiseoldman.net/groups/13727"},
	};

	private final VeritasEventsConfig config;
	private final ItemManager itemManager;
	/** This session, one entry per kill. */
	private final Deque<Sent> sent = new ArrayDeque<>();

	/** Every session, added up per source. Survives restarts and machines. */
	private final Map<String, Sent> history = new LinkedHashMap<>();

	private final Gson gson;
	private final ConfigManager configManager;

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
	private final BooleanSupplier lootTrackerOff;

	/** Told what to look up, because only it may read an item's composition. */
	private final Consumer<List<Integer>> onFactsWanted;

	/**
	 * What each item id is called and what it is worth, for the tooltips.
	 *
	 * Filled by the plugin. An id it has not told us about yet simply has no
	 * tooltip until it does.
	 */
	private Map<Integer, Facts> facts = Collections.emptyMap();

	/** Ids already asked about, so one that cannot be resolved is asked once. */
	private final Set<Integer> askedAbout = new LinkedHashSet<>();

	/** Ids noticed while drawing, sent off once the drawing is finished. */
	private final Set<Integer> toAsk = new LinkedHashSet<>();
	private final JPanel eventTab = column();
	private final JPanel activityTab = column();
	private final JPanel weekTab = column();

	/** Kills this client has watched, by monster, lower cased. */
	private Map<String, Integer> killsSeen = new HashMap<>();

	private final JComboBox<String> viewSelect = new JComboBox<>();
	private final JPanel pageTab = column();
	private final JPanel pageContent = column();
	private final JComboBox<String> subSelect = new JComboBox<>();
	private final JLabel subCaption = caption("Page");
	private int openPage = -1;
	private String problem = "";
	private final JPanel display = new JPanel(new BorderLayout());
	private JsonObject lastDetails;
	private JsonObject lastClan;
	private boolean fillingPages;

	private final JButton resend = new JButton("Send again");
	private final JComboBox<String> lootSelect = new JComboBox<>();
	private final JButton groupButton = new JButton();
	private final JButton collapseButton = new JButton();
	private final JButton clearButton = new JButton();

	/** Waits for drops to stop arriving before redrawing the loot tab. */
	private Timer settling;
	private boolean grouped = true;
	private boolean collapsed;
	private final Runnable onRefresh;

	VeritasEventsPanel(VeritasEventsConfig config, ItemManager itemManager,
		@Nullable ImageIcon logo, Runnable onResend, Runnable onRefresh,
		BiConsumer<String, String> onGained, BooleanSupplier lootTrackerOff,
		Consumer<List<Integer>> onFactsWanted, Gson gson, ConfigManager configManager)
	{
		this.config = config;
		this.itemManager = itemManager;
		this.gson = gson;
		this.configManager = configManager;
		this.onRefresh = onRefresh;
		this.onGained = onGained;
		this.lootTrackerOff = lootTrackerOff;
		this.onFactsWanted = onFactsWanted;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		pressable(resend);
		resend.setEnabled(false);
		resend.addActionListener(e -> onResend.run());

		style(subSelect);
		subSelect.addActionListener(e ->
		{
			if (!fillingPages)
			{
				drawPageContent();
			}
		});

		style(lootSelect);
		lootSelect.addActionListener(e ->
		{
			if (!fillingPages)
			{
				drawActivity();
			}
		});
		for (JButton button : new JButton[]{groupButton, collapseButton, clearButton})
		{
			pressable(button);
			button.setPreferredSize(new Dimension(28, 24));
		}
		clearButton.setIcon(bin());
		clearButton.setToolTipText("Clear the tracker. Right click one source to clear just that.");
		clearButton.addActionListener(e -> confirmForget(null));
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

		drawViews();
		style(viewSelect);
		viewSelect.addActionListener(e ->
		{
			if (!fillingPages)
			{
				showView();
			}
		});
		JButton refreshButton = new JButton(reload());
		refreshButton.setToolTipText("Ask the board for the latest");
		refreshButton.setPreferredSize(new Dimension(30, 28));
		refreshButton.setFocusable(false);
		refreshButton.addActionListener(e -> onRefresh.run());

		JPanel chooser = new JPanel(new BorderLayout(4, 0));
		chooser.setBackground(ColorScheme.DARK_GRAY_COLOR);
		chooser.setAlignmentX(Component.LEFT_ALIGNMENT);
		chooser.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		chooser.add(viewSelect, BorderLayout.CENTER);
		chooser.add(refreshButton, BorderLayout.EAST);

		top.add(caption("View"));
		top.add(chooser);
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
			metricCaption.setText(spaced(boss ? "Boss" : "Skill"));
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
	private void drawHome()
	{
		homeTab.removeAll();

		homeTab.add(line("Old School RuneScape clan", Color.GRAY));
		homeTab.add(Box.createVerticalStrut(12));

		homeTab.add(caption("Community"));
		links(homeTab, COMMUNITY);
		homeTab.add(Box.createVerticalStrut(12));

		homeTab.add(caption("Tracking"));
		links(homeTab, TRACKING);

		homeTab.revalidate();
		homeTab.repaint();
	}

	/**
	 * The plugin's own links, which are constants a few lines above.
	 *
	 * These go straight to a button rather than through the check a board's
	 * links face. That check exists because a board could name any address it
	 * liked; one written into this file cannot change at runtime and is as
	 * verifiable as anything gets. Putting them through it anyway dropped the
	 * clan's own website off the Home page.
	 */
	private void links(JPanel into, String[][] links)
	{
		for (String[] entry : links)
		{
			into.add(button(entry[0], entry[1]));
			into.add(Box.createVerticalStrut(4));
		}
	}

	/** Shows whichever view is chosen, including the board's own pages. */
	private void showView()
	{
		int chosen = viewSelect.getSelectedIndex();
		JPanel view;

		if (chosen == 1)
		{
			view = weekTab;
			drawWeek();
		}
		else if (chosen == 2)
		{
			view = eventTab;
		}
		else if (chosen == 3)
		{
			view = statsTab;
			if (!statsAsked)
			{
				askGained(metric);
			}
		}
		else if (chosen == 4)
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

	/**
	 * A small heading above a control, so it is obvious what it picks. Spaced
	 * out and in brass, since at this size a word of solid capitals is hard to
	 * read and easy to mistake for the thing underneath it.
	 */
	private static JLabel caption(String text)
	{
		JLabel label = new JLabel(spaced(text));
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(BRASS);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(0, 1, 4, 0));
		return label;
	}

	/** A word in spaced capitals, which is how every caption in here is set. */
	private static String spaced(String text)
	{
		StringBuilder out = new StringBuilder();
		for (char letter : text.toUpperCase().toCharArray())
		{
			out.append(out.length() == 0 ? "" : "\u2009").append(letter);
		}
		return out.toString();
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

	/** A waste bin, for the button that clears the tracker. */
	private static ImageIcon bin()
	{
		BufferedImage image = new BufferedImage(14, 14, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(Color.LIGHT_GRAY);
		graphics.fillRect(4, 1, 6, 2);
		graphics.fillRect(1, 3, 12, 2);
		graphics.fillRect(3, 5, 8, 8);
		graphics.setColor(ColorScheme.DARKER_GRAY_COLOR);
		graphics.fillRect(5, 7, 1, 5);
		graphics.fillRect(8, 7, 1, 5);
		graphics.dispose();
		return new ImageIcon(image);
	}

	/** A circular arrow for the refresh button. */
	private static ImageIcon reload()
	{
		BufferedImage image = new BufferedImage(14, 14, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(GOLD);
		graphics.setStroke(new BasicStroke(2f));
		// Most of a circle, with an arrow head where it stops.
		graphics.drawArc(2, 2, 10, 10, 60, 290);
		graphics.drawLine(11, 1, 11, 5);
		graphics.drawLine(11, 5, 7, 5);
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
	/**
	 * A button that behaves like one.
	 *
	 * Left to the look and feel these drew flat, with no edge and nothing
	 * happening under the pointer or the press, so Send again looked broken
	 * rather than merely disabled.
	 */
	private static void pressable(JButton button)
	{
		/*
		 * Through the basic UI, not the look and feel's own. RuneLite's paints
		 * its buttons its way and ignores a background set on them, which is
		 * why styling this one appeared to do nothing at all.
		 */
		button.setUI(new BasicButtonUI());
		button.setOpaque(true);
		button.setFont(FontManager.getRunescapeFont());
		button.setForeground(BONE);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setFocusPainted(false);
		button.setFocusable(false);
		button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
			BorderFactory.createEmptyBorder(3, 9, 3, 9)));

		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent event)
			{
				if (button.isEnabled())
				{
					button.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
				}
			}

			@Override
			public void mouseExited(MouseEvent event)
			{
				button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}

			@Override
			public void mousePressed(MouseEvent event)
			{
				if (button.isEnabled())
				{
					// a shade darker, so a click is felt as well as seen
					button.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				}
			}

			@Override
			public void mouseReleased(MouseEvent event)
			{
				button.setBackground(button.isEnabled() && button.contains(event.getPoint())
					? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			}
		});
	}

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

		JLabel title = new JLabel("VERITAS");
		title.setFont(FontManager.getRunescapeBoldFont().deriveFont(19f));
		title.setForeground(BRASS);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);

		// a thread of teal under the name, as on the clan's own pages
		JPanel thread = new JPanel();
		thread.setBackground(TEAL_D);
		thread.setAlignmentX(Component.LEFT_ALIGNMENT);
		thread.setPreferredSize(new Dimension(Integer.MAX_VALUE, 2));
		thread.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));

		rsn.setText("Not logged in");
		rsn.setFont(FontManager.getRunescapeFont());
		rsn.setForeground(BONE);
		rsn.setAlignmentX(Component.LEFT_ALIGNMENT);
		rsn.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

		status.setFont(FontManager.getRunescapeSmallFont());
		status.setAlignmentX(Component.LEFT_ALIGNMENT);

		names.add(title);
		names.add(Box.createVerticalStrut(3));
		names.add(thread);
		names.add(rsn);
		names.add(status);
		header.add(names, BorderLayout.CENTER);
		header.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		return header;
	}

	/** The name you are playing as, shown under the title. */
	/** An item's name and its two prices, as read on the client thread. */
	static final class Facts
	{
		private final String name;
		private final int ge;
		private final int ha;

		Facts(String name, int ge, int ha)
		{
			this.name = name;
			this.ge = ge;
			this.ha = ha;
		}
	}

	/** What each item is called and worth, once the plugin has looked it up. */
	void setFacts(Map<Integer, Facts> known)
	{
		// Only worth a redraw if it taught us something. Otherwise every
		// drawing of the tab caused a second one straight after it.
		boolean better = known.size() > facts.size();
		facts = known;
		if (better)
		{
			SwingUtilities.invokeLater(this::drawActivity);
		}
	}

	/** What the plugin has watched you kill since it started. */
	void setKillsSeen(Map<String, Integer> seen)
	{
		killsSeen = seen;
		SwingUtilities.invokeLater(this::drawWeek);
	}

	/**
	 * Skill and boss of the week.
	 *
	 * The standings are the board's, counted by Wise Old Man over exactly the
	 * dates staff set, so nothing is worked out here. What this adds is the
	 * part a website cannot: it knows who you are. Your row is picked out,
	 * your position is stated in words rather than left to be counted, and
	 * for the boss it says how many kills it has watched you get since you
	 * logged in, which is the one figure on the page that is live.
	 */
	private void drawWeek()
	{
		weekTab.removeAll();

		JsonObject week = object(lastClan, "week");
		boolean any = false;

		for (String[] which : new String[][]{
			{"sotw", "Skill of the week", "XP"},
			{"botw", "Boss of the week", "kills"},
		})
		{
			JsonObject one = object(week, which[0]);
			if (one == null)
			{
				continue;
			}
			any = true;
			weekTab.add(title(which[1]));

			JsonArray standings = array(one, "standings");
			// RuneLite writes names with a non breaking space between the words
			String me = rsn.getText().replace(' ', ' ').trim();
			int place = 0;
			long mine = 0;

			if (standings != null)
			{
				for (int i = 0; i < standings.size(); i++)
				{
					JsonObject row = standings.get(i).getAsJsonObject();
					if (text(row, "name").equalsIgnoreCase(me))
					{
						place = i + 1;
						mine = number(row, "gained");
						break;
					}
				}
			}

			JsonArray lines = new JsonArray();
			lines.add(orDash(text(one, "name")));
			if (!text(one, "end").isEmpty())
			{
				lines.add((has(one, "live") && one.get("live").getAsBoolean()
					? "Ends " : "Ended ") + text(one, "end"));
			}
			weekTab.add(card(lines));
			weekTab.add(Box.createVerticalStrut(6));

			// where you stand, in words
			if (place > 0)
			{
				weekTab.add(figure(QuantityFormatter.quantityToStackSize(mine) + " " + which[2],
					"You are " + ordinal(place) + " of " + standings.size()));
			}
			else if (standings != null && standings.size() > 0)
			{
				weekTab.add(hint("You are not on the board for this one yet."));
			}

			// the only live figure here: kills this client has watched
			if (which[0].equals("botw"))
			{
				int seen = killsSeen.getOrDefault(text(one, "name").toLowerCase(), 0);
				if (seen > 0)
				{
					weekTab.add(figure(String.valueOf(seen), "Watched since you logged in"));
				}
			}

			if (standings != null && standings.size() > 0)
			{
				weekTab.add(Box.createVerticalStrut(4));
				for (int i = 0; i < Math.min(10, standings.size()); i++)
				{
					JsonObject row = standings.get(i).getAsJsonObject();
					boolean you = text(row, "name").equalsIgnoreCase(me);
					weekTab.add(cells(
						(i + 1) + "  " + text(row, "name"),
						QuantityFormatter.quantityToStackSize(number(row, "gained")),
						you ? GOLD : BONE, you ? GOLD : BRASS));
				}
			}
			else
			{
				weekTab.add(hint("Nobody has gained anything yet."));
			}

			JButton open = link("Competition page", text(one, "link"));
			if (open != null)
			{
				weekTab.add(Box.createVerticalStrut(6));
				weekTab.add(open);
			}
			weekTab.add(Box.createVerticalStrut(14));
		}

		// Whatever staff have written in the admin's news box. This is the
		// page for what is going on, so it is the page their posts belong on.
		JsonArray news = array(lastClan != null ? lastClan : lastDetails, "home");
		if (news != null && news.size() > 0)
		{
			blocks(weekTab, news);
			any = true;
		}

		if (!any)
		{
			weekTab.add(title("This week"));
			weekTab.add(hint("Nothing set for this week yet. When staff pick a "
				+ "skill or a boss, or post any news, it shows here."));
		}

		weekTab.revalidate();
		weekTab.repaint();
	}

	/** 1st, 2nd, 3rd, and everything after. */
	private static String ordinal(int place)
	{
		if (place % 100 >= 11 && place % 100 <= 13)
		{
			return place + "th";
		}
		switch (place % 10)
		{
			case 1:
				return place + "st";
			case 2:
				return place + "nd";
			case 3:
				return place + "rd";
			default:
				return place + "th";
		}
	}

	/** A large brass number over a quiet line saying what it counts. */
	private static JPanel figure(String value, String caption)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, TEAL_D),
			BorderFactory.createEmptyBorder(8, 9, 9, 9)));

		JLabel big = new JLabel(value);
		big.setFont(FontManager.getRunescapeBoldFont().deriveFont(FIGURE));
		big.setForeground(BRASS);
		big.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel small = new JLabel(html(caption, CARD_WIDTH));
		small.setFont(FontManager.getRunescapeFont());
		small.setForeground(Color.GRAY);
		small.setAlignmentX(Component.LEFT_ALIGNMENT);

		panel.add(big);
		panel.add(small);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		return panel;
	}

	void setPlayer(String name)
	{
		SwingUtilities.invokeLater(() ->
		{
			rsn.setText(name);
			// RuneLite keeps settings per account, so this reads the right
			// totals back without us knowing anything about who is playing.
			load();
			drawActivity();
		});
	}

	/** Updates the connection line. */
	/**
	 * Redraws the loot tab, but not once per drop.
	 *
	 * On a busy task drops arrive faster than the panel can be rebuilt, and
	 * rebuilding it is not cheap. Doing it on every one kept the Swing thread
	 * busy enough that a click on collapse sat in the queue behind the work
	 * and felt like the button had stuck.
	 *
	 * Anything the player does themselves still redraws at once. This is only
	 * for things arriving on their own.
	 */
	private void redrawSoon()
	{
		if (settling == null)
		{
			settling = new Timer(400, e -> drawActivity());
			settling.setRepeats(false);
		}
		settling.restart();
	}

	/** Draws the tabs that read the settings directly. */
	void redraw()
	{
		SwingUtilities.invokeLater(() ->
		{
			drawActivity();
			drawWeek();
		});
	}

	void refresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			boolean ready = !config.eventUrl().trim().isEmpty();
			status.setText("\u25CF " + (ready ? "Event live" : "No live event"));
			status.setForeground(ready
				? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
		});
	}

	void setClan(@Nullable JsonObject clan)
	{
		SwingUtilities.invokeLater(() ->
		{
			lastClan = clan;
			drawHome();
			drawWeek();
			drawViews();
		});
	}

	void setEvent(@Nullable JsonObject details, @Nullable String problem)
	{
		SwingUtilities.invokeLater(() ->
		{
			this.problem = problem == null ? "" : problem;
			lastDetails = details;
			drawHome();
			drawEvent(details);
			drawViews();
			drawActivity();
		});
	}

	private void drawEvent(@Nullable JsonObject details)
	{
		eventTab.removeAll();

		String name = text(details, "event");
		if (name.isEmpty())
		{
			if (!problem.isEmpty())
			{
				eventTab.add(hint(problem));
			}
			else if (config.eventUrl().trim().isEmpty())
			{
				eventTab.add(title("No event running"));
				eventTab.add(hint("When one starts, the host gives out an address and a key. "
					+ "Put them in this plugin's settings, under Event."));
				eventTab.add(Box.createVerticalStrut(8));
				eventTab.add(hint("Hosting one yourself? See HOSTING.md in the plugin's repository."));
				JButton repo = link("Open the repository",
					"https://github.com/Hegget34/veritas-events");
				if (repo != null)
				{
					eventTab.add(repo);
				}
			}
			else
			{
				eventTab.add(hint("Connected, but this board is not reporting an event yet."));
			}
		}
		else
		{
			JLabel heading = new JLabel(name);
			heading.setFont(FontManager.getRunescapeBoldFont().deriveFont(HEADING + 1f));
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

			eventTab.add(Box.createVerticalStrut(12));
			eventTab.add(stats(details));

			JsonObject progress = object(details, "progress");
			if (progress != null && has(progress, "total"))
			{
				eventTab.add(Box.createVerticalStrut(12));
				eventTab.add(cells(orElse(text(progress, "label"), "Progress"),
					number(progress, "done") + " / " + number(progress, "total"), GOLD, Color.WHITE));
				eventTab.add(bar(progress));
			}

			blocks(eventTab, array(details, "blocks"));
		}

		teams(eventTab, details);

		eventTab.revalidate();
		eventTab.repaint();
	}

	/**
	 * Hosts a board is allowed to offer a button to.
	 *
	 * The clan's own places and the trackers it uses. A board can put a link
	 * on a page, but it cannot invent a destination: anything not on this list
	 * or typed into the settings by the player is simply not drawn.
	 */
	private static final String[] KNOWN_HOSTS = {
		"veritasclan.cc", "wiseoldman.net", "droptracker.io", "templeosrs.com",
		"discord.gg", "discord.com", "github.com", "oldschool.runescape.wiki",
	};

	/** The host of an address, lower cased, or nothing if it has none. */
	private static String hostOf(String url)
	{
		try
		{
			String host = new URI(url.trim()).getHost();
			return host == null ? "" : host.toLowerCase();
		}
		catch (Exception notAnAddress)
		{
			return "";
		}
	}

	/**
	 * Whether the player's browser may be sent to this address.
	 *
	 * Somewhere the clan already uses, or wherever the player has pointed the
	 * plugin themselves. A board answering with a link to anywhere else gets
	 * no button, so every address this plugin can open is either written here
	 * or was typed into the settings.
	 */
	private boolean mayOpen(String url)
	{
		String host = hostOf(url);
		if (host.isEmpty())
		{
			return false;
		}
		for (String known : KNOWN_HOSTS)
		{
			if (host.equals(known) || host.endsWith("." + known))
			{
				return true;
			}
		}
		for (String typed : new String[]{config.clanUrl(), config.eventUrl()})
		{
			String mine = hostOf(typed);
			if (!mine.isEmpty() && mine.equals(host))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * A board's link, as a button where we are willing to open it and as plain
	 * text where we are not.
	 *
	 * An event hosted somewhere the clan does not already use is a perfectly
	 * ordinary thing, and hiding its address to avoid opening it would lose
	 * the member the one piece of information they need. So it is written
	 * out to be read and copied instead.
	 */
	private JComponent offer(String label, String url)
	{
		JButton button = link(label, url);
		if (button != null)
		{
			return button;
		}

		JPanel told = new JPanel();
		told.setLayout(new BoxLayout(told, BoxLayout.Y_AXIS));
		told.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		told.setAlignmentX(Component.LEFT_ALIGNMENT);
		told.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, TEAL_D),
			BorderFactory.createEmptyBorder(7, 8, 8, 7)));

		JLabel what = new JLabel(html(label.isEmpty() ? "Address" : label, CARD_WIDTH));
		what.setFont(FontManager.getRunescapeBoldFont());
		what.setForeground(BONE);
		what.setAlignmentX(Component.LEFT_ALIGNMENT);
		told.add(what);

		JTextField address = new JTextField(url);
		address.setEditable(false);
		address.setFont(FontManager.getRunescapeSmallFont());
		address.setForeground(Color.GRAY);
		address.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		address.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
		address.setAlignmentX(Component.LEFT_ALIGNMENT);
		address.setCaretPosition(0);
		address.setToolTipText("Select and copy this into the Event URL setting");
		told.add(address);

		told.setMaximumSize(new Dimension(Integer.MAX_VALUE, told.getPreferredSize().height));
		return told;
	}

	/**
	 * A button that opens one of the board's links. Only ordinary web
	 * addresses are offered, only to hosts the clan already uses or the player
	 * has set, and nothing opens until the player clicks it.
	 */
	@Nullable
	private JButton link(String label, String url)
	{
		if (label.isEmpty()
			|| !(url.startsWith("https://") || url.startsWith("http://"))
			|| !mayOpen(url))
		{
			return null;
		}
		return button(label, url);
	}

	/** The button itself, once something has decided it may exist. */
	private static JButton button(String label, String url)
	{
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
			stats.add(stat("Your drops", String.valueOf(count())));
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
	private void drawViews()
	{
		String open = (String) viewSelect.getSelectedItem();

		fillingPages = true;
		viewSelect.removeAllItems();
		for (String view : VIEWS)
		{
			viewSelect.addItem(view);
		}
		for (JsonObject page : pages())
		{
			viewSelect.addItem(text(page, "name"));
		}
		if (open != null)
		{
			viewSelect.setSelectedItem(open);
		}
		fillingPages = false;

		showView();
	}

	/**
	 * One of the board's pages. A page may carry pages of its own, in which case
	 * a second chooser appears for them, so related pages can be grouped without
	 * the top chooser growing forever.
	 */
	private void drawBoardPage(int index)
	{
		openPage = index;
		pageTab.removeAll();

		JsonObject page = pageAt(index);
		JsonArray within = array(page, "pages");
		if (within != null)
		{
			String open = (String) subSelect.getSelectedItem();
			fillingPages = true;
			subSelect.removeAllItems();
			for (JsonElement element : within)
			{
				subSelect.addItem(text(element.getAsJsonObject(), "name"));
			}
			if (open != null)
			{
				subSelect.setSelectedItem(open);
			}
			fillingPages = false;

			pageTab.add(subCaption);
			pageTab.add(subSelect);
			pageTab.add(Box.createVerticalStrut(12));
		}

		pageTab.add(pageContent);
		drawPageContent();

		pageTab.revalidate();
		pageTab.repaint();
	}

	private void drawPageContent()
	{
		pageContent.removeAll();

		JsonObject page = pageAt(openPage);
		if (page == null)
		{
			pageContent.add(hint(!problem.isEmpty() ? problem
				: "This page is no longer being published."));
		}
		else
		{
			JsonArray within = array(page, "pages");
			if (within == null)
			{
				blocks(pageContent, array(page, "blocks"));
			}
			else
			{
				int chosen = Math.max(0, subSelect.getSelectedIndex());
				if (chosen < within.size())
				{
					blocks(pageContent, array(within.get(chosen).getAsJsonObject(), "blocks"));
				}
			}
		}

		pageContent.revalidate();
		pageContent.repaint();
	}

	/**
	 * Every published page: the clan's own first, then whatever the running
	 * event adds. Clan pages are there with no event set, which is the point of
	 * keeping the two addresses apart.
	 */
	private List<JsonObject> pages()
	{
		List<JsonObject> all = new ArrayList<>();
		for (JsonObject board : new JsonObject[]{lastClan, lastDetails})
		{
			JsonArray published = array(board, "pages");
			if (published != null)
			{
				for (JsonElement element : published)
				{
					all.add(element.getAsJsonObject());
				}
			}
		}
		return all;
	}

	@Nullable
	private JsonObject pageAt(int index)
	{
		List<JsonObject> all = pages();
		return index < 0 || index >= all.size() ? null : all.get(index);
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
					if (lines != null && lines.size() > 0)
					{
						into.add(card(lines));
						into.add(Box.createVerticalStrut(5));
					}
					break;

				case "stat":
					into.add(Box.createVerticalStrut(6));
					JLabel figure = line(text(block, "value"), GOLD);
					figure.setFont(FontManager.getRunescapeBoldFont().deriveFont(FIGURE));
					into.add(figure);
					into.add(line(text(block, "label"), Color.GRAY));
					into.add(Box.createVerticalStrut(6));
					break;

				case "table":
					into.add(Box.createVerticalStrut(2));
					into.add(table(block));
					into.add(Box.createVerticalStrut(6));
					break;

				case "link":
					into.add(Box.createVerticalStrut(4));
					into.add(offer(text(block, "label"), text(block, "url")));
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
		boolean stack = crowded(rows);

		if (!stack && columns != null && columns.size() > 0)
		{
			table.add(cells(lead(columns), last(columns), BLUE, BLUE));
		}

		if (rows != null)
		{
			for (JsonElement element : rows)
			{
				JsonArray row = element.getAsJsonArray();
				table.add(stack
					? stacked(lead(row), last(row))
					: cells(lead(row), last(row), Color.WHITE, GOLD));
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
	void record(String source, List<int[]> items, long value, int state, String why)
	{
		Sent one = new Sent(source, items, value, state);
		one.reason = why;
		synchronized (sent)
		{
			sent.addFirst(one);
			while (sent.size() > HISTORY)
			{
				sent.removeLast();
			}
		}

		Sent total;
		synchronized (history)
		{
			total = history.get(source);
			if (total == null)
			{
				total = one.copy();
				history.put(source, total);
			}
			else
			{
				total.merge(one);
			}
		}
		save(total);
		SwingUtilities.invokeLater(() ->
		{
			resend.setEnabled(true);
			redrawSoon();
		});
	}

	private void drawActivity()
	{
		activityTab.removeAll();

		JsonArray wanted = array(lastDetails, "wanted");
		boolean goFor = wanted != null || !text(lastDetails, "event").isEmpty();

		/*
		 * Only rebuilt when its options have actually changed. Tearing the
		 * chooser down and building it again on every redraw made collapsing
		 * a long list feel like the panel had stuck.
		 */
		if (lootSelect.getItemCount() != (goFor ? 2 : 1))
		{
			String open = (String) lootSelect.getSelectedItem();
			fillingPages = true;
			lootSelect.removeAllItems();
			lootSelect.addItem("Your loot");
			if (goFor)
			{
				lootSelect.addItem("Items to go for");
			}
			if (open != null)
			{
				lootSelect.setSelectedItem(open);
			}
			fillingPages = false;
		}

		// Worth the room only while an event is running; without one there is
		// nothing to go for and the chooser would just be in the way.
		boolean event = goFor;
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

		// Asked for after the drawing rather than during it, so one pass over
		// the list costs one request however many unknown items are in it.
		if (!toAsk.isEmpty())
		{
			List<Integer> asking = new ArrayList<>(toAsk);
			toAsk.clear();
			onFactsWanted.accept(asking);
		}
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
			boolean pending = !found && has(item, "pending") && item.get("pending").getAsBoolean();

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

			String said = found ? "Complete" : pending ? "Pending" : text(item, "note");
			JLabel note = new JLabel(said);
			note.setFont(FontManager.getRunescapeFont());
			note.setForeground(found ? ColorScheme.PROGRESS_COMPLETE_COLOR
				: pending ? ColorScheme.PROGRESS_INPROGRESS_COLOR : GOLD);
			note.setToolTipText(pending ? "Found by " + text(item, "note")
				+ ", waiting for the host to approve it" : null);
			line.add(note, BorderLayout.EAST);

			line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
			activityTab.add(line);
			activityTab.add(Box.createVerticalStrut(4));
		}
	}

	private void drawLoot()
	{
		int drops = 0;
		int sends = 0;
		long loot = 0;
		synchronized (history)
		{
			for (Sent one : history.values())
			{
				drops += one.count;
				sends += one.state == SENT ? one.count : 0;
				loot += one.value;
			}
		}

		JPanel counts = new JPanel(new GridLayout(1, 3, 4, 0));
		counts.setBackground(ColorScheme.DARK_GRAY_COLOR);
		counts.setAlignmentX(Component.LEFT_ALIGNMENT);
		// Not "kills": a barrows chest, a moon, a raid and a clue casket all
		// land here, and calling the total a kill count misreads most of them.
		counts.add(stat("Drops", String.valueOf(drops)));
		counts.add(stat("Sent", String.valueOf(sends)));
		counts.add(stat("Value", QuantityFormatter.quantityToStackSize(loot)));
		activityTab.add(counts);
		activityTab.add(Box.createVerticalStrut(8));

		resend.setForeground(resend.isEnabled() ? BONE : Color.GRAY);
		resend.setToolTipText(resend.isEnabled()
			? "Send the last thing again, if the board missed it"
			: "Nothing has been sent yet, so there is nothing to send again");

		groupButton.setIcon(bars(grouped ? 4 : 2));
		groupButton.setToolTipText(grouped ? "Show each drop separately" : "Group loot by source");
		collapseButton.setIcon(chevron(collapsed));
		collapseButton.setToolTipText(collapsed ? "Expand all" : "Collapse all");

		// Send again belongs beside the two it works with, not on a row below
		JPanel switches = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		switches.setBackground(ColorScheme.DARK_GRAY_COLOR);
		switches.add(groupButton);
		switches.add(collapseButton);
		switches.add(clearButton);

		JPanel tools = new JPanel(new BorderLayout(5, 0));
		tools.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tools.setAlignmentX(Component.LEFT_ALIGNMENT);
		tools.add(switches, BorderLayout.WEST);
		tools.add(resend, BorderLayout.CENTER);
		tools.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		activityTab.add(tools);
		activityTab.add(Box.createVerticalStrut(10));

		List<Sent> entries = entries();
		if (entries.isEmpty())
		{
			activityTab.add(hint(lootTrackerOff.getAsBoolean()
				? "No drops yet. Everything is tracked either way, but chest and raid "
					+ "loot needs RuneLite's own Loot Tracker plugin, which is off."
				: "No drops yet."));
			return;
		}
		/*
		 * Only the newest few are drawn.
		 *
		 * Listing every drop separately meant a panel and a set of item icons
		 * for each of up to five hundred of them, which takes long enough on
		 * the Swing thread that the button looks broken. Nobody scrolls five
		 * hundred entries in a sidebar, and the totals above still count all
		 * of them.
		 */
		int drawn = 0;
		for (Sent entry : entries)
		{
			if (drawn++ >= MOST_SHOWN)
			{
				break;
			}
			activityTab.add(box(entry));
			activityTab.add(Box.createVerticalStrut(6));
		}

		if (entries.size() > MOST_SHOWN)
		{
			activityTab.add(hint("Showing the newest " + MOST_SHOWN + " of "
				+ entries.size() + ". The totals above count all of them."));
		}
	}

	/**
	 * Forgets a source, or everything.
	 *
	 * Clears what is on screen and what is written to the profile, because
	 * clearing only the first means it all returns at the next login.
	 */
	private void forget(@Nullable String source)
	{
		synchronized (history)
		{
			if (source == null)
			{
				for (String held : new ArrayList<>(history.keySet()))
				{
					unsave(held);
				}
				history.clear();
			}
			else
			{
				history.remove(source);
				unsave(source);
			}
		}

		synchronized (sent)
		{
			if (source == null)
			{
				sent.clear();
			}
			else
			{
				sent.removeIf((one) -> one.source.equals(source));
			}
		}

		resend.setEnabled(false);
		drawActivity();
	}

	/** Removes one source's total from the profile it was written to. */
	private void unsave(String source)
	{
		try
		{
			configManager.unsetRSProfileConfiguration(
				VeritasEventsConfig.GROUP, DROPS + source);
		}
		catch (Exception e)
		{
			log.debug("could not clear the total for {}", source, e);
		}
	}

	/** Asks first. Months of tracking is not something to lose to a stray click. */
	private void confirmForget(@Nullable String source)
	{
		int answer = JOptionPane.showConfirmDialog(this,
			source == null
				? "Clear every drop and every total from this tracker?"
				: "Clear everything recorded for " + source + "?",
			"Veritas", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

		if (answer == JOptionPane.YES_OPTION)
		{
			forget(source);
		}
	}

	/** How many drops are on record. */
	private int count()
	{
		synchronized (history)
		{
			int total = 0;
			for (Sent one : history.values())
			{
				total += one.count;
			}
			return total;
		}
	}

	/**
	 * Reads this account's totals back.
	 *
	 * They live in RuneLite's own per account settings, which is where its loot
	 * tracker keeps its own. Two accounts on one machine stay apart without us
	 * doing anything, and anyone signed in to a RuneLite account finds their
	 * totals waiting on another computer, because RuneLite syncs its settings.
	 */
	private void load()
	{
		String profile = configManager.getRSProfileKey();
		if (profile == null)
		{
			return;
		}

		synchronized (history)
		{
			history.clear();
			for (String full : configManager.getRSProfileConfigurationKeys(
				VeritasEventsConfig.GROUP, profile, DROPS))
			{
				String key = full.substring(full.lastIndexOf(DROPS));
				try
				{
					Sent one = gson.fromJson(
						configManager.getConfiguration(VeritasEventsConfig.GROUP, profile, key),
						Sent.class);
					if (one != null && one.source != null && one.items != null)
					{
						history.put(one.source, one);
					}
				}
				catch (Exception e)
				{
					log.debug("could not read {}", key, e);
				}
			}
		}
	}

	/** Writes one source's running total back. */
	private void save(Sent total)
	{
		try
		{
			configManager.setRSProfileConfiguration(
				VeritasEventsConfig.GROUP, DROPS + total.source, gson.toJson(total));
		}
		catch (Exception e)
		{
			log.debug("could not write the total for {}", total.source, e);
		}
	}

	/**
	 * Grouped shows every session added up per source; ungrouped shows this
	 * session kill by kill. Only the totals are kept, so older kills are only
	 * ever available added up, the same way RuneLite's loot tracker works.
	 */
	private List<Sent> entries()
	{
		if (!grouped)
		{
			synchronized (sent)
			{
				return new ArrayList<>(sent);
			}
		}
		synchronized (history)
		{
			// What you just killed belongs at the top. Sorted by total value,
			// the kill you are looking for sat wherever its running total
			// happened to fall, which is never where you look first.
			List<Sent> all = new ArrayList<>(history.values());
			all.sort((a, b) -> Long.compare(b.at, a.at));
			return all;
		}
	}

	/** One sent drop: source and value on top, item icons underneath. */
	private JPanel box(Sent entry)
	{
		JPanel box = new JPanel(new BorderLayout());
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setAlignmentX(Component.LEFT_ALIGNMENT);

		// right click one source to forget just that one
		JPopupMenu justThis = new JPopupMenu();
		JMenuItem clearOne = new JMenuItem("Reset " + entry.source);
		clearOne.addActionListener(e -> confirmForget(entry.source));
		justThis.add(clearOne);
		box.setComponentPopupMenu(justThis);
		box.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0,
				entry.state == FAILED ? ColorScheme.PROGRESS_ERROR_COLOR
					: entry.state == SENT ? TEAL_D : ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(4, 6, 5, 6)));

		JPanel top = new JPanel(new BorderLayout(8, 0));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		top.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, TEAL_D),
			BorderFactory.createEmptyBorder(4, 5, 4, 5)));

		/*
		 * The monster on its own line and the count on the next, beside the
		 * value. Hung off the end of the name, a long one pushed the count out
		 * of sight, and the count is the part you came to read.
		 */
		JPanel head = new JPanel();
		head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
		head.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel source = new JLabel(entry.source);
		source.setFont(FontManager.getRunescapeBoldFont());
		source.setForeground(entry.state == FAILED ? ColorScheme.PROGRESS_ERROR_COLOR : BONE);
		source.setToolTipText(entry.source);
		source.setAlignmentX(Component.LEFT_ALIGNMENT);
		head.add(source);

		JPanel tally = new JPanel(new BorderLayout(8, 0));
		tally.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tally.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel count = new JLabel(String.valueOf(entry.count));
		count.setFont(FontManager.getRunescapeFont());
		count.setForeground(Color.GRAY);
		tally.add(count, BorderLayout.WEST);

		if (entry.value > 0)
		{
			JLabel value = new JLabel(QuantityFormatter.quantityToStackSize(entry.value) + " gp");
			value.setFont(FontManager.getRunescapeBoldFont());
			value.setForeground(entry.value >= config.bigDropValue() ? GOLD : BRASS);
			tally.add(value, BorderLayout.EAST);
		}
		tally.setMaximumSize(new Dimension(Integer.MAX_VALUE, tally.getPreferredSize().height));
		head.add(tally);

		top.add(head, BorderLayout.CENTER);
		box.add(top, BorderLayout.NORTH);

		if (!entry.items.isEmpty() && !collapsed)
		{
			/*
			 * A grid, because a FlowLayout claims everything fits on one line
			 * however many things it holds. The box was sized from that claim
			 * and clipped the rest, so a kill with more than five distinct
			 * drops appeared to have lost them.
			 */
			/*
			 * One pixel gaps over a lighter background, so what shows through
			 * between the cells is a grid line. Drawing borders on the labels
			 * themselves would double them up wherever two cells meet.
			 */
			int rows = (entry.items.size() + ICON_COLUMNS - 1) / ICON_COLUMNS;
			JPanel icons = new JPanel(new GridLayout(rows, ICON_COLUMNS, 1, 1));
			icons.setBackground(GRID);
			icons.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, GRID));

			for (int[] item : entry.items)
			{
				JLabel icon = new JLabel();
				// taller than the 36 by 32 artwork, so it is not pressed
				// against the rules above and below it
				icon.setPreferredSize(new Dimension(38, 38));
				icon.setToolTipText(describe(item[0], item[1]));
				icon.setVerticalAlignment(SwingConstants.CENTER);
				icon.setHorizontalAlignment(SwingConstants.CENTER);
				icon.setOpaque(true);
				icon.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				// No name in the tooltip: reading an item's composition has to
				// happen on the client thread, and this is the Swing one.
				// Asking for it here threw, which took the whole tab with it.
				itemManager.getImage(item[0], item[1], item[1] > 1).addTo(icon);
				icons.add(icon);
			}

			// the last row is padded so the grid does not stretch what is in it
			for (int spare = rows * ICON_COLUMNS - entry.items.size(); spare > 0; spare--)
			{
				JLabel blank = new JLabel();
				blank.setOpaque(true);
				blank.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				icons.add(blank);
			}
			box.add(icons, BorderLayout.CENTER);
		}

		/*
		 * A colour down the side is not a meaning. Every box that is out of the
		 * ordinary says in words what became of it, in the same colour as its
		 * bar, and the ordinary case says nothing at all and has no bar.
		 */
		if (!entry.reason.trim().isEmpty())
		{
			JLabel why = line(html(entry.reason, CARD_WIDTH),
				entry.state == FAILED ? ColorScheme.PROGRESS_ERROR_COLOR
					: entry.state == SENT ? TEAL : Color.GRAY);
			why.setBorder(BorderFactory.createEmptyBorder(4, 1, 1, 1));
			box.add(why, BorderLayout.SOUTH);
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

	/**
	 * A figure over a caption, the three across the top of the loot tracker.
	 *
	 * Brass on a teal underline, so the totals read as the clan's rather than
	 * as three more grey boxes.
	 */
	private static JPanel stat(String caption, String value)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 2, 0, TEAL_D),
			BorderFactory.createEmptyBorder(6, 4, 5, 4)));

		JLabel top = new JLabel(value, SwingConstants.CENTER);
		top.setFont(FontManager.getRunescapeBoldFont());
		top.setForeground(BRASS);
		top.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel bottom = new JLabel(caption.toUpperCase(), SwingConstants.CENTER);
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

	/**
	 * One full width line: something on the left, something on the right.
	 *
	 * The name goes in the centre rather than the west so that a long one is
	 * squeezed and ends in an ellipsis. In the west it kept its full width and
	 * BorderLayout let it run underneath the figure, which is why long rows
	 * were printed on top of each other.
	 */
	private static JPanel cells(String left, String right, Color leftColour, Color rightColour)
	{
		JPanel panel = new JPanel(new BorderLayout(8, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel name = new JLabel(left);
		name.setFont(FontManager.getRunescapeFont());
		name.setForeground(leftColour);
		name.setToolTipText(left);
		name.setMinimumSize(new Dimension(20, 1));
		panel.add(name, BorderLayout.CENTER);

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

	/**
	 * A section heading: brass over a teal rule, the way the clan site marks
	 * one. Plain white text gave no sense of where a section started.
	 */
	private static JPanel title(String text)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 7, 0));

		JLabel label = new JLabel(html(text, TEXT_WIDTH));
		label.setFont(FontManager.getRunescapeBoldFont().deriveFont(HEADING));
		label.setForeground(BRASS);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel underline = new JPanel();
		underline.setBackground(TEAL_D);
		underline.setAlignmentX(Component.LEFT_ALIGNMENT);
		underline.setPreferredSize(new Dimension(Integer.MAX_VALUE, 2));
		underline.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));

		panel.add(label);
		panel.add(Box.createVerticalStrut(4));
		panel.add(underline);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		return panel;
	}

	/**
	 * A stacked row: who on top, what underneath, wrapped.
	 *
	 * For tables whose second column is a sentence rather than a figure. Side
	 * by side there is not room for both in a sidebar, and shortening either
	 * one loses the part that matters.
	 */
	private static JPanel stacked(String top, String bottom)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(6, 7, 7, 7)));

		JLabel who = new JLabel(html(top, CARD_WIDTH));
		who.setFont(FontManager.getRunescapeBoldFont());
		who.setForeground(BONE);
		who.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(who);

		if (!bottom.isEmpty())
		{
			JLabel what = new JLabel(html(bottom, CARD_WIDTH));
			what.setFont(FontManager.getRunescapeFont());
			what.setForeground(BRASS);
			what.setAlignmentX(Component.LEFT_ALIGNMENT);
			what.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
			panel.add(what);
		}

		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		return panel;
	}

	/**
	 * Loose lines gathered into a card with an accent down the side, so one
	 * notice or one event is plainly separate from the next. The first line is
	 * the thing itself and the rest are its details.
	 */
	private static JPanel card(JsonArray lines)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, TEAL_D),
			BorderFactory.createEmptyBorder(7, 8, 8, 7)));

		boolean first = true;
		for (JsonElement entry : lines)
		{
			JLabel label = new JLabel(html(entry.getAsString(), CARD_WIDTH));
			label.setFont(first ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeFont());
			label.setForeground(first ? BONE : Color.GRAY);
			label.setAlignmentX(Component.LEFT_ALIGNMENT);
			label.setBorder(BorderFactory.createEmptyBorder(first ? 0 : 3, 0, 0, 0));
			panel.add(label);
			first = false;
		}

		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		return panel;
	}

	/**
	 * Whether a table's rows are too wide to sit side by side.
	 *
	 * Measured in the font they will be drawn in rather than guessed at, and a
	 * single row that does not fit stacks the whole table, because a list that
	 * changes shape halfway down is worse than either form.
	 */
	private static boolean crowded(@Nullable JsonArray rows)
	{
		if (rows == null)
		{
			return false;
		}

		FontMetrics metrics = new JLabel().getFontMetrics(FontManager.getRunescapeFont());
		int room = PluginPanel.PANEL_WIDTH - 60;

		for (JsonElement element : rows)
		{
			JsonArray row = element.getAsJsonArray();
			String right = last(row);
			if (right.isEmpty() || figure(right))
			{
				continue;
			}
			if (metrics.stringWidth(lead(row)) + metrics.stringWidth(right) + 14 > room)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * What to show when the pointer rests on an item.
	 *
	 * Nothing at all until the plugin has told us about that id, because the
	 * alternative is reading it here, on the Swing thread, which is not
	 * allowed and took the whole tab down when it was tried.
	 */
	@Nullable
	private String describe(int id, int quantity)
	{
		Facts known = facts.get(id);
		if (known == null)
		{
			if (askedAbout.add(id))
			{
				toAsk.add(id);
			}
			return null;
		}

		StringBuilder out = new StringBuilder("<html>");
		out.append(escape(known.name));
		if (quantity > 1)
		{
			out.append(" x ").append(String.format("%,d", quantity));
		}
		out.append("<br>GE: ").append(worth(known.ge, quantity));
		out.append("<br>HA: ").append(worth(known.ha, quantity));
		return out.append("</html>").toString();
	}

	/** "9,315 (23 ea)", or the one figure when there is only one of them. */
	private static String worth(int each, int quantity)
	{
		String total = String.format("%,d", (long) each * quantity);
		return quantity > 1 ? total + " (" + String.format("%,d", each) + " ea)" : total;
	}

	/** So an item with a bracket in its name does not break the tooltip. */
	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Whether a value is a figure, as against prose: 500, 12.4m, 1,204, 93%. */
	private static boolean figure(String value)
	{
		return value.length() <= 12 && value.matches("(?i)[0-9][0-9.,:%kmb+\\-]*");
	}

	/**
	 * Text in a label that wraps at a given width.
	 *
	 * Swing labels do not wrap on their own, and the width has to be stated.
	 * It used to be written in as 200, which is wider than the sidebar, so
	 * every wrapped line was cut off on the right.
	 */
	private static String html(String text, int width)
	{
		String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		return "<html><body style='width:" + width + "px'>" + safe + "</body></html>";
	}

	private static JLabel line(String text, Color colour)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeFont());
		label.setForeground(colour);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		return label;
	}

	/** Wrapped grey text, for the empty states. */
	private static JLabel hint(String text)
	{
		JLabel label = line(html(text, TEXT_WIDTH), Color.GRAY);
		label.setBorder(BorderFactory.createEmptyBorder(4, 0, 6, 0));
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

		/** Why it was sent, kept or refused, in words the player can act on. */
		private String reason = "";

		/**
		 * When this source was last seen, in seconds.
		 *
		 * Kept so the list can be ordered by what happened most recently. It
		 * is written out with the rest, so the order survives a restart.
		 */
		private long at;

		Sent(String source, List<int[]> items, long value, int state)
		{
			this.source = source;
			this.items = items;
			this.value = value;
			this.state = state;
			this.count = 1;
			this.at = System.currentTimeMillis() / 1000L;
		}

		/** A copy that can be merged into without touching what was recorded. */
		Sent copy()
		{
			List<int[]> copied = new ArrayList<>();
			for (int[] item : items)
			{
				copied.add(new int[]{item[0], item[1]});
			}
			Sent one = new Sent(source, copied, value, state);
			one.reason = reason;
			one.at = at;
			return one;
		}

		void merge(Sent other)
		{
			count += other.count;
			value += other.value;
			state = Math.max(state, other.state);
			at = Math.max(at, other.at);
			if (!other.reason.isEmpty())
			{
				reason = other.reason;
			}
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
