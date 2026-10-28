package dev.salamiclient.modules.visual;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.render.GuiDraw;
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

	/** Text hinter dem Namen und Breiten – einmal pro Tick statt in jedem Frame gebaut. */
	private String rest;
	private int nameWidth;
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
		if (SalamiClient.get().theme().isSalami()) {
			text.append("  ").append(I18n.get("salami.theme.tagline"));
		}
		rest = text.toString().trim();
		nameWidth = mc().font.width(SalamiClient.NAME);
		width = 15 + nameWidth + (rest.isEmpty() ? 0 : 4 + mc().font.width(rest));
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
		Font font = mc().font;
		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, 0, 0, 0f, 0f, 12, 12,
			LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE);
		if (rest == null) {
			updateText();
		}
		int x = 15;
		GuiDraw.text(graphics, font, SalamiClient.NAME, x, 2, accentColor(), shadow());
		x += nameWidth + 4;
		if (!rest.isEmpty()) {
			GuiDraw.text(graphics, font, rest, x, 2, dimColor(), shadow());
		}
	}
}
