package dev.salamiclient.capes;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Holt die Cosmetics-Kataloge vom Cape-Server ({@code GET /v1/<kind>/catalog}) – für
 * {@link dev.salamiclient.cosmetics.RemoteCatalog}. Nur auf dem Client-Thread aufrufen; das Netzwerk
 * läuft im Hintergrund.
 */
public final class CatalogSync {
	private CatalogSync() {}

	/**
	 * Katalog {@code emotes}, {@code hats} oder {@code capes} vom Server holen; {@code null} ohne
	 * Cape-Server (Start ohne Launcher).
	 */
	public static @Nullable CompletableFuture<JsonObject> fetch(String kind) {
		CapeServer server = SharedCapes.currentServer();
		return server == null ? null : server.catalog(kind);
	}
}
