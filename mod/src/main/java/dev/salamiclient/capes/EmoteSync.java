package dev.salamiclient.capes;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.emotes.Emote;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Schickt eigene Emotes an den Cape-Server, der sie live an alle Mitspieler mit Salami Client
 * weitergibt (dort {@code POST /v1/emotes}). Beim ersten Emote meldet sich das Spiel einmal an
 * (Mojang wie beim Cape, gut eine Sekunde), danach gehen Emotes mit dem Token sofort raus.
 * Gespeichert wird auf dem Server nichts.
 *
 * <p>Alles hier läuft auf dem Client-Thread; Netzwerk im Hintergrund.
 */
public final class EmoteSync {
	/** Höchstens ein Emote pro so vielen Millisekunden an den Server (der erlaubt 20 pro Minute). */
	private static final long MIN_GAP_MS = 2_500L;
	/** Nach einem Fehler so lange nichts senden (Server weg, Mojang nicht erreichbar …). */
	private static final long BACKOFF_MS = 60_000L;

	private static @Nullable String token;
	private static boolean busy;
	private static long lastSentAt;
	private static long pausedUntil;
	/** Wartendes Emote, falls gerade angemeldet wird. */
	private static @Nullable Emote pending;

	private EmoteSync() {}

	/** Eigenes Emote teilen – ohne Cape-Server (Start ohne Launcher) passiert nichts. */
	public static void send(Emote emote) {
		send(emote, true);
	}

	/**
	 * {@code retry}: bei 401 einmal neu anmelden. Nach so einer Neuanmeldung nicht noch einmal – sonst könnte ein
	 * Server, der jedes Token ablehnt, das Spiel endlos bei Mojang anmelden lassen.
	 */
	private static void send(Emote emote, boolean retry) {
		Minecraft mc = Minecraft.getInstance();
		CapeServer server = SharedCapes.currentServer();
		long now = System.currentTimeMillis();
		if (server == null || now < pausedUntil || now - lastSentAt < MIN_GAP_MS) {
			return;
		}
		if (busy) {
			pending = emote;
			return;
		}
		lastSentAt = now;
		if (token == null) {
			busy = true;
			pending = emote;
			server.emoteSession(mc.getUser(), mc.services().sessionService()).whenComplete((result, error) -> mc.execute(() -> {
				busy = false;
				if (error != null) {
					fail(error);
					return;
				}
				token = result;
				Emote next = pending;
				pending = null;
				if (next != null) {
					post(server, next, retry);
				}
			}));
			return;
		}
		post(server, emote, retry);
	}

	private static void post(CapeServer server, Emote emote, boolean retry) {
		String current = token;
		if (current == null) {
			return;
		}
		busy = true;
		server.emote(current, emote.id()).whenComplete((ok, error) -> Minecraft.getInstance().execute(() -> {
			busy = false;
			if (error == null) {
				return;
			}
			Throwable cause = CapeServer.unwrap(error);
			if (cause instanceof CapeServer.ServerException server401 && server401.status == 401 && retry) {
				// Token abgelaufen oder Server neu gestartet: einmal neu anmelden (danach ohne weiteren Versuch)
				token = null;
				lastSentAt = 0;
				send(emote, false);
				return;
			}
			fail(error);
		}));
	}

	private static void fail(Throwable error) {
		pending = null;
		pausedUntil = System.currentTimeMillis() + BACKOFF_MS;
		SalamiClient.LOG.info("Emote nicht geteilt: {}", CapeServer.unwrap(error).getMessage());
	}

	/** Neue Welt/neuer Server: Anmeldung behalten, nur Wartendes verwerfen. */
	public static void reset() {
		pending = null;
	}
}
