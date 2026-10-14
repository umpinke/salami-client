package dev.salamiclient.waypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.salamiclient.SalamiClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wegpunkte pro Server bzw. Einzelspieler-Welt in {@code config/salami/waypoints.json}.
 * Liest defensiv (Größen- und Mengenlimits, falsche Typen werden übersprungen).
 */
public final class WaypointStore {
	/** Passt zu den Mengenlimits (200 Welten × 500 Wegpunkte ≈ 20 MB). */
	private static final long MAX_FILE_BYTES = 32L * 1024 * 1024;
	private static final int MAX_PER_WORLD = 500;
	private static final int MAX_WORLDS = 200;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private final Map<String, List<Waypoint>> byWorld = new LinkedHashMap<>();
	/** Unlesbare Datei ließ sich nicht sichern – dann lieber nichts speichern als sie überschreiben. */
	private boolean saveBlocked;

	public WaypointStore(Path file) {
		this.file = file;
		load();
	}

	/**
	 * Schlüssel der aktuellen Welt: Server-Adresse oder Ordner der Einzelspieler-Welt (der Name kann
	 * bei mehreren Welten gleich sein); {@code null} = keine Welt.
	 */
	public static String currentWorldKey() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}
		if (mc.getSingleplayerServer() != null) {
			Path folder = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
			return folder != null ? "spdir:" + folder : "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName();
		}
		if (mc.getCurrentServer() != null) {
			return "mp:" + mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
		}
		return null;
	}

	public List<Waypoint> current() {
		String key = currentWorldKey();
		if (key == null) {
			return List.of();
		}
		migrateLegacyKey(key);
		return byWorld.computeIfAbsent(key, k -> new ArrayList<>());
	}

	/** Bis 1.1.0-beta.5 hingen Einzelspieler-Wegpunkte am Weltnamen – einmalig zum Ordner umziehen. */
	private void migrateLegacyKey(String key) {
		if (!key.startsWith("spdir:") || byWorld.containsKey(key)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.getSingleplayerServer() == null) {
			return;
		}
		List<Waypoint> legacy = byWorld.remove("sp:" + mc.getSingleplayerServer().getWorldData().getLevelName());
		if (legacy != null) {
			byWorld.put(key, legacy);
			save();
		}
	}

	/** {@code false}, wenn die Welt schon {@link #MAX_PER_WORLD} Wegpunkte hat. */
	public boolean add(Waypoint waypoint) {
		String key = currentWorldKey();
		if (key != null) {
			migrateLegacyKey(key);
		}
		if (key == null || byWorld.size() >= MAX_WORLDS && !byWorld.containsKey(key)) {
			return false;
		}
		List<Waypoint> list = byWorld.computeIfAbsent(key, k -> new ArrayList<>());
		if (list.size() >= MAX_PER_WORLD) {
			return false;
		}
		list.add(waypoint);
		save();
		return true;
	}

	public void remove(Waypoint waypoint) {
		current().remove(waypoint);
		save();
	}

	// ------------------------------------------------------------------ Datei

	private void load() {
		try {
			if (!Files.isRegularFile(file)) {
				return;
			}
			if (Files.size(file) > MAX_FILE_BYTES) {
				keepBrokenFile("larger than 32 MiB");
				return;
			}
			JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!root.isJsonObject()) {
				keepBrokenFile("not a JSON object");
				return;
			}
			for (Map.Entry<String, JsonElement> world : root.getAsJsonObject().entrySet()) {
				if (byWorld.size() >= MAX_WORLDS || !world.getValue().isJsonArray()) {
					continue;
				}
				List<Waypoint> list = new ArrayList<>();
				for (JsonElement entry : world.getValue().getAsJsonArray()) {
					Waypoint waypoint = parse(entry);
					if (waypoint != null && list.size() < MAX_PER_WORLD) {
						list.add(waypoint);
					}
				}
				byWorld.put(world.getKey(), list);
			}
		} catch (Exception e) {
			keepBrokenFile(e.getMessage());
		}
	}

	/**
	 * Unlesbare Datei beiseitelegen ({@code waypoints.broken.json}), statt sie beim nächsten
	 * Speichern mit einer leeren Liste zu überschreiben.
	 */
	private void keepBrokenFile(String reason) {
		Path broken = file.resolveSibling("waypoints.broken.json");
		try {
			Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
			SalamiClient.LOG.warn("Could not read waypoints.json ({}) – kept it as waypoints.broken.json", reason);
		} catch (IOException e) {
			saveBlocked = true;
			SalamiClient.LOG.warn("Could not read waypoints.json ({}) and could not back it up: {} – not saving waypoints", reason, e.getMessage());
		}
	}

	private static Waypoint parse(JsonElement element) {
		if (!element.isJsonObject()) {
			return null;
		}
		JsonObject o = element.getAsJsonObject();
		try {
			Waypoint waypoint = new Waypoint(
				o.get("name").getAsString(),
				o.get("x").getAsInt(),
				o.get("y").getAsInt(),
				o.get("z").getAsInt(),
				o.get("dimension").getAsString(),
				o.get("color").getAsInt());
			waypoint.visible = !o.has("visible") || o.get("visible").getAsBoolean();
			return waypoint;
		} catch (Exception e) {
			return null; // fehlende Felder oder falsche Typen: Eintrag überspringen
		}
	}

	public void save() {
		if (saveBlocked) {
			return;
		}
		JsonObject root = new JsonObject();
		for (Map.Entry<String, List<Waypoint>> world : byWorld.entrySet()) {
			if (world.getValue().isEmpty()) {
				continue;
			}
			JsonArray list = new JsonArray();
			for (Waypoint waypoint : world.getValue()) {
				JsonObject o = new JsonObject();
				o.addProperty("name", waypoint.name);
				o.addProperty("x", waypoint.x);
				o.addProperty("y", waypoint.y);
				o.addProperty("z", waypoint.z);
				o.addProperty("dimension", waypoint.dimension);
				o.addProperty("color", waypoint.color);
				o.addProperty("visible", waypoint.visible);
				list.add(o);
			}
			root.add(world.getKey(), list);
		}
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("waypoints.json.tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			SalamiClient.LOG.warn("Could not save waypoints.json: {}", e.getMessage());
		}
	}
}
