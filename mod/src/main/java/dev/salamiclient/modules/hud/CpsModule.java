package dev.salamiclient.modules.hud;

import dev.salamiclient.module.TextHudModule;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.util.ClickTracker;

/** CPS: Klicks pro Sekunde (links, optional auch rechts). Text wird pro Tick neu gebaut. */
public final class CpsModule extends TextHudModule {
	private final BoolSetting showRight = add(new BoolSetting("show_right", true));

	public CpsModule() {
		super("cps", 0.005, 0.30);
	}

	@Override
	protected String label() {
		return "CPS";
	}

	@Override
	protected String value() {
		String left = String.valueOf(ClickTracker.left());
		return showRight.isOn() ? left + " | " + ClickTracker.right() : left;
	}
}
