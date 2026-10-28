package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.NumberSetting;

/** FullBright: hebt die Helligkeit über das Vanilla-Maximum an (nur Optik, nur bei dir). */
public final class FullBrightModule extends Module {
	private static FullBrightModule instance;

	private final NumberSetting brightness = add(new NumberSetting("brightness", 10, 1, 15, 0.5));

	public FullBrightModule() {
		super("fullbright", Category.VISUAL);
		instance = this;
	}

	/** Gamma für {@code LightTextureMixin}: nie dunkler als der eigene Optionswert. */
	public static float gamma(float vanilla) {
		float own = override();
		return own < 0 ? vanilla : Math.max(vanilla, own);
	}

	/** Eigener Gamma-Wert oder -1, wenn aus – {@code LightTextureMixin} erkennt daran Änderungen. */
	public static float override() {
		return instance == null || !instance.isEnabled() ? -1 : instance.brightness.get().floatValue();
	}
}
