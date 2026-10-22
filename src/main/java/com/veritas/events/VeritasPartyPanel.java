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
	private static final int CELL = 36;

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
	 * Redraws once things have stopped arriving.
	 *
	 * Five people in a raid produce updates faster than a panel can sensibly
	 * be rebuilt. Anything the person themselves clicks still redraws at
	 * once; this is only for what turns up on its own.
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
			settling.restart();
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
			JButton create = wide("Create party");
			create.setToolTipText("Start one and hand the phrase around");
			create.addActionListener(e -> createParty());
			controls.add(create);
			controls.add(Box.createVerticalStrut(4));

			JButton join = wide("Join party");
			join.setToolTipText("Type in a phrase somebody gave you");
			join.addActionListener(e -> joinParty());
			controls.add(join);

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
		JButton copy = wide("Copy passphrase");
		copy.addActionListener(e ->
		{
			Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(hub.passphrase()), null);
			copy.setText("Copied");
			Timer back = new Timer(1200, ev -> copy.setText("Copy passphrase"));
			back.setRepeats(false);
			back.start();
		});
		controls.add(copy);
		controls.add(Box.createVerticalStrut(4));

		JButton leave = wide("Leave party");
		leave.addActionListener(e ->
		{
			hub.leave();
			draw();
		});
		controls.add(leave);
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
			BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP)));

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
		name.setFont(FontManager.getRunescapeBoldFont().deriveFont(17f));
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
			level.setFont(FontManager.getRunescapeFont().deriveFont(15f));
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
		JPanel row = new JPanel(new GridLayout(1, TAB_SPRITES.length, 3, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		for (int n = 0; n < TAB_SPRITES.length; n++)
		{
			final int which = n;
			boolean on = n == open;

			JLabel tab = new JLabel();
			tab.setHorizontalAlignment(SwingConstants.CENTER);
			tab.setVerticalAlignment(SwingConstants.CENTER);
			tab.setOpaque(true);
			tab.setBackground(on ? VeritasEventsPanel.TEAL_SEAT : ColorScheme.DARK_GRAY_COLOR);
			tab.setBorder(BorderFactory.createMatteBorder(1, 1, 3, 1,
				on ? VeritasEventsPanel.BRASS : ColorScheme.DARKER_GRAY_COLOR));
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
		int[] lit = mate.getPrayers();
		if (lit.length == 0)
		{
			return nothing("No prayers on.");
		}

		Prayer[] all = Prayer.values();
		int rows = (lit.length + PRAYER_COLUMNS - 1) / PRAYER_COLUMNS;
		JPanel grid = grid(rows, PRAYER_COLUMNS);

		for (int n = 0; n < rows * PRAYER_COLUMNS; n++)
		{
			if (n >= lit.length || lit[n] < 0 || lit[n] >= all.length)
			{
				grid.add(blank(false));
				continue;
			}

			Prayer prayer = all[lit[n]];
			JLabel icon = emptyCell();
			icon.setToolTipText(pretty(prayer.name()));

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
				icon.setForeground(VeritasEventsPanel.BRASS);
			}
			grid.add(icon);
		}
		return grid;
	}

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
		JPanel grid = new JPanel(new GridLayout(0, 3, 1, 1));
		grid.setBackground(VeritasEventsPanel.GRID);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		grid.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, VeritasEventsPanel.GRID));

		for (int n = 0; n < all.length && n < boosted.length; n++)
		{
			int now = boosted[n];
			int was = n < real.length ? real[n] : now;
			if (now <= 0 && was <= 0)
			{
				continue;
			}
			grid.add(level(all[n], now, was));
		}
		return grid;
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
		one.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 1));

		JLabel icon = new JLabel();
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setPreferredSize(new Dimension(18, 20));
		int sprite = SkillIcons.spriteFor(skill);
		if (sprite >= 0)
		{
			spriteManager.addSpriteTo(icon, sprite, 0);
		}

		JLabel figures = new JLabel(String.valueOf(now));
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
		JPanel grid = new JPanel(new GridLayout(rows, columns, 1, 1));
		grid.setBackground(VeritasEventsPanel.GRID);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		grid.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, VeritasEventsPanel.GRID));
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
		icon.setPreferredSize(new Dimension(CELL, CELL - 4));
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

	/** Nothing at all, for a tab with nothing behind it yet. */
	private static JPanel empty()
	{
		JPanel holder = VeritasEventsPanel.column();
		holder.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		holder.setAlignmentX(Component.LEFT_ALIGNMENT);
		holder.setMaximumSize(new Dimension(Integer.MAX_VALUE, 0));
		return holder;
	}

	private JPanel nothing(String said)
	{
		JPanel holder = VeritasEventsPanel.column();
		holder.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		holder.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel line = new JLabel(said);
		line.setFont(FontManager.getRunescapeSmallFont());
		line.setForeground(Color.GRAY);
		line.setAlignmentX(Component.LEFT_ALIGNMENT);
		line.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
		holder.add(line);
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
