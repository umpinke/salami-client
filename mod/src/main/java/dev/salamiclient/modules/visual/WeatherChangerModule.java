package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.ModeSetting;

/** Weather Changer: eigenes Wetter – nur bei dir. Klare Sicht trotz Regen oder Regen zur Stimmung. */
public final class WeatherChangerModule extends Module {
	public enum Weather {
		CLEAR,
		RAIN,
		THUNDER
	}

	private static WeatherChangerModule instance;

	private final ModeSetting<Weather> weather = add(new ModeSetting<>("weather", Weather.CLEAR));

	public WeatherChangerModule() {
		super("weather_changer", Category.VISUAL);
		instance = this;
	}

	/** Für {@code LevelMixin}: Regenstärke (0..1) der Client-Welt. */
	public static float rain(float vanilla) {
		if (instance == null || !instance.isEnabled()) {
			return vanilla;
		}
		return instance.weather.get() == Weather.CLEAR ? 0.0F : 1.0F;
	}

	/** Für {@code LevelMixin}: Gewitterstärke (0..1) der Client-Welt. */
	public static float thunder(float vanilla) {
		if (instance == null || !instance.isEnabled()) {
			return vanilla;
		}
		return instance.weather.get() == Weather.THUNDER ? 1.0F : 0.0F;
	}
}
