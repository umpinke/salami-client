package dev.salamiclient.emotes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Ein Emote als Daten: Schlüsselbilder für Kopf, Arme und Beine des Spielermodells. Die Liste kommt
 * aus dem Emote-Katalog ({@link EmoteCatalog}) – vom Salami-Server, damit neue Emotes kein Update
 * brauchen, sonst aus der Kopie in der Mod. Format und Grenzen wie auf dem Server
 * ({@code server/capes/emotes.mjs}); hier wird alles noch einmal geprüft.
 */
public final class Emote {
	/** Endlose Emotes (Tanzen, T-Pose) hören spätestens nach dieser Zeit auf. */
	public static final long MAX_LOOP_MS = 15_000L;
	private static final float MAX_SECONDS = 15f;
	private static final int MAX_KEYS = 32;
	private static final int MAX_NAME = 24;
	private static final float MAX_DEGREES = 720f;
	private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,24}");
	private static final Pattern LANG = Pattern.compile("[a-z]{2,3}_[a-z]{2,3}");

	/** Körperteile, die ein Emote bewegen kann. */
	public enum Part {
		HEAD,
		RIGHT_ARM,
		LEFT_ARM,
		RIGHT_LEG,
		LEFT_LEG
	}

	/** Worauf sich die Winkel beziehen. */
	public enum Base {
		/** Die Winkel gelten direkt. */
		NONE,
		/** Zur normalen Haltung des Körperteils addiert (Kopf: Blickrichtung bleibt). */
		CURRENT,
		/** Zur Blickrichtung des Kopfs addiert (z. B. der zeigende Arm). */
		HEAD
	}

	/** Schlüsselbild: Zeit in Sekunden, Drehung in Radiant. */
	public record Key(float t, float x, float y, float z) {}

	/**
	 * Bewegung eines Körperteils. Mit {@code cycle > 0} wiederholen sich die Schlüsselbilder in diesem
	 * Takt (vom letzten zurück zum ersten), sonst laufen sie einmal und halten das letzte.
	 */
	public record Track(Part part, Base base, float cycle, boolean linear, Key[] keys) {
		/** Drehung zur Zeit {@code t} (Sekunden seit Beginn) nach {@code out} = {x, y, z}. */
		public void sample(float t, float[] out) {
			Key from;
			Key to;
			float span;
			float into;
			int n = keys.length;
			if (n == 1) {
				set(out, keys[0]);
				return;
			}
			if (cycle > 0f) {
				float tt = t % cycle;
				int next = 0;
				while (next < n && keys[next].t() <= tt) {
					next++;
				}
				// next = erstes Schlüsselbild nach tt; davor liegt next-1 (oder das letzte aus dem vorigen Durchlauf)
				from = keys[(next - 1 + n) % n];
				to = keys[next % n];
				float fromT = next == 0 ? from.t() - cycle : from.t();
				float toT = next == n ? to.t() + cycle : to.t();
				span = toT - fromT;
				into = tt - fromT;
			} else {
				if (t <= keys[0].t()) {
					set(out, keys[0]);
					return;
				}
				if (t >= keys[n - 1].t()) {
					set(out, keys[n - 1]);
					return;
				}
				int next = 1;
				while (keys[next].t() <= t) {
					next++;
				}
				from = keys[next - 1];
				to = keys[next];
				span = to.t() - from.t();
				into = t - from.t();
			}
			float f = span <= 0f ? 1f : Math.max(0f, Math.min(1f, into / span));
			if (!linear) {
				f = (1f - (float) Math.cos(Math.PI * f)) / 2f;
			}
			out[0] = from.x() + (to.x() - from.x()) * f;
			out[1] = from.y() + (to.y() - from.y()) * f;
			out[2] = from.z() + (to.z() - from.z()) * f;
		}

		private static void set(float[] out, Key key) {
			out[0] = key.x();
			out[1] = key.y();
			out[2] = key.z();
		}
	}

	private final String id;
	private final Map<String, String> names;
	private final long durationMs;
	private final boolean loops;
	private final float iconTime;
	private final List<Track> tracks;

	private Emote(String id, Map<String, String> names, long durationMs, boolean loops, float iconTime, List<Track> tracks) {
		this.id = id;
		this.names = names;
		this.durationMs = durationMs;
		this.loops = loops;
		this.iconTime = iconTime;
		this.tracks = tracks;
	}

	/** Name für Server und Befehl ({@code wave}). */
	public String id() {
		return id;
	}

	/** Länge in ms; endlose Emotes haben {@link #MAX_LOOP_MS}. */
	public long durationMs() {
		return durationMs;
	}

	/** Läuft, bis sich der Spieler bewegt. */
	public boolean loops() {
		return loops;
	}

	public List<Track> tracks() {
		return tracks;
	}

	/** Zeitpunkt (s), dessen Haltung das Symbol im Menü zeigt. */
	public float iconTime() {
		return iconTime;
	}

	/** Anzeigename in der Sprache des Spiels, sonst englisch. */
	public String displayName() {
		String language = Minecraft.getInstance().options.languageCode;
		String name = language == null ? null : names.get(language.toLowerCase(Locale.ROOT));
		if (name == null) {
			name = names.get("en_us");
		}
		return name != null ? name : id;
	}

	// ------------------------------------------------------------------ Einlesen

	/** Liest und prüft ein Emote; wirft {@link IllegalArgumentException} mit dem Grund, wenn etwas nicht stimmt. */
	public static Emote parse(JsonObject json) {
		String id = string(json, "id");
		if (id == null || !ID.matcher(id).matches()) {
			throw new IllegalArgumentException("id fehlt oder ist ungültig");
		}
		Map<String, String> names = new LinkedHashMap<>();
		JsonElement rawNames = json.get("names");
		if (rawNames != null && rawNames.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : rawNames.getAsJsonObject().entrySet()) {
				String name = entry.getValue().isJsonPrimitive() ? entry.getValue().getAsString().strip() : "";
				if (names.size() < 12 && LANG.matcher(entry.getKey()).matches() && !name.isEmpty() && name.length() <= MAX_NAME
					&& name.chars().noneMatch(c -> c < 0x20 || c == '§')) {
					names.put(entry.getKey(), name);
				}
			}
		}
		if (!names.containsKey("en_us")) {
			throw new IllegalArgumentException(id + ": Name für en_us fehlt");
		}
		boolean loops = json.has("loop") && json.get("loop").isJsonPrimitive() && json.get("loop").getAsBoolean();
		long durationMs = loops ? MAX_LOOP_MS : Math.round(number(json.get("duration"), 0.2f, MAX_SECONDS, id + ": duration") * 1000f);

		JsonElement rawTracks = json.get("tracks");
		if (rawTracks == null || !rawTracks.isJsonObject()) {
			throw new IllegalArgumentException(id + ": tracks fehlt");
		}
		List<Track> tracks = new ArrayList<>();
		for (Map.Entry<String, JsonElement> entry : rawTracks.getAsJsonObject().entrySet()) {
			Part part;
			try {
				part = Part.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException unknown) {
				throw new IllegalArgumentException(id + ": Körperteil " + entry.getKey() + " unbekannt");
			}
			if (!entry.getValue().isJsonObject()) {
				throw new IllegalArgumentException(id + ": " + entry.getKey() + " ist kein Objekt");
			}
			tracks.add(track(id, part, entry.getValue().getAsJsonObject()));
		}
		if (tracks.isEmpty()) {
			throw new IllegalArgumentException(id + ": mindestens ein Körperteil nötig");
		}
		float iconTime = json.has("icon") ? number(json.get("icon"), 0f, MAX_SECONDS, id + ": icon") : defaultIconTime(tracks);
		return new Emote(id, Map.copyOf(names), durationMs, loops, iconTime, List.copyOf(tracks));
	}

	private static Track track(String id, Part part, JsonObject json) {
		String where = id + "/" + part.name().toLowerCase(Locale.ROOT);
		Base base = Base.NONE;
		String rawBase = string(json, "base");
		if (rawBase != null) {
			try {
				base = Base.valueOf(rawBase.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException unknown) {
				throw new IllegalArgumentException(where + ": base unbekannt");
			}
		}
		float cycle = json.has("cycle") ? number(json.get("cycle"), 0.1f, MAX_SECONDS, where + ": cycle") : 0f;
		String ease = string(json, "ease");
		if (ease != null && !ease.equals("sine") && !ease.equals("linear")) {
			throw new IllegalArgumentException(where + ": ease unbekannt");
		}
		JsonElement rawKeys = json.get("keys");
		if (rawKeys == null || !rawKeys.isJsonArray() || rawKeys.getAsJsonArray().isEmpty() || rawKeys.getAsJsonArray().size() > MAX_KEYS) {
			throw new IllegalArgumentException(where + ": 1 bis " + MAX_KEYS + " Schlüsselbilder nötig");
		}
		JsonArray array = rawKeys.getAsJsonArray();
		Key[] keys = new Key[array.size()];
		float last = -1f;
		for (int i = 0; i < keys.length; i++) {
			JsonElement element = array.get(i);
			JsonElement rot = element.isJsonObject() ? element.getAsJsonObject().get("rot") : null;
			if (rot == null || !rot.isJsonArray() || rot.getAsJsonArray().size() != 3) {
				throw new IllegalArgumentException(where + ": Schlüsselbild braucht t und rot [x, y, z]");
			}
			float t = number(element.getAsJsonObject().get("t"), 0f, MAX_SECONDS, where + ": t");
			if (t <= last || (cycle > 0f && t >= cycle)) {
				throw new IllegalArgumentException(where + ": Zeiten müssen aufsteigen und vor cycle liegen");
			}
			last = t;
			JsonArray r = rot.getAsJsonArray();
			keys[i] = new Key(t, radians(r.get(0), where), radians(r.get(1), where), radians(r.get(2), where));
		}
		return new Track(part, base, cycle, "linear".equals(ease), keys);
	}

	/** Ohne Angabe: das zweite Schlüsselbild der ersten bewegten Spur – meist der Ausschlag der Bewegung. */
	private static float defaultIconTime(List<Track> tracks) {
		for (Track track : tracks) {
			if (track.keys().length > 1) {
				return track.keys()[1].t();
			}
		}
		return 0f;
	}

	private static float radians(JsonElement value, String where) {
		return (float) Math.toRadians(number(value, -MAX_DEGREES, MAX_DEGREES, where + ": Winkel"));
	}

	private static float number(@Nullable JsonElement value, float min, float max, String what) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			throw new IllegalArgumentException(what + " fehlt");
		}
		float number = value.getAsFloat();
		if (Float.isNaN(number) || number < min || number > max) {
			throw new IllegalArgumentException(what + " muss von " + min + " bis " + max + " sein");
		}
		return number;
	}

	private static @Nullable String string(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}
}
