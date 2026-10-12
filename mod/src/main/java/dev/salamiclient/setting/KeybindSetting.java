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

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(json.getAsInt());
		}
	}
}
