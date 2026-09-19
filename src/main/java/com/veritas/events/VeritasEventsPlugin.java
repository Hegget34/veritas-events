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
	private static final String PET = "funny feeling like";
	private static final String CLOG = "new item added to your collection log:";

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

	@Provides
	VeritasEventsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(VeritasEventsConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new VeritasEventsPanel(config);
		navButton = NavigationButton.builder()
			.tooltip("Veritas Events")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
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
		long total = 0;

		for (ItemStack stack : event.getItems())
		{
			long each = itemManager.getItemPrice(stack.getId());
			total += each * stack.getQuantity();

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
		payload.add("items", items);
		send(payload, items.get(0).getAsJsonObject().get("name").getAsString());
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

		if (config.sendPets() && lower.contains(PET))
		{
			send(payload("PET"), "Pet");
		}
		else if (config.sendCollectionLog() && lower.startsWith(CLOG))
		{
			String item = message.substring(CLOG.length()).trim();
			JsonObject payload = payload("COLLECTION_LOG");
			payload.addProperty("item", item);
			send(payload, item);
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

	private void send(JsonObject payload, String describedAs)
	{
		if (config.sendScreenshot())
		{
			drawManager.requestNextFrameListener(image -> post(payload, describedAs, png(image)));
		}
		else
		{
			post(payload, describedAs, null);
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

	private void post(JsonObject payload, String describedAs, @Nullable byte[] screenshot)
	{
		String json = gson.toJson(payload);
		RequestBody body = screenshot == null
			? RequestBody.create(json, JSON)
			: new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart("payload_json", json)
				.addFormDataPart("file", "screenshot.png", RequestBody.create(screenshot, PNG))
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
				report(describedAs, false);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				report(describedAs, response.isSuccessful());
				response.close();
			}
		});
	}

	private void report(String what, boolean ok)
	{
		VeritasEventsPanel p = panel;
		if (p != null)
		{
			p.record(what, ok);
		}
	}
}
