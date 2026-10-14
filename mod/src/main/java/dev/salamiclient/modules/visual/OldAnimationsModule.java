package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;

/**
 * Old Animations: Hand und Item senken sich nach einem Schlag nicht mehr ab (wie vor 1.9),
 * optional auch kein Ab- und Auftauchen beim Wechseln des Items. Nur Optik.
 */
public final class OldAnimationsModule extends Module {
	private static OldAnimationsModule instance;

	private final BoolSetting noCooldownDip = add(new BoolSetting("no_cooldown_dip", true));
	private final BoolSetting instantSwitch = add(new BoolSetting("instant_switch", false));

	public OldAnimationsModule() {
		super("old_animations", Category.VISUAL);
		instance = this;
	}

	private static boolean on() {
		return instance != null && instance.isEnabled();
	}

	/** Für {@code ItemInHandRendererMixin}: Hand bleibt oben, solange dasselbe Item gehalten wird. */
	public static boolean noCooldownDip() {
		return on() && instance.noCooldownDip.isOn();
	}

	/** Für {@code ItemInHandRendererMixin}: neues Item sofort zeigen, ohne Absenken. */
	public static boolean instantSwitch() {
		return on() && instance.instantSwitch.isOn();
	}
}
