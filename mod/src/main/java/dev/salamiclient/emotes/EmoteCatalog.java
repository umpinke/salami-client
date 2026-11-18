package dev.salamiclient.emotes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.capes.EmoteSync;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Alle Emotes, die es gerade gibt. Die Liste kommt vom Salami-Server ({@code GET /v1/emotes/catalog}),
 * damit neue Emotes ohne Update bei allen ankommen: geladen beim Betreten einer Welt, danach alle
 * 15 Minuten und sofort, wenn der Server eine Änderung meldet. Solange der Server nicht erreichbar
 * ist (oder ohne Launcher), gilt die Kopie aus der Mod ({@code assets/salami/emotes.json} – beim
 * Bauen aus {@code server/capes/emotes.json} genommen).
 *
 * <p>Alles auf dem Client-Thread.
 */
public final class EmoteCatalog {
	/** Wie auf dem Server ({@code MAX_EMOTES}). */
	private static final int MAX_EMOTES = 64;
	private static final String BUILTIN = "/assets/salami/emotes.json";
	private static final long REFRESH_MS = 15 * 60_000L;
	private static final long RETRY_MS = 2 * 60_000L;
	/** Mindestabstand zwischen zwei Abrufen, auch wenn der Server mehrmals „geändert“ meldet. */
	private static final long MIN_GAP_MS = 5_000L;

	private static @Nullable List<Emote> builtin;
	private static @Nullable List<Emote> remote;
	/** Stand des Server-Katalogs ({@code version}); gleicher Stand = nichts neu aufbauen. */
	private static @Nullable String remoteVersion;
	private static int revision;
	private static boolean fetching;
	private static long nextFetchAt;
	private static long lastFetchAt;

	private EmoteCatalog() {}

	/** Alle Emotes in Anzeige-Reihenfolge: die vom Server, sonst die mitgelieferten. */
	public static List<Emote> all() {
		List<Emote> fromServer = remote;
		return fromServer != null ? fromServer : builtin();
	}

	public static @Nullable Emote get(@Nullable String id) {
		if (id == null) {
			return null;
		}
		for (Emote emote : all()) {
			if (emote.id().equals(id)) {
				return emote;
			}
		}
		return null;
	}

	/** Zählt hoch, wenn sich die Liste ändert – Menüs bauen sich dann neu auf. */
	public static int revision() {
		return revision;
	}

	private static List<Emote> builtin() {
		List<Emote> list = builtin;
		if (list == null) {
			list = List.of();
			try (InputStream in = EmoteCatalog.class.getResourceAsStream(BUILTIN)) {
				if (in != null) {
					try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
						list = parse(JsonParser.parseReader(reader).getAsJsonObject());
					}
				}
			} catch (Exception e) {
				SalamiClient.LOG.warn("Mitgelieferte Emotes nicht lesbar: {}", e.toString());
			}
			if (list.isEmpty()) {
				SalamiClient.LOG.warn("Keine mitgelieferten Emotes gefunden ({})", BUILTIN);
			}
			builtin = list;
		}
		return list;
	}

	/**
	 * Liest einen Katalog ({@code {"emotes":[…]}}). Fehlerhafte oder doppelte Einträge werden
	 * übersprungen, damit ein kaputtes Emote nicht alle anderen mitnimmt.
	 */
	public static List<Emote> parse(JsonObject root) {
		JsonElement list = root.get("emotes");
		List<Emote> emotes = new ArrayList<>();
		if (list == null || !list.isJsonArray()) {
			return emotes;
		}
		Set<String> seen = new HashSet<>();
		for (JsonElement element : list.getAsJsonArray()) {
			if (emotes.size() >= MAX_EMOTES) {
				break;
			}
			try {
				Emote emote = Emote.parse(element.getAsJsonObject());
				if (seen.add(emote.id())) {
					emotes.add(emote);
				}
			} catch (RuntimeException e) {
				SalamiClient.LOG.info("Emote übersprungen: {}", e.getMessage());
			}
		}
		return List.copyOf(emotes);
	}

	/**
	 * Katalog vom Server übernehmen (auch für Gametests). Ein leerer oder kaputter Katalog ändert
	 * nichts – dann bleiben die bisherigen Emotes.
	 */
	public static boolean applyRemote(JsonObject root) {
		JsonElement rawVersion = root.get("version");
		String version = rawVersion != null && rawVersion.isJsonPrimitive() ? rawVersion.getAsString() : null;
		if (version != null && version.equals(remoteVersion) && remote != null) {
			return true;
		}
		List<Emote> emotes = parse(root);
		if (emotes.isEmpty()) {
			return false;
		}
		remote = emotes;
		remoteVersion = version;
		revision++;
		return true;
	}

	/** Zurück zu den mitgelieferten Emotes (Gametests). */
	public static void clearRemote() {
		if (remote != null) {
			remote = null;
			remoteVersion = null;
			revision++;
		}
	}

	/** Bald neu laden: der Server hat eine Änderung gemeldet oder ein unbekanntes Emote kam an. */
	public static void refreshSoon() {
		nextFetchAt = Math.min(nextFetchAt, lastFetchAt + MIN_GAP_MS);
	}

	/** Vom Modul „Emotes“ pro Tick in einer Welt: lädt den Katalog, wenn es Zeit ist. */
	public static void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		if (fetching || now < nextFetchAt) {
			return;
		}
		CompletableFuture<JsonObject> request = EmoteSync.catalog();
		lastFetchAt = now;
		if (request == null) {
			// ohne Cape-Server (Start ohne Launcher): mitgelieferte Emotes, später nochmal nachsehen
			nextFetchAt = now + REFRESH_MS;
			return;
		}
		fetching = true;
		request.whenComplete((json, error) -> mc.execute(() -> {
			fetching = false;
			long done = System.currentTimeMillis();
			if (error != null || json == null) {
				nextFetchAt = done + RETRY_MS;
				SalamiClient.LOG.debug("Emote-Katalog nicht geladen: {}", String.valueOf(error));
				return;
			}
			nextFetchAt = done + REFRESH_MS;
			int before = all().size();
			if (applyRemote(json) && all().size() != before) {
				SalamiClient.LOG.info("Emote-Katalog vom Salami-Server: {} Emotes", all().size());
			}
		}));
	}
}
