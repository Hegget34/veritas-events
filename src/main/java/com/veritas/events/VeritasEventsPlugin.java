/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
@PluginDescriptor(
	name = "Veritas Events",
	description = "Sends your drops to a clan event board",
	tags = {"veritas", "event", "bingo", "loot", "clan"}
)
public class VeritasEventsPlugin extends Plugin
{
	private static final MediaType JSON = MediaType.get("application/json");
	private static final MediaType PNG = MediaType.get("image/png");

	@Inject
	private Client client;

	@Inject
	private VeritasEventsConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private DrawManager drawManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private ClientToolbar clientToolbar;

	private VeritasEventsPanel panel;
	private NavigationButton navButton;
	private Runnable lastSend;

	@Provides
	VeritasEventsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(VeritasEventsConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new VeritasEventsPanel(config, itemManager, this::resend);
		navButton = NavigationButton.builder()
			.tooltip("Veritas Events")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		refreshEvent();
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (VeritasEventsConfig.GROUP.equals(event.getGroup()) && panel != null)
		{
			panel.refresh();
			refreshEvent();
		}
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (!config.sendLoot() || config.eventUrl().trim().isEmpty())
		{
			return;
		}

		JsonArray items = new JsonArray();
		List<int[]> icons = new ArrayList<>();
		long total = 0;

		for (ItemStack stack : event.getItems())
		{
			long each = itemManager.getItemPrice(stack.getId());
			total += each * stack.getQuantity();
			icons.add(new int[]{stack.getId(), stack.getQuantity()});

			JsonObject item = new JsonObject();
			item.addProperty("id", stack.getId());
			item.addProperty("name", itemManager.getItemComposition(stack.getId()).getName());
			item.addProperty("quantity", stack.getQuantity());
			item.addProperty("priceEach", each);
			items.add(item);
		}

		if (items.size() == 0 || total < config.minimumValue())
		{
			return;
		}

		JsonObject payload = payload("LOOT");
		payload.addProperty("source", event.getName());
		payload.addProperty("totalValue", total);
		payload.addProperty("big", total >= config.bigDropValue());
		payload.add("items", items);
		send(payload, event.getName(), icons, total);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE || config.eventUrl().trim().isEmpty())
		{
			return;
		}

		String message = Text.removeTags(event.getMessage());
		String lower = message.toLowerCase();

		String type = null;
		if (config.sendPets() && lower.contains("funny feeling like"))
		{
			type = "PET";
		}
		else if (config.sendCollectionLog() && lower.startsWith("new item added to your collection log:"))
		{
			type = "COLLECTION_LOG";
		}
		else if (config.sendLevels() && lower.contains("you've just advanced your"))
		{
			type = "LEVEL";
		}
		else if (config.sendQuests() && lower.contains("congratulations, you've completed a quest"))
		{
			type = "QUEST";
		}
		else if (config.sendAchievements()
			&& (lower.contains("combat achievement task") || lower.contains("achievement diary")))
		{
			type = "ACHIEVEMENT";
		}
		else if (config.sendClues() && lower.contains("treasure trail"))
		{
			type = "CLUE";
		}
		else if (config.sendPersonalBests() && lower.contains("personal best"))
		{
			type = "PERSONAL_BEST";
		}

		if (type != null)
		{
			JsonObject payload = payload(type);
			payload.addProperty("message", message);
			send(payload, message, Collections.emptyList(), 0);
		}
	}

	private JsonObject payload(String type)
	{
		Player local = client.getLocalPlayer();
		JsonObject payload = new JsonObject();
		payload.addProperty("type", type);
		payload.addProperty("player", local == null ? "" : local.getName());
		payload.addProperty("sentAt", System.currentTimeMillis());
		return payload;
	}

	private void send(JsonObject payload, String source, List<int[]> icons, long value)
	{
		lastSend = () -> post(payload, source, icons, value, null);
		if (config.sendScreenshot())
		{
			drawManager.requestNextFrameListener(image -> post(payload, source, icons, value, png(image)));
		}
		else
		{
			post(payload, source, icons, value, null);
		}
	}

	@Nullable
	private static byte[] png(Image image)
	{
		try
		{
			BufferedImage copy = new BufferedImage(
				image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
			copy.getGraphics().drawImage(image, 0, 0, null);

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(copy, "png", out);
			return out.toByteArray();
		}
		catch (IOException e)
		{
			log.debug("could not capture a screenshot", e);
			return null;
		}
	}

	/** Sends the last thing again, for when the board was down at the time. */
	private void resend()
	{
		Runnable again = lastSend;
		if (again != null)
		{
			again.run();
		}
	}

	/**
	 * Asks the board what event this is and how the player's team is doing. The
	 * board answers a plain GET; if it does not, the panel simply stays quiet.
	 */
	private void refreshEvent()
	{
		VeritasEventsPanel p = panel;
		String url = config.eventUrl().trim();
		if (p == null)
		{
			return;
		}
		p.setEvent("", "");
		if (url.isEmpty())
		{
			return;
		}

		Request request = new Request.Builder()
			.url(url)
			.header("X-Event-Key", config.eventKey().trim())
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("no event details", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					JsonObject details = gson.fromJson(body.string(), JsonObject.class);
					String name = text(details, "event");
					String team = text(details, "team");
					p.setEvent(team.isEmpty() ? name : name + " - " + team,
						details.has("done") && details.has("total")
							? details.get("done").getAsInt() + " of " + details.get("total").getAsInt() + " tiles"
							: "");
				}
				catch (Exception e)
				{
					log.debug("could not read event details", e);
				}
			}
		});
	}

	private static String text(JsonObject object, String key)
	{
		return object.has(key) ? object.get(key).getAsString() : "";
	}

	private void post(JsonObject payload, String source, List<int[]> icons, long value, @Nullable byte[] screenshot)
	{
		String json = gson.toJson(payload);
		RequestBody body = screenshot == null
			? RequestBody.create(JSON, json)
			: new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart("payload_json", json)
				.addFormDataPart("file", "screenshot.png", RequestBody.create(PNG, screenshot))
				.build();

		Request request = new Request.Builder()
			.url(config.eventUrl().trim())
			.header("X-Event-Key", config.eventKey().trim())
			.post(body)
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("could not reach the event board", e);
				report(source, icons, value, false);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				report(source, icons, value, response.isSuccessful());
				response.close();
			}
		});
	}


	private void report(String source, List<int[]> icons, long value, boolean ok)
	{
		VeritasEventsPanel p = panel;
		if (p != null)
		{
			p.record(source, icons, value, ok);
		}
	}
}
