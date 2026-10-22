/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events;

import java.awt.Color;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

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

	@ConfigSection(name = "Party Hub", description = "Raiding together: pings, "
		+ "and what shows above your team", position = 3)
	String partySection = "party";

	@ConfigItem(keyName = "eventUrl", name = "Event URL", position = 1, section = eventSection,
		description = "Where event drops go. The host of a bingo or a drop "
			+ "competition gives you this address. Blank sends nothing.")
	default String eventUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "eventKey", name = "Event key", position = 2, section = eventSection, secret = true,
		description = "The key that lets you post to the event's board, if the "
			+ "host uses one. They will give it to you with the address.")
	default String eventKey()
	{
		return "";
	}

	/*
	 * Hidden, but still a setting.
	 *
	 * 13727 is Veritas and is not something anyone here should be changing,
	 * so it has no business taking up a row in the settings. It stays a
	 * setting rather than being written into the code because there are
	 * occasions worth keeping the door open for: a group that is renumbered,
	 * or 0 to turn the Clan stats view off. Anyone with reason to change it
	 * can still set it, they simply will not trip over it.
	 */
	@ConfigItem(keyName = "womGroupId", name = "Wise Old Man group", position = 5, section = eventSection,
		hidden = true,
		description = "The group id the Gained view reads from. Veritas is 13727. Set it to 0 to turn that view off.")
	default int womGroupId()
	{
		return 13727;
	}

	@ConfigItem(keyName = "sendScreenshot", name = "Include a screenshot", position = 3, section = eventSection,
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

	@ConfigItem(keyName = "announceDrops", name = "Say it in chat", position = 3, section = eventSection,
		description = "Write a line in the chat box when a drop is sent to an "
			+ "event, and when the board will not take one.")
	default boolean announceDrops()
	{
		return true;
	}

	@ConfigItem(keyName = "announceEvents", name = "Tell me about events", position = 4, section = eventSection,
		description = "Notify when the clan board says an event has started or "
			+ "finished, so the start is not missed.")
	default boolean announceEvents()
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
		description = "The word your organiser announced. Shown on screen so your screenshots prove when they were taken. Leave it blank to take the event's own, or Veritas when there is no event.")
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
	@ConfigItem(keyName = "passwordColor", name = "Password color", position = 3, section = overlaySection,
		description = "Pick a color a screenshot cannot easily be faked with.")
	default Color passwordColor()
	{
		return Color.YELLOW;
	}

	@Alpha
	@ConfigItem(keyName = "dateTimeColor", name = "Date and time color", position = 4, section = overlaySection,
		description = "Make this different to the password color.")
	default Color dateTimeColor()
	{
		return new Color(0x46, 0x8F, 0xB1);
	}

	// ------------------------------------------------------- Party Hub ----

	@ConfigItem(keyName = "partyPings", name = "Tile pings", position = 0, section = partySection,
		description = "Hold the key below and click the ground to mark a tile "
			+ "for everyone in the party, and see theirs.")
	default boolean partyPings()
	{
		return true;
	}

	@ConfigItem(keyName = "partyPingKey", name = "Ping hotkey", position = 1, section = partySection,
		description = "Held down while you click. Back quote is the key above Tab.")
	default Keybind partyPingKey()
	{
		return new Keybind(KeyEvent.VK_BACK_QUOTE, 0);
	}

	@ConfigItem(keyName = "partyPingNames", name = "Show who pinged", position = 2,
		section = partySection,
		description = "Put the name of whoever pinged over the tile, in their "
			+ "own color. Turn off if it gets busy.")
	default boolean partyPingNames()
	{
		return true;
	}

	@ConfigItem(keyName = "partyPingSound", name = "Sound on ping", position = 3, section = partySection,
		description = "Play a note when somebody pings a tile.")
	default boolean partyPingSound()
	{
		return false;
	}

	@ConfigItem(keyName = "partyOverlay", name = "Show health above the team", position = 4,
		section = partySection,
		description = "Health and prayer over the heads of the people in your "
			+ "party, so you can see who is in trouble without looking away.")
	default boolean partyOverlay()
	{
		return true;
	}

	@ConfigItem(keyName = "partyNames", name = "Mark party names", position = 5, section = partySection,
		description = "Color the names of people in your party so they stand "
			+ "out from everyone else around you.")
	default boolean partyNames()
	{
		return true;
	}

	@Alpha
	@ConfigItem(keyName = "partyColor", name = "Your party color", position = 6, section = partySection,
		description = "The color your tile pings and your name are drawn in "
			+ "on everyone else's screen. Theirs show in their own colors.")
	default Color partyColor()
	{
		return new Color(0xE0, 0xC0, 0x90);
	}

	/*
	 * The last party joined, remembered so you can get back into it.
	 *
	 * Hidden, because it is not a setting anybody would sit down to change.
	 * It is what the Join previous party button reads, and the only way to
	 * set it is to join a party.
	 */
	@ConfigItem(keyName = "partyPrevious", name = "Last party", position = 7,
		section = partySection, hidden = true,
		description = "The last passphrase used, for the Join previous party button.")
	default String partyPrevious()
	{
		return "";
	}

	@ConfigItem(keyName = "partyPrevious", name = "Last party", position = 7,
		section = partySection, hidden = true,
		description = "The last passphrase used, for the Join previous party button.")
	void setPartyPrevious(String passphrase);
}
