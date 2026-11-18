package dev.salamiclient.emotes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.salamiclient.cosmetics.BoxModel;
import dev.salamiclient.cosmetics.CatalogEntry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ein Emote als Daten: Schlüsselbilder für Kopf, Arme und Beine des Spielermodells. Die Liste kommt
 * aus dem Emote-Katalog ({@link EmoteCatalog}) – vom Salami-Server, damit neue Emotes kein Update
 * brauchen, sonst aus der Kopie in der Mod. Format und Grenzen wie auf dem Server
 * ({@code server/capes/emotes.mjs}); hier wird alles noch einmal geprüft.
 *
 * <p>Ein Emote kann außerdem etwas mitbringen: Requisiten an einem Körperteil ({@link Prop}), einen
 * Wurf ({@link Throw}), Geräusche ({@link Sound}) und eine Verwandlung ({@link Costume}) – alles aus
 * Würfeln ({@link BoxModel}) und nur zum Ansehen.
 */
public final class Emote {
	/** Endlose Emotes (Tanzen, T-Pose) hören spätestens nach dieser Zeit auf. */
	public static final long MAX_LOOP_MS = 15_000L;
	private static final float MAX_SECONDS = 15f;
	private static final int MAX_KEYS = 32;
	private static final float MAX_DEGREES = 720f;

	/** Körperteile, die ein Emote bewegen kann. */
	public enum Part {
		HEAD,
		RIGHT_ARM,
		LEFT_ARM,
		RIGHT_LEG,
		LEFT_LEG
	}

	/** Woran eine Requisite hängen kann. */
	public enum Limb {
		HEAD,
		BODY,
		RIGHT_ARM,
		LEFT_ARM,
		RIGHT_LEG,
		LEFT_LEG
	}

	/**
	 * Requisite: ein Modell, das von {@code from} bis {@code to} (Sekunden; {@code to} < 0 = bis zum
	 * Schluss) an einem Körperteil hängt. {@code at} in Pixeln ab dessen Gelenk, {@code rot} in Radiant.
	 */
	public record Prop(Limb on, float from, float to, float[] at, float[] rot, float scale, BoxModel model) {
		public boolean visibleAt(float t) {
			return t >= from && (to < 0f || t < to);
		}
	}

	/**
	 * Wurf: zum Zeitpunkt {@code t} fliegt das Modell in Blickrichtung los – {@code speed} Blöcke je
	 * Tick, {@code up} zusätzlich nach oben, {@code gravity} zieht es herunter, {@code spin} dreht es
	 * (Radiant je Tick). {@code impact}: was beim Aufprall zu sehen ist ({@code none}, {@code snow},
	 * {@code poof}). Es trifft niemanden.
	 */
	public record Throw(float t, float speed, float up, float gravity, float scale, float spin, String impact, BoxModel model) {}

	/** Geräusch zum Zeitpunkt {@code t}: {@code snowball}, {@code snow}, {@code bell} oder {@code pop}. */
	public record Sound(float t, String sound) {}

	/** Ein Teil einer Verwandlung, das sich um {@code at} dreht wie das Körperteil {@code follow}. */
	public record Piece(Part follow, float[] at, float[] rot, BoxModel model) {}

	/**
	 * Verwandlung: solange das Emote läuft, steht diese Figur anstelle des Spielers. {@code body} sind
	 * die festen Teile ({@code null}, wenn alle folgen), {@code pieces} bewegen sich nach {@code tracks}.
	 * Maße im Raum des Spielermodells (Nullpunkt mitten im Hals, Boden bei y 24).
	 */
	public record Costume(float scale, float hop, float hopCycle, @Nullable BoxModel body, List<Piece> pieces, List<Track> tracks) {
		/** Bewegung für dieses Körperteil oder {@code null}. */
		public @Nullable Track track(Part part) {
			for (Track track : tracks) {
				if (track.part() == part) {
					return track;
				}
			}
			return null;
		}
	}

	/** Wie auf dem Server. */
	private static final int MAX_PROPS = 4;
	private static final int MAX_SOUNDS = 8;
	private static final List<String> IMPACTS = List.of("none", "snow", "poof");
	private static final List<String> SOUNDS = List.of("snowball", "snow", "bell", "pop");

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
	private final List<Prop> props;
	private final @Nullable Throw thrown;
	private final List<Sound> sounds;
	private final @Nullable Costume costume;

	private Emote(String id, Map<String, String> names, long durationMs, boolean loops, float iconTime, List<Track> tracks,
			List<Prop> props, @Nullable Throw thrown, List<Sound> sounds, @Nullable Costume costume) {
		this.id = id;
		this.names = names;
		this.durationMs = durationMs;
		this.loops = loops;
		this.iconTime = iconTime;
		this.tracks = tracks;
		this.props = props;
		this.thrown = thrown;
		this.sounds = sounds;
		this.costume = costume;
	}

	/** Requisiten, die während des Emotes an Körperteilen hängen. */
	public List<Prop> props() {
		return props;
	}

	/** Was das Emote wirft, oder {@code null}. */
	public @Nullable Throw thrown() {
		return thrown;
	}

	public List<Sound> sounds() {
		return sounds;
	}

	/** Die Figur, zu der der Spieler wird, oder {@code null}. */
	public @Nullable Costume costume() {
		return costume;
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
		return CatalogEntry.displayName(names, id);
	}

	// ------------------------------------------------------------------ Einlesen

	/** Liest und prüft ein Emote; wirft {@link IllegalArgumentException} mit dem Grund, wenn etwas nicht stimmt. */
	public static Emote parse(JsonObject json) {
		String id = CatalogEntry.id(json);
		Map<String, String> names = CatalogEntry.names(json, id);
		boolean loops = json.has("loop") && json.get("loop").isJsonPrimitive() && json.get("loop").getAsBoolean();
		long durationMs = loops ? MAX_LOOP_MS : Math.round(number(json.get("duration"), 0.2f, MAX_SECONDS, id + ": duration") * 1000f);

		JsonElement rawTracks = json.get("tracks");
		if (rawTracks == null || !rawTracks.isJsonObject()) {
			throw new IllegalArgumentException(id + ": tracks fehlt");
		}
		List<Track> tracks = tracks(id, rawTracks.getAsJsonObject());
		if (tracks.isEmpty()) {
			throw new IllegalArgumentException(id + ": mindestens ein Körperteil nötig");
		}
		float iconTime = json.has("icon") ? number(json.get("icon"), 0f, MAX_SECONDS, id + ": icon") : defaultIconTime(tracks);

		List<Prop> props = new ArrayList<>();
		JsonElement rawProps = json.get("props");
		if (rawProps != null) {
			if (!rawProps.isJsonArray() || rawProps.getAsJsonArray().size() > MAX_PROPS) {
				throw new IllegalArgumentException(id + ": props: höchstens " + MAX_PROPS);
			}
			for (JsonElement prop : rawProps.getAsJsonArray()) {
				props.add(prop(id, prop));
			}
		}
		Throw thrown = json.has("throw") ? thrown(id, json.get("throw")) : null;
		List<Sound> sounds = new ArrayList<>();
		JsonElement rawSounds = json.get("sounds");
		if (rawSounds != null) {
			if (!rawSounds.isJsonArray() || rawSounds.getAsJsonArray().size() > MAX_SOUNDS) {
				throw new IllegalArgumentException(id + ": sounds: höchstens " + MAX_SOUNDS);
			}
			for (JsonElement sound : rawSounds.getAsJsonArray()) {
				JsonObject entry = object(sound, id + ": sounds");
				String name = string(entry, "sound");
				if (name == null || !SOUNDS.contains(name)) {
					throw new IllegalArgumentException(id + ": sounds: sound unbekannt");
				}
				sounds.add(new Sound(number(entry.get("t"), 0f, MAX_SECONDS, id + ": sounds: t"), name));
			}
		}
		Costume costume = json.has("costume") ? costume(id, json.get("costume")) : null;
		return new Emote(id, names, durationMs, loops, iconTime, List.copyOf(tracks), List.copyOf(props), thrown, List.copyOf(sounds), costume);
	}

	private static List<Track> tracks(String id, JsonObject raw) {
		List<Track> tracks = new ArrayList<>();
		for (Map.Entry<String, JsonElement> entry : raw.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				throw new IllegalArgumentException(id + ": " + entry.getKey() + " ist kein Objekt");
			}
			tracks.add(track(id, part(id, entry.getKey()), entry.getValue().getAsJsonObject()));
		}
		return tracks;
	}

	private static Part part(String id, String name) {
		try {
			return Part.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException unknown) {
			throw new IllegalArgumentException(id + ": Körperteil " + name + " unbekannt");
		}
	}

	private static JsonObject object(@Nullable JsonElement raw, String what) {
		if (raw == null || !raw.isJsonObject()) {
			throw new IllegalArgumentException(what + ": kein Objekt");
		}
		return raw.getAsJsonObject();
	}

	/** {@code [x, y, z]} in Grad → Radiant; fehlt es, gilt {0, 0, 0}. */
	private static float[] angles(JsonObject json, String what) {
		float[] rot = json.has("rot") ? BoxModel.triple(json.get("rot"), 360f, what + ": rot") : new float[3];
		for (int i = 0; i < 3; i++) {
			rot[i] = (float) Math.toRadians(rot[i]);
		}
		return rot;
	}

	private static Prop prop(String id, JsonElement raw) {
		String where = id + ": props";
		JsonObject json = object(raw, where);
		Limb on;
		try {
			on = Limb.valueOf(String.valueOf(string(json, "on")).toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException unknown) {
			throw new IllegalArgumentException(where + ": on unbekannt");
		}
		float from = json.has("from") ? number(json.get("from"), 0f, MAX_SECONDS, where + ": from") : 0f;
		float to = json.has("to") ? number(json.get("to"), 0f, MAX_SECONDS, where + ": to") : -1f;
		if (json.has("to") && to <= from) {
			throw new IllegalArgumentException(where + ": to muss nach from liegen");
		}
		float[] at = json.has("at") ? BoxModel.triple(json.get("at"), 32f, where + ": at") : new float[3];
		float scale = json.has("scale") ? number(json.get("scale"), 0.1f, 3f, where + ": scale") : 1f;
		return new Prop(on, from, to, at, angles(json, where), scale, BoxModel.parse(json.get("parts"), where));
	}

	private static Throw thrown(String id, JsonElement raw) {
		String where = id + ": throw";
		JsonObject json = object(raw, where);
		float t = number(json.get("t"), 0f, MAX_SECONDS, where + ": t");
		float speed = json.has("speed") ? number(json.get("speed"), 0.1f, 3f, where + ": speed") : 1f;
		float up = json.has("up") ? number(json.get("up"), 0f, 1f, where + ": up") : 0.1f;
		float gravity = json.has("gravity") ? number(json.get("gravity"), 0f, 0.2f, where + ": gravity") : 0.03f;
		float scale = json.has("scale") ? number(json.get("scale"), 0.1f, 3f, where + ": scale") : 1f;
		float spin = json.has("spin") ? (float) Math.toRadians(number(json.get("spin"), 0f, 90f, where + ": spin")) : 0f;
		String impact = json.has("impact") ? String.valueOf(string(json, "impact")) : "none";
		if (!IMPACTS.contains(impact)) {
			throw new IllegalArgumentException(where + ": impact unbekannt");
		}
		return new Throw(t, speed, up, gravity, scale, spin, impact, BoxModel.parse(json.get("parts"), where));
	}

	private static Costume costume(String id, JsonElement raw) {
		String where = id + ": costume";
		JsonObject json = object(raw, where);
		float scale = json.has("scale") ? number(json.get("scale"), 0.1f, 3f, where + ": scale") : 1f;
		float hop = json.has("hop") ? number(json.get("hop"), 0f, 8f, where + ": hop") : 0f;
		float hopCycle = json.has("hop_cycle") ? number(json.get("hop_cycle"), 0.1f, MAX_SECONDS, where + ": hop_cycle") : 0.5f;
		JsonElement rawParts = json.get("parts");
		// Teile mit "follow" werden je ein eigenes Modell um ihren Drehpunkt, der Rest steht fest
		List<BoxModel> models = BoxModel.parseSplit(rawParts, where, part -> part.has("follow"));
		List<Piece> pieces = new ArrayList<>();
		int next = 1;
		for (JsonElement element : rawParts.getAsJsonArray()) {
			JsonObject part = element.getAsJsonObject();
			if (!part.has("follow")) {
				continue;
			}
			Part follow = part(where, String.valueOf(string(part, "follow")));
			float[] at = part.has("at") ? BoxModel.triple(part.get("at"), 32f, where + ": at") : new float[3];
			pieces.add(new Piece(follow, at, angles(part, where), models.get(next++)));
		}
		List<Track> tracks = json.has("tracks") ? tracks(where, object(json.get("tracks"), where + ": tracks")) : List.of();
		return new Costume(scale, hop, hopCycle, models.get(0), List.copyOf(pieces), List.copyOf(tracks));
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
