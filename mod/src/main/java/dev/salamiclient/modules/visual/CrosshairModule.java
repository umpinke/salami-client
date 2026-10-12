package dev.salamiclient.modules.visual;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Custom Crosshair: ersetzt das Vanilla-Fadenkreuz (Kreuz, Punkt, Kreis oder
 * Salamischeibe), mit Farbe, Größe, Abstand und Dicke.
 * Eingehängt über {@code HudElementRegistry.replaceElement(CROSSHAIR)}.
 */
public final class CrosshairModule extends Module {
	public enum Style { CROSS, DOT, CIRCLE, SALAMI }

	private final ModeSetting<Style> style = add(new ModeSetting<>("style", Style.CROSS));
	private final ColorSetting color = add(new ColorSetting("color", 0xFFFFFFFF));
	private final BoolSetting themeColor = add(new BoolSetting("theme_color", false));
	private final NumberSetting size = add(new NumberSetting("size", 5, 1, 20, 1));
	private final NumberSetting gap = add(new NumberSetting("gap", 2, 0, 10, 1));
	private final NumberSetting thickness = add(new NumberSetting("thickness", 1, 1, 4, 1));
	private final BoolSetting centerDot = add(new BoolSetting("center_dot", false));
	private final BoolSetting outline = add(new BoolSetting("outline", true));

	public CrosshairModule() {
		super("crosshair", Category.VISUAL);
	}

	public void render(GuiGraphics g) {
		int argb = themeColor.isOn() ? SalamiClient.get().theme().accent() : color.argb();
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		int s = size.intValue();
		int gp = gap.intValue();
		int t = thickness.intValue();
		int half = t / 2;

		switch (style.get()) {
			case CROSS -> {
				rect(g, cx - gp - s, cy - half, cx - gp, cy - half + t, argb);
				rect(g, cx + gp + 1, cy - half, cx + gp + 1 + s, cy - half + t, argb);
				rect(g, cx - half, cy - gp - s, cx - half + t, cy - gp, argb);
				rect(g, cx - half, cy + gp + 1, cx - half + t, cy + gp + 1 + s, argb);
			}
			case DOT -> rect(g, cx - half - 1, cy - half - 1, cx - half + t + 1, cy - half + t + 1, argb);
			case CIRCLE -> {
				int r = s + gp;
				for (int i = 0; i < 48; i++) {
					double a = Math.PI * 2 * i / 48;
					int px = cx + (int) Math.round(Math.cos(a) * r);
					int py = cy + (int) Math.round(Math.sin(a) * r);
					g.fill(px - half, py - half, px - half + t, py - half + t, argb);
				}
			}
			case SALAMI -> {
				int d = Math.max(6, s * 2);
				g.blit(RenderPipelines.GUI_TEXTURED, WatermarkModule.LOGO, cx - d / 2, cy - d / 2, 0f, 0f, d, d,
					WatermarkModule.LOGO_TEXTURE_SIZE, WatermarkModule.LOGO_TEXTURE_SIZE,
					WatermarkModule.LOGO_TEXTURE_SIZE, WatermarkModule.LOGO_TEXTURE_SIZE);
			}
		}
		if (centerDot.isOn() && style.get() != Style.DOT) {
			g.fill(cx, cy, cx + 1, cy + 1, argb);
		}
	}

	/** Rechteck mit optionaler dunkler Kontur für Sichtbarkeit auf hellem Grund. */
	private void rect(GuiGraphics g, int x1, int y1, int x2, int y2, int argb) {
		if (outline.isOn()) {
			g.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xA0000000);
		}
		g.fill(x1, y1, x2, y2, argb);
	}
}
