package dev.salamiclient.minigames;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Lokale Bestenlisten der Minispiele (die besten {@link #MAX_ENTRIES} je Spiel mit Name und Datum)
 * und der Ton-Schalter, gespeichert als JSON, z. B. {@code config/salami/minigames.json}:
 *
 * <pre>{"version": 1, "muted": false, "games": {"cold_cuts": [{"name": "Steve", "score": 1200, "date": "2026-11-15"}]}}</pre>
 *
 * Kaputte Dateien oder Einträge werden ignoriert statt das Spiel abstürzen zu lassen.
 */
public final class Highscores {
	public static final int MAX_ENTRIES = 5;
	private static final int VERSION = 1;
	private static final int MAX_NAME = 32;
	/** Größere Dateien sind kaputt oder manipuliert. */
	private static final long MAX_FILE_BYTES = 256 * 1024;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Ein Eintrag der Bestenliste; {@code date} im ISO-Format (2026-11-15). */
	public record Entry(String name, int score, String date) {}

	private final Path file;
	private final Map<String, List<Entry>> games = new HashMap<>();
	private boolean muted;
	private Consumer<String> log = message -> {};

	public Highscores(Path file) {
		this.file = file;
	}

	/** Wohin Warnungen beim Laden und Speichern gehen (Standard: nirgends). */
	public Highscores logTo(Consumer<String> log) {
		this.log = log;
		return this;
	}

	public void load() {
		games.clear();
		muted = false;
		if (!Files.exists(file)) {
			return;
		}
		try {
			if (Files.size(file) > MAX_FILE_BYTES) {
				log.accept("Minigame highscores " + file + " are too large – ignored");
				return;
			}
			JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!parsed.isJsonObject()) {
				log.accept("Minigame highscores " + file + " are not a JSON object – ignored");
				return;
			}
			JsonObject root = parsed.getAsJsonObject();
			if (root.get("muted") instanceof JsonPrimitive m && m.isBoolean()) {
				muted = m.getAsBoolean();
			}
			if (root.get("games") instanceof JsonObject saved) {
				for (Map.Entry<String, JsonElement> game : saved.entrySet()) {
					if (!(game.getValue() instanceof JsonArray array)) {
						continue;
					}
					for (JsonElement element : array) {
						Entry entry = parse(element);
						if (entry != null) {
							insert(game.getKey(), entry);
						}
					}
				}
			}
		} catch (Exception e) {
			log.accept("Minigame highscores " + file + " are unreadable – ignored (" + e.getMessage() + ")");
			games.clear();
		}
	}

	private static Entry parse(JsonElement element) {
		if (!(element instanceof JsonObject obj)) {
			return null;
		}
		if (!(obj.get("score") instanceof JsonPrimitive score) || !score.isNumber()) {
			return null;
		}
		String name = obj.get("name") instanceof JsonPrimitive n && n.isString() ? n.getAsString() : "?";
		String date = obj.get("date") instanceof JsonPrimitive d && d.isString() ? d.getAsString() : "";
		long value = score.getAsLong();
		if (value <= 0 || value > Integer.MAX_VALUE) {
			return null;
		}
		return new Entry(cleanName(name), (int) value, date.length() > 10 ? date.substring(0, 10) : date);
	}

	private static String cleanName(String name) {
		String clean = name.replaceAll("\\p{Cntrl}", "").strip();
		if (clean.isEmpty()) {
			clean = "?";
		}
		return clean.length() > MAX_NAME ? clean.substring(0, MAX_NAME) : clean;
	}

	/** Bestenliste eines Spiels, beste zuerst (höchstens {@link #MAX_ENTRIES}). */
	public List<Entry> top(String game) {
		return List.copyOf(games.getOrDefault(game, List.of()));
	}

	/** Bester Wert eines Spiels, 0 ohne Einträge. */
	public int best(String game) {
		List<Entry> list = games.get(game);
		return list == null || list.isEmpty() ? 0 : list.get(0).score();
	}

	/**
	 * Trägt ein Ergebnis ein und speichert. Gleich gute ältere Einträge bleiben davor.
	 *
	 * @return Platz in der Bestenliste (0 = bester) oder -1, wenn das Ergebnis nicht hineinkommt
	 */
	public int submit(String game, String name, int score, LocalDate date) {
		if (score <= 0) {
			return -1;
		}
		int rank = insert(game, new Entry(cleanName(name), score, date.toString()));
		if (rank >= 0) {
			save();
		}
		return rank;
	}

	private int insert(String game, Entry entry) {
		List<Entry> list = games.computeIfAbsent(game, id -> new ArrayList<>());
		int index = 0;
		while (index < list.size() && list.get(index).score() >= entry.score()) {
			index++;
		}
		if (index >= MAX_ENTRIES) {
			return -1;
		}
		list.add(index, entry);
		while (list.size() > MAX_ENTRIES) {
			list.remove(list.size() - 1);
		}
		return index;
	}

	public boolean muted() {
		return muted;
	}

	public void setMuted(boolean muted) {
		if (this.muted != muted) {
			this.muted = muted;
			save();
		}
	}

	/** Schreibt die Datei (über eine Zwischendatei, damit sie nie halb geschrieben ist). */
	public void save() {
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		root.addProperty("muted", muted);
		JsonObject saved = new JsonObject();
		games.keySet().stream().sorted().forEach(game -> {
			JsonArray array = new JsonArray();
			for (Entry entry : games.get(game)) {
				JsonObject obj = new JsonObject();
				obj.addProperty("name", entry.name());
				obj.addProperty("score", entry.score());
				obj.addProperty("date", entry.date());
				array.add(obj);
			}
			saved.add(game, array);
		});
		root.add("games", saved);
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			log.accept("Could not save minigame highscores to " + file + " (" + e.getMessage() + ")");
		}
	}
}
