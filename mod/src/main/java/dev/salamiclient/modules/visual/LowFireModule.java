package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.NumberSetting;

/** Low Fire: das Feuer im Bild sitzt tiefer und verdeckt weniger Sicht. */
public final class LowFireModule extends Module {
	private static LowFireModule instance;

	private final NumberSetting height = add(new NumberSetting("height", 0.3, 0.05, 0.6, 0.05));

	public LowFireModule() {
		super("low_fire", Category.VISUAL);
		instance = this;
	}

	/** Verschiebung nach unten für {@code ScreenEffectRendererMixin} (0 = Vanilla). */
	public static float offset() {
		return instance != null && instance.isEnabled() ? instance.height.get().floatValue() : 0.0F;
	}
}
