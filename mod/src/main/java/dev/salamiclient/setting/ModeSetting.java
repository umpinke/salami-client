package dev.salamiclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Auswahl aus den Konstanten eines Enums. */
public final class ModeSetting<E extends Enum<E>> extends Setting<E> {
	private final E[] values;
	/** Werte, die es (in dieser Minecraft-Version) nicht gibt: nicht wählbar, geladene fallen auf den Standard zurück. */
	private final Set<E> excluded;

	public ModeSetting(String id, E defaultValue) {
		super(id, defaultValue);
		this.values = defaultValue.getDeclaringClass().getEnumConstants();
		this.excluded = EnumSet.noneOf(defaultValue.getDeclaringClass());
	}

	/** Diese Werte nicht anbieten (z. B. das Speer-Kit vor 1.21.11). Der Standardwert bleibt immer wählbar. */
	@SafeVarargs
	public final ModeSetting<E> without(E... values) {
		for (E value : values) {
			if (value != defaultValue()) {
				excluded.add(value);
			}
		}
		if (excluded.contains(get())) {
			reset();
		}
		return this;
	}

	public void cycle(int direction) {
		int step = direction < 0 ? -1 : 1;
		int next = get().ordinal();
		for (int i = 0; i < values.length; i++) {
			next = Math.floorMod(next + step, values.length);
			if (!excluded.contains(values[next])) {
				break;
			}
		}
		set(values[next]);
	}

	@Override
	protected E sanitize(E raw) {
		E value = super.sanitize(raw);
		return excluded != null && excluded.contains(value) ? defaultValue() : value;
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
