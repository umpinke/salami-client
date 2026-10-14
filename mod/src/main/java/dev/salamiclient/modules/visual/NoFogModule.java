package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.level.material.FogType;

/** No Fog: entfernt den Nebel am Rand der Sichtweite und optional unter Wasser und in Lava. */
public final class NoFogModule extends Module {
	private static final float FAR = 1.0e6F;

	private static NoFogModule instance;

	private final BoolSetting distance = add(new BoolSetting("distance", true));
	private final BoolSetting water = add(new BoolSetting("water", false));
	private final BoolSetting lava = add(new BoolSetting("lava", false));

	public NoFogModule() {
		super("no_fog", Category.VISUAL);
		instance = this;
	}

	/** Für {@code FogRendererMixin}: Nebelgrenzen vor dem Hochladen anpassen. */
	public static void apply(FogData fog, FogType type) {
		if (instance == null || !instance.isEnabled()) {
			return;
		}
		boolean clearEnvironment = switch (type) {
			case WATER -> instance.water.isOn();
			case LAVA -> instance.lava.isOn();
			case ATMOSPHERIC -> instance.distance.isOn();
			default -> false;
		};
		if (clearEnvironment) {
			fog.environmentalStart = FAR;
			fog.environmentalEnd = FAR;
		}
		if (instance.distance.isOn() && type == FogType.ATMOSPHERIC) {
			fog.renderDistanceStart = FAR;
			fog.renderDistanceEnd = FAR;
		}
	}
}
