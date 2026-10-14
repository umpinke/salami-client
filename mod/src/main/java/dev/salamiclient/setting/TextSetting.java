package dev.salamiclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Kurzer Text (z. B. eine Chat-Nachricht). Bearbeitbar im Mods-Menü, max. {@link #maxLength} Zeichen. */
public final class TextSetting extends Setting<String> {
	private final int maxLength;

	public TextSetting(String id, String defaultValue, int maxLength) {
		super(id, defaultValue);
		this.maxLength = maxLength;
	}

	public int maxLength() {
		return maxLength;
	}

	/** Steuerzeichen raus, auf die Höchstlänge kürzen. */
	@Override
	protected String sanitize(String raw) {
		if (raw == null) {
			return defaultValue();
		}
		StringBuilder clean = new StringBuilder();
		raw.codePoints()
			.filter(c -> c >= 0x20 && c != 0x7F && c != 0xA7) // kein „§“ (Formatierungscodes)
			.forEach(clean::appendCodePoint);
		String text = clean.toString();
		return text.length() > maxLength ? text.substring(0, maxLength) : text;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
			set(json.getAsString());
		}
	}
}
