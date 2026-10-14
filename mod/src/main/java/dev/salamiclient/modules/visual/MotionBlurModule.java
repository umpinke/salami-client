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
	private final NumberSetting strength = add(new NumberSetting("strength", 5, 1, 9, 1));

	public MotionBlurModule() {
		super("motion_blur", Category.VISUAL);
		PostEffects.register(this::effect);
	}

	private Identifier effect() {
		return isEnabled() ? PostEffects.id("motion_blur_" + strength.intValue()) : null;
	}
}
