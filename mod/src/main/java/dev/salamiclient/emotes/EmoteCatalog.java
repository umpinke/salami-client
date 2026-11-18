package dev.salamiclient.emotes;

import com.google.gson.JsonObject;
import dev.salamiclient.cosmetics.RemoteCatalog;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Alle Emotes, die es gerade gibt. Die Liste kommt vom Salami-Server ({@code GET /v1/emotes/catalog}),
 * damit neue Emotes ohne Update bei allen ankommen; solange der Server nicht erreichbar ist (oder
 * ohne Launcher), gilt die Kopie aus der Mod ({@code assets/salami/emotes.json} – beim Bauen aus
 * {@code server/capes/emotes.json} genommen). Laden und Auffrischen: {@link RemoteCatalog}.
 *
 * <p>Alles auf dem Client-Thread.
 */
public final class EmoteCatalog {
	/** Wie auf dem Server ({@code MAX_EMOTES}). */
	private static final int MAX_EMOTES = 64;
	private static final RemoteCatalog<Emote> CATALOG =
		new RemoteCatalog<>("emotes", "Emote", MAX_EMOTES, "/assets/salami/emotes.json", false, Emote::parse, Emote::id);

	private EmoteCatalog() {}

	/** Alle Emotes in Anzeige-Reihenfolge: die vom Server, sonst die mitgelieferten. */
	public static List<Emote> all() {
		return CATALOG.all();
	}

	public static @Nullable Emote get(@Nullable String id) {
		return CATALOG.get(id);
	}

	/** Zählt hoch, wenn sich die Liste ändert – Menüs bauen sich dann neu auf. */
	public static int revision() {
		return CATALOG.revision();
	}

	/** Liest einen Katalog ({@code {"emotes":[…]}}); fehlerhafte oder doppelte Einträge werden übersprungen. */
	public static List<Emote> parse(JsonObject root) {
		return CATALOG.parse(root);
	}

	/**
	 * Katalog vom Server übernehmen (auch für Gametests). Ein leerer oder kaputter Katalog ändert
	 * nichts – dann bleiben die bisherigen Emotes.
	 */
	public static boolean applyRemote(JsonObject root) {
		return CATALOG.applyRemote(root);
	}

	/** Zurück zu den mitgelieferten Emotes (Gametests). */
	public static void clearRemote() {
		CATALOG.clearRemote();
	}

	/** Bald neu laden: der Server hat eine Änderung gemeldet oder ein unbekanntes Emote kam an. */
	public static void refreshSoon() {
		CATALOG.refreshSoon();
	}

	public static void tick(Minecraft mc) {
		CATALOG.tick(mc);
	}
}
