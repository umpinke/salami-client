package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import net.minecraft.client.Minecraft;

/** Clear Background: kein Abdunkeln hinter Inventar und Kisten, optional keine Unschärfe im Pausenmenü. */
public final class ClearBackgroundModule extends Module {
	private static ClearBackgroundModule instance;

	private final BoolSetting inventories = add(new BoolSetting("inventories", true));
	private final BoolSetting menuBlur = add(new BoolSetting("menu_blur", true));

	public ClearBackgroundModule() {
		super("clear_background", Category.VISUAL);
		instance = this;
	}

	/** Für {@code ScreenMixin}: dunklen Verlauf hinter Inventaren weglassen. */
	public static boolean hidesDarkening() {
		return instance != null && instance.isEnabled() && instance.inventories.isOn() && Minecraft.getInstance().level != null;
	}

	/** Für {@code ScreenMixin}: Unschärfe hinter Menüs in der Welt weglassen. */
	public static boolean hidesBlur() {
		return instance != null && instance.isEnabled() && instance.menuBlur.isOn() && Minecraft.getInstance().level != null;
	}
}
