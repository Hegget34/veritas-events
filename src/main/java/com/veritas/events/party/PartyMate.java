/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events.party;

import java.awt.Color;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.Item;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.client.party.PartyMember;

/**
 * One person in the party, as the panel wants to read them.
 *
 * This used to hold everything itself, filled in from messages of our own
 * carrying inventory, worn gear, prayers and levels. RuneLite 1.13.4 moved
 * all of that into the party proper, so PartyMember already knows it and
 * sending it a second time would be sending it twice.
 *
 * What is left is a reader: it turns what PartyMember holds into the shapes
 * the panel draws, and adds the two things core still does not carry, which
 * are the world somebody is on and which spellbook they have open.
 */
public class PartyMate
{
	private static final Prayer[] PRAYERS = Prayer.values();
	private static final Skill[] SKILLS = Skill.values();

	/** Inventory is 28 slots and worn gear 14, as id and quantity pairs. */
	private static final int CARRIED_SLOTS = 28;
	private static final int WORN_SLOTS = 14;

	private final PartyMember member;

	/** The world and the spellbook, which the party itself does not carry. */
	private final VeritasExtras extras;

	@Nullable
	private final Color color;

	public PartyMate(PartyMember member, @Nullable VeritasExtras extras, @Nullable Color color)
	{
		this.member = member;
		this.extras = extras == null ? new VeritasExtras(0, -1) : extras;
		this.color = color;
	}

	public long getMemberId()
	{
		return member.getMemberId();
	}

	public String getName()
	{
		String said = member.getDisplayName();
		return said == null ? "" : said;
	}

	public boolean isLoggedIn()
	{
		return member.isLoggedIn();
	}

	public int getWorld()
	{
		return extras.getWorld();
	}

	public int getSpellbook()
	{
		return extras.getSpellbook();
	}

	@Nullable
	public Color getColor()
	{
		return color;
	}

	/**
	 * Health and prayer, which are skills rather than fields of their own.
	 *
	 * The boosted figure is how much is left and the base is the most it
	 * could be, which is exactly what a health bar is made of.
	 */
	public int getHealth()
	{
		return level(Skill.HITPOINTS, true);
	}

	public int getHealthMax()
	{
		return level(Skill.HITPOINTS, false);
	}

	public int getPrayer()
	{
		return level(Skill.PRAYER, true);
	}

	public int getPrayerMax()
	{
		return level(Skill.PRAYER, false);
	}

	public int getRun()
	{
		return member.getRunEnergy();
	}

	public int getSpec()
	{
		return member.getSpecEnergy();
	}

	/**
	 * Combat level, worked out rather than sent.
	 *
	 * The party carries the levels it is made of, so sending the answer as
	 * well would be sending something anyone holding the question can get.
	 */
	public int getCombat()
	{
		int attack = level(Skill.ATTACK, false);
		int strength = level(Skill.STRENGTH, false);
		int defence = level(Skill.DEFENCE, false);
		int hitpoints = level(Skill.HITPOINTS, false);
		int prayer = level(Skill.PRAYER, false);
		int ranged = level(Skill.RANGED, false);
		int magic = level(Skill.MAGIC, false);

		if (attack + strength + defence + hitpoints == 0)
		{
			return 0;
		}

		double base = 0.25 * (defence + hitpoints + Math.floor(prayer / 2.0));
		double melee = 0.325 * (attack + strength);
		double range = 0.325 * Math.floor(ranged * 3.0 / 2.0);
		double mage = 0.325 * Math.floor(magic * 3.0 / 2.0);
		return (int) Math.floor(base + Math.max(melee, Math.max(range, mage)));
	}

	/** The inventory as id and quantity pairs, empty slots kept. */
	public int[] getCarried()
	{
		return slots(member.getInv(), CARRIED_SLOTS);
	}

	public int[] getWorn()
	{
		return slots(member.getWorn(), WORN_SLOTS);
	}

	/** Which prayers are lit, as ordinals of net.runelite.api.Prayer. */
	public int[] getPrayers()
	{
		Set<Prayer> lit = member.getPrayers();
		if (lit == null || lit.isEmpty())
		{
			return new int[0];
		}

		int[] out = new int[lit.size()];
		int count = 0;
		for (int n = 0; n < PRAYERS.length && count < out.length; n++)
		{
			if (lit.contains(PRAYERS[n]))
			{
				out[count++] = n;
			}
		}
		return count == out.length ? out : java.util.Arrays.copyOf(out, count);
	}

	public int[] getBoosted()
	{
		return levels(true);
	}

	public int[] getReal()
	{
		return levels(false);
	}

	/**
	 * Whether anything at all has been heard about this person.
	 *
	 * A card with nothing in it is not a person who has nothing; it is a
	 * person we have not heard from, which is a distinction nobody reading
	 * the panel mid raid should have to make. Somebody joining fills this
	 * within a tick, so the card simply waits until there is something.
	 */
	public boolean heardFrom()
	{
		return member.getInv() != null || member.getWorn() != null
			|| level(Skill.HITPOINTS, false) > 0;
	}

	private int level(Skill skill, boolean boosted)
	{
		try
		{
			return boosted ? member.getStatBoost(skill) : member.getStatBase(skill);
		}
		catch (Exception notKnown)
		{
			// a skill this member's client did not report
			return 0;
		}
	}

	private int[] levels(boolean boosted)
	{
		int[] out = new int[SKILLS.length];
		boolean any = false;
		for (int n = 0; n < SKILLS.length; n++)
		{
			out[n] = level(SKILLS[n], boosted);
			any |= out[n] > 0;
		}
		return any ? out : new int[0];
	}

	/**
	 * One container as id and quantity pairs, empty slots and all.
	 *
	 * The empty ones are kept so the grid drawn from this lines up with the
	 * player's own inventory and a glance finds things where it expects them.
	 */
	private static int[] slots(@Nullable Item[] items, int howMany)
	{
		if (items == null)
		{
			return new int[0];
		}

		int[] out = new int[howMany * 2];
		java.util.Arrays.fill(out, -1);
		for (int n = 0; n < howMany && n < items.length; n++)
		{
			Item item = items[n];
			out[n * 2] = item == null ? -1 : item.getId();
			out[n * 2 + 1] = item == null ? 0 : item.getQuantity();
		}
		return out;
	}

	/**
	 * Everything on screen about this person, run together.
	 *
	 * The panel keeps the card it built last time and only builds a new one
	 * when this changes, because an item's picture is made per quantity and
	 * rebuilding on every tick meant the whole party flickering. Anything
	 * drawn has to be in here or a change to it would go unseen.
	 */
	public String stamp(int tab)
	{
		StringBuilder out = new StringBuilder(getName())
			.append('|').append(tab)
			.append('|').append(isLoggedIn())
			.append('|').append(getCombat()).append('|').append(getWorld())
			.append('|').append(getSpellbook())
			.append('|').append(getHealth()).append('/').append(getHealthMax())
			.append('|').append(getPrayer()).append('/').append(getPrayerMax())
			.append('|').append(getRun()).append('|').append(getSpec());

		/*
		 * Only what the open tab shows.
		 *
		 * Flicking a prayer must not rebuild a card showing an inventory, and
		 * a folded card, which is -1 here, shows no grid at all and so is not
		 * changed by anything in one.
		 */
		int[] shown = tab == 1 ? getWorn() : tab == 2 ? getPrayers()
			: tab == 3 ? getBoosted() : tab == 0 ? getCarried() : new int[0];
		for (int value : shown)
		{
			out.append('|').append(value);
		}
		return out.toString();
	}
}
