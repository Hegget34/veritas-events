/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events;

import com.veritas.events.party.PartyHub;
import com.veritas.events.party.PartyMate;
import com.veritas.events.party.PrayerIcons;
import com.veritas.events.party.SkillIcons;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * The party, drawn.
 *
 * What this is for is one glance mid raid: has he got brews left, is she on
 * Rigour, did anyone's overload lapse. So the figures that answer those are
 * never more than one click away, the inventory is the tab a box opens on,
 * and the grid is laid out the way the game lays it out so things are where
 * the eye already expects them.
 *
 * <h2>Why nothing is rebuilt that has not changed</h2>
 *
 * An item's picture is made per quantity, since the number is drawn onto
 * it, so a party of five eating and drinking produces a steady stream of
 * pictures nothing has cached. Rebuilding every box on every tick made the
 * whole panel flicker, which in the middle of a raid is worse than useless.
 * Each box therefore carries a stamp of everything it shows, and a box
 * whose stamp has not moved is the same component as last time.
 */
class VeritasPartyPanel extends JPanel
{
	/** The inventory is four across, so this is too, and things sit where they do in game. */
	private static final int CARRIED_COLUMNS = 4;
	private static final int PRAYER_COLUMNS = 5;

	/** Stats go three across, as the game's own tab does. */
	private static final int LEVEL_COLUMNS = 3;

	/**
	 * Room for a tab picture, the rule that marks the open one, and air.
	 *
	 * Thirty for the picture, four for the border, and three above and below
	 * so the icon sits in the tab rather than against the edges of it.
	 */
	private static final int TAB_HEIGHT = 40;

	/**
	 * How big one slot is.
	 *
	 * Square, and the size the game draws an item at with room around it.
	 * It was smaller and flatter than a square, which packed more card onto
	 * the screen and made every grid harder to read than the inventory it
	 * was standing in for.
	 */
	private static final int CELL = 40;

	/**
	 * How tall the open part of a card is, whichever tab is showing.
	 *
	 * Every tab holds a different number of rows, so left to themselves they
	 * came out different heights and the whole list jumped every time
	 * somebody flicked a tab. They are all drawn into this instead, and each
	 * one divides it between the rows it has, so the inventory's seven and
	 * the gear's five fill the same space at different sizes.
	 *
	 * Seven rows of inventory, which is the one nobody would want smaller.
	 */
	private static final int CONTENT = 7 * CELL;

	/** The total level bar under the stats tab, which comes out of CONTENT. */
	private static final int TOTAL_BAR = 20;

	/**
	 * The one gap this panel uses.
	 *
	 * Between cards, inside cards, under headings. A list looks even
	 * because it is even, and the quickest way to stop it being even is to
	 * pick a number separately in each place it is needed.
	 */
	private static final int GAP = 6;

	/**
	 * Worn gear in the shape the equipment screen uses, by slot number.
	 *
	 * Reading a kit is a matter of pattern as much as detail, and the pattern
	 * people know is this one. -1 is a gap rather than an empty slot.
	 */
	private static final int[] WORN_SHAPE = {
		-1, 0, -1,
		1, 2, 13,
		3, 4, 5,
		-1, 7, -1,
		9, 10, 12,
	};

	/**
	 * The four tabs, as the game's own icons.
	 *
	 * Words took more room than the panel had and read as a sentence rather
	 * than as a row of choices. These are the same pictures that are down the
	 * side of the game window, so nobody has to learn what they mean.
	 */
	private static final int[] TAB_SPRITES = {
		SpriteID.TAB_INVENTORY,
		SpriteID.TAB_EQUIPMENT,
		SpriteID.TAB_PRAYER,
		SpriteID.TAB_STATS,
	};

	private static final String[] TAB_NAMES = {"Carried", "Worn", "Prayers", "Levels"};

	/**
	 * The four figures, as the orbs they sit beside in the game.
	 *
	 * A word spelt out says what the number is; the orb says it faster, and
	 * it is the one already in the corner of everyone's eye.
	 */
	private static final int[] ORB_ICONS = {
		SpriteID.MINIMAP_ORB_HITPOINTS_ICON,
		SpriteID.MINIMAP_ORB_PRAYER_ICON,
		SpriteID.MINIMAP_ORB_RUN_ICON,
		SpriteID.MINIMAP_ORB_SPECIAL_ICON,
	};

	/** Standard, ancient, lunar, arceuus, in the order the game numbers them. */
	private static final int[] SPELLBOOK_ICONS = {
		SpriteID.TAB_MAGIC,
		SpriteID.TAB_MAGIC_SPELLBOOK_ANCIENT_MAGICKS,
		SpriteID.TAB_MAGIC_SPELLBOOK_LUNAR,
		SpriteID.TAB_MAGIC_SPELLBOOK_ARCEUUS,
	};

	private static final String[] SPELLBOOK_NAMES =
		{"Standard spellbook", "Ancient magicks", "Lunar spellbook", "Arceuus spellbook"};

	private final PartyHub hub;
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;

	/** What an item id is called, and how to ask when it is not known yet. */
	private final IntFunction<String> nameOf;
	private final Consumer<List<Integer>> onNamesWanted;

	/** Ids already asked about, so one that cannot be read is asked once. */
	private final Set<Integer> asked = new LinkedHashSet<>();

	/** Ids noticed while drawing, sent off in one go once the drawing is done. */
	private final Set<Integer> toAsk = new LinkedHashSet<>();

	private final JPanel controls = VeritasEventsPanel.column();
	private final JPanel members = VeritasEventsPanel.column();

	/** Which tab each person's box is open on, and whether it is folded away. */
	private final Map<Long, Integer> openTab = new HashMap<>();
	private final Map<Long, Boolean> folded = new HashMap<>();

	/** Last draw's boxes, so an unchanged one is not built again. */
	private Map<String, JPanel> lastBoxes = new HashMap<>();

	/** Waits for updates to stop arriving before redrawing. */
	private Timer settling;

	VeritasPartyPanel(PartyHub hub, ItemManager itemManager, SpriteManager spriteManager,
		IntFunction<String> nameOf, Consumer<List<Integer>> onNamesWanted)
	{
		this.hub = hub;
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
		this.nameOf = nameOf;
		this.onNamesWanted = onNamesWanted;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		controls.setAlignmentX(Component.LEFT_ALIGNMENT);
		members.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(controls);
		add(Box.createVerticalStrut(12));
		add(members);

		draw();
	}

	/**
	 * Redraws, at most this often.
	 *
	 * Five people in a raid produce updates faster than a panel can sensibly
	 * be rebuilt. Anything the person themselves clicks still redraws at
	 * once; this is only for what turns up on its own.
	 *
	 * The wait is left to run rather than restarted. Restarting it waits for
	 * a quiet moment, and a party in the middle of a fight does not have
	 * one: every update pushed the redraw further out and the figures sat
	 * still exactly when they were worth watching. Letting it run puts a
	 * floor under how often the panel is rebuilt instead of a condition on
	 * whether it ever is.
	 */
	void drawSoon()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (settling == null)
			{
				settling = new Timer(250, e -> draw());
				settling.setRepeats(false);
			}
			if (!settling.isRunning())
			{
				settling.start();
			}
		});
	}

	void draw()
	{
		drawControls();
		drawMembers();
		revalidate();
		repaint();

		/*
		 * Whatever turned up unnamed, asked for in one go.
		 *
		 * Reading what an item is called has to happen on the client thread,
		 * so it cannot be done while drawing. A party of five carrying forty
		 * different things would be forty trips over there if each cell asked
		 * for itself; this is one, and the tooltips appear on the next draw.
		 */
		if (!toAsk.isEmpty())
		{
			List<Integer> wanted = new ArrayList<>(toAsk);
			toAsk.clear();
			onNamesWanted.accept(wanted);
		}
	}

	// ------------------------------------------------- joining and leaving ---

	/**
	 * The three buttons, or the two.
	 *
	 * Deliberately the same three everybody already knows from the other
	 * party plugins, in the same order, because somebody moving over to this
	 * should not have to work out where anything went.
	 */
	private void drawControls()
	{
		controls.removeAll();

		if (!hub.isInParty())
		{
			JButton create = narrow("Create party");
			create.setToolTipText("Start one and hand the phrase around. "
				+ "Only people running Veritas can join it");
			create.addActionListener(e -> createParty());

			JButton join = narrow("Join party");
			join.setToolTipText("Type in a phrase somebody gave you");
			join.addActionListener(e -> joinParty());
			controls.add(pair(create, join));

			String before = hub.lastParty();
			if (!before.isEmpty())
			{
				controls.add(Box.createVerticalStrut(4));
				JButton again = wide("Join previous party");
				again.setToolTipText("Back into " + before);
				again.addActionListener(e ->
				{
					hub.join(before);
					draw();
				});
				controls.add(again);
			}
			return;
		}

		/*
		 * No passphrase block here any more.
		 *
		 * It is on the Party heading below, and one string printed twice on
		 * one panel is one of them wasted.
		 */
		JButton copy = narrow("Copy passphrase");
		copy.addActionListener(e ->
		{
			Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(hub.passphrase()), null);
			copy.setText("Copied");
			Timer back = new Timer(1200, ev -> copy.setText("Copy passphrase"));
			back.setRepeats(false);
			back.start();
		});
		JButton leave = narrow("Leave party");
		leave.addActionListener(e ->
		{
			hub.leave();
			draw();
		});

		/*
		 * Side by side rather than stacked.
		 *
		 * Every row above the party is a row of party nobody can see, and
		 * eight people is already more scrolling than anybody wants. Two
		 * short labels fit across the panel, so they go across it.
		 */
		controls.add(pair(leave, copy));
	}

	/** Two buttons across the panel, sharing the width evenly. */
	private static JPanel pair(JButton left, JButton right)
	{
		JPanel row = new JPanel(new GridLayout(1, 2, 4, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		row.add(left);
		row.add(right);
		return row;
	}

	/** A button meant to sit beside another, so it does not claim the row. */
	private static JButton narrow(String text)
	{
		JButton button = wide(text);
		button.setMaximumSize(null);
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 1, 1, 1, VeritasEventsPanel.TEAL_D),
			BorderFactory.createEmptyBorder(4, 2, 4, 2)));
		return button;
	}

	/**
	 * Starts a party, on a phrase of your choosing.
	 *
	 * Offered already filled in with a generated one, because most of the
	 * time any phrase will do and the quickest answer is to press return.
	 * Anyone who wants their own simply types over it.
	 */
	private void createParty()
	{
		String suggested = hub.makePassphrase();
		Object answer = JOptionPane.showInputDialog(this,
			"Create a passphrase",
			"Veritas party", JOptionPane.PLAIN_MESSAGE, null, null, suggested);

		if (answer == null)
		{
			return;
		}
		String chosen = String.valueOf(answer).trim();
		if (chosen.isEmpty())
		{
			chosen = suggested;
		}
		hub.join(chosen);
		draw();
	}

	private void joinParty()
	{
		Object answer = JOptionPane.showInputDialog(this,
			"The passphrase you were given.",
			"Join a party", JOptionPane.PLAIN_MESSAGE, null, null, hub.lastParty());

		if (answer == null)
		{
			return;
		}
		String said = String.valueOf(answer).trim();
		if (said.isEmpty())
		{
			return;
		}
		hub.join(said);
		draw();
	}

	// -------------------------------------------------------- the people ----

	private void drawMembers()
	{
		members.removeAll();

		Map<String, JPanel> spare = lastBoxes;
		lastBoxes = new HashMap<>();

		/*
		 * Only the people actually playing.
		 *
		 * Somebody logged out has no supplies worth checking and no bars
		 * worth reading, so a card for them is a card in the way of the ones
		 * that matter. They come back the moment they log in.
		 */
		List<PartyMate> all = new ArrayList<>();
		for (PartyMate mate : hub.mates())
		{
			if (mate.isLoggedIn() && mate.heardFrom())
			{
				all.add(mate);
			}
		}

		if (all.isEmpty())
		{
			if (hub.isInParty())
			{
				members.add(heading("Party"));
				members.add(Box.createVerticalStrut(6));
				members.add(VeritasEventsPanel.line(VeritasEventsPanel.html(
					"Nobody else yet. The phrase above is what they need.",
					VeritasEventsPanel.TEXT_WIDTH), Color.GRAY));
			}
			return;
		}

		members.add(heading("Party", hub.passphrase()));
		members.add(Box.createVerticalStrut(GAP));

		for (PartyMate mate : all)
		{
			members.add(kept(mate, spare));
			members.add(Box.createVerticalStrut(GAP));
		}
	}

	/** The box for one person, built only if last draw's will no longer do. */
	private JPanel kept(PartyMate mate, Map<String, JPanel> spare)
	{
		int tab = openTab.getOrDefault(mate.getMemberId(), 0);
		boolean shut = folded.getOrDefault(mate.getMemberId(), false);
		String stamp = mate.stamp(shut ? -1 : tab) + "|" + shut;

		JPanel had = spare.remove(stamp);
		if (had == null)
		{
			had = box(mate, tab, shut);
		}
		lastBoxes.put(stamp, had);
		return had;
	}

	/**
	 * One person's card.
	 *
	 * Framed all the way round rather than only down one edge. A row of
	 * boxes that share a background runs together into one grey column, and
	 * the thing being read here is one person at a time.
	 */
	private JPanel box(PartyMate mate, int tab, boolean shut)
	{
		JPanel box = new JPanel(new BorderLayout());
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 3, 1, 1,
				mate.isLoggedIn() ? VeritasEventsPanel.TEAL_D : ColorScheme.DARK_GRAY_COLOR),
			/*
			 * Thin at the sides, ordinary top and bottom.
			 *
			 * A panel is 225 across and a scrollbar takes 17 of them, so
			 * every pixel spent on a margin here is a pixel the grids do
			 * not get. Down the sides it buys nothing: the card already has
			 * a coloured edge saying where it starts.
			 */
			BorderFactory.createEmptyBorder(GAP, 2, GAP, 2)));

		box.add(head(mate, shut), BorderLayout.NORTH);

		/*
		 * The four figures survive folding; the tabs and the grid do not.
		 *
		 * Folding a card is for getting the inventories out of the way, not
		 * for hiding whether somebody is about to die. A folded party is
		 * still a party you can scan in one look, which is the whole reason
		 * anybody folds it.
		 */
		JPanel body = VeritasEventsPanel.column();
		body.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(bars(mate));

		if (!shut)
		{
			body.add(Box.createVerticalStrut(GAP));
			body.add(tabs(mate, tab));
			body.add(Box.createVerticalStrut(GAP - 2));
			body.add(contents(mate, tab));
		}

		box.add(body, BorderLayout.CENTER);
		return box;
	}

	/** Name, combat level and world, with the fold arrow on the end. */
	private JPanel head(PartyMate mate, boolean shut)
	{
		JPanel names = VeritasEventsPanel.column();
		names.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		String who = mate.getName().isEmpty() ? "Someone" : mate.getName();

		JLabel name = new JLabel(who);
		// a size up on everything around it, because the name is what tells
		// you whose supplies you are looking at
		name.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
		name.setForeground(mate.isLoggedIn() ? VeritasEventsPanel.BONE : Color.GRAY);
		name.setToolTipText(who);

		/*
		 * The combat level, in its own label rather than on the end of the
		 * name. Sharing a label meant sharing a size, and a parenthetical
		 * set as large as the name it follows reads as part of it.
		 */
		JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		line.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		line.setAlignmentX(Component.LEFT_ALIGNMENT);
		line.add(name);

		if (mate.getCombat() > 0)
		{
			JLabel level = new JLabel("(" + mate.getCombat() + ")");
			// one size under the name and the same color as it: a
			// parenthetical rather than a different kind of thing
			level.setFont(FontManager.getRunescapeFont().deriveFont(16f));
			level.setForeground(mate.isLoggedIn() ? VeritasEventsPanel.BONE : Color.GRAY);
			level.setToolTipText("Combat level");
			line.add(level);
		}
		names.add(line);

		JLabel where = new JLabel(!mate.isLoggedIn() ? "Offline"
			: mate.getWorld() > 0 ? "World " + mate.getWorld() : "");
		// the ordinary font, not the small one shrunk. RuneLite's small font
		// is a different typeface at the same point size, so deriving it down
		// made this smaller than it started rather than larger.
		where.setFont(FontManager.getRunescapeFont());
		where.setForeground(Color.GRAY);
		where.setAlignmentX(Component.LEFT_ALIGNMENT);
		names.add(where);

		JLabel mark = new JLabel(shut ? "▸" : "▾");
		mark.setFont(FontManager.getRunescapeBoldFont());
		mark.setForeground(VeritasEventsPanel.BRASS);

		/*
		 * Which spellbook, beside the fold arrow.
		 *
		 * Up here rather than inside a tab because it decides what somebody
		 * can do for you: whether there is a vengeance coming, whether they
		 * can barrage. That is worth knowing without opening anything.
		 */
		JPanel end = new JPanel(new BorderLayout(4, 0));
		end.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		int book = mate.getSpellbook();
		if (book >= 0 && book < SPELLBOOK_ICONS.length)
		{
			JLabel spellbook = new JLabel();
			spellbook.setPreferredSize(new Dimension(20, 20));
			spellbook.setHorizontalAlignment(SwingConstants.CENTER);
			spellbook.setToolTipText(SPELLBOOK_NAMES[book]);
			spriteManager.addSpriteTo(spellbook, SPELLBOOK_ICONS[book], 0);
			end.add(spellbook, BorderLayout.CENTER);
		}
		end.add(mark, BorderLayout.EAST);

		JPanel head = new JPanel(new BorderLayout(6, 0));
		head.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		head.setBorder(BorderFactory.createEmptyBorder(0, 0, GAP - 1, 0));
		head.add(names, BorderLayout.CENTER);
		head.add(end, BorderLayout.EAST);
		head.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
		head.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				folded.put(mate.getMemberId(), !shut);
				draw();
			}
		});
		return head;
	}

	/**
	 * The four numbers that decide whether somebody is about to die.
	 *
	 * Spelt out rather than drawn as orbs: at this width a number is quicker
	 * to read than a part filled circle, and it does not need a picture
	 * fetched before it means anything.
	 */
	private JPanel bars(PartyMate mate)
	{
		/*
		 * One row, packed from the left.
		 *
		 * As a four column grid each figure sat in a cell a quarter of the
		 * panel wide with its number adrift in the middle of it, and the
		 * band of empty space that left pushed every card apart. Flowed
		 * instead, they sit together and the card closes up around them.
		 */
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

		row.add(figure(0, "Health", mate.getHealth(), mate.getHealthMax(),
			new Color(0xE0, 0x6A, 0x6A)));
		row.add(figure(1, "Prayer", mate.getPrayer(), mate.getPrayerMax(),
			new Color(0x8F, 0xCF, 0xE8)));
		row.add(figure(2, "Run energy", mate.getRun(), 100, new Color(0xE8, 0xD0, 0x70)));
		row.add(figure(3, "Special attack", mate.getSpec(), 100, new Color(0x9C, 0xD8, 0x7F)));
		return row;
	}

	/** One orb and its number, side by side the way the game has them. */
	private JPanel figure(int which, String what, int now, int most, Color color)
	{
		JPanel one = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
		one.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel orb = new JLabel();
		orb.setHorizontalAlignment(SwingConstants.CENTER);
		orb.setPreferredSize(new Dimension(14, 20));
		spriteManager.addSpriteTo(orb, ORB_ICONS[which], 0);

		JLabel value = new JLabel(now < 0 ? "-" : String.valueOf(now), SwingConstants.LEFT);
		value.setFont(FontManager.getRunescapeBoldFont().deriveFont(15f));
		// turns on a low one, so it catches the eye without needing a bar
		value.setForeground(now < 0 ? Color.GRAY
			: most > 0 && now * 4 <= most ? new Color(0xE0, 0x50, 0x50) : color);

		one.add(orb);
		one.add(value);
		one.setToolTipText(most > 0 && now >= 0 ? what + " " + now + " of " + most : what);
		return one;
	}

	/**
	 * The four tabs, as the game's own icons.
	 *
	 * The one you are on sits on a teal seat with a brass rule under it and
	 * the others are flat and dark, so which is which reads at a glance
	 * rather than having to be worked out from a shade of grey.
	 */
	private JPanel tabs(PartyMate mate, int open)
	{
		/*
		 * Tall enough for the pictures in it.
		 *
		 * The game's tab icons are about thirty across and thirty tall, and
		 * this row was twenty eight high with four of those going on border,
		 * so every icon lost its top and bottom.
		 */
		JPanel row = new JPanel(new GridLayout(1, TAB_SPRITES.length, 3, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setPreferredSize(new Dimension(CELL * TAB_SPRITES.length, TAB_HEIGHT));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, TAB_HEIGHT));

		for (int n = 0; n < TAB_SPRITES.length; n++)
		{
			final int which = n;
			boolean on = n == open;

			JLabel tab = new JLabel();
			tab.setHorizontalAlignment(SwingConstants.CENTER);
			tab.setVerticalAlignment(SwingConstants.CENTER);
			tab.setOpaque(true);
			tab.setBackground(on ? VeritasEventsPanel.TEAL_SEAT : ColorScheme.DARK_GRAY_COLOR);
			tab.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 1, 3, 1,
					on ? VeritasEventsPanel.BRASS : ColorScheme.DARKER_GRAY_COLOR),
				BorderFactory.createEmptyBorder(3, 0, 3, 0)));
			tab.setToolTipText(TAB_NAMES[n]);
			tab.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
			spriteManager.addSpriteTo(tab, TAB_SPRITES[n], 0);
			tab.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent event)
				{
					openTab.put(mate.getMemberId(), which);
					draw();
				}
			});
			row.add(tab);
		}
		return row;
	}

	private JPanel contents(PartyMate mate, int tab)
	{
		switch (tab)
		{
			case 1:
				return worn(mate);
			case 2:
				return prayers(mate);
			case 3:
				return levels(mate);
			default:
				return carried(mate);
		}
	}

	private JPanel carried(PartyMate mate)
	{
		int[] slots = mate.getCarried();
		if (slots.length == 0)
		{
			return empty();
		}

		int count = slots.length / 2;
		int rows = (count + CARRIED_COLUMNS - 1) / CARRIED_COLUMNS;
		JPanel grid = grid(rows, CARRIED_COLUMNS);
		for (int n = 0; n < count; n++)
		{
			grid.add(cell(slots[n * 2], slots[n * 2 + 1]));
		}
		return grid;
	}

	private JPanel worn(PartyMate mate)
	{
		int[] slots = mate.getWorn();
		if (slots.length == 0)
		{
			return empty();
		}

		JPanel grid = grid(WORN_SHAPE.length / 3, 3);
		for (int slot : WORN_SHAPE)
		{
			if (slot < 0 || slot * 2 + 1 >= slots.length)
			{
				grid.add(blank(true));
				continue;
			}
			grid.add(cell(slots[slot * 2], slots[slot * 2 + 1]));
		}
		return grid;
	}

	private JPanel prayers(PartyMate mate)
	{
		Prayer[] all = Prayer.values();
		Set<Prayer> on = EnumSet.noneOf(Prayer.class);
		boolean ruinous = false;
		for (int ordinal : mate.getPrayers())
		{
			if (ordinal >= 0 && ordinal < all.length)
			{
				on.add(all[ordinal]);
				ruinous |= isRuinous(all[ordinal]);
			}
		}

		Prayer[] book = ruinous ? RUINOUS : STANDARD;
		int rows = (book.length + PRAYER_COLUMNS - 1) / PRAYER_COLUMNS;
		JPanel grid = grid(rows, PRAYER_COLUMNS);

		for (int n = 0; n < rows * PRAYER_COLUMNS; n++)
		{
			if (n >= book.length)
			{
				grid.add(blank(false));
				continue;
			}
			grid.add(prayerCell(book[n], on.contains(book[n])));
		}
		return grid;
	}

	/**
	 * One prayer, lit or not.
	 *
	 * The whole book is drawn either way, as the game draws it, because a
	 * grid of only the prayers somebody has on tells you nothing about the
	 * ones they have not and moves every time they flick one.
	 */
	private JLabel prayerCell(Prayer prayer, boolean lit)
	{
		JLabel icon = emptyCell();
		icon.setToolTipText(pretty(prayer.name()) + (lit ? " (on)" : ""));

		/*
		 * Every prayer drawn bright, and the lit ones boxed.
		 *
		 * The client carries a dimmed picture of each prayer for the ones
		 * you do not have on, and at this size they went nearly black: the
		 * book stopped being readable at a glance, which is the only thing
		 * it is for. So the bright picture throughout, and the square around
		 * it does the saying, the way the game marks the one you are on.
		 */
		if (lit)
		{
			icon.setBackground(VeritasEventsPanel.TEAL_SEAT);
			icon.setBorder(BorderFactory.createMatteBorder(2, 2, 2, 2,
				VeritasEventsPanel.BRASS));
		}

		int sprite = PrayerIcons.spriteFor(prayer);
		if (sprite >= 0)
		{
			spriteManager.addSpriteTo(icon, sprite, 0);
		}
		else
		{
			// no picture in this client, so it says what it is instead
			icon.setText(pretty(prayer.name()).substring(0, 2));
			icon.setFont(FontManager.getRunescapeSmallFont());
			icon.setForeground(lit ? VeritasEventsPanel.BRASS : Color.GRAY);
		}
		return icon;
	}

	/**
	 * The two prayer books, taken from the client rather than typed out.
	 *
	 * The client names every Ruinous Powers prayer with the same prefix, so
	 * the standard book is simply everything that is not one of those. A
	 * prayer added to the game in a later client turns up in the right book
	 * on its own, which is the point of reading it rather than listing it.
	 */
	private static boolean isRuinous(Prayer prayer)
	{
		return prayer.name().startsWith("RP_");
	}

	private static Prayer[] bookOf(boolean ruinous)
	{
		List<Prayer> out = new ArrayList<>();
		for (Prayer prayer : Prayer.values())
		{
			if (isRuinous(prayer) == ruinous)
			{
				out.add(prayer);
			}
		}
		return out.toArray(new Prayer[0]);
	}

	private static final Prayer[] STANDARD = bookOf(false);
	private static final Prayer[] RUINOUS = bookOf(true);

	/**
	 * Levels, with anything boosted or drained picked out.
	 *
	 * The plain ones are there for completeness; the point of the tab is the
	 * handful that are not plain, so those are the ones that are colored.
	 */
	private JPanel levels(PartyMate mate)
	{
		int[] boosted = mate.getBoosted();
		int[] real = mate.getReal();
		if (boosted.length == 0)
		{
			return empty();
		}

		Skill[] all = Skill.values();
		int total = 0;
		int shown = Math.min(all.length, boosted.length);
		int rows = (shown + LEVEL_COLUMNS - 1) / LEVEL_COLUMNS;
		JPanel grid = grid(rows, LEVEL_COLUMNS, CONTENT - TOTAL_BAR);

		for (int n = 0; n < rows * LEVEL_COLUMNS; n++)
		{
			if (n >= shown)
			{
				grid.add(blank(false));
				continue;
			}
			int now = boosted[n];
			int was = n < real.length ? real[n] : now;
			grid.add(level(all[n], now, was));
			total += was;
		}

		JPanel holder = VeritasEventsPanel.column();
		holder.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		holder.setAlignmentX(Component.LEFT_ALIGNMENT);
		holder.add(grid);

		// the figure the game puts under its own stats tab, in the same place
		JLabel sum = new JLabel("Total level: " + QuantityFormatter.formatNumber(total));
		sum.setFont(FontManager.getRunescapeFont());
		sum.setForeground(VeritasEventsPanel.BRASS);
		sum.setHorizontalAlignment(SwingConstants.CENTER);
		sum.setAlignmentX(Component.LEFT_ALIGNMENT);
		sum.setOpaque(true);
		sum.setBackground(VeritasEventsPanel.TEAL_SEAT);
		sum.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		sum.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
		holder.add(sum);
		return holder;
	}

	/**
	 * One skill: its picture and where it stands right now.
	 *
	 * Only the current level. The base used to sit under it so a boost
	 * could be read against something, but the color already says which
	 * way a number has moved, and what it moved from is a question for the
	 * hover rather than something worth a second line on every one of
	 * twenty three skills.
	 */
	private JPanel level(Skill skill, int now, int was)
	{
		JPanel one = new JPanel(new BorderLayout(2, 0));
		one.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		one.setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 1));

		JLabel icon = new JLabel();
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		// the game's stat pictures are 23 across; anything less clips them
		icon.setPreferredSize(new Dimension(24, 24));
		int sprite = SkillIcons.spriteFor(skill);
		if (sprite >= 0)
		{
			spriteManager.addSpriteTo(icon, sprite, 0);
		}

		JLabel figures = new JLabel(String.valueOf(now));
		// centred in what the icon leaves, rather than pushed up against it
		// with the rest of the cell empty to its right
		figures.setHorizontalAlignment(SwingConstants.CENTER);
		figures.setFont(FontManager.getRunescapeFont());
		figures.setForeground(now > was ? new Color(0x8C, 0xE8, 0x6F)
			: now < was ? new Color(0xE8, 0x80, 0x50) : VeritasEventsPanel.BONE);

		one.add(icon, BorderLayout.WEST);
		one.add(figures, BorderLayout.CENTER);
		one.setToolTipText(pretty(skill.name()) + ": " + now
			+ (now == was ? "" : " of " + was));
		return one;
	}

	// ------------------------------------------------------------ pieces ----

	private JPanel grid(int rows, int columns)
	{
		return grid(rows, columns, CONTENT);
	}

	/**
	 * A grid that is always the same height, however many rows it holds.
	 *
	 * GridLayout shares whatever height it is given evenly between its rows,
	 * so pinning the height here is all it takes for five rows of gear to
	 * fill the same space as seven rows of inventory.
	 */
	private JPanel grid(int rows, int columns, int height)
	{
		JPanel grid = new JPanel(new GridLayout(rows, columns, 1, 1));
		grid.setBackground(VeritasEventsPanel.GRID);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		grid.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, VeritasEventsPanel.GRID));
		grid.setPreferredSize(new Dimension(CELL * columns, height));
		grid.setMinimumSize(new Dimension(CELL * columns, height));
		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		return grid;
	}

	/** One slot, with whatever is in it. */
	private JLabel cell(int id, int quantity)
	{
		JLabel icon = emptyCell();
		if (id < 0 || quantity <= 0)
		{
			return icon;
		}

		String name = nameOf.apply(id);
		if (name == null)
		{
			// noted rather than asked for here, because asking has to happen
			// on the client thread; the tooltip arrives on a later draw
			if (asked.add(id))
			{
				toAsk.add(id);
			}
			icon.setToolTipText(quantity > 1 ? QuantityFormatter.formatNumber(quantity) : null);
		}
		else
		{
			icon.setToolTipText(quantity > 1
				? name + " x " + QuantityFormatter.formatNumber(quantity) : name);
		}

		itemManager.getImage(id, quantity, quantity > 1).addTo(icon);
		return icon;
	}

	private JLabel emptyCell()
	{
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(CELL, CELL));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setVerticalAlignment(SwingConstants.CENTER);
		icon.setOpaque(true);
		icon.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		return icon;
	}

	/** A cell with nothing in it; a gap in the kit is darker than an empty slot. */
	private JLabel blank(boolean gap)
	{
		JLabel icon = emptyCell();
		icon.setBackground(gap ? ColorScheme.DARK_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
		return icon;
	}

	/**
	 * A tab with nothing behind it yet, holding its place.
	 *
	 * The same height as a tab with something in it, so a card does not
	 * change size the moment the first update for it arrives.
	 */
	private static JPanel empty()
	{
		JPanel holder = VeritasEventsPanel.column();
		holder.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		holder.setAlignmentX(Component.LEFT_ALIGNMENT);
		holder.setPreferredSize(new Dimension(CELL, CONTENT));
		holder.setMaximumSize(new Dimension(Integer.MAX_VALUE, CONTENT));
		return holder;
	}


	/**
	 * A section heading, which has to not look like a person.
	 *
	 * A name and a heading were both brass-ish text at the top of a box, so
	 * the eye had to read them to tell them apart. A heading now sits on its
	 * own teal bar across the full width, which is a shape nothing else on
	 * the page has.
	 */
	private static JPanel heading(String text)
	{
		return heading(text, "");
	}

	/**
	 * A heading, with something small on its right when there is something
	 * to put there.
	 *
	 * The passphrase lives here rather than in a block of its own, because
	 * the moment anybody wants it is the moment somebody has asked to join,
	 * and the heading is where the eye already is.
	 */
	private static JPanel heading(String text, String trailing)
	{
		JLabel label = new JLabel(VeritasEventsPanel.spaced(text));
		/*
		 * Smaller than the names it sits over, and still obviously a heading.
		 *
		 * It is spaced capitals in brass on its own bar with a rule beneath
		 * it, and not one name is any of those things. The hierarchy is in
		 * the treatment; size was only making it shout.
		 */
		label.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
		label.setForeground(VeritasEventsPanel.BRASS);
		label.setOpaque(true);
		label.setBackground(VeritasEventsPanel.TEAL_SEAT);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(null);

		JPanel bar = new JPanel(new BorderLayout(8, 0));
		bar.setBackground(VeritasEventsPanel.TEAL_SEAT);
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		bar.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 2, 0, VeritasEventsPanel.BRASS),
			BorderFactory.createEmptyBorder(6, 8, 5, 8)));
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		bar.add(label, BorderLayout.WEST);

		if (!trailing.isEmpty())
		{
			JLabel said = new JLabel(trailing, SwingConstants.RIGHT);
			said.setFont(FontManager.getRunescapeFont());
			said.setForeground(VeritasEventsPanel.GOLD);
			said.setToolTipText("The passphrase you are in: " + trailing);
			bar.add(said, BorderLayout.CENTER);
		}
		return bar;
	}

	/** A button that fills the panel, since all of these are their own row. */
	private static JButton wide(String text)
	{
		JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeFont());
		button.setForeground(VeritasEventsPanel.BONE);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setFocusPainted(false);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 1, 1, 1, VeritasEventsPanel.TEAL_D),
			BorderFactory.createEmptyBorder(4, 8, 4, 8)));
		return button;
	}

	/** THICK_SKIN as Thick skin, which is what it is called everywhere else. */
	private static String pretty(String constant)
	{
		String words = constant.replace('_', ' ').toLowerCase();
		return words.isEmpty() ? words
			: Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}
}
