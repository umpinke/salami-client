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
		JsonObject root = null;
		if (Files.exists(file)) {
			try {
				root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (Exception e) {
				SalamiClient.LOG.error("Config {} is unreadable – using defaults (backup kept)", file, e);
				backupBrokenFile();
			}
		}
		JsonObject moduleStates = root != null && root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
		for (Module module : modules.all()) {
			JsonObject state = moduleStates.has(module.id()) ? moduleStates.getAsJsonObject(module.id()) : null;
			if (state != null && state.has("settings")) {
				JsonObject settings = state.getAsJsonObject("settings");
				for (Setting<?> setting : module.settings()) {
					try {
						setting.fromJson(settings.get(setting.id()));
					} catch (Exception e) {
						SalamiClient.LOG.warn("Ignoring invalid value for {}.{}", module.id(), setting.id());
					}
				}
			}
			boolean enabled = state != null && state.has("enabled")
				? state.get("enabled").getAsBoolean()
				: module.enabledByDefault();
			modules.restoreState(module, enabled);
		}
		if (root != null && root.has("gui") && root.get("gui").isJsonObject()) {
			gui = root.getAsJsonObject("gui");
		}
		dirtySince = -1;
		SalamiClient.LOG.info("Config loaded ({} modules)", modules.all().size());
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

	private void backupBrokenFile() {
		try {
			Files.copy(file, file.resolveSibling("config.broken.json"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
			// Backup ist best effort
		}
	}
}
