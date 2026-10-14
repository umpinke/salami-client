package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.render.PostEffects;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.resources.Identifier;

/**
 * Motion Blur: mischt jedes Bild mit dem vorherigen – Bewegungen ziehen Schlieren. Kostet etwas
 * Grafikleistung (drei Bildschirm-Durchgänge), nur solange das Modul an ist.
 */
public final class MotionBlurModule extends Module {
	/** Stufe 1 … 9 = Anteil des vorherigen Bildes 10 … 90 % ({@code post_effect/motion_blur_1..9}). */
	private static final Identifier[] STEPS = new Identifier[10];

	static {
		for (int i = 1; i < STEPS.length; i++) {
			STEPS[i] = PostEffects.id("motion_blur_" + i);
		}
	}

	private final NumberSetting strength = add(new NumberSetting("strength", 5, 1, 9, 1));

	public MotionBlurModule() {
		super("motion_blur", Category.VISUAL);
		PostEffects.register(this::effect);
	}

	private Identifier effect() {
		return isEnabled() ? STEPS[Math.max(1, Math.min(9, strength.intValue()))] : null;
	}
}
