/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(VeritasEventsConfig.GROUP)
public interface VeritasEventsConfig extends Config
{
	String GROUP = "veritasevents";

	@ConfigSection(name = "Event", description = "Where your drops are sent", position = 0)
	String eventSection = "event";

	@ConfigSection(name = "What to send", description = "Choose which things get reported", position = 1)
	String sendSection = "send";

	@ConfigSection(name = "Overlay", description = "Stamp the game window while you play", position = 2)
	String overlaySection = "overlay";

	@ConfigItem(keyName = "eventUrl", name = "Event URL", position = 0, section = eventSection,
		description = "The address your event organiser gave you. Leave blank to turn the plugin off.")
	default String eventUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "eventKey", name = "Event key", position = 1, section = eventSection, secret = true,
		description = "The key that lets you post to this board. Keep it to yourself.")
	default String eventKey()
	{
		return "";
	}

	@ConfigItem(keyName = "refreshMinutes", name = "Refresh every", position = 3, section = eventSection,
		description = "How often to ask the board for the latest standings, in minutes.")
	@Range(min = 1, max = 60)
	@Units(Units.MINUTES)
	default int refreshMinutes()
	{
		return 5;
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

	@ConfigItem(keyName = "showOverlay", name = "Display overlay", position = 0, section = overlaySection,
		description = "Draw the event password and the time on top of the game.")
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(keyName = "eventPassword", name = "Event password", position = 1, section = overlaySection,
		description = "The word your organiser announced. Shown on screen so your screenshots prove when they were taken.")
	default String eventPassword()
	{
		return "";
	}

	@ConfigItem(keyName = "showDateTime", name = "Date and time", position = 2, section = overlaySection,
		description = "Show the current date and time in UTC under the password.")
	default boolean showDateTime()
	{
		return true;
	}

	@Alpha
	@ConfigItem(keyName = "passwordColor", name = "Password colour", position = 3, section = overlaySection,
		description = "Pick a colour a screenshot cannot easily be faked with.")
	default Color passwordColor()
	{
		return Color.YELLOW;
	}

	@Alpha
	@ConfigItem(keyName = "dateTimeColor", name = "Date and time colour", position = 4, section = overlaySection,
		description = "Make this different to the password colour.")
	default Color dateTimeColor()
	{
		return new Color(0x46, 0x8F, 0xB1);
	}
}
