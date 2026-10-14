package dev.salamiclient.modules.visual;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;

/** Salami-Logo + Name im HUD (Standard: oben links, verschiebbar im HUD-Editor). */
public final class WatermarkModule extends HudModule {
	static final Identifier LOGO = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "textures/gui/logo.png");
	static final int LOGO_TEXTURE_SIZE = 64;

	private final BoolSetting showVersion = add(new BoolSetting("show_version", true));

	public WatermarkModule() {
		super("watermark", Category.HUD, true, 0.005, 0.008);
	}

	/** Text hinter dem Namen und Gesamtbreite – einmal pro Tick statt in jedem Frame gebaut. */
	private String rest;
	private int width;

	@Override
	public void onTick() {
		updateText();
	}

	private void updateText() {
		StringBuilder text = new StringBuilder();
		if (showVersion.isOn()) {
			text.append(SalamiClient.version());
		}
		if (SalamiClient.get().theme() == Theme.SALAMI) {
			text.append("  ").append(I18n.get("salami.theme.tagline"));
		}
		rest = text.toString().trim();
		width = 15 + mc().font.width(SalamiClient.NAME) + (rest.isEmpty() ? 0 : 4 + mc().font.width(rest));
	}

	@Override
	public int width() {
		if (rest == null) {
			updateText();
		}
		return width;
	}

	@Override
	public int height() {
		return 12;
	}

	@Override
	protected void render(GuiGraphics graphics, DeltaTracker delta) {
		Theme theme = SalamiClient.get().theme();
		Font font = mc().font;
		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, 0, 0, 0f, 0f, 12, 12,
			LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE);
		int x = 15;
		graphics.drawString(font, SalamiClient.NAME, x, 2, theme.accent(), true);
		x += font.width(SalamiClient.NAME) + 4;
		if (rest == null) {
			updateText();
		}
		if (!rest.isEmpty()) {
			graphics.drawString(font, rest, x, 2, theme.textDim(), true);
		}
	}
}
