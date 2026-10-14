package dev.salamiclient.modules.client;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.NumberSetting;

/**
 * Oberfläche: Größe der Salami-HUD-Elemente und der ClickGUI – zusätzlich zur
 * Minecraft-GUI-Skalierung. Ausgeschaltet gilt 1.0.
 */
public final class GuiScaleModule extends Module {
	private static GuiScaleModule instance;

	private final NumberSetting hud = add(new NumberSetting("hud_scale", 1.0, 0.5, 2.0, 0.05));
	private final NumberSetting clickGui = add(new NumberSetting("clickgui_scale", 1.0, 0.5, 2.0, 0.05));

	public GuiScaleModule() {
		super("gui_scale", Category.CLIENT, true);
		instance = this;
	}

	/** Faktor für alle HUD-Elemente (multipliziert mit ihrer eigenen Größe). */
	public static float hudScale() {
		GuiScaleModule module = instance;
		return module != null && module.isEnabled() ? module.hud.get().floatValue() : 1f;
	}

	public static float clickGuiScale() {
		GuiScaleModule module = instance;
		return module != null && module.isEnabled() ? module.clickGui.get().floatValue() : 1f;
	}

	public static GuiScaleModule get() {
		return instance;
	}

	/** ClickGUI-Größe um {@code steps} × 10 % ändern; 0 setzt sie auf 100 % zurück. */
	public void stepClickGui(int steps) {
		if (steps == 0) {
			clickGui.reset();
		} else {
			clickGui.set(clickGui.get() + steps * 0.1);
		}
	}
}
