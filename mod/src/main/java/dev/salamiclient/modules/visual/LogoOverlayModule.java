package dev.salamiclient.modules.visual;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;

/** PvP-Logo-Overlay: großes Salami-Logo (z. B. für Aufnahmen/Streams), Größe und Deckkraft einstellbar. */
public final class LogoOverlayModule extends HudModule {
	private static final int SIZE = 48;

	private final NumberSetting opacity = add(new NumberSetting("opacity", 0.8, 0.1, 1.0, 0.05));
	private final BoolSetting showName = add(new BoolSetting("show_name", true));

	public LogoOverlayModule() {
		super("logo_overlay", Category.HUD, false, 0.90, 0.02);
	}

	@Override
	public int width() {
		return showName.isOn() ? Math.max(SIZE, mc().font.width(SalamiClient.NAME)) : SIZE;
	}

	@Override
	public int height() {
		return SIZE + (showName.isOn() ? 11 : 0);
	}

	@Override
	protected void render(GuiGraphics graphics, DeltaTracker delta) {
		int alpha = Math.round(opacity.get().floatValue() * 255);
		int x = (width() - SIZE) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, WatermarkModule.LOGO, x, 0, 0f, 0f, SIZE, SIZE,
			WatermarkModule.LOGO_TEXTURE_SIZE, WatermarkModule.LOGO_TEXTURE_SIZE,
			WatermarkModule.LOGO_TEXTURE_SIZE, WatermarkModule.LOGO_TEXTURE_SIZE, ARGB.white(alpha / 255f));
		if (showName.isOn()) {
			int color = ARGB.color(Math.max(alpha, 26), accentColor());
			graphics.drawCenteredString(mc().font, SalamiClient.NAME, width() / 2, SIZE + 2, color);
		}
	}
}
