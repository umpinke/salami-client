package dev.salamiclient.capes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Cape-Auswahl aus dem Launcher, live: der Launcher gibt beim Spielstart seinen Cape-Ordner
 * mit ({@code -Dsalami.capes.dir}). Dort steht in {@code capes.json}, welches Cape gewählt
 * ist (siehe {@code salami_capes.rs}). Wählt man im Launcher ein anderes Cape oder ändert
 * das gewählte Bild, während das Spiel läuft, gilt das nach spätestens einer Sekunde.
 *
 * <p>Ohne Ordner (ältere Launcher, Gametests) gilt nur {@code -Dsalami.cape} vom Spielstart.
 */
public final class LauncherCapes {
	public static final String DIR_PROPERTY = "salami.capes.dir";
	private static final long CHECK_MS = 1_000L;
	/** Wie {@code CapeStore::default} im Launcher. */
	private static final String DEFAULT = "builtin:salami";
	private static final Pattern CAPE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

	private static @Nullable String dir;
	private static long checkedAt;
	private static long storeModified = Long.MIN_VALUE;
	private static long imageModified = Long.MIN_VALUE;
	private static @Nullable String selection;
	private static int revision;

	private LauncherCapes() {}

	/**
	 * Gewähltes Cape im Format von {@code -Dsalami.cape}: {@code none}, {@code builtin:<name>}
	 * oder der Pfad zur PNG-Datei. {@code null} = kein Launcher-Ordner bekannt.
	 */
	public static synchronized @Nullable String selection() {
		refresh();
		return selection;
	}

	/** Ändert sich, sobald sich die Auswahl oder das gewählte Bild ändert. */
	public static synchronized int revision() {
		refresh();
		return revision;
	}

	private static void refresh() {
		String configured = System.getProperty(DIR_PROPERTY);
		long now = System.currentTimeMillis();
		boolean dirChanged = !Objects.equals(configured, dir);
		if (!dirChanged && now - checkedAt < CHECK_MS) {
			return;
		}
		dir = configured;
		checkedAt = now;
		Path base = null;
		if (configured != null && !configured.isBlank()) {
			try {
				base = Path.of(configured);
			} catch (InvalidPathException ignored) {
				// wie kein Ordner
			}
		}
		if (base == null) {
			update(null, Long.MIN_VALUE);
			return;
		}
		long modified = lastModified(base.resolve("capes.json"));
		String next = dirChanged || modified != storeModified ? read(base) : selection;
		storeModified = modified;
		boolean image = next != null && !next.equals("none") && !next.startsWith("builtin:");
		update(next, image ? lastModified(Path.of(next)) : Long.MIN_VALUE);
	}

	private static void update(@Nullable String next, long image) {
		if (!Objects.equals(next, selection) || image != imageModified) {
			selection = next;
			imageModified = image;
			revision++;
		}
	}

	/** Liest {@code capes.json} wie {@code launch_arg} im Launcher. */
	private static String read(Path base) {
		Path store = base.resolve("capes.json");
		if (!Files.isRegularFile(store)) {
			return DEFAULT;
		}
		try {
			JsonObject json = JsonParser.parseString(Files.readString(store)).getAsJsonObject();
			JsonElement selected = json.get("selected");
			if (selected == null || selected.isJsonNull()) {
				return "none";
			}
			String value = selected.getAsString();
			if (value.startsWith("builtin:")) {
				return value;
			}
			if (!CAPE_ID.matcher(value).matches() || !listed(json, value)) {
				return DEFAULT;
			}
			Path png = base.resolve(value + ".png");
			return Files.isRegularFile(png) ? png.toString() : DEFAULT;
		} catch (IOException | RuntimeException e) {
			// gerade halb geschrieben o. ä.: bisherige Auswahl behalten
			return selection != null ? selection : DEFAULT;
		}
	}

	private static boolean listed(JsonObject json, String id) {
		JsonElement capes = json.get("capes");
		if (capes == null || !capes.isJsonArray()) {
			return false;
		}
		for (JsonElement cape : (JsonArray) capes) {
			if (cape.isJsonObject() && cape.getAsJsonObject().has("id")
				&& id.equals(cape.getAsJsonObject().get("id").getAsString())) {
				return true;
			}
		}
		return false;
	}

	private static long lastModified(Path file) {
		try {
			return Files.getLastModifiedTime(file).toMillis();
		} catch (IOException | InvalidPathException e) {
			return Long.MIN_VALUE + 1;
		}
	}
}
