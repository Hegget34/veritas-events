/*
 * Copyright (c) 2026, Veritas
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE for details.
 */
package com.veritas.events;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.runelite.api.events.GameTick;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.LootRecordType;
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
	name = "Veritas",
	description = "Clan pages, stats and event drops",
	tags = {"veritas", "event", "bingo", "loot", "clan"}
)
public class VeritasEventsPlugin extends Plugin
{
	private static final String WISE_OLD_MAN = "https://api.wiseoldman.net/v2/groups/";

	/** The teal off the clan's own logo, for the lines it writes in chat. */
	private static final Color VERITAS = new Color(0x5F, 0xA8, 0xD3);

	/** For the lines that mean something did not work. */
	private static final Color TROUBLE = new Color(0xD3, 0x5F, 0x5F);

	/**
	 * Sources that are a conversion rather than a drop, lower cased.
	 *
	 * Cleaning a tarnished item is reported as loot named after the tarnished
	 * one, which already sits under whatever dropped it.
	 */
	private static final String[] NOT_LOOT = {"tarnished"};
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
	private ConfigManager configManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private VeritasEventsOverlay overlay;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Notifier notifier;

	private VeritasEventsPanel panel;
	private NavigationButton navButton;
	private Runnable lastSend;
	private ScheduledFuture<?> refresher;
	private String boardPassword = "";

	/**
	 * The event the board says is running, by name.
	 *
	 * Only so the plugin can say when one starts and when it ends. It is
	 * deliberately the name and not the address: nothing here may learn a
	 * host from a server's answer.
	 */
	private String liveEvent = "";
	private boolean toldAboutEvent;
	private boolean askedByHand;

	/** Who the panel currently believes is playing. */
	private String knownAs = "";

	/**
	 * Kills of each monster this client has watched, since it started.
	 *
	 * Wise Old Man knows the week's total but is minutes behind; this is the
	 * part that is live, and the two are shown side by side rather than added
	 * together, because they overlap and nobody could say by how much.
	 */
	private final Map<String, Integer> killsSeen = new HashMap<>();

	/**
	 * The items the running event is after, lower cased. While the board
	 * publishes a list, only drops containing one of them are sent; everything
	 * else stays on this machine in the loot tracker where it belongs.
	 */
	private Set<String> wanted = Collections.emptySet();

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
			this::resend, this::refreshByHand, this::gained, this::lootTrackerOff,
			gson, configManager);
		navButton = NavigationButton.builder()
			.tooltip("Veritas")
			.icon(icon)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);
		refresh();
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
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			knownAs = "";
		}
	}

	/**
	 * Keeps the name in the panel in step with who is playing.
	 *
	 * It used to be read once, when the client said it had logged in, but the
	 * local player is very often still null at that moment, and nothing came
	 * along afterwards to try again. So the panel said Not logged in to
	 * somebody who plainly was.
	 *
	 * A tick is cheap and this does nothing at all unless the name has
	 * actually changed.
	 */
	@Subscribe
	public void onGameTick(GameTick tick)
	{
		Player local = client.getLocalPlayer();
		if (local == null || panel == null)
		{
			return;
		}
		String now = Text.sanitize(String.valueOf(local.getName()));
		if (!now.isEmpty() && !now.equals(knownAs))
		{
			knownAs = now;
			panel.setPlayer(now);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (VeritasEventsConfig.GROUP.equals(event.getGroup()) && panel != null)
		{
			// the loot tab reads settings of its own, so it has to be redrawn
			// too; without this, turning on the example drops did nothing
			panel.refresh();
			panel.redraw();
			refresh();
			reschedule();
		}
	}

	/**
	 * Whether this drop is worth sending. With no list published, everything
	 * goes; with one, only drops holding something on it.
	 */
	private boolean onTheList(JsonArray items)
	{
		if (wanted.isEmpty())
		{
			return true;
		}
		for (JsonElement element : items)
		{
			JsonObject item = element.getAsJsonObject();
			if (item.has("name") && wanted.contains(item.get("name").getAsString().toLowerCase()))
			{
				return true;
			}
		}
		return false;
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		log.debug("npc loot: {}", event.getComposition().getName());
		loot(event.getComposition().getName(), event.getItems());
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event)
	{
		loot(Text.sanitize(event.getPlayer().getName()), event.getItems());
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		// Kills come straight from the client above, so this is only for what
		// RuneLite's Loot Tracker alone can see: chests, raids, pickpocketing.
		if (event.getType() != LootRecordType.NPC && event.getType() != LootRecordType.PLAYER)
		{
			log.debug("other loot: {} as {}", event.getName(), event.getType());
			if (!ignored(event.getName()))
			{
				loot(event.getName(), event.getItems());
			}
		}
	}

	/**
	 * Whether this is a conversion rather than a drop.
	 *
	 * Turning one item into another reaches RuneLite as loot in its own right,
	 * named after what went in. Cleaning a tarnished spear is the case that
	 * prompted this: the spear is already recorded under the monster that
	 * dropped it, so a second box for the cleaned version is noise.
	 */
	private static boolean ignored(String source)
	{
		String name = String.valueOf(source).toLowerCase();
		for (String word : NOT_LOOT)
		{
			if (name.contains(word))
			{
				return true;
			}
		}
		return false;
	}

	/** Everything is tracked; only some of it is worth sending anywhere. */
	private void loot(String source, Collection<ItemStack> stacks)
	{
		JsonArray items = new JsonArray();
		List<int[]> icons = new ArrayList<>();
		long total = 0;

		for (ItemStack stack : stacks)
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

		log.debug("loot from {}: {} items worth {}", source, items.size(), total);

		synchronized (killsSeen)
		{
			String key = String.valueOf(source).toLowerCase();
			killsSeen.merge(key, 1, Integer::sum);
			if (panel != null)
			{
				panel.setKillsSeen(new HashMap<>(killsSeen));
			}
		}

		if (items.size() == 0)
		{
			return;
		}

		String why = whyKept(total, items);
		if (!why.isEmpty())
		{
			report(source, icons, total, VeritasEventsPanel.KEPT, why);
			return;
		}

		JsonObject payload = payload("LOOT");
		payload.addProperty("source", source);
		payload.addProperty("totalValue", total);
		payload.addProperty("big", total >= config.bigDropValue());
		payload.add("items", items);
		send(payload, source, icons, total);
	}

	/**
	 * Why a drop is not going anywhere, or nothing if it is.
	 *
	 * Four quite different situations ended up as the same "Not sent", which
	 * told the player what had happened and never why, when why is the only
	 * part they can do anything about.
	 */
	private String whyKept(long total, JsonArray items)
	{
		if (!config.sendLoot())
		{
			return "Sending drops is switched off in the settings.";
		}
		if (eventAddress().isEmpty())
		{
			// The ordinary case, and the ordinary case needs no remark.
			return " ";
		}
		if (total < config.minimumValue())
		{
			return "Under the minimum you set, so the event was not troubled with it.";
		}
		if (!onTheList(items))
		{
			return "Nothing here is on the event's list of wanted items.";
		}
		return "";
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE
			|| !config.sendPets() || eventAddress().isEmpty())
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
	 * as the standings refresh. Left blank, the board sets it for everyone, and
	 * with no board either it falls back to the clan name.
	 */
	String password()
	{
		String typed = config.eventPassword().trim();
		if (!typed.isEmpty())
		{
			return typed;
		}
		// Falling back to the clan name keeps the stamp on every screenshot, so
		// one is never taken without something to date it by.
		return boardPassword.isEmpty() ? "Veritas" : boardPassword;
	}

	/** Both boards: the clan's own, and whichever event is running. */
	private void refresh()
	{
		refreshClan();
		refreshEvent();
	}

	/**
	 * The same, asked for by a person rather than by the timer.
	 *
	 * The board keeps its answer for a few minutes so that 326 plugins asking
	 * every few minutes does not rebuild it 326 times. That is right for the
	 * timer and wrong for a button, which should show what staff just saved,
	 * so this one says it is asking by hand.
	 */
	private void refreshByHand()
	{
		askedByHand = true;
		refresh();
	}

	/**
	 * The clan's own pages. Nothing here belongs to an event, so it is asked for
	 * whether or not one is running and outlives any that is.
	 */
	private void refreshClan()
	{
		VeritasEventsPanel p = panel;
		String url = config.clanUrl().trim();
		if (p == null)
		{
			return;
		}
		if (url.isEmpty())
		{
			p.setClan(null);
			return;
		}

		if (askedByHand)
		{
			askedByHand = false;
			url += (url.contains("?") ? "&" : "?") + "fresh=1";
		}

		okHttpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				// One refresh not arriving is not news that the board has no
				// pages. Throwing them away emptied the chooser until the next
				// one came back, which looked like tabs coming and going.
				// Keep the pages and say nothing: one refresh not arriving is
				// not worth a line in the header, and the next one usually does.
				log.debug("no clan pages this time, keeping the last ones", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					JsonObject clan = gson.fromJson(body.string(), JsonObject.class);
					p.setClan(clan);
					noticeEvent(clan != null && clan.has("liveEventName")
						? clan.get("liveEventName").getAsString().trim() : "");
				}
				catch (Exception e)
				{
					log.debug("could not read the clan pages, keeping the last ones", e);
				}
			}
		});
	}

	/**
	 * Says when an event starts and when it finishes.
	 *
	 * The first answer after the client starts is not announced, otherwise
	 * every login would report an event that has been running for days.
	 */
	private void noticeEvent(String running)
	{
		if (running.equals(liveEvent))
		{
			return;
		}

		String was = liveEvent;
		liveEvent = running;

		if (!toldAboutEvent)
		{
			toldAboutEvent = true;
			return;
		}
		if (!config.announceEvents())
		{
			return;
		}

		if (!running.isEmpty())
		{
			notifier.notify(running + " has started.");
			say(running + " is running now. Put the address in the settings to take part.", true);
		}
		else if (!was.isEmpty())
		{
			notifier.notify(was + " has finished.");
			say(was + " has finished. You can clear the event address now.", true);
		}
	}

	/** A line in the chat box, in the clan's own colours. */
	private void say(String words, boolean good)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(VERITAS, "[Veritas] ")
				.append(good ? Color.WHITE : TROUBLE, words)
				.build())
			.build());
	}

	/**
	 * Where event drops go: the address typed into the settings, and nowhere
	 * else. Blank means the event side of the plugin sits idle.
	 */
	private String eventAddress()
	{
		return config.eventUrl().trim();
	}

	/** The key that event's board wants, also from the settings. */
	private String eventSecret()
	{
		return config.eventKey().trim();
	}

	/** Asks the board for the standings again every few minutes. */
	private void reschedule()
	{
		if (refresher != null)
		{
			refresher.cancel(false);
		}
		int minutes = Math.max(1, config.refreshMinutes());
		refresher = executor.scheduleWithFixedDelay(this::refresh, minutes, minutes, TimeUnit.MINUTES);
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

	/**
	 * Drops reach us as RuneLite's own LootReceived, which only its Loot Tracker
	 * plugin ever posts. With that switched off nothing arrives and there is no
	 * error to show for it, so say so rather than look broken.
	 */
	boolean lootTrackerOff()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
			if (descriptor != null && "Loot Tracker".equals(descriptor.name()))
			{
				return !pluginManager.isPluginEnabled(plugin);
			}
		}
		return false;
	}

	/** The names in the board's "wanted" list, if it published one. */
	private static Set<String> wantedFrom(@Nullable JsonObject details)
	{
		if (details == null || !details.has("wanted") || !details.get("wanted").isJsonArray())
		{
			return Collections.emptySet();
		}

		Set<String> names = new HashSet<>();
		for (JsonElement element : details.getAsJsonArray("wanted"))
		{
			JsonObject item = element.getAsJsonObject();
			if (item.has("name"))
			{
				names.add(item.get("name").getAsString().toLowerCase());
			}
		}
		return names;
	}

	/** The start of whatever came back, so a misconfigured board says so itself. */
	private static String summary(String answer)
	{
		// The hint this ends up in is rendered as HTML, and a misbehaving board
		// often answers with an HTML error page, so keep it printable and inert.
		StringBuilder clean = new StringBuilder();
		for (int i = 0; i < answer.length(); i++)
		{
			if (clean.length() >= 80)
			{
				clean.append("...");
				break;
			}
			char c = answer.charAt(i);
			clean.append(c < ' ' || c == '<' || c == '>' || c == '&' ? ' ' : c);
		}

		String trimmed = clean.toString().trim();
		return trimmed.isEmpty() ? "nothing at all." : trimmed;
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
		String url = eventAddress();
		if (p == null)
		{
			return;
		}
		boardPassword = "";
		wanted = Collections.emptySet();
		p.setEvent(null, null);
		if (url.isEmpty())
		{
			return;
		}

		Request request = new Request.Builder()
			.url(url)
			.header("X-Event-Key", eventSecret())
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
				String answer = "";
				try (ResponseBody body = response.body())
				{
					answer = body.string();
					JsonObject details = gson.fromJson(answer, JsonObject.class);
					boardPassword = details != null && details.has("password")
						? details.get("password").getAsString() : "";
					wanted = wantedFrom(details);
					p.setEvent(details, null);
				}
				catch (Exception e)
				{
					log.warn("could not read event details: {}", answer, e);
					p.setEvent(null, "The board answered with: " + summary(answer));
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
			.url(eventAddress())
			.header("X-Event-Key", eventSecret())
			.post(body)
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("could not reach the event board", e);
				if (config.announceDrops())
				{
					say("could not reach the event board. Press Send again to retry.", false);
				}
				report(source, icons, value, VeritasEventsPanel.FAILED,
					"Could not reach the event. Press Send again when you are back online.");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				boolean took = response.isSuccessful();
				if (config.announceDrops())
				{
					say(took ? source + " counted for the event."
						: "the event board would not take that drop.", took);
				}
				report(source, icons, value,
					took ? VeritasEventsPanel.SENT : VeritasEventsPanel.FAILED,
					took ? "Counted for the event."
						: "The event would not take this one. Press Send again.");
				response.close();
			}
		});
	}

	private void report(String source, List<int[]> icons, long value, int state, String why)
	{
		VeritasEventsPanel p = panel;
		if (p != null)
		{
			p.record(source, icons, value, state, why);
		}
	}
}
