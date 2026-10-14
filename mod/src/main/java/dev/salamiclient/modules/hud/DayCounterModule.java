package dev.salamiclient.modules.hud;

import dev.salamiclient.module.TextHudModule;
import net.minecraft.client.resources.language.I18n;

/** Day Counter: welcher Minecraft-Tag gerade ist. */
public final class DayCounterModule extends TextHudModule {
	public DayCounterModule() {
		super("day_counter", 0.20, 0.23);
	}

	@Override
	protected String label() {
		return I18n.get("salami.module.day_counter.label");
	}

	@Override
	protected String value() {
		return String.valueOf(mc().level.getDayTime() / 24000L + 1);
	}
}
