package dev.salamiclient.modules.visual;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.setting.BoolSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;

/**
 * Salami-Logo + Name im HUD (Standard: oben links, verschiebbar im HUD-Editor). Schlicht: Name in der
 * Textfarbe, Version und Slogan nur auf Wunsch.
 */
public final class WatermarkModule extends HudModule {
	static final Identifier LOGO = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "textures/gui/logo.png");
	static final int LOGO_TEXTURE_SIZE = 64;
	private static final int LOGO_SIZE = 10;

	private final BoolSetting logo = add(new BoolSetting("logo", true));
	private final BoolSetting showVersion = add(new BoolSetting("version", false));
	private final BoolSetting tagline = add(new BoolSetting("tagline", false));

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
			text.append(SalamiClient.shortVersion());
		}
		if (tagline.isOn() && SalamiClient.get().theme().isSalami()) {
			text.append("  ").append(I18n.get("salami.theme.tagline"));
		}
		rest = text.toString().trim();
		nameWidth = mc().font.width(SalamiClient.NAME);
		width = textX() + nameWidth - 1 + (rest.isEmpty() ? 0 : 5 + mc().font.width(rest));
	}

	private int textX() {
		return logo.isOn() ? LOGO_SIZE + 4 : 0;
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
		return LOGO_SIZE;
	}

	@Override
	protected void render(GuiGraphics graphics, DeltaTracker delta) {
		Font font = mc().font;
		if (rest == null) {
			updateText();
		}
		if (logo.isOn()) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, 0, 0, 0f, 0f, LOGO_SIZE, LOGO_SIZE,
				LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE);
		}
		int x = textX();
		GuiDraw.text(graphics, font, SalamiClient.NAME, x, 1, textColor(), shadow());
		x += nameWidth + 4;
		if (!rest.isEmpty()) {
			GuiDraw.text(graphics, font, rest, x, 1, labelColor(), shadow());
		}
	}
}
