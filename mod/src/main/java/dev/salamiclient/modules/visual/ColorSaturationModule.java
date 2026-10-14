package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.render.PostEffects;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.resources.Identifier;

/** Color Saturation: Farben kräftiger oder blasser (Nachbearbeitung, nur das Weltbild – HUD bleibt normal). */
public final class ColorSaturationModule extends Module {
	/** Vorbereitete Stufen 0.0 … 2.0 in 0,25er-Schritten ({@code post_effect/saturation_0..8}). */
	private static final Identifier[] STEPS = new Identifier[9];

	static {
		for (int i = 0; i < STEPS.length; i++) {
			STEPS[i] = PostEffects.id("saturation_" + i);
		}
	}

	private final NumberSetting saturation = add(new NumberSetting("saturation", 1.5, 0.0, 2.0, 0.25));

	public ColorSaturationModule() {
		super("color_saturation", Category.VISUAL);
		PostEffects.register(this::effect);
	}

	private Identifier effect() {
		if (!isEnabled()) {
			return null;
		}
		int step = (int) Math.round(saturation.get() / 0.25);
		if (step == 4) {
			return null; // 1.0 = unverändert, Durchgang sparen
		}
		return STEPS[Math.max(0, Math.min(8, step))];
	}
}
