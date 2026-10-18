package dev.salamiclient.capes;

import com.mojang.blaze3d.platform.NativeImage;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.modules.visual.CapesModule;
import dev.salamiclient.util.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.PngInfo;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Salami Capes für alle: zeigt die Capes anderer Salami-Spieler und teilt das eigene
 * über den Cape-Server ({@link CapeServer}). Mitgelieferte Capes sehen andere sofort,
 * eigene Bilder erst, nachdem ein Admin sie auf der Prüf-Webseite freigegeben hat.
 *
 * <p>Alles hier läuft auf dem Client-Thread; Netzwerk passiert im Hintergrund und die
 * Ergebnisse kommen per {@code Minecraft#execute} zurück.
 */
public final class SharedCapes {
	/** So lange gilt das Cape eines Mitspielers, danach wird neu gefragt (Freigaben kommen so an). */
	private static final long REFRESH_MS = 10 * 60_000L;
	private static final long LOOKUP_INTERVAL_MS = 1_000L;
	private static final long LOOKUP_RETRY_MS = 60_000L;
	private static final long IMAGE_RETRY_MS = 5 * 60_000L;
	/** Erst teilen, wenn die Auswahl kurz stabil ist (Durchklicken in der ClickGUI). */
	private static final long PUBLISH_DELAY_MS = 3_000L;
	private static final long PUBLISH_RETRY_MIN_MS = 60_000L;
	private static final long PUBLISH_RETRY_MAX_MS = 30 * 60_000L;
	private static final int LOOKUP_BATCH = 100;
	private static final int MAX_PLAYERS = 5_000;
	private static final int MAX_IMAGES = 128;
	private static final Pattern CAPE_ID = Pattern.compile("[0-9a-f]{64}");

	private record Entry(CapeServer.@Nullable Remote remote, long fetchedAt) {}

	private static final class Image {
		@Nullable ClientAsset.Texture texture;
		@Nullable Identifier id;
		boolean loading;
		long retryAt;
	}

	private static boolean initialized;
	private static @Nullable String serverProperty;
	private static @Nullable CapeServer server;

	// --- Capes der Mitspieler
	private static final Map<UUID, Entry> players = new HashMap<>();
	private static final Map<String, Image> images = new HashMap<>();
	private static final Set<UUID> queue = new LinkedHashSet<>();
	private static final Set<UUID> inFlight = new HashSet<>();
	private static long nextLookupAt;
	/** Wird beim Leeren erhöht, damit späte Antworten aus der alten Welt verworfen werden. */
	private static int generation;

	// --- eigenes Cape
	private static @Nullable String publishedKey;
	private static @Nullable String wantedKey;
	private static long publishAt;
	private static long publishRetryMs = PUBLISH_RETRY_MIN_MS;
	private static boolean publishing;
	private static @Nullable String loggedFailure;

	private SharedCapes() {}

	/** Nur auf dem Client-Thread. Die Adresse wird bei Änderung neu gelesen (Gametests setzen sie zur Laufzeit). */
	private static @Nullable CapeServer server() {
		String property = System.getProperty(CapeServer.URL_PROPERTY);
		if (!initialized || !Objects.equals(property, serverProperty)) {
			if (initialized) {
				clear(Minecraft.getInstance());
			}
			initialized = true;
			serverProperty = property;
			server = CapeServer.fromProperty();
			if (server != null) {
				SalamiClient.LOG.info("Salami Capes werden mit anderen Salami-Spielern geteilt");
			}
		}
		return server;
	}

	/**
	 * Cape eines anderen Spielers oder {@code null} (unbekannt, keins oder noch nicht geladen).
	 * {@code builtin} liefert die Textur zu einem mitgelieferten Cape ({@code "salami"} …).
	 */
	public static ClientAsset.@Nullable Texture capeOf(UUID player, Function<String, ClientAsset.@Nullable Texture> builtin) {
		if (!Minecraft.getInstance().isSameThread()) {
			return null;
		}
		CapeServer cs = server();
		// Nur echte Accounts (UUID v4) – Offline-Spieler und NPCs nie anfragen
		if (cs == null || player.version() != 4) {
			return null;
		}
		long now = System.currentTimeMillis();
		Entry entry = players.get(player);
		if ((entry == null || now - entry.fetchedAt() > REFRESH_MS) && !inFlight.contains(player)) {
			queue.add(player);
		}
		if (entry == null || entry.remote() == null) {
			return null;
		}
		if (entry.remote().builtin() != null) {
			return builtin.apply(entry.remote().builtin());
		}
		String id = entry.remote().id();
		return id != null && CAPE_ID.matcher(id).matches() ? image(cs, id, now) : null;
	}

	/** Jeden Client-Tick: Mitspieler-Capes abfragen und das eigene Cape teilen. */
	public static void tick(Minecraft mc) {
		CapeServer cs = server();
		if (cs == null) {
			return;
		}
		if (mc.level == null || mc.player == null) {
			if (!players.isEmpty() || !images.isEmpty() || !queue.isEmpty()) {
				clear(mc);
			}
			return;
		}
		long now = System.currentTimeMillis();
		lookup(cs, mc, now);
		publish(cs, mc, now);
	}

	// ------------------------------------------------------------------ Mitspieler

	private static void lookup(CapeServer cs, Minecraft mc, long now) {
		if (!inFlight.isEmpty() || queue.isEmpty() || now < nextLookupAt) {
			return;
		}
		List<UUID> batch = new ArrayList<>(Math.min(queue.size(), LOOKUP_BATCH));
		for (Iterator<UUID> it = queue.iterator(); it.hasNext() && batch.size() < LOOKUP_BATCH; ) {
			batch.add(it.next());
			it.remove();
		}
		inFlight.addAll(batch);
		int gen = generation;
		cs.lookup(batch).whenComplete((result, error) -> mc.execute(() -> {
			if (gen != generation) {
				return;
			}
			inFlight.clear();
			long done = System.currentTimeMillis();
			if (error != null) {
				// Server nicht erreichbar: altes Ergebnis behalten, in einer Minute erneut fragen
				for (UUID uuid : batch) {
					players.compute(uuid, (key, old) -> new Entry(old == null ? null : old.remote(), done - REFRESH_MS + LOOKUP_RETRY_MS));
				}
				nextLookupAt = done + LOOKUP_RETRY_MS;
				SalamiClient.LOG.debug("Cape-Abfrage fehlgeschlagen: {}", CapeServer.unwrap(error).getMessage());
				return;
			}
			if (players.size() > MAX_PLAYERS) {
				players.clear();
			}
			for (UUID uuid : batch) {
				players.put(uuid, new Entry(result.get(uuid), done));
			}
			nextLookupAt = done + LOOKUP_INTERVAL_MS;
		}));
	}

	private static ClientAsset.@Nullable Texture image(CapeServer cs, String id, long now) {
		Image image = images.get(id);
		if (image == null) {
			if (images.size() >= MAX_IMAGES) {
				return null;
			}
			image = new Image();
			images.put(id, image);
		}
		if (image.texture == null && !image.loading && now >= image.retryAt) {
			download(cs, id, image);
		}
		return image.texture;
	}

	private static void download(CapeServer cs, String id, Image image) {
		image.loading = true;
		int gen = generation;
		Minecraft mc = Minecraft.getInstance();
		cs.image(id).whenComplete((data, error) -> mc.execute(() -> {
			if (gen != generation) {
				return;
			}
			image.loading = false;
			if (error != null) {
				image.retryAt = System.currentTimeMillis() + IMAGE_RETRY_MS;
				SalamiClient.LOG.debug("Cape {} konnte nicht geladen werden: {}", id, CapeServer.unwrap(error).getMessage());
				return;
			}
			Identifier texture = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "cape/shared/" + id);
			if (register(mc, texture, data)) {
				image.id = texture;
				image.texture = new ClientAsset.ResourceTexture(texture, texture);
			} else {
				image.retryAt = Long.MAX_VALUE; // kaputtes Bild nicht immer wieder laden
			}
		}));
	}

	private static boolean register(Minecraft mc, Identifier texture, byte[] data) {
		NativeImage image = null;
		try {
			// Maße aus dem PNG-Kopf prüfen, bevor das Bild dekodiert wird
			PngInfo info = PngInfo.fromBytes(data);
			if (!CapesModule.validCapeSize(info.width(), info.height())) {
				return false;
			}
			image = NativeImage.read(data);
			if (!CapesModule.validCapeSize(image.getWidth(), image.getHeight())) {
				return false;
			}
			mc.getTextureManager().register(texture, new DynamicTexture(() -> "salami shared cape", image));
			image = null; // gehört jetzt der Textur
			return true;
		} catch (Exception e) {
			SalamiClient.LOG.warn("Geteiltes Cape ist kein gültiges Bild: {}", e.toString());
			return false;
		} finally {
			if (image != null) {
				image.close();
			}
		}
	}

	private static void clear(Minecraft mc) {
		generation++;
		for (Image image : images.values()) {
			if (image.id != null) {
				mc.getTextureManager().release(image.id);
			}
		}
		images.clear();
		players.clear();
		queue.clear();
		inFlight.clear();
		nextLookupAt = 0;
	}

	// ------------------------------------------------------------------ eigenes Cape

	private static void publish(CapeServer cs, Minecraft mc, long now) {
		// Ohne echten Account (Entwicklungsumgebung) gibt es keine Mojang-Anmeldung
		CapeShare wanted = mc.isOfflineDeveloperMode() ? null : CapesModule.sharedState();
		if (wanted == null || publishing) {
			return;
		}
		if (wanted.key().equals(publishedKey)) {
			wantedKey = null;
			return;
		}
		if (!wanted.key().equals(wantedKey)) {
			wantedKey = wanted.key();
			publishAt = Math.max(publishAt, now + PUBLISH_DELAY_MS);
			return;
		}
		if (now < publishAt) {
			return;
		}
		publishing = true;
		cs.publish(mc.getUser(), mc.services().sessionService(), wanted).whenComplete((result, error) -> mc.execute(() -> {
			publishing = false;
			if (error == null) {
				publishedKey = wanted.key();
				publishRetryMs = PUBLISH_RETRY_MIN_MS;
				announce(wanted, result);
				return;
			}
			Throwable cause = CapeServer.unwrap(error);
			if (cause instanceof CapeServer.ServerException server && server.permanent()) {
				// Bild ungültig: nicht endlos neu versuchen
				publishedKey = wanted.key();
				Chat.warn("salami.capes.shared.failed", server.getMessage());
				return;
			}
			if (!wanted.key().equals(loggedFailure)) {
				loggedFailure = wanted.key();
				SalamiClient.LOG.warn("Cape konnte nicht geteilt werden: {}", cause.getMessage());
			}
			publishAt = System.currentTimeMillis() + publishRetryMs;
			publishRetryMs = Math.min(publishRetryMs * 2, PUBLISH_RETRY_MAX_MS);
		}));
	}

	private static void announce(CapeShare share, CapeServer.Published result) {
		if (share.png() == null) {
			return;
		}
		switch (result.status()) {
			case "pending" -> Chat.info("salami.capes.shared.pending");
			case "rejected" -> {
				if (result.reason() != null && !result.reason().isBlank()) {
					Chat.warn("salami.capes.shared.rejected", result.reason());
				} else {
					Chat.warn("salami.capes.shared.rejected_no_reason");
				}
			}
			default -> {
				// freigegeben: nichts zu melden
			}
		}
	}
}
