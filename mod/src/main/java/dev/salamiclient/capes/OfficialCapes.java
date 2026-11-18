package dev.salamiclient.capes;

import com.google.gson.JsonObject;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.cosmetics.CatalogEntry;
import dev.salamiclient.cosmetics.RemoteCatalog;
import dev.salamiclient.modules.visual.CapesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Offizielle Capes: Bilder, die der Salami-Server mitbringt ({@code GET /v1/capes/catalog}). Ein
 * neues Cape ist dort ein Bild und ein Eintrag in {@code official-capes.json} – ohne Update steht es
 * im Cosmetics-Menü. Anlegen heißt: das Bild zu den eigenen Capes holen ({@link CapeLibrary}); weil
 * der Server es schon kennt und freigegeben hat, sehen es alle sofort.
 *
 * <p>Ohne Verbindung zum Server gibt es keine offiziellen Capes – die Bilder liegen nur dort.
 * Alles auf dem Client-Thread.
 */
public final class OfficialCapes {
	/** Ein offizielles Cape: Name im Katalog, Anzeigenamen, Bild-ID auf dem Server und Bildgröße. */
	public record Cape(String id, Map<String, String> names, String capeId, int width, int height) {
		/** Anzeigename in der Sprache des Spiels, sonst englisch. */
		public String displayName() {
			return CatalogEntry.displayName(names, id);
		}
	}

	/** Wie auf dem Server ({@code MAX_OFFICIAL_CAPES}). */
	private static final int MAX_CAPES = 64;
	private static final Pattern CAPE_ID = Pattern.compile("[0-9a-f]{64}");
	private static final long RETRY_MS = 60_000L;
	/** Eigene Capes, die von hier geholt wurden, heißen so – daran erkennt das Menü sie wieder. */
	private static final String OWN_PREFIX = "official-";
	private static final RemoteCatalog<Cape> CATALOG =
		new RemoteCatalog<>("capes", "Cape", MAX_CAPES, null, true, OfficialCapes::parse, Cape::id);

	private static final class Preview {
		@Nullable Identifier texture;
		boolean loading;
		long retryAt;
	}

	/** Vorschau-Texturen nach Bild-ID. */
	private static final Map<String, Preview> PREVIEWS = new HashMap<>();

	private OfficialCapes() {}

	private static Cape parse(JsonObject json) {
		String id = CatalogEntry.id(json);
		Map<String, String> names = CatalogEntry.names(json, id);
		String capeId = CatalogEntry.string(json, "cape");
		if (capeId == null || !CAPE_ID.matcher(capeId).matches()) {
			throw new IllegalArgumentException(id + ": Bild-ID fehlt");
		}
		int width = Math.round(CatalogEntry.number(json.get("width"), 1f, 4096f, id + ": width"));
		int height = Math.round(CatalogEntry.number(json.get("height"), 1f, 4096f, id + ": height"));
		if (!CapesModule.validCapeSize(width, height)) {
			throw new IllegalArgumentException(id + ": kein Cape-Format");
		}
		return new Cape(id, names, capeId, width, height);
	}

	/** Alle offiziellen Capes in Anzeige-Reihenfolge (leer ohne Server). */
	public static List<Cape> all() {
		return CATALOG.all();
	}

	public static @Nullable Cape get(@Nullable String id) {
		return CATALOG.get(id);
	}

	/** Zählt hoch, wenn sich die Liste ändert – das Menü baut sich dann neu auf. */
	public static int revision() {
		return CATALOG.revision();
	}

	/** Katalog vom Server übernehmen (auch für Gametests). */
	public static boolean applyRemote(JsonObject root) {
		return CATALOG.applyRemote(root);
	}

	/** Wieder ohne offizielle Capes (Gametests). */
	public static void clearRemote() {
		CATALOG.clearRemote();
	}

	public static void refreshSoon() {
		CATALOG.refreshSoon();
	}

	public static void tick(Minecraft mc) {
		CATALOG.tick(mc);
	}

	/** Unter diesem Namen liegt das Cape bei den eigenen Capes, wenn es angelegt wurde. */
	public static String ownId(Cape cape) {
		return OWN_PREFIX + cape.capeId().substring(0, 32);
	}

	/** Offizielles Cape zu einem eigenen ({@link #ownId}) oder {@code null}. */
	public static @Nullable Cape byOwnId(@Nullable String ownId) {
		if (ownId == null || !ownId.startsWith(OWN_PREFIX)) {
			return null;
		}
		for (Cape cape : all()) {
			if (ownId(cape).equals(ownId)) {
				return cape;
			}
		}
		return null;
	}

	/** Vorschau-Textur des Capes oder {@code null}, solange das Bild lädt (oder ohne Server). */
	public static @Nullable Identifier preview(Cape cape) {
		Preview preview = PREVIEWS.computeIfAbsent(cape.capeId(), id -> new Preview());
		long now = System.currentTimeMillis();
		if (preview.texture == null && !preview.loading && now >= preview.retryAt) {
			CompletableFuture<byte[]> download = download(cape);
			if (download == null) {
				preview.retryAt = now + RETRY_MS;
				return null;
			}
			preview.loading = true;
			Minecraft mc = Minecraft.getInstance();
			download.whenComplete((data, error) -> mc.execute(() -> {
				preview.loading = false;
				if (error != null) {
					preview.retryAt = System.currentTimeMillis() + RETRY_MS;
					SalamiClient.LOG.debug("Offizielles Cape {} nicht geladen: {}", cape.id(), CapeServer.unwrap(error).getMessage());
					return;
				}
				Identifier texture = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "cape/official/" + cape.capeId());
				if (SharedCapes.register(mc, texture, data)) {
					preview.texture = texture;
				} else {
					preview.retryAt = Long.MAX_VALUE; // kaputtes Bild nicht immer wieder laden
				}
			}));
		}
		return preview.texture;
	}

	/** Bild des Capes vom Server (PNG) – im Hintergrund; {@code null} ohne Cape-Server. */
	public static @Nullable CompletableFuture<byte[]> download(Cape cape) {
		CapeServer server = SharedCapes.currentServer();
		return server == null ? null : server.image(cape.capeId());
	}
}
