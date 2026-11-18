package dev.salamiclient.cosmetics;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Alle Hüte, die es gerade gibt (Cosmetics → Hüte). Die Liste kommt vom Salami-Server
 * ({@code GET /v1/hats/catalog}), damit neue Hüte ohne Update bei allen ankommen; ohne Verbindung
 * gilt die Kopie aus der Mod ({@code assets/salami/hats.json} – beim Bauen aus
 * {@code server/capes/hats.json} genommen).
 */
public final class HatCatalog {
	/** Wie auf dem Server ({@code MAX_HATS}). */
	private static final int MAX_HATS = 64;
	private static final RemoteCatalog<Hat> CATALOG =
		new RemoteCatalog<>("hats", "Hut", MAX_HATS, "/assets/salami/hats.json", false, Hat::parse, Hat::id);

	private HatCatalog() {}

	/** Alle Hüte in Anzeige-Reihenfolge. */
	public static List<Hat> all() {
		return CATALOG.all();
	}

	/** Hut zu einem Namen ({@code top_hat}) oder {@code null}, wenn es ihn (noch) nicht gibt. */
	public static @Nullable Hat get(@Nullable String id) {
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

	/** Zurück zu den mitgelieferten Hüten (Gametests). */
	public static void clearRemote() {
		CATALOG.clearRemote();
	}

	/** Bald neu laden: der Server hat eine Änderung gemeldet oder jemand trägt einen unbekannten Hut. */
	public static void refreshSoon() {
		CATALOG.refreshSoon();
	}

	public static void tick(Minecraft mc) {
		CATALOG.tick(mc);
	}
}
