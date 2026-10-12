package dev.salamiclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

/** Auswahl aus den Konstanten eines Enums. */
public final class ModeSetting<E extends Enum<E>> extends Setting<E> {
	private final E[] values;

	public ModeSetting(String id, E defaultValue) {
		super(id, defaultValue);
		this.values = defaultValue.getDeclaringClass().getEnumConstants();
	}

	public void cycle(int direction) {
		int next = Math.floorMod(get().ordinal() + direction, values.length);
		set(values[next]);
	}

	/** Übersetzungsschlüssel für den aktuell gewählten Wert. */
	public String valueTranslationKey() {
		return translationKey() + "." + get().name().toLowerCase(Locale.ROOT);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get().name());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json == null || !json.isJsonPrimitive()) {
			return;
		}
		String name = json.getAsString();
		for (E value : values) {
			if (value.name().equalsIgnoreCase(name)) {
				set(value);
				return;
			}
		}
	}
}
