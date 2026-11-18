package dev.salamiclient.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Was alle Katalog-Einträge gemeinsam haben (Emotes, Hüte, offizielle Capes): eine {@code id} und
 * Anzeigenamen je Sprache. Die Regeln sind dieselben wie auf dem Server ({@code server/capes/catalog.mjs}).
 */
public final class CatalogEntry {
	private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,24}");
	private static final Pattern LANG = Pattern.compile("[a-z]{2,3}_[a-z]{2,3}");
	private static final int MAX_NAMES = 12;
	private static final int MAX_NAME = 24;

	private CatalogEntry() {}

	/** {@code id} des Eintrags; wirft {@link IllegalArgumentException}, wenn sie fehlt oder ungültig ist. */
	public static String id(JsonObject json) {
		String id = string(json, "id");
		if (id == null || !ID.matcher(id).matches()) {
			throw new IllegalArgumentException("id fehlt oder ist ungültig");
		}
		return id;
	}

	/** Anzeigenamen je Sprache ({@code en_us} ist Pflicht); ungültige Namen fallen weg. */
	public static Map<String, String> names(JsonObject json, String id) {
		Map<String, String> names = new LinkedHashMap<>();
		JsonElement raw = json.get("names");
		if (raw != null && raw.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : raw.getAsJsonObject().entrySet()) {
				String name = entry.getValue().isJsonPrimitive() ? entry.getValue().getAsString().strip() : "";
				if (names.size() < MAX_NAMES && LANG.matcher(entry.getKey()).matches() && !name.isEmpty() && name.length() <= MAX_NAME
					&& name.chars().noneMatch(c -> c < 0x20 || c == '§')) {
					names.put(entry.getKey(), name);
				}
			}
		}
		if (!names.containsKey("en_us")) {
			throw new IllegalArgumentException(id + ": Name für en_us fehlt");
		}
		return Map.copyOf(names);
	}

	/** Anzeigename in der Sprache des Spiels, sonst englisch. */
	public static String displayName(Map<String, String> names, String id) {
		String language = Minecraft.getInstance().options.languageCode;
		String name = language == null ? null : names.get(language.toLowerCase(Locale.ROOT));
		if (name == null) {
			name = names.get("en_us");
		}
		return name != null ? name : id;
	}

	public static @Nullable String string(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
	}

	/** Zahl im erlaubten Bereich; wirft {@link IllegalArgumentException} mit {@code what} im Text. */
	public static float number(@Nullable JsonElement value, float min, float max, String what) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			throw new IllegalArgumentException(what + " fehlt");
		}
		float number = value.getAsFloat();
		if (!Float.isFinite(number) || number < min || number > max) {
			throw new IllegalArgumentException(what + " muss von " + min + " bis " + max + " sein");
		}
		return number;
	}
}
