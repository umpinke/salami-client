package dev.salamiclient.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.capes.CatalogSync;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Eine Liste von Cosmetics, die vom Salami-Server kommt ({@code GET /v1/<kind>/catalog}), damit Neues
 * ohne Update bei allen ankommt: geladen beim Start, danach alle 15 Minuten und sofort, wenn der
 * Server eine Änderung meldet. Solange der Server nicht erreichbar ist (oder ohne Launcher), gilt
 * die Kopie aus der Mod – beim Bauen aus {@code server/capes/} genommen.
 *
 * <p>Alles auf dem Client-Thread.
 */
public final class RemoteCatalog<T> {
	private static final long REFRESH_MS = 15 * 60_000L;
	private static final long RETRY_MS = 2 * 60_000L;
	/** Mindestabstand zwischen zwei Abrufen, auch wenn der Server mehrmals „geändert“ meldet. */
	private static final long MIN_GAP_MS = 5_000L;

	private final String kind;
	private final String label;
	private final int max;
	private final @Nullable String bundled;
	private final boolean mayBeEmpty;
	private final Function<JsonObject, T> parser;
	private final Function<T, String> idOf;

	private @Nullable List<T> builtin;
	private @Nullable List<T> remote;
	/** Stand des Server-Katalogs ({@code version}); gleicher Stand = nichts neu aufbauen. */
	private @Nullable String remoteVersion;
	private @Nullable Map<String, T> index;
	private int revision;
	private boolean fetching;
	private long nextFetchAt;
	private long lastFetchAt;

	/**
	 * @param kind       Name beim Server und Schlüssel der Liste im JSON ({@code emotes}, {@code hats}, {@code capes})
	 * @param label      fürs Log („Emote“)
	 * @param max        höchstens so viele Einträge, wie auf dem Server
	 * @param bundled    Kopie in der Jar ({@code /assets/salami/emotes.json}) oder {@code null}
	 * @param mayBeEmpty ob eine leere Liste vom Server gilt (sonst bleibt die bisherige)
	 * @param parser     liest einen Eintrag, wirft {@link RuntimeException} bei Fehlern
	 */
	public RemoteCatalog(String kind, String label, int max, @Nullable String bundled, boolean mayBeEmpty,
			Function<JsonObject, T> parser, Function<T, String> idOf) {
		this.kind = kind;
		this.label = label;
		this.max = max;
		this.bundled = bundled;
		this.mayBeEmpty = mayBeEmpty;
		this.parser = parser;
		this.idOf = idOf;
	}

	/** Alle Einträge in Anzeige-Reihenfolge: die vom Server, sonst die mitgelieferten. */
	public List<T> all() {
		List<T> fromServer = remote;
		return fromServer != null ? fromServer : builtin();
	}

	public @Nullable T get(@Nullable String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		Map<String, T> byId = index;
		if (byId == null) {
			byId = new HashMap<>();
			for (T entry : all()) {
				byId.put(idOf.apply(entry), entry);
			}
			index = byId;
		}
		return byId.get(id);
	}

	/** Zählt hoch, wenn sich die Liste ändert – Menüs bauen sich dann neu auf. */
	public int revision() {
		return revision;
	}

	private List<T> builtin() {
		List<T> list = builtin;
		if (list == null) {
			list = List.of();
			if (bundled != null) {
				try (InputStream in = RemoteCatalog.class.getResourceAsStream(bundled)) {
					if (in != null) {
						try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
							list = parse(JsonParser.parseReader(reader).getAsJsonObject());
						}
					}
				} catch (Exception e) {
					SalamiClient.LOG.warn("Mitgelieferter {}-Katalog nicht lesbar: {}", label, e.toString());
				}
				if (list.isEmpty()) {
					SalamiClient.LOG.warn("Mitgelieferter {}-Katalog ist leer ({})", label, bundled);
				}
			}
			builtin = list;
			index = null;
		}
		return list;
	}

	/**
	 * Liest einen Katalog ({@code {"<kind>":[…]}}). Fehlerhafte oder doppelte Einträge werden
	 * übersprungen, damit ein kaputter Eintrag nicht alle anderen mitnimmt.
	 */
	public List<T> parse(JsonObject root) {
		JsonElement list = root.get(kind);
		List<T> entries = new ArrayList<>();
		if (list == null || !list.isJsonArray()) {
			return entries;
		}
		Map<String, T> seen = new HashMap<>();
		for (JsonElement element : list.getAsJsonArray()) {
			if (entries.size() >= max) {
				break;
			}
			try {
				T entry = parser.apply(element.getAsJsonObject());
				if (seen.putIfAbsent(idOf.apply(entry), entry) == null) {
					entries.add(entry);
				}
			} catch (RuntimeException e) {
				SalamiClient.LOG.info("{} übersprungen: {}", label, e.getMessage());
			}
		}
		return List.copyOf(entries);
	}

	/**
	 * Katalog vom Server übernehmen (auch für Gametests). Ein kaputter Katalog ändert nichts – dann
	 * bleiben die bisherigen Einträge; dasselbe gilt für einen leeren, wenn leer hier nicht vorkommt.
	 */
	public boolean applyRemote(JsonObject root) {
		JsonElement rawVersion = root.get("version");
		String version = rawVersion != null && rawVersion.isJsonPrimitive() ? rawVersion.getAsString() : null;
		if (version != null && version.equals(remoteVersion) && remote != null) {
			return true;
		}
		JsonElement list = root.get(kind);
		if (list == null || !list.isJsonArray()) {
			return false;
		}
		List<T> entries = parse(root);
		if (entries.isEmpty() && !mayBeEmpty) {
			return false;
		}
		remote = entries;
		remoteVersion = version;
		index = null;
		revision++;
		return true;
	}

	/** Zurück zu den mitgelieferten Einträgen (Gametests). */
	public void clearRemote() {
		if (remote != null) {
			remote = null;
			remoteVersion = null;
			index = null;
			revision++;
		}
	}

	/** Bald neu laden: der Server hat eine Änderung gemeldet oder ein unbekannter Eintrag kam an. */
	public void refreshSoon() {
		nextFetchAt = Math.min(nextFetchAt, lastFetchAt + MIN_GAP_MS);
	}

	/** Jeden Client-Tick: lädt den Katalog, wenn es Zeit ist. */
	public void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		if (fetching || now < nextFetchAt) {
			return;
		}
		CompletableFuture<JsonObject> request = CatalogSync.fetch(kind);
		lastFetchAt = now;
		if (request == null) {
			// ohne Cape-Server (Start ohne Launcher): mitgelieferte Einträge, später nochmal nachsehen
			nextFetchAt = now + REFRESH_MS;
			return;
		}
		fetching = true;
		request.whenComplete((json, error) -> mc.execute(() -> {
			fetching = false;
			long done = System.currentTimeMillis();
			if (error != null || json == null) {
				nextFetchAt = done + RETRY_MS;
				SalamiClient.LOG.debug("{}-Katalog nicht geladen: {}", label, String.valueOf(error));
				return;
			}
			nextFetchAt = done + REFRESH_MS;
			int before = revision;
			if (applyRemote(json) && revision != before) {
				SalamiClient.LOG.info("{}-Katalog vom Salami-Server: {} Einträge", label, all().size());
			}
		}));
	}
}
