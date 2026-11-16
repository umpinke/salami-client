package dev.salamiclient.friends;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.exceptions.AuthenticationException;
//? if >=26.3 {
/*import com.mojang.authlib.minecraft.SessionService;
*///?} else
import com.mojang.authlib.minecraft.MinecraftSessionService;
import dev.salamiclient.SalamiClient;
import net.minecraft.client.User;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Verbindung zur Freunde-API des Salami-Servers ({@code server/capes/friends.mjs}) – derselbe
 * Server wie für die Capes, Adresse vom Launcher als {@code -Dsalami.capes.url}.
 *
 * <p>Anmeldung wie beim Betreten eines Minecraft-Servers: der Server gibt eine zufällige
 * serverId aus, das Spiel meldet sie bei Mojang an, der Server fragt bei Mojang nach und gibt
 * ein eigenes Sitzungs-Token zurück. Das Minecraft-Zugangstoken geht nur an Mojang.
 */
final class FriendsApi {
	/** Wie {@code CapeServer.URL_PROPERTY}: Freunde laufen über den Cape-Server. */
	static final String URL_PROPERTY = "salami.capes.url";
	private static final Duration TIMEOUT = Duration.ofSeconds(15);
	private static final Gson GSON = new Gson();

	record Friend(UUID uuid, String name, boolean online) {}

	record Request(UUID uuid, String name) {}

	record Message(String id, UUID from, UUID to, String text, long at) {}

	record Overview(UUID me, String myName, List<Friend> friends, List<Request> incoming, List<Request> outgoing,
			Map<UUID, Integer> unread) {}

	/** Antwort auf Anfragen/Annehmen/Entfernen: {@code requested}, {@code friends} oder {@code none}. */
	record Result(String status, @Nullable UUID uuid, @Nullable String name) {}

	/** Antwort mit Fehlerstatus; {@code message} kommt vom Server (deutsch, für Spieler gedacht). */
	static final class ServerException extends IOException {
		final int status;

		ServerException(int status, String message) {
			super(message);
			this.status = status;
		}
	}

	/** Live-Verbindung ({@code v1/friends/events}); {@link #close()} beendet sie, auch während sie liest. */
	static final class EventStream implements AutoCloseable {
		private volatile @Nullable InputStream body;
		volatile boolean closed;
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

	private final URI base;
	private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, "Salami Friends");
		thread.setDaemon(true);
		return thread;
	});
	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(TIMEOUT)
		.followRedirects(HttpClient.Redirect.NEVER)
		.executor(executor)
		.build();

	private FriendsApi(URI base) {
		this.base = base;
	}

	/** Aus {@code -Dsalami.capes.url}; {@code null}, wenn nicht gesetzt oder ungültig. */
	static @Nullable FriendsApi fromProperty() {
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
				return new FriendsApi(uri);
			}
		} catch (URISyntaxException ignored) {
			// unten melden
		}
		SalamiClient.LOG.warn("Server-Adresse ungültig, Freunde sind aus: {}", raw);
		return null;
	}

	// ------------------------------------------------------------------ Anmeldung

	/** Meldet den Spieler an (Mojang) und liefert das Sitzungs-Token. Läuft im Hintergrund. */
	//? if >=26.3 {
	/*CompletableFuture<String> login(User user, SessionService session) {
	*///?} else
	CompletableFuture<String> login(User user, MinecraftSessionService session) {
		return send(post("v1/auth/challenge", new JsonObject(), null)).thenApplyAsync(challenge -> {
			String serverId = string(challenge, "serverId");
			if (serverId == null) {
				throw new IllegalStateException("Server hat keine serverId geschickt");
			}
			try {
				session.joinServer(user.getProfileId(), user.getAccessToken(), serverId);
			} catch (AuthenticationException e) {
				throw new IllegalStateException("Anmeldung bei Mojang fehlgeschlagen: " + e.getMessage(), e);
			}
			JsonObject body = new JsonObject();
			body.addProperty("name", user.getName());
			body.addProperty("serverId", serverId);
			return body;
		}, executor).thenCompose(body -> send(post("v1/friends/session", body, null))).thenApply(json -> {
			String token = string(json, "token");
			if (token == null || token.isBlank()) {
				throw new CompletionException(new ServerException(500, "Server hat kein Token geschickt"));
			}
			return token;
		});
	}

	// ------------------------------------------------------------------ Freunde

	CompletableFuture<Overview> overview(String token) {
		return send(builder("v1/friends", token).GET().build()).thenApply(json -> {
			JsonObject me = json.getAsJsonObject("me");
			UUID myUuid = me != null ? uuid(me, "uuid") : null;
			String myName = me != null ? string(me, "name") : null;
			List<Friend> friends = new ArrayList<>();
			for (JsonObject entry : objects(json, "friends")) {
				UUID uuid = uuid(entry, "uuid");
				if (uuid != null) {
					JsonElement online = entry.get("online");
					friends.add(new Friend(uuid, nameOr(entry, uuid),
						online != null && online.isJsonPrimitive() && online.getAsBoolean()));
				}
			}
			Map<UUID, Integer> unread = new HashMap<>();
			JsonElement counts = json.get("unread");
			if (counts != null && counts.isJsonObject()) {
				for (Map.Entry<String, JsonElement> entry : counts.getAsJsonObject().entrySet()) {
					try {
						unread.put(UUID.fromString(entry.getKey()), entry.getValue().getAsInt());
					} catch (RuntimeException ignored) {
						// kaputter Eintrag
					}
				}
			}
			return new Overview(myUuid, myName != null ? myName : "", friends, requests(json, "incoming"), requests(json, "outgoing"), unread);
		});
	}

	CompletableFuture<Result> request(String token, String name) {
		JsonObject body = new JsonObject();
		body.addProperty("name", name);
		return send(post("v1/friends/request", body, token)).thenApply(FriendsApi::result);
	}

	CompletableFuture<Result> accept(String token, UUID uuid) {
		return send(post("v1/friends/accept", uuidBody("uuid", uuid), token)).thenApply(FriendsApi::result);
	}

	/** Lehnt eine Anfrage ab oder zieht die eigene zurück. */
	CompletableFuture<Result> deny(String token, UUID uuid) {
		return send(post("v1/friends/deny", uuidBody("uuid", uuid), token)).thenApply(FriendsApi::result);
	}

	CompletableFuture<Result> remove(String token, UUID uuid) {
		return send(post("v1/friends/remove", uuidBody("uuid", uuid), token)).thenApply(FriendsApi::result);
	}

	// ------------------------------------------------------------------ Chat

	/** Die letzten Nachrichten mit diesem Freund (älteste zuerst); setzt „ungelesen“ zurück. */
	CompletableFuture<List<Message>> messages(String token, UUID with) {
		String query = "v1/friends/messages?with=" + URLEncoder.encode(with.toString(), StandardCharsets.UTF_8);
		return send(builder(query, token).GET().build()).thenApply(json -> {
			List<Message> result = new ArrayList<>();
			for (JsonObject entry : objects(json, "messages")) {
				Message message = message(entry);
				if (message != null) {
					result.add(message);
				}
			}
			return result;
		});
	}

	CompletableFuture<Message> send(String token, UUID to, String text) {
		JsonObject body = uuidBody("to", to);
		body.addProperty("text", text);
		return send(post("v1/friends/messages", body, token)).thenApply(json -> {
			JsonElement entry = json.get("message");
			Message message = entry != null && entry.isJsonObject() ? message(entry.getAsJsonObject()) : null;
			if (message == null) {
				throw new CompletionException(new ServerException(500, "Server hat die Nachricht nicht bestätigt"));
			}
			return message;
		});
	}

	CompletableFuture<Void> read(String token, UUID with) {
		return send(post("v1/friends/read", uuidBody("with", with), token)).thenApply(json -> null);
	}

	// ------------------------------------------------------------------ Live

	/**
	 * Öffnet die Live-Verbindung. Jede Zeile {@code data: {…}} kommt als JSON an {@code onEvent};
	 * {@code onEnd} meldet das Ende ({@code null} = mit {@link EventStream#close()} beendet).
	 * Alle Rückrufe kommen aus einem Hintergrund-Thread.
	 */
	void events(String token, EventStream stream, Consumer<JsonObject> onEvent, Runnable onOpen, Consumer<@Nullable Throwable> onEnd) {
		// Ohne Timeout: die Verbindung bleibt offen; tote Verbindungen erkennt Friends am fehlenden Lebenszeichen
		HttpRequest request = HttpRequest.newBuilder(base.resolve("v1/friends/events"))
			.header("Accept", "text/event-stream")
			.header("Authorization", "Bearer " + token)
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

	private static void read(EventStream stream, HttpResponse<InputStream> response, Consumer<JsonObject> onEvent,
			Runnable onOpen, Consumer<@Nullable Throwable> onEnd) {
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
					try {
						JsonElement json = JsonParser.parseString(line.substring("data:".length()).trim());
						if (json.isJsonObject()) {
							onEvent.accept(json.getAsJsonObject());
						}
					} catch (RuntimeException ignored) {
						// kaputte Zeile überspringen
					}
				}
			}
		} catch (Exception e) {
			failure = e;
		} finally {
			onEnd.accept(stream.closed ? null : failure);
		}
	}

	// ------------------------------------------------------------------ JSON

	static @Nullable Message message(JsonObject json) {
		String id = string(json, "id");
		UUID from = uuid(json, "from");
		UUID to = uuid(json, "to");
		String text = string(json, "text");
		if (id == null || from == null || to == null || text == null) {
			return null;
		}
		long at;
		try {
			String raw = string(json, "at");
			at = raw != null ? Instant.parse(raw).toEpochMilli() : System.currentTimeMillis();
		} catch (RuntimeException e) {
			at = System.currentTimeMillis();
		}
		return new Message(id, from, to, text, at);
	}

	private static Result result(JsonObject json) {
		String status = string(json, "status");
		return new Result(status != null ? status : "none", uuid(json, "uuid"), string(json, "name"));
	}

	private static List<Request> requests(JsonObject json, String key) {
		List<Request> result = new ArrayList<>();
		for (JsonObject entry : objects(json, key)) {
			UUID uuid = uuid(entry, "uuid");
			if (uuid != null) {
				result.add(new Request(uuid, nameOr(entry, uuid)));
			}
		}
		return result;
	}

	private static List<JsonObject> objects(JsonObject json, String key) {
		JsonElement value = json.get(key);
		List<JsonObject> result = new ArrayList<>();
		if (value != null && value.isJsonArray()) {
			for (JsonElement entry : (JsonArray) value) {
				if (entry.isJsonObject()) {
					result.add(entry.getAsJsonObject());
				}
			}
		}
		return result;
	}

	private static String nameOr(JsonObject json, UUID uuid) {
		String name = string(json, "name");
		return name != null && !name.isBlank() ? name : uuid.toString().substring(0, 8);
	}

	static @Nullable String string(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	static @Nullable UUID uuid(JsonObject json, String key) {
		String raw = string(json, key);
		if (raw == null) {
			return null;
		}
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static JsonObject uuidBody(String key, UUID uuid) {
		JsonObject body = new JsonObject();
		body.addProperty(key, uuid.toString());
		return body;
	}

	// ------------------------------------------------------------------ HTTP

	private HttpRequest post(String path, JsonObject body, @Nullable String token) {
		return builder(path, token).POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8)).build();
	}

	private HttpRequest.Builder builder(String path, @Nullable String token) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path))
			.timeout(TIMEOUT)
			.header("Content-Type", "application/json");
		if (token != null) {
			builder.header("Authorization", "Bearer " + token);
		}
		return builder;
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
				throw new CompletionException(
					new ServerException(response.statusCode(), error != null ? error : "HTTP " + response.statusCode()));
			}
			return json;
		});
	}

	/** Eigentliche Ursache aus CompletableFuture-Hüllen. */
	static Throwable unwrap(Throwable error) {
		Throwable cause = error;
		while ((cause instanceof CompletionException || cause instanceof java.io.UncheckedIOException)
			&& cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause;
	}
}
