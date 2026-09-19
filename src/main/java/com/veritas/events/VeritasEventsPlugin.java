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
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.DrawManager;
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
	description = "Sends your drops to a Veritas clan event board",
	tags = {"veritas", "event", "bingo", "loot", "clan"}
)
public class VeritasEventsPlugin extends Plugin
{
	private static final MediaType JSON = MediaType.get("application/json");
	private static final int PAYLOAD_VERSION = 1;

	/** Chat lines the game sends when a pet appears. */
	private static final String[] PET_MESSAGES = {
		"you have a funny feeling like you're being followed",
		"you feel something weird sneaking into your backpack",
		"you have a funny feeling like you would have been followed"
	};
	private static final String COLLECTION_LOG_PREFIX = "new item added to your collection log:";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

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

	@Provides
	VeritasEventsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(VeritasEventsConfig.class);
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (!config.sendLoot() || notConfigured())
		{
			return;
		}

		clientThread.invokeLater(() ->
		{
			JsonArray items = new JsonArray();
			long total = 0;

			for (ItemStack stack : safe(event.getItems()))
			{
				ItemComposition comp = itemManager.getItemComposition(stack.getId());
				long each = (long) itemManager.getItemPrice(stack.getId());
				long worth = each * stack.getQuantity();
				total += worth;

				JsonObject item = new JsonObject();
				item.addProperty("id", stack.getId());
				item.addProperty("name", comp.getName());
				item.addProperty("quantity", stack.getQuantity());
				item.addProperty("priceEach", each);
				items.add(item);
			}

			if (items.size() == 0 || total < config.minimumValue())
			{
				return;
			}

			JsonObject payload = base("LOOT");
			payload.addProperty("source", event.getName());
			payload.addProperty("sourceType", String.valueOf(event.getType()));
			payload.addProperty("quantity", event.getAmount());
			payload.addProperty("totalValue", total);
			payload.add("items", items);
			send(payload);
		});
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (notConfigured()
			|| (event.getType() != ChatMessageType.GAMEMESSAGE
			&& event.getType() != ChatMessageType.SPAM))
		{
			return;
		}

		String message = Text.removeTags(event.getMessage()).toLowerCase();

		if (config.sendPets() && isPetMessage(message))
		{
			send(base("PET"));
			return;
		}

		if (config.sendCollectionLog() && message.startsWith(COLLECTION_LOG_PREFIX))
		{
			String item = Text.removeTags(event.getMessage())
				.substring(COLLECTION_LOG_PREFIX.length())
				.trim();

			JsonObject payload = base("COLLECTION_LOG");
			payload.addProperty("item", item);
			send(payload);
		}
	}

	private static boolean isPetMessage(String message)
	{
		for (String line : PET_MESSAGES)
		{
			if (message.contains(line))
			{
				return true;
			}
		}
		return false;
	}

	/** Fields every payload carries. */
	private JsonObject base(String type)
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("v", PAYLOAD_VERSION);
		payload.addProperty("type", type);
		payload.addProperty("player", playerName());
		payload.addProperty("sentAt", System.currentTimeMillis());
		return payload;
	}

	private String playerName()
	{
		Player local = client.getLocalPlayer();
		return local == null ? "" : local.getName();
	}

	private boolean notConfigured()
	{
		return config.eventUrl() == null || config.eventUrl().trim().isEmpty();
	}

	private static <T> Collection<T> safe(@Nullable Collection<T> in)
	{
		return in == null ? new ArrayList<>() : in;
	}

	/** Posts the payload, with a screenshot attached when the player has asked for one. */
	private void send(JsonObject payload)
	{
		if (!config.sendScreenshot())
		{
			post(payload, null);
			return;
		}

		drawManager.requestNextFrameListener(image -> post(payload, toPng(image)));
	}

	@Nullable
	private static byte[] toPng(Image image)
	{
		try
		{
			BufferedImage buffered = new BufferedImage(
				image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
			buffered.getGraphics().drawImage(image, 0, 0, null);

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(buffered, "png", out);
			return out.toByteArray();
		}
		catch (Exception e)
		{
			log.debug("could not capture a screenshot", e);
			return null;
		}
	}

	private void post(JsonObject payload, @Nullable byte[] screenshot)
	{
		String url = config.eventUrl().trim();
		String json = gson.toJson(payload);

		RequestBody body;
		if (screenshot == null)
		{
			body = RequestBody.create(JSON, json);
		}
		else
		{
			body = new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart("payload_json", json)
				.addFormDataPart("file", "screenshot.png",
					RequestBody.create(MediaType.get("image/png"), screenshot))
				.build();
		}

		Request.Builder request = new Request.Builder().url(url).post(body);
		String key = config.eventKey();
		if (key != null && !key.trim().isEmpty())
		{
			request.header("X-Event-Key", key.trim());
		}

		okHttpClient.newCall(request.build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Veritas Events: could not reach the event server", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				if (!response.isSuccessful())
				{
					log.warn("Veritas Events: the event server replied {}", response.code());
				}
				response.close();
			}
		});
	}
}
