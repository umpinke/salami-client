package dev.salamiclient.modules.hud;

import dev.salamiclient.module.TextHudModule;
import dev.salamiclient.setting.BoolSetting;
import net.minecraft.client.resources.language.I18n;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Clock: echte Uhrzeit auf deinem PC. */
public final class ClockModule extends TextHudModule {
	private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter H24_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss");

	private final BoolSetting seconds = add(new BoolSetting("seconds", false));

	public ClockModule() {
		super("clock", 0.20, 0.11);
	}

	@Override
	protected String label() {
		return I18n.get("salami.module.clock.label");
	}

	@Override
	protected String value() {
		return LocalTime.now().format(seconds.isOn() ? H24_SECONDS : H24);
	}
}
