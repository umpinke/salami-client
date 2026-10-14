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
		if (instance == null || !instance.isEnabled()) {
			return vanilla;
		}
		return Math.max(vanilla, instance.brightness.get().floatValue());
	}
}
