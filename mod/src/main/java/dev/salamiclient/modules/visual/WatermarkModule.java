package dev.salamiclient.modules.visual;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;

/** Salami-Logo + Name oben links im HUD. */
public final class WatermarkModule extends Module {
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "textures/gui/logo.png");
	private static final int LOGO_TEXTURE_SIZE = 64;

	private final BoolSetting showVersion = add(new BoolSetting("show_version", true));
	private final NumberSetting scale = add(new NumberSetting("scale", 1.0, 0.5, 2.0, 0.1));

	public WatermarkModule() {
		super("watermark", Category.VISUAL, true);
	}

	@Override
	public void onHud(GuiGraphics graphics, DeltaTracker delta) {
		Theme theme = SalamiClient.get().theme();
		Font font = mc().font;
		float s = scale.get().floatValue();

		graphics.pose().pushMatrix();
		graphics.pose().translate(4, 4);
		graphics.pose().scale(s, s);

		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, 0, 0, 0f, 0f, 12, 12,
			LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE);

		int x = 15;
		graphics.drawString(font, SalamiClient.NAME, x, 2, theme.accent(), true);
		x += font.width(SalamiClient.NAME) + 4;
		if (showVersion.isOn()) {
			graphics.drawString(font, SalamiClient.version(), x, 2, theme.textDim(), true);
			x += font.width(SalamiClient.version()) + 4;
		}
		if (theme == Theme.SALAMI) {
			graphics.drawString(font, I18n.get("salami.theme.tagline"), x, 2, theme.textDim(), true);
		}

		graphics.pose().popMatrix();
	}
}
