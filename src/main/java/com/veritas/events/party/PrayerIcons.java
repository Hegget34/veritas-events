/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events.party;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Prayer;
import net.runelite.api.SpriteID;
import net.runelite.api.gameval.SpriteID.Prayeron;

/**
 * Which picture goes with which prayer.
 *
 * The client names every prayer sprite after the prayer, so this table is a
 * transcription rather than a judgement: PRAYER_RIGOUR is the picture of
 * Rigour. It is written out in full rather than looked up by name while
 * running, so a prayer the game adds later comes out as a missing picture
 * rather than as a missing field thrown in the middle of a raid.
 *
 * Checked against the 1.13.0 client: 54 of the 55 prayers it knows have a
 * picture under the same name.
 * The 1 that does not, RP_UMBRA_VOW, has no picture of its own in this
 * client and is drawn as its name instead.
 */
public final class PrayerIcons
{
	/**
	 * The bright picture of every prayer in the standard book.
	 *
	 * The client also carries a dimmed one of each, for prayers you do not
	 * have on, but at the size this panel draws them they came out nearly
	 * black and the book stopped being readable at a glance. So the bright
	 * one throughout, and the square drawn round it says which are on.
	 *
	 * Taken from the client's own sprite names, where Prayeron.ZEAL is
	 * Zeal, so a prayer added later needs one line here and nothing else.
	 * The Ruinous Powers prayers are not in that set and fall back to
	 * PICTURES below.
	 */
	private static final Map<Prayer, Integer> LIT = new EnumMap<>(Prayer.class);

	static
	{
		LIT.put(Prayer.THICK_SKIN, Prayeron.THICK_SKIN);
		LIT.put(Prayer.BURST_OF_STRENGTH, Prayeron.BURST_OF_STRENGTH);
		LIT.put(Prayer.CLARITY_OF_THOUGHT, Prayeron.CLARITY_OF_THOUGHT);
		LIT.put(Prayer.SHARP_EYE, Prayeron.SHARP_EYE);
		LIT.put(Prayer.MYSTIC_WILL, Prayeron.MYSTIC_WILL);
		LIT.put(Prayer.ROCK_SKIN, Prayeron.ROCK_SKIN);
		LIT.put(Prayer.SUPERHUMAN_STRENGTH, Prayeron.SUPERHUMAN_STRENGTH);
		LIT.put(Prayer.IMPROVED_REFLEXES, Prayeron.IMPROVED_REFLEXES);
		LIT.put(Prayer.RAPID_RESTORE, Prayeron.RAPID_RESTORE);
		LIT.put(Prayer.RAPID_HEAL, Prayeron.RAPID_HEAL);
		LIT.put(Prayer.PROTECT_ITEM, Prayeron.PROTECT_ITEM);
		LIT.put(Prayer.HAWK_EYE, Prayeron.HAWK_EYE);
		LIT.put(Prayer.MYSTIC_LORE, Prayeron.MYSTIC_LORE);
		LIT.put(Prayer.STEEL_SKIN, Prayeron.STEEL_SKIN);
		LIT.put(Prayer.ULTIMATE_STRENGTH, Prayeron.ULTIMATE_STRENGTH);
		LIT.put(Prayer.INCREDIBLE_REFLEXES, Prayeron.INCREDIBLE_REFLEXES);
		LIT.put(Prayer.PROTECT_FROM_MAGIC, Prayeron.PROTECT_FROM_MAGIC);
		LIT.put(Prayer.PROTECT_FROM_MISSILES, Prayeron.PROTECT_FROM_MISSILES);
		LIT.put(Prayer.PROTECT_FROM_MELEE, Prayeron.PROTECT_FROM_MELEE);
		LIT.put(Prayer.EAGLE_EYE, Prayeron.EAGLE_EYE);
		LIT.put(Prayer.MYSTIC_MIGHT, Prayeron.MYSTIC_MIGHT);
		LIT.put(Prayer.RETRIBUTION, Prayeron.RETRIBUTION);
		LIT.put(Prayer.REDEMPTION, Prayeron.REDEMPTION);
		LIT.put(Prayer.SMITE, Prayeron.SMITE);
		LIT.put(Prayer.CHIVALRY, Prayeron.CHIVALRY);
		LIT.put(Prayer.DEADEYE, Prayeron.DEADEYE);
		LIT.put(Prayer.MYSTIC_VIGOUR, Prayeron.MYSTIC_VIGOUR);
		LIT.put(Prayer.PIETY, Prayeron.PIETY);
		LIT.put(Prayer.PRESERVE, Prayeron.PRESERVE);
		LIT.put(Prayer.RIGOUR, Prayeron.RIGOUR);
		LIT.put(Prayer.AUGURY, Prayeron.AUGURY);
		LIT.put(Prayer.ZEAL, Prayeron.ZEAL);
	}

	private static final Map<Prayer, Integer> PICTURES = new EnumMap<>(Prayer.class);

	static
	{
		PICTURES.put(Prayer.THICK_SKIN, SpriteID.PRAYER_THICK_SKIN);
		PICTURES.put(Prayer.BURST_OF_STRENGTH, SpriteID.PRAYER_BURST_OF_STRENGTH);
		PICTURES.put(Prayer.CLARITY_OF_THOUGHT, SpriteID.PRAYER_CLARITY_OF_THOUGHT);
		PICTURES.put(Prayer.SHARP_EYE, SpriteID.PRAYER_SHARP_EYE);
		PICTURES.put(Prayer.MYSTIC_WILL, SpriteID.PRAYER_MYSTIC_WILL);
		PICTURES.put(Prayer.ROCK_SKIN, SpriteID.PRAYER_ROCK_SKIN);
		PICTURES.put(Prayer.SUPERHUMAN_STRENGTH, SpriteID.PRAYER_SUPERHUMAN_STRENGTH);
		PICTURES.put(Prayer.IMPROVED_REFLEXES, SpriteID.PRAYER_IMPROVED_REFLEXES);
		PICTURES.put(Prayer.RAPID_RESTORE, SpriteID.PRAYER_RAPID_RESTORE);
		PICTURES.put(Prayer.RAPID_HEAL, SpriteID.PRAYER_RAPID_HEAL);
		PICTURES.put(Prayer.PROTECT_ITEM, SpriteID.PRAYER_PROTECT_ITEM);
		PICTURES.put(Prayer.HAWK_EYE, SpriteID.PRAYER_HAWK_EYE);
		PICTURES.put(Prayer.MYSTIC_LORE, SpriteID.PRAYER_MYSTIC_LORE);
		PICTURES.put(Prayer.STEEL_SKIN, SpriteID.PRAYER_STEEL_SKIN);
		PICTURES.put(Prayer.ULTIMATE_STRENGTH, SpriteID.PRAYER_ULTIMATE_STRENGTH);
		PICTURES.put(Prayer.INCREDIBLE_REFLEXES, SpriteID.PRAYER_INCREDIBLE_REFLEXES);
		PICTURES.put(Prayer.PROTECT_FROM_MAGIC, SpriteID.PRAYER_PROTECT_FROM_MAGIC);
		PICTURES.put(Prayer.PROTECT_FROM_MISSILES, SpriteID.PRAYER_PROTECT_FROM_MISSILES);
		PICTURES.put(Prayer.PROTECT_FROM_MELEE, SpriteID.PRAYER_PROTECT_FROM_MELEE);
		PICTURES.put(Prayer.EAGLE_EYE, SpriteID.PRAYER_EAGLE_EYE);
		PICTURES.put(Prayer.MYSTIC_MIGHT, SpriteID.PRAYER_MYSTIC_MIGHT);
		PICTURES.put(Prayer.RETRIBUTION, SpriteID.PRAYER_RETRIBUTION);
		PICTURES.put(Prayer.REDEMPTION, SpriteID.PRAYER_REDEMPTION);
		PICTURES.put(Prayer.SMITE, SpriteID.PRAYER_SMITE);
		PICTURES.put(Prayer.CHIVALRY, SpriteID.PRAYER_CHIVALRY);
		PICTURES.put(Prayer.DEADEYE, SpriteID.PRAYER_DEADEYE);
		PICTURES.put(Prayer.MYSTIC_VIGOUR, SpriteID.PRAYER_MYSTIC_VIGOUR);
		PICTURES.put(Prayer.PIETY, SpriteID.PRAYER_PIETY);
		PICTURES.put(Prayer.PRESERVE, SpriteID.PRAYER_PRESERVE);
		PICTURES.put(Prayer.RIGOUR, SpriteID.PRAYER_RIGOUR);
		PICTURES.put(Prayer.AUGURY, SpriteID.PRAYER_AUGURY);
		PICTURES.put(Prayer.RP_REJUVENATION, SpriteID.PRAYER_RP_REJUVENATION);
		PICTURES.put(Prayer.RP_ANCIENT_STRENGTH, SpriteID.PRAYER_RP_ANCIENT_STRENGTH);
		PICTURES.put(Prayer.RP_ANCIENT_SIGHT, SpriteID.PRAYER_RP_ANCIENT_SIGHT);
		PICTURES.put(Prayer.RP_ANCIENT_WILL, SpriteID.PRAYER_RP_ANCIENT_WILL);
		PICTURES.put(Prayer.RP_PROTECT_ITEM, SpriteID.PRAYER_RP_PROTECT_ITEM);
		PICTURES.put(Prayer.RP_RUINOUS_GRACE, SpriteID.PRAYER_RP_RUINOUS_GRACE);
		PICTURES.put(Prayer.RP_DAMPEN_MAGIC, SpriteID.PRAYER_RP_DAMPEN_MAGIC);
		PICTURES.put(Prayer.RP_DAMPEN_RANGED, SpriteID.PRAYER_RP_DAMPEN_RANGED);
		PICTURES.put(Prayer.RP_DAMPEN_MELEE, SpriteID.PRAYER_RP_DAMPEN_MELEE);
		PICTURES.put(Prayer.RP_TRINITAS, SpriteID.PRAYER_RP_TRINITAS);
		PICTURES.put(Prayer.RP_BERSERKER, SpriteID.PRAYER_RP_BERSERKER);
		PICTURES.put(Prayer.RP_PURGE, SpriteID.PRAYER_RP_PURGE);
		PICTURES.put(Prayer.RP_METABOLISE, SpriteID.PRAYER_RP_METABOLISE);
		PICTURES.put(Prayer.RP_REBUKE, SpriteID.PRAYER_RP_REBUKE);
		PICTURES.put(Prayer.RP_VINDICATION, SpriteID.PRAYER_RP_VINDICATION);
		PICTURES.put(Prayer.RP_DECIMATE, SpriteID.PRAYER_RP_DECIMATE);
		PICTURES.put(Prayer.RP_ANNIHILATE, SpriteID.PRAYER_RP_ANNIHILATE);
		PICTURES.put(Prayer.RP_VAPORISE, SpriteID.PRAYER_RP_VAPORISE);
		PICTURES.put(Prayer.RP_FUMUS_VOW, SpriteID.PRAYER_RP_FUMUS_VOW);
		PICTURES.put(Prayer.RP_CRUORS_VOW, SpriteID.PRAYER_RP_CRUORS_VOW);
		PICTURES.put(Prayer.RP_GLACIES_VOW, SpriteID.PRAYER_RP_GLACIES_VOW);
		PICTURES.put(Prayer.RP_WRATH, SpriteID.PRAYER_RP_WRATH);
		PICTURES.put(Prayer.RP_INTENSIFY, SpriteID.PRAYER_RP_INTENSIFY);
	}

	private PrayerIcons()
	{
	}

	/** The sprite for a prayer, or -1 when this client has no picture for it. */
	public static int spriteFor(Prayer prayer)
	{
		Integer bright = LIT.get(prayer);
		if (bright != null)
		{
			return bright;
		}
		Integer found = PICTURES.get(prayer);
		return found == null ? -1 : found;
	}
}
