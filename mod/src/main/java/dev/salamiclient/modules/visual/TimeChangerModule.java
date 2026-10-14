package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.NumberSetting;

/** Time Changer: eigene Tageszeit – nur bei dir, der Server und andere Spieler merken nichts. */
public final class TimeChangerModule extends Module {
	private static TimeChangerModule instance;

	/** In Minecraft-Ticks: 0 = Morgen, 6000 = Mittag, 13000 = Abend, 18000 = Mitternacht. */
	private final NumberSetting time = add(new NumberSetting("time", 6000, 0, 23999, 250));

	public TimeChangerModule() {
		super("time_changer", Category.VISUAL);
		instance = this;
	}

	/** Für {@code ClientLevelDataMixin}: Tageszeit für Himmel, Licht und Sonne. */
	public static long dayTime(long vanilla) {
		if (instance == null || !instance.isEnabled()) {
			return vanilla;
		}
		// Tag-Zähler behalten (Mondphasen), nur die Uhrzeit ersetzen
		return Math.floorDiv(vanilla, 24000L) * 24000L + instance.time.get().longValue();
	}
}
