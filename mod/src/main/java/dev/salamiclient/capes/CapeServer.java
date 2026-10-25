package dev.salamiclient.capes;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.client.User;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Verbindung zum Salami Cape-Server ({@code server/capes/}). Die Adresse kommt vom
 * Launcher als {@code -Dsalami.capes.url}; ohne sie bleibt das Teilen aus.
 *
 * <p>Anmeldung wie beim Betreten eines Minecraft-Servers: der Cape-Server gibt eine
 * zufällige serverId aus, das Spiel meldet sie bei Mojang an ({@code joinServer}), der
 * Cape-Server fragt bei Mojang nach. Das Zugangstoken geht nur an Mojang.
 */
final class CapeServer {
	static final String URL_PROPERTY = "salami.capes.url";
	/** Wie im Launcher und auf dem Server. */
	static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
	private static final Duration TIMEOUT = Duration.ofSeconds(15);
	private static final Gson GSON = new Gson();

	/** Antwort auf {@code lookup}: mitgeliefertes Cape ({@code builtin}) oder freigegebenes eigenes ({@code id}). */
	record Remote(@Nullable String builtin, @Nullable String id) {}

	/** Ergebnis des Teilens: {@code approved}, {@code pending}, {@code rejected} oder {@code none}; {@code id} bei eigenen Bildern. */
	record Published(String status, @Nullable String id, @Nullable String reason) {}

	/** Live-Verbindung ({@code v1/capes/events}); {@link #close()} beendet sie, auch während sie liest. */
	static final class EventStream implements AutoCloseable {
		private volatile @Nullable InputStream body;
		private volatile boolean closed;
		/** Zuletzt etwas empfangen (auch das Lebenszeichen alle 20 s). */
		volatile long lastSeen = System.currentTimeMillis();

		@Override
		public void close() {
			closed = true;
			InputStream in = body;
			if (in != null) {
				try {
					in.close();
				} catch (IOException ignored) {
					// schon zu
				}
			}
		}
	}

	/** Antwort des Cape-Servers mit Fehlerstatus; {@code message} kommt vom Server. */
	static final class ServerException extends IOException {
		final int status;

		ServerException(int status, String message) {
			super(message);
			this.status = status;
		}

		/** Das Bild bzw. die Anfrage ist ungültig – erneut senden bringt nichts. */
		boolean permanent() {
			return status == 400 || status == 413 || status == 422;
		}
	}

	private final URI base;
	private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, "Salami Capes");
		thread.setDaemon(true);
		return thread;
	});
	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(TIMEOUT)
		.followRedirects(HttpClient.Redirect.NEVER)
		.executor(executor)
		.build();

	private CapeServer(URI base) {
		this.base = base;
	}

	/** Aus {@code -Dsalami.capes.url}; {@code null}, wenn nicht gesetzt oder ungültig. */
	static @Nullable CapeServer fromProperty() {
		String raw = System.getProperty(URL_PROPERTY);
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			URI uri = new URI(raw.trim().replaceAll("/+$", "") + "/");
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			String host = uri.getHost() == null ? "" : uri.getHost();
			// HTTP nur zum lokalen Testen
			boolean local = host.equals("localhost") || host.equals("127.0.0.1");
			if (scheme.equals("https") || (scheme.equals("http") && local)) {
				return new CapeServer(uri);
			}
		} catch (URISyntaxException ignored) {
			// unten melden
		}
		dev.salamiclient.SalamiClient.LOG.warn("Cape-Server-Adresse ungültig, Capes werden nicht geteilt: {}", raw);
		return null;
	}

	/** Capes dieser Spieler (nur sichtbare; wer fehlt, hat keins). */
	CompletableFuture<Map<UUID, Remote>> lookup(Collection<UUID> players) {
		JsonObject body = new JsonObject();
		body.add("uuids", GSON.toJsonTree(players.stream().map(UUID::toString).toList()));
		return send(post("v1/capes/lookup", body)).thenApply(json -> {
			Map<UUID, Remote> result = new HashMap<>();
			JsonObject capes = json.getAsJsonObject("capes");
			if (capes == null) {
				return result;
			}
			for (Map.Entry<String, JsonElement> entry : capes.entrySet()) {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}
				JsonObject cape = entry.getValue().getAsJsonObject();
				try {
					result.put(UUID.fromString(entry.getKey()), new Remote(string(cape, "builtin"), string(cape, "id")));
				} catch (IllegalArgumentException ignored) {
					// kaputte UUID überspringen
				}
			}
			return result;
		});
	}

	/** Freigegebenes Cape-Bild (höchstens {@link #MAX_IMAGE_BYTES}). */
	CompletableFuture<byte[]> image(String id) {
		HttpRequest request = HttpRequest.newBuilder(base.resolve("v1/capes/img/" + id + ".png")).timeout(TIMEOUT).GET().build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenApply(response -> {
			try (InputStream in = response.body()) {
				if (response.statusCode() != 200) {
					throw new ServerException(response.statusCode(), "Bild nicht verfügbar");
				}
				byte[] data = in.readNBytes(MAX_IMAGE_BYTES + 1);
				if (data.length > MAX_IMAGE_BYTES) {
					throw new ServerException(413, "Bild zu groß");
				}
				return data;
			} catch (IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		});
	}

	/** Teilt das eigene Cape: anmelden (Mojang) und setzen. Läuft im Hintergrund. */
	CompletableFuture<Published> publish(User user, MinecraftSessionService session, CapeShare share) {
		return send(post("v1/auth/challenge", new JsonObject())).thenApplyAsync(challenge -> {
			String serverId = string(challenge, "serverId");
			if (serverId == null) {
				throw new IllegalStateException("Cape-Server hat keine serverId geschickt");
			}
			try {
				session.joinServer(user.getProfileId(), user.getAccessToken(), serverId);
			} catch (AuthenticationException e) {
				throw new IllegalStateException("Anmeldung bei Mojang fehlgeschlagen: " + e.getMessage(), e);
			}
			JsonObject body = new JsonObject();
			body.addProperty("name", user.getName());
			body.addProperty("serverId", serverId);
			body.add("cape", share.toJson());
			return body;
		}, executor).thenCompose(body -> send(request("v1/capes/me").PUT(json(body)).build()))
			.thenApply(json -> new Published(String.valueOf(string(json, "status")), string(json, "id"), string(json, "reason")));
	}

	/**
	 * Öffnet die Live-Verbindung: jede Cape-Änderung eines Salami-Spielers kommt sofort als
	 * (Spieler, sichtbares Cape oder {@code null}) an {@code onEvent}. {@code onOpen} meldet die
	 * Verbindung, {@code onEnd} ihr Ende ({@code null} = mit {@link EventStream#close()} beendet).
	 * Alle Rückrufe kommen aus einem Hintergrund-Thread.
	 */
	void events(EventStream stream, BiConsumer<UUID, @Nullable Remote> onEvent, Runnable onOpen, Consumer<@Nullable Throwable> onEnd) {
		// Ohne Timeout: die Verbindung bleibt offen; tote Verbindungen erkennt SharedCapes am fehlenden Lebenszeichen
		HttpRequest request = HttpRequest.newBuilder(base.resolve("v1/capes/events"))
			.header("Accept", "text/event-stream")
			.GET()
			.build();
		http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).whenComplete((response, error) -> {
			if (error != null) {
				onEnd.accept(stream.closed ? null : error);
				return;
			}
			executor.execute(() -> read(stream, response, onEvent, onOpen, onEnd));
		});
	}

	private static void read(EventStream stream, HttpResponse<InputStream> response,
			BiConsumer<UUID, @Nullable Remote> onEvent, Runnable onOpen, Consumer<@Nullable Throwable> onEnd) {
		Throwable failure = null;
		try (InputStream in = response.body()) {
			stream.body = in;
			if (stream.closed) {
				return;
			}
			if (response.statusCode() != 200) {
				throw new ServerException(response.statusCode(), "Live-Verbindung abgelehnt");
			}
			onOpen.run();
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			for (String line; (line = reader.readLine()) != null; ) {
				stream.lastSeen = System.currentTimeMillis();
				if (line.startsWith("data:")) {
					event(line.substring("data:".length()).trim(), onEvent);
				}
			}
		} catch (Exception e) {
			failure = e;
		} finally {
			onEnd.accept(stream.closed ? null : failure);
		}
	}

	private static void event(String data, BiConsumer<UUID, @Nullable Remote> onEvent) {
		try {
			JsonObject json = JsonParser.parseString(data).getAsJsonObject();
			String uuid = string(json, "uuid");
			if (uuid == null) {
				return;
			}
			JsonElement cape = json.get("cape");
			Remote remote = cape != null && cape.isJsonObject()
				? new Remote(string(cape.getAsJsonObject(), "builtin"), string(cape.getAsJsonObject(), "id"))
				: null;
			onEvent.accept(UUID.fromString(uuid), remote);
		} catch (RuntimeException ignored) {
			// kaputte Zeile überspringen
		}
	}

	private HttpRequest post(String path, JsonObject body) {
		return request(path).POST(json(body)).build();
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(base.resolve(path))
			.timeout(TIMEOUT)
			.header("Content-Type", "application/json");
	}

	private static HttpRequest.BodyPublisher json(JsonObject body) {
		return HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8);
	}

	private CompletableFuture<JsonObject> send(HttpRequest request) {
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(response -> {
			JsonObject json;
			try {
				JsonElement parsed = JsonParser.parseString(response.body());
				json = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
			} catch (RuntimeException e) {
				json = new JsonObject();
			}
			if (response.statusCode() / 100 != 2) {
				String error = string(json, "error");
				throw new java.util.concurrent.CompletionException(
					new ServerException(response.statusCode(), error != null ? error : "HTTP " + response.statusCode()));
			}
			return json;
		});
	}

	private static @Nullable String string(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	/** Eigentliche Ursache aus CompletableFuture-Hüllen. */
	static Throwable unwrap(Throwable error) {
		Throwable cause = error;
		while ((cause instanceof java.util.concurrent.CompletionException || cause instanceof java.io.UncheckedIOException)
			&& cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause;
	}
}
