package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;

/** No Hurt Cam: die Kamera wackelt nicht mehr, wenn du Schaden nimmst. */
public final class NoHurtCamModule extends Module {
	private static NoHurtCamModule instance;

	public NoHurtCamModule() {
		super("no_hurt_cam", Category.VISUAL);
		instance = this;
	}

	/** Für {@code GameRendererTweaksMixin}. */
	public static boolean active() {
		return instance != null && instance.isEnabled();
	}
}
