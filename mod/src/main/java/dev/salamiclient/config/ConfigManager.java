package dev.salamiclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.setting.Setting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Speichert Modulzustände, Einstellungen und GUI-Zustand als JSON unter
 * {@code .minecraft/config/salami/config.json}.
 *
 * <p>Änderungen markieren die Config nur als "dirty"; gespeichert wird gebündelt
 * frühestens {@link #SAVE_DELAY_MS} nach der letzten Änderung (und beim Beenden),
 * damit z. B. Slider-Ziehen nicht hunderte Schreibvorgänge auslöst.
 */
public final class ConfigManager {
	private static final int VERSION = 1;
	private static final long SAVE_DELAY_MS = 1500;
	/** Größere Dateien sind kaputt oder manipuliert – sie werden gar nicht erst gelesen. */
	private static final long MAX_FILE_BYTES = 1024 * 1024;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path file;
	private final ModuleManager modules;
	/** Frei verwendbarer Bereich für GUI-Zustand (Panel-Positionen …). */
	private JsonObject gui = new JsonObject();
	private long dirtySince = -1;

	public ConfigManager(Path directory, ModuleManager modules) {
		this.file = directory.resolve("config.json");
		this.modules = modules;
	}

	public void load() {
		JsonObject root = readRoot();
		// Typen prüfen statt blind getAsJsonObject()/getAsBoolean(): ein falscher Typ wird
		// übersprungen (Standardwert), statt den Start abstürzen zu lassen.
		JsonObject moduleStates = object(root, "modules", "modules");
		for (Module module : modules.all()) {
			JsonObject state = object(moduleStates, module.id(), "modules." + module.id());
			JsonObject settings = object(state, "settings", module.id() + ".settings");
			if (settings != null) {
				for (Setting<?> setting : module.settings()) {
					try {
						setting.fromJson(settings.get(setting.id()));
					} catch (Exception e) {
						SalamiClient.LOG.warn("Ignoring invalid value for {}.{}", module.id(), setting.id());
					}
				}
			}
			JsonElement enabledJson = state != null ? state.get("enabled") : null;
			boolean enabled = module.enabledByDefault();
			if (enabledJson != null && enabledJson.isJsonPrimitive() && enabledJson.getAsJsonPrimitive().isBoolean()) {
				enabled = enabledJson.getAsBoolean();
			} else if (enabledJson != null) {
				SalamiClient.LOG.warn("Ignoring invalid value for {}.enabled", module.id());
			}
			modules.restoreState(module, enabled);
		}
		JsonObject savedGui = object(root, "gui", "gui");
		if (savedGui != null) {
			gui = savedGui;
		}
		dirtySince = -1;
		SalamiClient.LOG.info("Config loaded ({} modules)", modules.all().size());
	}

	/**
	 * Liest die Config-Datei; {@code null} = keine Datei oder unbrauchbar. Unbrauchbare
	 * Dateien (zu groß, kein gültiges JSON-Objekt) werden vor dem nächsten Speichern als
	 * Backup beiseitegelegt, es gelten die Standardwerte.
	 */
	private JsonObject readRoot() {
		if (!Files.exists(file)) {
			return null;
		}
		try {
			long size = Files.size(file);
			if (size > MAX_FILE_BYTES) {
				SalamiClient.LOG.warn("Config {} is too large ({} bytes, max {}) – using defaults (backup kept)", file, size, MAX_FILE_BYTES);
				backupBrokenFile();
				return null;
			}
			JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (parsed.isJsonObject()) {
				return parsed.getAsJsonObject();
			}
			SalamiClient.LOG.error("Config {} is not a JSON object – using defaults (backup kept)", file);
		} catch (Exception e) {
			SalamiClient.LOG.error("Config {} is unreadable – using defaults (backup kept)", file, e);
		}
		backupBrokenFile();
		return null;
	}

	/** {@code parent.key} als Objekt; fehlt es oder hat es einen anderen Typ: {@code null}. */
	private static JsonObject object(JsonObject parent, String key, String path) {
		JsonElement value = parent != null ? parent.get(key) : null;
		if (value == null || value.isJsonNull()) {
			return null;
		}
		if (!value.isJsonObject()) {
			SalamiClient.LOG.warn("Ignoring invalid config entry {} (expected an object)", path);
			return null;
		}
		return value.getAsJsonObject();
	}

	public void markDirty() {
		if (dirtySince < 0) {
			dirtySince = System.currentTimeMillis();
		}
	}

	/** Pro Tick aufrufen; speichert verzögert. */
	public void tick() {
		if (dirtySince >= 0 && System.currentTimeMillis() - dirtySince >= SAVE_DELAY_MS) {
			saveNow();
		}
	}

	public JsonObject gui() {
		return gui;
	}

	public void saveNow() {
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		JsonObject moduleStates = new JsonObject();
		for (Module module : modules.all()) {
			JsonObject state = new JsonObject();
			state.addProperty("enabled", module.isEnabled());
			JsonObject settings = new JsonObject();
			for (Setting<?> setting : module.settings()) {
				JsonElement value = setting.toJson();
				settings.add(setting.id(), value);
			}
			state.add("settings", settings);
			moduleStates.add(module.id(), state);
		}
		root.add("modules", moduleStates);
		root.add("gui", gui);

		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("config.json.tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			dirtySince = -1;
		} catch (IOException e) {
			SalamiClient.LOG.error("Could not save config {}", file, e);
		}
	}

	/**
	 * Legt die unbrauchbare Datei als {@code config.broken.json} beiseite, bevor sie beim
	 * nächsten Speichern mit Standardwerten überschrieben wird. Verschieben statt Kopieren:
	 * geht auch bei übergroßen Dateien sofort.
	 */
	private void backupBrokenFile() {
		try {
			Files.move(file, file.resolveSibling("config.broken.json"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			// Backup ist best effort
			SalamiClient.LOG.warn("Could not back up broken config {}", file, e);
		}
	}
}
