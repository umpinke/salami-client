package dev.salamiclient.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Liste von Strings (z. B. Server-Adressen). Wird vorerst über die Config-Datei
 * bearbeitet; die ClickGUI zeigt nur die Anzahl der Einträge.
 */
public final class StringListSetting extends Setting<List<String>> {
	public StringListSetting(String id, List<String> defaultValue) {
		super(id, List.copyOf(defaultValue));
	}

	@Override
	protected List<String> sanitize(List<String> raw) {
		if (raw == null) {
			return defaultValue();
		}
		List<String> cleaned = new ArrayList<>(raw.size());
		for (String entry : raw) {
			if (entry != null && !entry.isBlank()) {
				cleaned.add(entry.trim().toLowerCase(Locale.ROOT));
			}
		}
		return List.copyOf(cleaned);
	}

	@Override
	public JsonElement toJson() {
		JsonArray array = new JsonArray();
		get().forEach(array::add);
		return array;
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json == null || !json.isJsonArray()) {
			return;
		}
		List<String> values = new ArrayList<>();
		for (JsonElement element : json.getAsJsonArray()) {
			if (element.isJsonPrimitive()) {
				values.add(element.getAsString());
			}
		}
		set(values);
	}
}
