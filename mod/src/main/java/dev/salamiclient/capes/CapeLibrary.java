package dev.salamiclient.capes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.salamiclient.modules.visual.CapesModule;
import net.minecraft.util.PngInfo;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Die eigenen Capes aus dem Launcher ({@code -Dsalami.capes.dir}, siehe {@code salami_capes.rs})
 * lesen, eins anlegen (auswählen) oder ein neues Bild hinzufügen – fürs Cosmetics-Menü im Spiel.
 * Launcher und Spiel teilen sich {@code capes.json}; {@link LauncherCapes} bemerkt Änderungen.
 */
public final class CapeLibrary {
	/** Eigenes Cape aus dem Launcher: ID (Dateiname), Name, Pfad zum PNG. */
	public record OwnCape(String id, String name, Path png) {}

	/** Wie im Launcher und auf dem Server. */
	public static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
	private static final int MAX_NAME = 40;
	private static final Pattern CAPE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String STORE = "capes.json";

	private CapeLibrary() {}

	/** Cape-Ordner des Launchers; {@code null} = Spiel nicht über den Salami Launcher gestartet. */
	public static @Nullable Path launcherDir() {
		String raw = System.getProperty(LauncherCapes.DIR_PROPERTY);
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return Path.of(raw);
		} catch (InvalidPathException e) {
			return null;
		}
	}

	/** Eigene Capes in der Reihenfolge des Launchers (nur solche, deren Bild existiert). */
	public static List<OwnCape> ownCapes(Path dir) {
		List<OwnCape> result = new ArrayList<>();
		JsonObject store = load(dir);
		JsonElement capes = store.get("capes");
		if (capes == null || !capes.isJsonArray()) {
			return result;
		}
		for (JsonElement entry : (JsonArray) capes) {
			if (!entry.isJsonObject()) {
				continue;
			}
			JsonObject cape = entry.getAsJsonObject();
			String id = string(cape, "id");
			if (id == null || !CAPE_ID.matcher(id).matches()) {
				continue;
			}
			Path png = dir.resolve(id + ".png");
			if (Files.isRegularFile(png)) {
				String name = string(cape, "name");
				result.add(new OwnCape(id, name != null && !name.isBlank() ? name : id, png));
			}
		}
		return result;
	}

	/**
	 * Auswahl im Launcher: {@code builtin:<name>}, ID eines eigenen Capes oder {@code null}
	 * (kein Cape). Ohne {@code capes.json} gilt wie im Launcher das Salami-Cape.
	 */
	public static @Nullable String selected(Path dir) {
		if (!Files.isRegularFile(dir.resolve(STORE))) {
			return "builtin:salami";
		}
		JsonElement selected = load(dir).get("selected");
		return selected == null || selected.isJsonNull() ? null : selected.getAsString();
	}

	/** Auswahl im Launcher setzen (wie {@code select} in {@code salami_capes.rs}). */
	public static void select(Path dir, @Nullable String selection) throws IOException {
		JsonObject store = loadForWrite(dir);
		store.add("selected", selection == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(selection));
		if (!store.has("capes")) {
			store.add("capes", new JsonArray());
		}
		save(dir, store);
		LauncherCapes.refreshNow();
	}

	/**
	 * Neues eigenes Cape zum Launcher hinzufügen: Bild prüfen, ohne Metadaten neu speichern,
	 * in {@code capes.json} eintragen. Liefert das neue Cape (noch nicht ausgewählt).
	 */
	public static OwnCape add(Path dir, Path source) throws IOException, InvalidCapeException {
		String id = UUID.randomUUID().toString();
		Path target = dir.resolve(id + ".png");
		Files.createDirectories(dir);
		writeCape(source, target);
		JsonObject store = loadForWrite(dir);
		JsonArray capes = store.has("capes") && store.get("capes").isJsonArray() ? store.getAsJsonArray("capes") : new JsonArray();
		JsonObject cape = new JsonObject();
		cape.addProperty("id", id);
		cape.addProperty("name", nameOf(source));
		cape.addProperty("created", Instant.now().toString());
		capes.add(cape);
		store.add("capes", capes);
		if (!store.has("selected")) {
			store.addProperty("selected", "builtin:salami");
		}
		save(dir, store);
		return new OwnCape(id, string(cape, "name"), target);
	}

	/** Ohne Launcher: Bild als {@code config/salami/cape.png} übernehmen (Cape „Eigenes“). */
	public static void replaceCustom(Path source) throws IOException, InvalidCapeException {
		Path target = CapesModule.customCapeFile();
		Files.createDirectories(target.getParent());
		writeCape(source, target);
	}

	/** Bild ist kein gültiges Cape; die Meldung ist ein Übersetzungsschlüssel. */
	public static final class InvalidCapeException extends Exception {
		public InvalidCapeException(String key) {
			super(key);
		}
	}

	/** Prüft das Bild (PNG, bis 4 MiB, Cape-Format) und schreibt es neu kodiert nach {@code target}. */
	private static void writeCape(Path source, Path target) throws IOException, InvalidCapeException {
		if (!source.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
			throw new InvalidCapeException("salami.cosmetics.error.not_png");
		}
		byte[] data;
		try (InputStream in = Files.newInputStream(source)) {
			data = in.readNBytes(MAX_FILE_BYTES + 1);
		}
		if (data.length > MAX_FILE_BYTES) {
			throw new InvalidCapeException("salami.cosmetics.error.too_big");
		}
		PngInfo info;
		try {
			info = PngInfo.fromBytes(data);
		} catch (Exception e) {
			throw new InvalidCapeException("salami.cosmetics.error.not_png");
		}
		if (!CapesModule.validCapeSize(info.width(), info.height())) {
			throw new InvalidCapeException("salami.cosmetics.error.size");
		}
		Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
		try (NativeImage image = NativeImage.read(data)) {
			if (!CapesModule.validCapeSize(image.getWidth(), image.getHeight())) {
				throw new InvalidCapeException("salami.cosmetics.error.size");
			}
			// Neu kodieren: keine Metadaten aus der Originaldatei
			image.writeToFile(tmp);
		} catch (IOException e) {
			Files.deleteIfExists(tmp);
			throw new InvalidCapeException("salami.cosmetics.error.not_png");
		}
		Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		LauncherCapes.refreshNow();
	}

	private static String nameOf(Path source) {
		String file = source.getFileName().toString();
		int dot = file.lastIndexOf('.');
		String name = (dot > 0 ? file.substring(0, dot) : file).trim();
		if (name.length() > MAX_NAME) {
			name = name.substring(0, MAX_NAME);
		}
		return name.isEmpty() ? "Mein Cape" : name;
	}

	private static JsonObject load(Path dir) {
		Path file = dir.resolve(STORE);
		try {
			if (Files.isRegularFile(file)) {
				JsonElement json = JsonParser.parseString(Files.readString(file));
				if (json.isJsonObject()) {
					return json.getAsJsonObject();
				}
			}
		} catch (IOException | RuntimeException ignored) {
			// kaputt oder halb geschrieben: wie leer
		}
		JsonObject empty = new JsonObject();
		empty.addProperty("selected", "builtin:salami");
		empty.add("capes", new JsonArray());
		return empty;
	}

	/** Wie {@link #load}, aber eine kaputte Datei wird nie überschrieben (sonst wären alle Capes weg). */
	private static JsonObject loadForWrite(Path dir) throws IOException {
		Path file = dir.resolve(STORE);
		if (!Files.isRegularFile(file)) {
			return load(dir);
		}
		try {
			JsonElement json = JsonParser.parseString(Files.readString(file));
			if (json.isJsonObject()) {
				return json.getAsJsonObject();
			}
		} catch (RuntimeException e) {
			throw new IOException("capes.json ist beschädigt", e);
		}
		throw new IOException("capes.json ist beschädigt");
	}

	private static void save(Path dir, JsonObject store) throws IOException {
		Files.createDirectories(dir);
		Path tmp = dir.resolve(STORE + ".tmp");
		Files.writeString(tmp, GSON.toJson(store));
		Files.move(tmp, dir.resolve(STORE), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	private static @Nullable String string(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}
}
