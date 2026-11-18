package dev.salamiclient.cosmetics;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Alle Buddys, die es gerade gibt (Cosmetics → Buddys): kleine Begleiter aus Würfeln, die an der
 * Hand hängen, über der Schulter schweben, auf dem Kopf stehen oder hinterherlaufen. Die Liste kommt
 * vom Salami-Server ({@code GET /v1/buddies/catalog}), damit neue Buddys ohne Update bei allen
 * ankommen; ohne Verbindung gilt die Kopie aus der Mod ({@code assets/salami/buddies.json} – beim
 * Bauen aus {@code server/capes/buddies.json} genommen).
 *
 * <p>Ein Buddy ist als Eintrag ein {@link Hat} ohne festen Platz: sein Nullpunkt liegt unten in der
 * Mitte. Ältere Spielversionen kennen Buddys nur als Schulter-Hut; dafür stehen sie zusätzlich in
 * {@code hats.json} – hier gelten solche Hüte als Buddy ({@link #isBuddy}).
 */
public final class BuddyCatalog {
	/** Wie auf dem Server ({@code MAX_BUDDIES}). */
	private static final int MAX_BUDDIES = 64;
	private static final RemoteCatalog<Hat> CATALOG =
		new RemoteCatalog<>("buddies", "Buddy", MAX_BUDDIES, "/assets/salami/buddies.json", false, Hat::parse, Hat::id);

	private BuddyCatalog() {}

	/** Alle Buddys in Anzeige-Reihenfolge. */
	public static List<Hat> all() {
		return CATALOG.all();
	}

	/** Buddy zu einem Namen ({@code tree_buddy}) oder {@code null}, wenn es ihn (noch) nicht gibt. */
	public static @Nullable Hat get(@Nullable String id) {
		return CATALOG.get(id);
	}

	/** Gibt es zu diesem Hut einen Buddy? Dann steht er nur bei den Buddys, nicht bei den Hüten. */
	public static boolean isBuddy(@Nullable String id) {
		return CATALOG.get(id) != null;
	}

	/** Zählt hoch, wenn sich die Liste ändert – das Menü baut sich dann neu auf. */
	public static int revision() {
		return CATALOG.revision();
	}

	/** Katalog vom Server übernehmen (auch für Gametests). */
	public static boolean applyRemote(JsonObject root) {
		return CATALOG.applyRemote(root);
	}

	/** Zurück zu den mitgelieferten Buddys (Gametests). */
	public static void clearRemote() {
		CATALOG.clearRemote();
	}

	/** Bald neu laden: der Server hat eine Änderung gemeldet oder jemand hat einen unbekannten Buddy. */
	public static void refreshSoon() {
		CATALOG.refreshSoon();
	}

	public static void tick(Minecraft mc) {
		CATALOG.tick(mc);
	}
}
