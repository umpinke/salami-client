package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.Minecraft;

/**
 * FOV Changer: eigenes Sichtfeld (auch über die 110° von Vanilla hinaus) und optional ein
 * festes Sichtfeld ohne Zoomen beim Sprinten oder durch Speed.
 */
public final class FovChangerModule extends Module {
	private static FovChangerModule instance;

	private final NumberSetting fov = add(new NumberSetting("fov", 90, 30, 150, 1));
	private final BoolSetting staticFov = add(new BoolSetting("static", false));

	public FovChangerModule() {
		super("fov_changer", Category.VISUAL);
		instance = this;
	}

	/**
	 * Für {@code GameRendererTweaksMixin}. {@code fov} ist das fertige Vanilla-Sichtfeld; nur das
	 * Spiel-Sichtfeld wird ersetzt ({@code useFovSetting}), nicht das der Hand.
	 */
	public static float apply(float fov, boolean useFovSetting) {
		if (instance == null || !instance.isEnabled() || !useFovSetting) {
			return fov;
		}
		float base = Minecraft.getInstance().options.fov().get().floatValue();
		float wanted = instance.fov.get().floatValue();
		if (instance.staticFov.isOn() || base <= 0) {
			return wanted;
		}
		// Sprint-, Speed- und Unterwasser-Effekte bleiben anteilig erhalten
		return wanted * (fov / base);
	}
}
