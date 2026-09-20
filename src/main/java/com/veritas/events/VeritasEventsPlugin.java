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
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.inject.Inject;
import javax.swing.ImageIcon;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
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
import net.runelite.client.ui.overlay.OverlayManager;
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
	private static final String WISE_OLD_MAN = "https://api.wiseoldman.net/v2/groups/";
	private static final MediaType JSON = MediaType.get("application/json");
	private static final MediaType JPEG = MediaType.get("image/jpeg");
	private static final int MAX_WIDTH = 1920;
	private static final float QUALITY = 0.85f;

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

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private VeritasEventsOverlay overlay;

	@Inject
	private ScheduledExecutorService executor;

	private VeritasEventsPanel panel;
	private NavigationButton navButton;
	private Runnable lastSend;
	private ScheduledFuture<?> refresher;
	private String boardPassword = "";

	@Provides
	VeritasEventsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(VeritasEventsConfig.class);
	}

	@Override
	protected void startUp()
	{
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		panel = new VeritasEventsPanel(config, itemManager, new ImageIcon(icon),
			this::resend, this::refreshEvent, this::gained);
		navButton = NavigationButton.builder()
			.tooltip("Veritas Events")
			.icon(icon)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);
		refreshEvent();
		reschedule();
	}

	@Override
	protected void shutDown()
	{
		if (refresher != null)
		{
			refresher.cancel(false);
			refresher = null;
		}
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		Player local = client.getLocalPlayer();
		if (event.getGameState() == GameState.LOGGED_IN && local != null && panel != null)
		{
			panel.setPlayer(Text.sanitize(local.getName()));
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (VeritasEventsConfig.GROUP.equals(event.getGroup()) && panel != null)
		{
			panel.refresh();
			refreshEvent();
			reschedule();
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
		if (event.getType() != ChatMessageType.GAMEMESSAGE
			|| !config.sendPets() || config.eventUrl().trim().isEmpty())
		{
			return;
		}

		String message = Text.removeTags(event.getMessage());
		if (message.toLowerCase().contains("funny feeling like"))
		{
			JsonObject payload = payload("PET");
			payload.addProperty("message", message);
			send(payload, message, Collections.emptyList(), 0);
		}
	}


	private JsonObject payload(String type)
	{
		Player local = client.getLocalPlayer();
		JsonObject payload = new JsonObject();
		payload.addProperty("type", type);
		payload.addProperty("player", local == null ? "" : Text.sanitize(local.getName()));
		payload.addProperty("sentAt", System.currentTimeMillis());
		return payload;
	}

	private void send(JsonObject payload, String source, List<int[]> icons, long value)
	{
		lastSend = () -> post(payload, source, icons, value, null);
		if (config.sendScreenshot())
		{
			drawManager.requestNextFrameListener(image -> post(payload, source, icons, value, jpeg(image)));
		}
		else
		{
			post(payload, source, icons, value, null);
		}
	}

	/**
	 * A full colour PNG of the game runs to about half a megabyte, which is a lot
	 * to keep for every drop of an event. A JPEG of the same frame is about a
	 * fifth of that and still sharp enough to read the chatbox, so drops stay
	 * verifiable afterwards. Only very large clients are scaled down at all.
	 */
	@Nullable
	private static byte[] jpeg(Image image)
	{
		try
		{
			int width = image.getWidth(null);
			int height = image.getHeight(null);
			if (width <= 0 || height <= 0)
			{
				return null;
			}

			double scale = Math.min(1.0, (double) MAX_WIDTH / width);
			int scaledWidth = (int) Math.round(width * scale);
			int scaledHeight = (int) Math.round(height * scale);

			// JPEG has no alpha channel, so draw onto an opaque image first.
			BufferedImage copy = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
			Graphics2D graphics = copy.createGraphics();
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.drawImage(image, 0, 0, scaledWidth, scaledHeight, null);
			graphics.dispose();

			ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
			ImageWriteParam params = writer.getDefaultWriteParam();
			params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			params.setCompressionQuality(QUALITY);

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (ImageOutputStream stream = ImageIO.createImageOutputStream(out))
			{
				writer.setOutput(stream);
				writer.write(null, new IIOImage(copy, null, null), params);
			}
			finally
			{
				writer.dispose();
			}
			return out.toByteArray();
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not capture a screenshot", e);
			return null;
		}
	}

	/**
	 * The word shown on screen. What you typed wins, so it is never overwritten
	 * as the standings refresh; the board only fills it in when you left it blank.
	 */
	String password()
	{
		String typed = config.eventPassword().trim();
		return typed.isEmpty() ? boardPassword : typed;
	}

	/** Asks the board for the standings again every few minutes. */
	private void reschedule()
	{
		if (refresher != null)
		{
			refresher.cancel(false);
		}
		int minutes = Math.max(1, config.refreshMinutes());
		refresher = executor.scheduleWithFixedDelay(this::refreshEvent, minutes, minutes, TimeUnit.MINUTES);
	}

	/**
	 * Asks Wise Old Man what the clan has gained over a period. Their API is
	 * public and read only, so this needs no key and goes straight out rather
	 * than through the event board.
	 */
	void gained(String metric, String period)
	{
		VeritasEventsPanel p = panel;
		int group = config.womGroupId();
		if (p == null || group <= 0)
		{
			return;
		}

		Request request = new Request.Builder()
			.url(WISE_OLD_MAN + group + "/gained?metric=" + metric + "&period=" + period + "&limit=25")
			.header("User-Agent", "veritas-events RuneLite plugin")
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("no gains from Wise Old Man", e);
				p.setGained(null, "Could not reach Wise Old Man.");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					p.setGained(gson.fromJson(body.string(), JsonArray.class), null);
				}
				catch (Exception e)
				{
					log.debug("could not read the gains", e);
					p.setGained(null, "Wise Old Man's answer could not be read.");
				}
			}
		});
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
		boardPassword = "";
		p.setEvent(null, null);
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
				p.setEvent(null, "Could not reach the board. Check the event URL.");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					JsonObject details = gson.fromJson(body.string(), JsonObject.class);
					boardPassword = details != null && details.has("password")
						? details.get("password").getAsString() : "";
					p.setEvent(details, null);
				}
				catch (Exception e)
				{
					log.warn("could not read event details", e);
					p.setEvent(null, "The board answered, but not with readable JSON.");
				}
			}
		});
	}


	private void post(JsonObject payload, String source, List<int[]> icons, long value, @Nullable byte[] screenshot)
	{
		String json = gson.toJson(payload);
		RequestBody body = screenshot == null
			? RequestBody.create(JSON, json)
			: new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart("payload_json", json)
				.addFormDataPart("file", "screenshot.jpg", RequestBody.create(JPEG, screenshot))
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
