package dev.salamiclient.setting;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Basisklasse aller Modul-Einstellungen.
 *
 * <p>Eine Einstellung kennt ihren Wert, ihren Standardwert und kann sich selbst
 * nach JSON (de)serialisieren. Änderungen lösen den Change-Hook aus, über den
 * der ConfigManager vom Speichern erfährt.
 */
public abstract class Setting<T> {
	private final String id;
	private final T defaultValue;
	private T value;
	private final List<Consumer<T>> listeners = new ArrayList<>(1);
	private Runnable changeHook = () -> {};
	private String translationPrefix = "salami.setting";

	protected Setting(String id, T defaultValue) {
		this.id = id;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public final String id() {
		return id;
	}

	public final T get() {
		return value;
	}

	public final T defaultValue() {
		return defaultValue;
	}

	public final void set(T newValue) {
		T sanitized = sanitize(newValue);
		if (Objects.equals(sanitized, value)) {
			return;
		}
		value = sanitized;
		for (Consumer<T> listener : listeners) {
			listener.accept(sanitized);
		}
		changeHook.run();
	}

	public final void reset() {
		set(defaultValue);
	}

	/** Registriert einen Listener, der bei jeder Wertänderung aufgerufen wird. */
	public final Setting<T> onChange(Consumer<T> listener) {
		listeners.add(listener);
		return this;
	}

	/** Wird vom Modul beim Registrieren gesetzt. */
	public final void bind(String translationPrefix, Runnable changeHook) {
		this.translationPrefix = translationPrefix;
		this.changeHook = changeHook;
	}

	public final String translationKey() {
		return translationPrefix + ".setting." + id;
	}

	/** Klemmt/normalisiert Werte; Standard: unverändert. */
	protected T sanitize(T raw) {
		return raw == null ? defaultValue : raw;
	}

	public abstract JsonElement toJson();

	/** Liest einen Wert tolerant ein; ungültige Werte fallen auf den Standard zurück. */
	public abstract void fromJson(JsonElement json);
}
