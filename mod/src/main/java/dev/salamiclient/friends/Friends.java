package dev.salamiclient.friends;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.modules.social.FriendsModule;
import dev.salamiclient.util.Chat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Freunde und Chat über den Salami-Server: Anmeldung, Live-Verbindung, Freundesliste und
 * Nachrichten. Läuft nur auf dem Client-Thread ({@link #tick} jeden Client-Tick); Antworten
 * aus dem Netz kommen per {@code mc.execute} zurück.
 *
 * <p>Online ist man, solange das Modul „Freunde“ an ist und die Live-Verbindung steht – auch
 * im Hauptmenü. Nachrichten bleiben auf dem Server (höchstens 30 Tage, 100 pro Unterhaltung).
 */
public final class Friends {
	public enum Status {
		/** Modul „Freunde“ aus. */
		OFF,
		/** Kein Server bekannt (Spiel nicht über den Launcher gestartet). */
		NO_SERVER,
		/** Entwicklungsumgebung ohne echten Account: keine Mojang-Anmeldung möglich. */
		NO_ACCOUNT,
		CONNECTING,
		ONLINE,
		/** Anmeldung oder Verbindung fehlgeschlagen, neuer Versuch läuft automatisch. */
		ERROR
	}

	public record Friend(UUID uuid, String name, boolean online) {}

	public record Request(UUID uuid, String name, boolean incoming) {}

	public record Message(String id, UUID from, UUID to, String text, long at) {}

	/** Längste Nachricht (wie auf dem Server). */
	public static final int MAX_MESSAGE = 256;
	private static final long LOGIN_RETRY_MIN_MS = 15_000L;
	private static final long LOGIN_RETRY_MAX_MS = 10 * 60_000L;
	private static final long STREAM_RETRY_MIN_MS = 3_000L;
	private static final long STREAM_RETRY_MAX_MS = 2 * 60_000L;
	/** Server schickt alle 20 s ein Lebenszeichen – bleibt es länger aus, ist die Verbindung tot. */
	private static final long STREAM_SILENCE_MS = 60_000L;
	/** Freundesliste zur Sicherheit regelmäßig neu holen (verpasste Ereignisse). */
	private static final long REFRESH_MS = 5 * 60_000L;

	private static boolean initialized;
	private static @Nullable String serverProperty;
	private static @Nullable FriendsApi api;

	private static Status status = Status.OFF;
	private static @Nullable Component error;
	private static @Nullable String token;
	private static boolean loggingIn;
	private static long loginRetryAt;
	private static long loginRetryMs = LOGIN_RETRY_MIN_MS;
	private static FriendsApi.@Nullable EventStream stream;
	private static long streamRetryAt;
	private static long streamRetryMs = STREAM_RETRY_MIN_MS;
	private static long refreshAt;
	private static boolean refreshing;

	private static @Nullable UUID me;
	private static List<Friend> friends = List.of();
	private static List<Request> requests = List.of();
	private static final Map<UUID, Integer> unread = new HashMap<>();
	private static final Map<UUID, List<Message>> conversations = new HashMap<>();
	private static final Set<UUID> loading = new HashSet<>();
	/** Unterhaltung, die gerade im Freunde-Menü offen ist (Nachrichten darin gelten als gelesen). */
	private static @Nullable UUID viewing;
	/** Letzte Rückmeldung für das Menü (Anfrage gesendet, Fehler …). */
	private static @Nullable Component notice;
	private static boolean noticeError;
	/** Zählt bei jeder Änderung hoch – das Menü scrollt dann z. B. ans Ende. */
	private static int revision;
	/** {@code /salami friends}: Menü im nächsten Tick öffnen (der Chat schließt sich erst noch). */
	private static boolean openPending;

	private Friends() {}

	// ------------------------------------------------------------------ Abfragen (für das Menü)

	public static Status status() {
		return status;
	}

	/** Fehlertext vom Server oder von Mojang (bei {@link Status#ERROR}). */
	/** Grund der Störung, für Spieler lesbar (Details stehen im Log). */
	public static @Nullable Component error() {
		return error;
	}

	public static @Nullable UUID me() {
		return me;
	}

	public static List<Friend> friends() {
		return friends;
	}

	public static List<Request> requests() {
		return requests;
	}

	public static int unread(UUID friend) {
		return unread.getOrDefault(friend, 0);
	}

	public static int unreadTotal() {
		int total = 0;
		for (int count : unread.values()) {
			total += count;
		}
		return total;
	}

	/** Geladene Nachrichten mit diesem Freund ({@code null} = noch nicht geladen). */
	public static @Nullable List<Message> conversation(UUID friend) {
		return conversations.get(friend);
	}

	public static @Nullable Friend friend(UUID uuid) {
		for (Friend friend : friends) {
			if (friend.uuid().equals(uuid)) {
				return friend;
			}
		}
		return null;
	}

	public static @Nullable Friend friendNamed(String name) {
		for (Friend friend : friends) {
			if (friend.name().equalsIgnoreCase(name)) {
				return friend;
			}
		}
		return null;
	}

	public static @Nullable Component notice() {
		return notice;
	}

	public static boolean noticeIsError() {
		return noticeError;
	}

	public static int revision() {
		return revision;
	}

	// ------------------------------------------------------------------ Laufzeit

	private static @Nullable FriendsApi api() {
		String property = System.getProperty(FriendsApi.URL_PROPERTY);
		if (!initialized || !Objects.equals(property, serverProperty)) {
			initialized = true;
			serverProperty = property;
			disconnect();
			token = null;
			api = FriendsApi.fromProperty();
		}
		return api;
	}

	/** Freunde-Menü im nächsten Client-Tick öffnen (aus einem Chat-Befehl). */
	public static void openScreenNextTick() {
		openPending = true;
	}

	/** Jeden Client-Tick: anmelden, Live-Verbindung halten. */
	public static void tick(Minecraft mc) {
		if (openPending) {
			openPending = false;
			mc.setScreen(new dev.salamiclient.gui.FriendsScreen(null));
		}
		FriendsModule module = FriendsModule.instance();
		FriendsApi friendsApi = api();
		if (module == null || !module.isEnabled()) {
			if (status != Status.OFF) {
				disconnect();
				status = Status.OFF;
			}
			return;
		}
		if (friendsApi == null) {
			status = Status.NO_SERVER;
			return;
		}
		if (mc.isOfflineDeveloperMode()) {
			status = Status.NO_ACCOUNT;
			return;
		}
		long now = System.currentTimeMillis();
		if (token == null) {
			if (status == Status.OFF || status == Status.NO_SERVER || status == Status.NO_ACCOUNT) {
				status = Status.CONNECTING;
			}
			if (!loggingIn && now >= loginRetryAt) {
				login(mc, friendsApi);
			}
			return;
		}
		live(mc, friendsApi, now);
		if (status == Status.ONLINE && !refreshing && now >= refreshAt) {
			refresh(mc);
		}
	}

	private static void login(Minecraft mc, FriendsApi friendsApi) {
		loggingIn = true;
		friendsApi.login(mc.getUser(), mc.services().sessionService()).whenComplete((result, failure) -> mc.execute(() -> {
			loggingIn = false;
			if (api != friendsApi) {
				return; // Adresse inzwischen geändert
			}
			if (failure == null) {
				token = result;
				loginRetryMs = LOGIN_RETRY_MIN_MS;
				streamRetryAt = 0;
				return;
			}
			Throwable cause = FriendsApi.unwrap(failure);
			status = Status.ERROR;
			error = describe(cause);
			SalamiClient.LOG.warn("Anmeldung für Freunde fehlgeschlagen: {}", cause.toString());
			loginRetryAt = System.currentTimeMillis() + loginRetryMs;
			loginRetryMs = Math.min(loginRetryMs * 2, LOGIN_RETRY_MAX_MS);
		}));
	}

	private static void live(Minecraft mc, FriendsApi friendsApi, long now) {
		if (stream != null) {
			if (now - stream.lastSeen > STREAM_SILENCE_MS) {
				stream.close(); // meldet sich über onEnd zurück
			}
			return;
		}
		if (now < streamRetryAt || token == null) {
			return;
		}
		FriendsApi.EventStream opened = new FriendsApi.EventStream();
		stream = opened;
		if (status != Status.ERROR) {
			status = Status.CONNECTING;
		}
		friendsApi.events(token, opened,
			event -> mc.execute(() -> {
				if (stream == opened) {
					onEvent(mc, event);
				}
			}),
			() -> mc.execute(() -> {
				if (stream == opened) {
					status = Status.ONLINE;
					error = null;
					streamRetryMs = STREAM_RETRY_MIN_MS;
					// Was während einer Unterbrechung passiert ist, kam nicht an: alles neu holen
					conversations.keySet().removeIf(uuid -> !uuid.equals(viewing));
					if (viewing != null) {
						load(mc, viewing, true);
					}
					refresh(mc);
				}
			}),
			failure -> mc.execute(() -> {
				if (stream != opened) {
					return;
				}
				stream = null;
				streamRetryAt = System.currentTimeMillis() + streamRetryMs;
				streamRetryMs = Math.min(streamRetryMs * 2, STREAM_RETRY_MAX_MS);
				if (failure == null) {
					return;
				}
				Throwable cause = FriendsApi.unwrap(failure);
				if (unauthorized(cause)) {
					streamRetryAt = 0;
					return;
				}
				status = Status.ERROR;
				error = describe(cause);
				SalamiClient.LOG.debug("Freunde-Live-Verbindung getrennt: {}", cause.toString());
			}));
	}

	/**
	 * Lesbare Fehlermeldung statt Java-Ausnahmetext. Meldungen des Salami-Servers sind schon für Spieler
	 * gedacht (deutsch) und bleiben; Netzwerkfehler werden übersetzt. Die technische Ursache steht im Log.
	 */
	static Component describe(Throwable cause) {
		SalamiClient.LOG.debug("Freunde: {}", cause.toString());
		if (cause instanceof FriendsApi.ServerException server) {
			String message = server.getMessage();
			if (message != null && !message.isBlank() && !message.startsWith("HTTP ")) {
				return Component.literal(message);
			}
			return Component.translatable("salami.friends.error.server", server.status);
		}
		if (cause instanceof FriendsApi.MojangLoginException) {
			return Component.translatable("salami.friends.error.mojang");
		}
		if (cause instanceof java.net.http.HttpTimeoutException) {
			return Component.translatable("salami.friends.error.timeout");
		}
		if (cause instanceof java.io.IOException || cause instanceof java.nio.channels.UnresolvedAddressException) {
			return Component.translatable("salami.friends.error.unreachable");
		}
		SalamiClient.LOG.warn("Freunde: unerwarteter Fehler", cause);
		return Component.translatable("salami.friends.error.unknown");
	}

	/** Modul aus oder Adresse geändert: Verbindung schließen, Daten vergessen. */
	private static void disconnect() {
		if (stream != null) {
			FriendsApi.EventStream closing = stream;
			stream = null;
			closing.close();
		}
		friends = List.of();
		requests = List.of();
		unread.clear();
		conversations.clear();
		loading.clear();
		error = null;
		notice = null;
		streamRetryAt = 0;
		loginRetryAt = 0;
		revision++;
	}

	/** 401: Sitzung abgelaufen (z. B. Server-Neustart ohne Daten) – neu anmelden. */
	private static boolean unauthorized(Throwable cause) {
		if (cause instanceof FriendsApi.ServerException server && server.status == 401) {
			token = null;
			if (stream != null) {
				FriendsApi.EventStream closing = stream;
				stream = null;
				closing.close();
			}
			status = Status.CONNECTING;
			return true;
		}
		return false;
	}

	private static void refresh(Minecraft mc) {
		FriendsApi friendsApi = api;
		String current = token;
		if (friendsApi == null || current == null || refreshing) {
			return;
		}
		refreshing = true;
		refreshAt = System.currentTimeMillis() + REFRESH_MS;
		friendsApi.overview(current).whenComplete((overview, failure) -> mc.execute(() -> {
			refreshing = false;
			if (failure != null) {
				Throwable cause = FriendsApi.unwrap(failure);
				if (!unauthorized(cause)) {
					SalamiClient.LOG.debug("Freundesliste nicht geladen: {}", cause.getMessage());
					refreshAt = System.currentTimeMillis() + 30_000L;
				}
				return;
			}
			me = overview.me();
			List<Friend> list = new ArrayList<>();
			for (FriendsApi.Friend friend : overview.friends()) {
				list.add(new Friend(friend.uuid(), friend.name(), friend.online()));
			}
			list.sort(FRIEND_ORDER);
			friends = List.copyOf(list);
			List<Request> open = new ArrayList<>();
			overview.incoming().forEach(r -> open.add(new Request(r.uuid(), r.name(), true)));
			overview.outgoing().forEach(r -> open.add(new Request(r.uuid(), r.name(), false)));
			requests = List.copyOf(open);
			unread.clear();
			unread.putAll(overview.unread());
			if (viewing != null) {
				unread.remove(viewing);
			}
			conversations.keySet().removeIf(uuid -> friend(uuid) == null);
			revision++;
		}));
	}

	private static final Comparator<Friend> FRIEND_ORDER = Comparator.comparing((Friend f) -> !f.online())
		.thenComparing(f -> f.name().toLowerCase(Locale.ROOT));

	// ------------------------------------------------------------------ Ereignisse

	private static void onEvent(Minecraft mc, JsonObject event) {
		String type = FriendsApi.string(event, "type");
		if (type == null) {
			return;
		}
		FriendsModule module = FriendsModule.instance();
		switch (type) {
			case "presence" -> {
				UUID uuid = FriendsApi.uuid(event, "uuid");
				JsonElement onlineValue = event.get("online");
				if (uuid == null || onlineValue == null || !onlineValue.isJsonPrimitive()) {
					return;
				}
				boolean online = onlineValue.getAsBoolean();
				Friend known = friend(uuid);
				if (known == null) {
					refresh(mc);
					return;
				}
				if (known.online() == online) {
					return;
				}
				List<Friend> list = new ArrayList<>();
				for (Friend friend : friends) {
					list.add(friend.uuid().equals(uuid) ? new Friend(uuid, friend.name(), online) : friend);
				}
				list.sort(FRIEND_ORDER);
				friends = List.copyOf(list);
				revision++;
				if (module != null && module.announceOnline()) {
					Chat.info(online ? "salami.friends.chat.online" : "salami.friends.chat.offline", known.name());
				}
			}
			case "message" -> {
				JsonElement raw = event.get("message");
				FriendsApi.Message parsed = raw != null && raw.isJsonObject() ? FriendsApi.message(raw.getAsJsonObject()) : null;
				if (parsed == null) {
					return;
				}
				Message message = new Message(parsed.id(), parsed.from(), parsed.to(), parsed.text(), parsed.at());
				boolean mine = parsed.from().equals(me);
				UUID partner = mine ? parsed.to() : parsed.from();
				add(partner, message);
				if (mine) {
					return;
				}
				if (partner.equals(viewing)) {
					markRead(mc, partner);
					return;
				}
				unread.merge(partner, 1, Integer::sum);
				revision++;
				String name = FriendsApi.string(event, "name");
				Friend friend = friend(partner);
				announceMessage(mc, module, name != null ? name : friend != null ? friend.name() : "?", message.text());
			}
			case "request" -> {
				refresh(mc);
				String name = FriendsApi.string(event, "name");
				if (name != null) {
					Chat.info("salami.friends.chat.request", name, SalamiClient.menuKeyName());
					ping(mc, module);
				}
			}
			case "friends" -> {
				refresh(mc);
				String name = FriendsApi.string(event, "name");
				if (name != null) {
					Chat.info("salami.friends.chat.accepted", name);
				}
			}
			case "removed" -> {
				UUID uuid = FriendsApi.uuid(event, "uuid");
				if (uuid != null) {
					conversations.remove(uuid);
					unread.remove(uuid);
				}
				refresh(mc);
			}
			default -> {
				// "ready" und Unbekanntes
			}
		}
	}

	private static void add(UUID partner, Message message) {
		List<Message> list = conversations.get(partner);
		if (list == null) {
			return; // wird beim Öffnen vollständig geladen
		}
		for (Message existing : list) {
			if (existing.id().equals(message.id())) {
				return;
			}
		}
		List<Message> next = new ArrayList<>(list);
		next.add(message);
		if (next.size() > 200) {
			next.subList(0, next.size() - 200).clear();
		}
		conversations.put(partner, next);
		revision++;
	}

	private static void announceMessage(Minecraft mc, @Nullable FriendsModule module, String name, String text) {
		if (module == null || !module.showMessages()) {
			return;
		}
		// Text kommt vom Server bereits ohne §-Formatierung; als Literal, nie als Übersetzung
		MutableComponent line = Component.literal("✉ ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(name).withStyle(ChatFormatting.YELLOW))
			.append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
			.append(Component.literal(text).withStyle(ChatFormatting.WHITE));
		String reply = "/salami msg " + name + " ";
		line = line.withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand(reply))
			.withHoverEvent(new HoverEvent.ShowText(Component.translatable("salami.friends.chat.reply_hover"))));
		Chat.raw(line);
		ping(mc, module);
	}

	private static void ping(Minecraft mc, @Nullable FriendsModule module) {
		if (module != null && module.playSound()) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.6f));
		}
	}

	// ------------------------------------------------------------------ Aktionen (aus Menü und Befehl)

	/** Das Freunde-Menü zeigt diese Unterhaltung ({@code null} = keine) – lädt sie bei Bedarf. */
	public static void view(@Nullable UUID friend) {
		viewing = friend;
		if (friend == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (!conversations.containsKey(friend)) {
			load(mc, friend, false);
		} else if (unread(friend) > 0) {
			markRead(mc, friend);
		}
	}

	private static void load(Minecraft mc, UUID friend, boolean force) {
		FriendsApi friendsApi = api;
		String current = token;
		if (friendsApi == null || current == null || (!force && loading.contains(friend))) {
			return;
		}
		loading.add(friend);
		friendsApi.messages(current, friend).whenComplete((list, failure) -> mc.execute(() -> {
			loading.remove(friend);
			if (failure != null) {
				Throwable cause = FriendsApi.unwrap(failure);
				if (!unauthorized(cause)) {
					setNotice(describe(cause), true);
				}
				return;
			}
			List<Message> messages = new ArrayList<>();
			for (FriendsApi.Message m : list) {
				messages.add(new Message(m.id(), m.from(), m.to(), m.text(), m.at()));
			}
			conversations.put(friend, messages);
			unread.remove(friend);
			revision++;
		}));
	}

	private static void markRead(Minecraft mc, UUID friend) {
		unread.remove(friend);
		revision++;
		FriendsApi friendsApi = api;
		String current = token;
		if (friendsApi != null && current != null) {
			friendsApi.read(current, friend).whenComplete((ok, failure) -> {
				// egal: beim nächsten Laden stimmt der Zähler wieder
			});
		}
	}

	public static boolean isLoading(UUID friend) {
		return loading.contains(friend);
	}

	/** Freundschaftsanfrage per Minecraft-Name. */
	public static void addFriend(String name) {
		String trimmed = name.trim();
		if (trimmed.isEmpty()) {
			return;
		}
		call(t -> api.request(t, trimmed), result -> {
			if ("friends".equals(result.status())) {
				setNotice(Component.translatable("salami.friends.notice.now_friends", result.name() != null ? result.name() : trimmed), false);
			} else {
				setNotice(Component.translatable("salami.friends.notice.requested", result.name() != null ? result.name() : trimmed), false);
			}
		});
	}

	public static void accept(UUID uuid) {
		call(t -> api.accept(t, uuid), result -> {});
	}

	public static void deny(UUID uuid) {
		call(t -> api.deny(t, uuid), result -> {});
	}

	public static void remove(UUID uuid) {
		Friend friend = friend(uuid);
		call(t -> api.remove(t, uuid), result -> {
			conversations.remove(uuid);
			unread.remove(uuid);
			if (friend != null) {
				setNotice(Component.translatable("salami.friends.notice.removed", friend.name()), false);
			}
		});
	}

	/** Nachricht senden; {@code onSent} bekommt {@code true}, wenn der Server sie angenommen hat. */
	public static void send(UUID to, String text, Consumer<Boolean> onSent) {
		String trimmed = text.strip();
		if (trimmed.isEmpty()) {
			onSent.accept(false);
			return;
		}
		String limited = trimmed.length() > MAX_MESSAGE ? trimmed.substring(0, MAX_MESSAGE) : trimmed;
		Minecraft mc = Minecraft.getInstance();
		FriendsApi friendsApi = api;
		String current = token;
		if (friendsApi == null || current == null || status != Status.ONLINE) {
			setNotice(Component.translatable("salami.friends.notice.not_connected"), true);
			onSent.accept(false);
			return;
		}
		friendsApi.send(current, to, limited).whenComplete((message, failure) -> mc.execute(() -> {
			if (failure != null) {
				Throwable cause = FriendsApi.unwrap(failure);
				if (!unauthorized(cause)) {
					setNotice(describe(cause), true);
				}
				onSent.accept(false);
				return;
			}
			add(to, new Message(message.id(), message.from(), message.to(), message.text(), message.at()));
			onSent.accept(true);
		}));
	}

	/** Ruft die API mit dem aktuellen Token auf, danach Liste neu laden; Fehler landen als Hinweis im Menü. */
	private static <T> void call(java.util.function.Function<String, java.util.concurrent.CompletableFuture<T>> request, Consumer<T> onSuccess) {
		Minecraft mc = Minecraft.getInstance();
		String current = token;
		if (api == null || current == null || status != Status.ONLINE) {
			setNotice(Component.translatable("salami.friends.notice.not_connected"), true);
			return;
		}
		request.apply(current).whenComplete((result, failure) -> mc.execute(() -> {
			if (failure != null) {
				Throwable cause = FriendsApi.unwrap(failure);
				if (!unauthorized(cause)) {
					setNotice(describe(cause), true);
				}
				return;
			}
			onSuccess.accept(result);
			refresh(mc);
		}));
	}

	private static void setNotice(Component text, boolean isError) {
		notice = text;
		noticeError = isError;
		revision++;
	}

	public static void clearNotice() {
		notice = null;
	}
}
