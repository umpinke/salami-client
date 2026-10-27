package dev.salamiclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.util.Keys;

/** Tastencode wie in {@link InputConstants} (bis 26.2 GLFW, ab 26.3 SDL-Scancode); {@link #NONE} = keine Taste. */
public final class KeybindSetting extends Setting<Integer> {
	public static final int NONE = Keys.NONE;

	public KeybindSetting(String id, int defaultKey) {
		super(id, defaultKey);
	}

	public int key() {
		return get();
	}

	public boolean isBound() {
		return get() != NONE;
	}

	/** Lokalisierter Tastenname, z. B. "R" oder "Rechte Umschalttaste". */
	public String keyName() {
		if (!isBound()) {
			return "-";
		}
		return InputConstants.Type.KEYSYM.getOrCreate(get()).getDisplayName().getString();
	}

	/** Nur gültige Tastencodes (siehe {@link Keys#isValid}); alles andere = keine Taste. */
	@Override
	protected Integer sanitize(Integer raw) {
		if (raw == null) {
			return defaultValue();
		}
		return Keys.isValid(raw) ? raw : NONE;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			double raw = json.getAsDouble();
			// Kommazahlen/NaN -> keine Taste; zu große Werte landen über sanitize() ebenfalls bei NONE
			set(raw == Math.rint(raw) ? (int) raw : NONE);
		}
	}
}
