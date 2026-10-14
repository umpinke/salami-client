package dev.salamiclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/** GLFW-Tastencode; {@link #NONE} = keine Taste. */
public final class KeybindSetting extends Setting<Integer> {
	public static final int NONE = GLFW.GLFW_KEY_UNKNOWN;

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

	/** Nur gültige GLFW-Tastencodes (Leertaste bis {@code GLFW_KEY_LAST}); alles andere = keine Taste. */
	@Override
	protected Integer sanitize(Integer raw) {
		if (raw == null) {
			return defaultValue();
		}
		return raw < GLFW.GLFW_KEY_SPACE || raw > GLFW.GLFW_KEY_LAST ? NONE : raw;
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
