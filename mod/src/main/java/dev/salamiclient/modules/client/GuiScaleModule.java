package dev.salamiclient.modules.client;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudStyle;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.theme.Theme;

/**
 * Oberfläche: Größe und Stil der Salami-HUD-Elemente, Größe der Menüs – zusätzlich zur
 * Minecraft-GUI-Skalierung – und auf Wunsch eigene Farben für ClickGUI, Mods-Menü und
 * HUD (Akzent, Kopfzeilen, Hintergrund). Ausgeschaltet gilt 1.0, der Standard-HUD-Stil und das
 * normale Theme.
 */
public final class GuiScaleModule extends Module {
	/** Aussehen aller HUD-Elemente ohne eigenen Stil. */
	public enum HudLook {
		/** Weich abgerundete, halbtransparente Flächen, ruhige Bezeichnungen, kein Textschatten. */
		CLEAN,
		/** Nur Text, kein Hintergrund. */
		MINIMAL,
		/** Eckige Kästen und Bezeichnungen in der Akzentfarbe (früheres Aussehen). */
		CLASSIC
	}

	public enum Shadow {
		/** Schatten nur dort, wo kein Hintergrund liegt (sonst ist Text auf hellem Himmel schwer lesbar). */
		AUTO,
		ON,
		OFF
	}

	private static GuiScaleModule instance;

	/** Standard-Stil, wenn das Modul aus ist: wie die Voreinstellungen unten. */
	private static final HudStyle DEFAULT_STYLE = resolve(HudLook.CLEAN, 45, 3, false, Shadow.AUTO, false);

	private final NumberSetting hud = add(new NumberSetting("hud_scale", 1.0, 0.5, 3.0, 0.05));
	private final ModeSetting<HudLook> hudLook = add(new ModeSetting<>("hud_style", HudLook.CLEAN));
	private final NumberSetting hudOpacity = add(new NumberSetting("hud_opacity", 45, 0, 100, 5));
	private final NumberSetting hudRadius = add(new NumberSetting("hud_radius", 3, 0, 6, 1));
	private final BoolSetting hudBorder = add(new BoolSetting("hud_border", false));
	private final ModeSetting<Shadow> hudShadow = add(new ModeSetting<>("hud_shadow", Shadow.AUTO));
	private final BoolSetting hudAccentLabels = add(new BoolSetting("hud_accent_labels", false));
	private final BoolSetting hudSnap = add(new BoolSetting("hud_snap", true));
	private final NumberSetting clickGui = add(new NumberSetting("clickgui_scale", 1.0, 0.5, 2.0, 0.05));
	private final BoolSetting customColors = add(new BoolSetting("custom_colors", false));
	private final ColorSetting accent = add(new ColorSetting("accent_color", 0xFFE0454A));
	private final ColorSetting header = add(new ColorSetting("header_color", 0xFF1E1E24));
	private final ColorSetting panel = add(new ColorSetting("panel_color", 0xE0141418));

	{
		hudOpacity.showIf(() -> hudLook.get() != HudLook.MINIMAL);
		hudRadius.showIf(() -> hudLook.get() == HudLook.CLEAN);
		hudBorder.showIf(() -> hudLook.get() != HudLook.MINIMAL);
		hudAccentLabels.showIf(() -> hudLook.get() != HudLook.CLASSIC);
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

	/** Gemeinsamer HUD-Stil für alle Elemente ohne eigenen Stil. */
	public static HudStyle hudStyle() {
		GuiScaleModule module = instance;
		if (module == null || !module.isEnabled()) {
			return DEFAULT_STYLE;
		}
		// Wird pro Element mehrmals im Frame gefragt: nur neu bauen, wenn sich eine Einstellung geändert hat
		int key = module.hudLook.get().ordinal() | module.hudOpacity.intValue() << 2 | module.hudRadius.intValue() << 10
			| (module.hudBorder.isOn() ? 1 << 14 : 0) | module.hudShadow.get().ordinal() << 15 | (module.hudAccentLabels.isOn() ? 1 << 17 : 0);
		if (module.cachedStyle == null || key != module.cachedStyleKey) {
			module.cachedStyleKey = key;
			module.cachedStyle = resolve(module.hudLook.get(), module.hudOpacity.intValue(), module.hudRadius.intValue(),
				module.hudBorder.isOn(), module.hudShadow.get(), module.hudAccentLabels.isOn());
		}
		return module.cachedStyle;
	}

	private HudStyle cachedStyle;
	private int cachedStyleKey;

	private static HudStyle resolve(HudLook look, int opacityPercent, int radius, boolean border, Shadow shadow, boolean accentLabels) {
		int alpha = Math.round(opacityPercent * 2.55F);
		int background = look == HudLook.MINIMAL || alpha == 0 ? 0 : (alpha << 24) | HudStyle.BASE;
		int corner = look == HudLook.CLEAN ? radius : 0;
		int line = border && look != HudLook.MINIMAL ? 0x30FFFFFF : 0;
		boolean textShadow = switch (shadow) {
			case ON -> true;
			case OFF -> false;
			case AUTO -> background == 0 || look == HudLook.CLASSIC;
		};
		int padding = switch (look) {
			case CLEAN -> 4;
			case CLASSIC -> 2;
			case MINIMAL -> 0;
		};
		return new HudStyle(background, corner, line, textShadow, accentLabels || look == HudLook.CLASSIC, padding);
	}

	/** HUD-Elemente im Editor an Rändern, Mitte und anderen Elementen einrasten lassen. */
	public static boolean hudSnap() {
		GuiScaleModule module = instance;
		return module == null || !module.isEnabled() || module.hudSnap.isOn();
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

	/** HUD-Größe um {@code steps} × 10 % ändern; 0 setzt sie auf 100 % zurück. */
	public void stepHud(int steps) {
		if (steps == 0) {
			hud.reset();
		} else {
			hud.set(hud.get() + steps * 0.1);
		}
	}

	/** Nächster HUD-Stil (Clean → Minimal → Klassisch → Clean). */
	public void cycleHudLook(int direction) {
		hudLook.cycle(direction);
	}

	public HudLook hudLook() {
		return hudLook.get();
	}

	public void toggleHudSnap() {
		hudSnap.toggle();
	}
}
