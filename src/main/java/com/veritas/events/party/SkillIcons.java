/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events.party;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.SpriteID;

/**
 * Which picture goes with which skill.
 *
 * The same transcription the prayers get, and written out for the same
 * reason: a skill the game adds later should come out as a missing picture
 * rather than as a missing field thrown while drawing.
 *
 * Checked against the 1.13.0 client: 23 of the 25 it knows have a picture
 * under the same name. The Sailing and Overall have none, being a total rather than
 * a skill drawn in the skills panel.
 */
public final class SkillIcons
{
	private static final Map<Skill, Integer> PICTURES = new EnumMap<>(Skill.class);

	static
	{
		PICTURES.put(Skill.ATTACK, SpriteID.SKILL_ATTACK);
		PICTURES.put(Skill.DEFENCE, SpriteID.SKILL_DEFENCE);
		PICTURES.put(Skill.STRENGTH, SpriteID.SKILL_STRENGTH);
		PICTURES.put(Skill.HITPOINTS, SpriteID.SKILL_HITPOINTS);
		PICTURES.put(Skill.RANGED, SpriteID.SKILL_RANGED);
		PICTURES.put(Skill.PRAYER, SpriteID.SKILL_PRAYER);
		PICTURES.put(Skill.MAGIC, SpriteID.SKILL_MAGIC);
		PICTURES.put(Skill.COOKING, SpriteID.SKILL_COOKING);
		PICTURES.put(Skill.WOODCUTTING, SpriteID.SKILL_WOODCUTTING);
		PICTURES.put(Skill.FLETCHING, SpriteID.SKILL_FLETCHING);
		PICTURES.put(Skill.FISHING, SpriteID.SKILL_FISHING);
		PICTURES.put(Skill.FIREMAKING, SpriteID.SKILL_FIREMAKING);
		PICTURES.put(Skill.CRAFTING, SpriteID.SKILL_CRAFTING);
		PICTURES.put(Skill.SMITHING, SpriteID.SKILL_SMITHING);
		PICTURES.put(Skill.MINING, SpriteID.SKILL_MINING);
		PICTURES.put(Skill.HERBLORE, SpriteID.SKILL_HERBLORE);
		PICTURES.put(Skill.AGILITY, SpriteID.SKILL_AGILITY);
		PICTURES.put(Skill.THIEVING, SpriteID.SKILL_THIEVING);
		PICTURES.put(Skill.SLAYER, SpriteID.SKILL_SLAYER);
		PICTURES.put(Skill.FARMING, SpriteID.SKILL_FARMING);
		PICTURES.put(Skill.RUNECRAFT, SpriteID.SKILL_RUNECRAFT);
		PICTURES.put(Skill.HUNTER, SpriteID.SKILL_HUNTER);
		PICTURES.put(Skill.CONSTRUCTION, SpriteID.SKILL_CONSTRUCTION);
	}

	private SkillIcons()
	{
	}

	/** The sprite for a skill, or -1 when this client has no picture for it. */
	public static int spriteFor(Skill skill)
	{
		Integer found = PICTURES.get(skill);
		return found == null ? -1 : found;
	}
}
