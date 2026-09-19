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

	@ConfigSection(
		name = "Event",
		description = "Where your drops are sent",
		position = 0
	)
	String eventSection = "event";

	@ConfigSection(
		name = "What to send",
		description = "Choose which things get reported",
		position = 1
	)
	String sendSection = "send";

	@ConfigItem(
		keyName = "eventUrl",
		name = "Event URL",
		description = "The address your event organiser gave you. Leave blank to turn the plugin off.",
		position = 0,
		section = eventSection
	)
	default String eventUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "eventKey",
		name = "Event password",
		description = "The password for this event. Keep it to yourself.",
		position = 1,
		section = eventSection,
		secret = true
	)
	default String eventKey()
	{
		return "";
	}

	@ConfigItem(
		keyName = "sendScreenshot",
		name = "Include a screenshot",
		description = "Attach a picture of your screen, so staff can see the drop without you uploading one.",
		position = 2,
		section = eventSection
	)
	default boolean sendScreenshot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sendLoot",
		name = "Drops",
		description = "Send items you receive from kills, chests and raids",
		position = 0,
		section = sendSection
	)
	default boolean sendLoot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sendPets",
		name = "Pets",
		description = "Send a message when you get a pet",
		position = 1,
		section = sendSection
	)
	default boolean sendPets()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sendCollectionLog",
		name = "Collection log",
		description = "Send new collection log entries",
		position = 2,
		section = sendSection
	)
	default boolean sendCollectionLog()
	{
		return true;
	}

	@ConfigItem(
		keyName = "minimumValue",
		name = "Minimum value",
		description = "Only send drops worth at least this much, in coins. Pets and collection log entries are always sent.",
		position = 3,
		section = sendSection
	)
	default int minimumValue()
	{
		return 0;
	}
}
