package dev.salamiclient.module;

import dev.salamiclient.setting.KeybindSetting;
import dev.salamiclient.setting.Setting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Basisklasse aller Salami-Module.
 *
 * <p>Ein Modul wird ausschließlich über den {@link ModuleManager} an- und
 * ausgeschaltet (der prüft z. B. Safe Mode). Nur aktive Module bekommen
 * {@link #onTick()} und {@link #onHud} – deaktivierte Module kosten keine Leistung.
 */
public abstract class Module {
	private final String id;
	private final Category category;
	private final boolean enabledByDefault;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean enabled;
	private Runnable changeHook = () -> {};

	/** Jedes Modul kann per eigener Taste umgeschaltet werden. */
	public final KeybindSetting keybind;

	protected Module(String id, Category category) {
		this(id, category, false);
	}

	protected Module(String id, Category category, boolean enabledByDefault) {
		this.id = id;
		this.category = category;
		this.enabledByDefault = enabledByDefault;
		this.keybind = add(new KeybindSetting("keybind", KeybindSetting.NONE));
	}

	/** Registriert eine Einstellung (Reihenfolge = Anzeige in der ClickGUI). */
	protected final <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		setting.bind(translationKey(), () -> changeHook.run());
		return setting;
	}

	protected static Minecraft mc() {
		return Minecraft.getInstance();
	}

	// --- Lebenszyklus (von Unterklassen überschreibbar) ---

	protected void onEnable() {}

	protected void onDisable() {}

	/** Pro Client-Tick, nur wenn aktiv und eine Welt geladen ist. */
	public void onTick() {}

	/** HUD-Zeichnen, nur wenn aktiv. */
	public void onHud(GuiGraphics graphics, DeltaTracker delta) {}

	// --- Zustand ---

	public final boolean isEnabled() {
		return enabled;
	}

	/** Nur für den ModuleManager – Module selbst nutzen {@code ModuleManager#setEnabled}. */
	final void applyEnabled(boolean value) {
		if (enabled == value) {
			return;
		}
		enabled = value;
		if (value) {
			onEnable();
		} else {
			onDisable();
		}
	}

	final void bindChangeHook(Runnable hook) {
		this.changeHook = hook;
	}

	public final String id() {
		return id;
	}

	public final Category category() {
		return category;
	}

	public final boolean enabledByDefault() {
		return enabledByDefault;
	}

	public final List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	public final String translationKey() {
		return "salami.module." + id;
	}

	public final String descriptionKey() {
		return translationKey() + ".desc";
	}
}
