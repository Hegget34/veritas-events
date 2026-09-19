/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(VeritasEventsConfig.GROUP)
public interface VeritasEventsConfig extends Config
{
	String GROUP = "veritasevents";

	@ConfigSection(name = "Event", description = "Where your drops are sent", position = 0)
	String eventSection = "event";

	@ConfigSection(name = "What to send", description = "Choose which things get reported", position = 1)
	String sendSection = "send";

	@ConfigItem(keyName = "eventUrl", name = "Event URL", position = 0, section = eventSection,
		description = "The address your event organiser gave you. Leave blank to turn the plugin off.")
	default String eventUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "eventKey", name = "Event password", position = 1, section = eventSection, secret = true,
		description = "The password for this event. Keep it to yourself.")
	default String eventKey()
	{
		return "";
	}

	@ConfigItem(keyName = "sendScreenshot", name = "Include a screenshot", position = 2, section = eventSection,
		description = "Attach a picture of your screen, so staff can see it without you uploading one.")
	default boolean sendScreenshot()
	{
		return true;
	}

	@ConfigItem(keyName = "sendLoot", name = "Drops", position = 0, section = sendSection,
		description = "Items from kills, chests and raids")
	default boolean sendLoot()
	{
		return true;
	}

	@ConfigItem(keyName = "minimumValue", name = "Minimum drop value", position = 1, section = sendSection,
		description = "Only send drops worth at least this much, in coins. Everything else is always sent.")
	default int minimumValue()
	{
		return 0;
	}

	@ConfigItem(keyName = "bigDropValue", name = "Big drop value", position = 2, section = sendSection,
		description = "Drops worth at least this much are called out on the event page.")
	default int bigDropValue()
	{
		return 1000000;
	}

	@ConfigItem(keyName = "sendPets", name = "Pets", position = 3, section = sendSection,
		description = "When you get a pet")
	default boolean sendPets()
	{
		return true;
	}

	@ConfigItem(keyName = "sendCollectionLog", name = "Collection log", position = 4, section = sendSection,
		description = "New collection log entries")
	default boolean sendCollectionLog()
	{
		return true;
	}

	@ConfigItem(keyName = "sendLevels", name = "Levels", position = 5, section = sendSection,
		description = "Levels, including 99s")
	default boolean sendLevels()
	{
		return true;
	}

	@ConfigItem(keyName = "sendQuests", name = "Quests", position = 6, section = sendSection,
		description = "Quest completions")
	default boolean sendQuests()
	{
		return true;
	}

	@ConfigItem(keyName = "sendAchievements", name = "Combat achievements", position = 7, section = sendSection,
		description = "Combat achievement tasks")
	default boolean sendAchievements()
	{
		return true;
	}

	@ConfigItem(keyName = "sendClues", name = "Clues", position = 8, section = sendSection,
		description = "Completed treasure trails")
	default boolean sendClues()
	{
		return true;
	}

	@ConfigItem(keyName = "sendPersonalBests", name = "Personal bests", position = 9, section = sendSection,
		description = "New personal best times")
	default boolean sendPersonalBests()
	{
		return true;
	}

}
