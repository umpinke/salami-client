package dev.salamiclient.cosmetics;

import dev.salamiclient.capes.OfficialCapes;
import dev.salamiclient.emotes.EmoteCatalog;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Alle Cosmetics-Kataloge, die vom Salami-Server kommen: Emotes, Hüte und offizielle Capes. Neues in
 * einem davon braucht kein Update – der Server meldet die Änderung, das Spiel lädt die Liste neu.
 */
public final class Catalogs {
	private Catalogs() {}

	/** Jeden Client-Tick: lädt Kataloge, wenn es Zeit ist. */
	public static void tick(Minecraft mc) {
		EmoteCatalog.tick(mc);
		HatCatalog.tick(mc);
		OfficialCapes.tick(mc);
	}

	/**
	 * Der Server hat eine Änderung gemeldet: {@code emotes}, {@code hats} oder {@code capes} – bei
	 * {@code null} (unbekannt, oder die Verbindung war unterbrochen) alle neu laden.
	 */
	public static void refreshSoon(@Nullable String kind) {
		if (kind == null || kind.equals("emotes")) {
			EmoteCatalog.refreshSoon();
		}
		if (kind == null || kind.equals("hats")) {
			HatCatalog.refreshSoon();
		}
		if (kind == null || kind.equals("capes")) {
			OfficialCapes.refreshSoon();
		}
	}
}
