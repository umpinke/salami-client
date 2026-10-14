package dev.salamiclient.modules.client;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.theme.Theme;

/**
 * Oberfläche: Größe der Salami-HUD-Elemente und der ClickGUI – zusätzlich zur
 * Minecraft-GUI-Skalierung – und auf Wunsch eigene Farben für ClickGUI, Mods-Menü und
 * HUD (Akzent, Kopfzeilen, Hintergrund). Ausgeschaltet gilt 1.0 und das normale Theme.
 */
public final class GuiScaleModule extends Module {
	private static GuiScaleModule instance;

	private final NumberSetting hud = add(new NumberSetting("hud_scale", 1.0, 0.5, 2.0, 0.05));
	private final NumberSetting clickGui = add(new NumberSetting("clickgui_scale", 1.0, 0.5, 2.0, 0.05));
	private final BoolSetting customColors = add(new BoolSetting("custom_colors", false));
	private final ColorSetting accent = add(new ColorSetting("accent_color", 0xFFE0454A));
	private final ColorSetting header = add(new ColorSetting("header_color", 0xFF1E1E24));
	private final ColorSetting panel = add(new ColorSetting("panel_color", 0xE0141418));

	{
		accent.showIf(customColors::isOn);
		header.showIf(customColors::isOn);
		panel.showIf(customColors::isOn);
	}

	private Theme lastBase;
	private int lastAccent;
	private int lastHeader;
	private int lastPanel;
	private Theme lastCustom;

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

	/** Theme mit den eigenen Farben, wenn eingeschaltet (sonst unverändert). */
	public static Theme customize(Theme base) {
		GuiScaleModule module = instance;
		if (module == null || !module.isEnabled() || !module.customColors.isOn()) {
			return base;
		}
		int a = module.accent.argb();
		int h = module.header.argb();
		int p = module.panel.argb();
		if (base != module.lastBase || a != module.lastAccent || h != module.lastHeader || p != module.lastPanel) {
			module.lastBase = base;
			module.lastAccent = a;
			module.lastHeader = h;
			module.lastPanel = p;
			module.lastCustom = base.withColors(a, h, p);
		}
		return module.lastCustom;
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
