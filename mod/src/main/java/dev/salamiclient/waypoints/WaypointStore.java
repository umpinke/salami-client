package dev.salamiclient.waypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.salamiclient.SalamiClient;
import net.minecraft.client.Minecraft;

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
	private static final long MAX_FILE_BYTES = 1024 * 1024;
	private static final int MAX_PER_WORLD = 500;
	private static final int MAX_WORLDS = 200;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private final Map<String, List<Waypoint>> byWorld = new LinkedHashMap<>();

	public WaypointStore(Path file) {
		this.file = file;
		load();
	}

	/** Schlüssel der aktuellen Welt: Server-Adresse oder Name der Einzelspieler-Welt; {@code null} = keine Welt. */
	public static String currentWorldKey() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}
		if (mc.getSingleplayerServer() != null) {
			return "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName();
		}
		if (mc.getCurrentServer() != null) {
			return "mp:" + mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
		}
		return null;
	}

	public List<Waypoint> current() {
		String key = currentWorldKey();
		return key == null ? List.of() : byWorld.computeIfAbsent(key, k -> new ArrayList<>());
	}

	/** {@code false}, wenn die Welt schon {@link #MAX_PER_WORLD} Wegpunkte hat. */
	public boolean add(Waypoint waypoint) {
		String key = currentWorldKey();
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
				SalamiClient.LOG.warn("waypoints.json is larger than 1 MiB – ignoring it");
				return;
			}
			JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!root.isJsonObject()) {
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
			SalamiClient.LOG.warn("Could not read waypoints.json: {}", e.getMessage());
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
